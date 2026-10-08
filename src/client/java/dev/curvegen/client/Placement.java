package dev.curvegen.client;

import dev.curvegen.core.CommandReplies;
import dev.curvegen.core.LastPlacement;
import dev.curvegen.net.PlaceBlocksPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Puts blocks into the world, takes the last placement back and puts it there again. The server does it when it has
 * the mod; otherwise the client sends {@code /setblock} commands and keeps their replies out of chat. The in-world
 * editor ({@link dev.curvegen.client.edit.Editor}) decides what to place.
 */
public final class Placement {
    private Placement() {}

    private static final int COMMANDS_PER_TICK = 40;

    /**
     * A placement: the states set at each position and the ones that were there before. {@code nearby} holds blocks
     * next to it that could break because of it, such as a torch on a carved wall or sand above a carved hole. Undo
     * puts back the ones that are gone.
     */
    private record Placed(List<BlockPos> pos, List<BlockState> states, List<BlockState> old, Map<BlockPos, BlockState> nearby) {}
    private static final LastPlacement<Placed> LAST = new LastPlacement<>();
    /** A cap on a placement's nearby blocks, so one beside a huge field of plants stays cheap. */
    private static final int NEARBY_LIMIT = 20000;

    /** A {@code /setblock} waiting to be sent to a server without the mod. */
    private record Command(BlockPos pos, String text) {}
    private static final ArrayDeque<Command> commandQueue = new ArrayDeque<>();
    private static final CommandReplies REPLIES = new CommandReplies();
    /** Set while the mod sends a command of its own. */
    private static boolean sending;

    /** Where the shape may put a block when "Replace" is off: air and things like grass, water or snow layers. */
    private static boolean free(BlockState current) { return current.isAir() || current.canBeReplaced(); }

    public static boolean canPlace(Minecraft mc) {
        return mc.player != null && mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static void tick(Minecraft mc) {
        if (mc.player != null && mc.getConnection() != null)
            for (int i = 0; i < COMMANDS_PER_TICK && !commandQueue.isEmpty(); i++) {
                Command c = commandQueue.poll();
                REPLIES.sent(c.pos.getX(), c.pos.getY(), c.pos.getZ());
                sending = true;
                try { mc.getConnection().sendCommand(c.text); } finally { sending = false; }
            }
        REPLIES.tick();
    }

    /**
     * Each loader calls this for every command the client sends to the server, with its text without the slash, so
     * the replies to the player's own aren't taken for the mod's.
     */
    public static void commandSent(String command) {
        if (!sending) REPLIES.other(command);
    }

    /**
     * Whether to keep a message from the server out of chat, because it answers one of the mod's own
     * {@code /setblock} commands. Each loader asks this for every system message. A success and "could not set the
     * block" are hidden. Another error, such as having no permission, shows the first time.
     */
    public static boolean hidesFeedback(Component message, boolean overlay) { return !overlay && hides(REPLIES, message); }

    /** Whether {@code replies} hides a message, read the way the game builds a command's success and its errors. */
    static boolean hides(CommandReplies replies, Component message) {
        if (message.getContents() instanceof TranslatableContents t) {
            int[] at = position(t);
            return message.getSiblings().isEmpty() && at != null && replies.hidesSuccess(t.getKey(), at[0], at[1], at[2]);
        }
        // An error comes wrapped in an empty red line.
        if (!empty(message) || message.getSiblings().size() != 1) return false;
        Component error = message.getSiblings().get(0);
        if (error.getContents() instanceof TranslatableContents t) return error.getSiblings().isEmpty() && replies.hidesFailure(t.getKey());
        // A syntax error has a second line, wrapped the same way: the command, then "<--[HERE]".
        List<Component> parts = error.getSiblings();
        return empty(error) && !parts.isEmpty() && parts.get(parts.size() - 1).getContents() instanceof TranslatableContents t
                && t.getKey().equals(CommandReplies.HERE) && error.getStyle().getClickEvent() instanceof ClickEvent.SuggestCommand(String command)
                && command.startsWith("/setblock ") && replies.hidesFailure(CommandReplies.HERE);
    }

    private static boolean empty(Component c) { return c.getContents() instanceof PlainTextContents plain && plain.text().isEmpty(); }

    /** The position a "Changed the block at x, y, z" names, or null. */
    private static int[] position(TranslatableContents t) {
        if (!t.getKey().equals(CommandReplies.SUCCESS) || t.getArgs().length != 3) return null;
        int[] at = new int[3];
        try {
            for (int k = 0; k < 3; k++)
                at[k] = t.getArgs()[k] instanceof Number n ? n.intValue()
                        : Integer.parseInt(t.getArgs()[k] instanceof Component c ? c.getString() : String.valueOf(t.getArgs()[k]));
        } catch (NumberFormatException _) {
            return null;
        }
        return at;
    }

    /** Sets {@code states} at {@code pos} and remembers {@code old}, the states there now, for undo. */
    public static void place(Minecraft mc, List<BlockPos> pos, List<BlockState> states, List<BlockState> old) {
        place(new Placed(pos, states, old, dependents(mc, pos)), "placing ");
    }

    private static void place(Placed p, String doing) {
        LAST.placed(p);
        send(p.pos, p.states, false, doing);
    }

    /** Whether a block could break or fall when a block beside it changes: anything that isn't a plain full block. */
    private static boolean needsSupport(Minecraft mc, BlockPos p, BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return false;
        return state.getBlock() instanceof FallingBlock || state.getBlock() instanceof SnowLayerBlock   // eight layers make a full block
                || !state.isCollisionShapeFullBlock(mc.level, p);
    }

    /**
     * The blocks around {@code changed} that need support, with their current states. From each one it also follows
     * the column up and down, for stacks such as sand, sugar cane and vines.
     */
    private static Map<BlockPos, BlockState> dependents(Minecraft mc, List<BlockPos> changed) {
        Set<BlockPos> inPlacement = new HashSet<>(changed);
        Map<BlockPos, BlockState> found = new LinkedHashMap<>();
        Predicate<BlockPos> add = p -> {
            if (found.size() >= NEARBY_LIMIT || inPlacement.contains(p) || found.containsKey(p)) return false;
            BlockState state = mc.level.getBlockState(p);
            if (!needsSupport(mc, p, state)) return false;
            found.put(p, state);
            return true;
        };
        for (BlockPos c : changed)
            for (Direction d : Direction.values()) {
                BlockPos first = c.relative(d);
                if (!add.test(first)) continue;
                for (Direction column : d.getAxis() == Direction.Axis.Y ? new Direction[]{d} : new Direction[]{Direction.UP, Direction.DOWN}) {
                    BlockPos p = first.relative(column);
                    for (int i = 0; i < 64 && add.test(p); i++) p = p.relative(column);
                }
            }
        return found;
    }

    /**
     * Says so and returns false when the player has lost the operator rights a placement needs. The server would
     * refuse anyway, and one without the mod kicks a player who sends it that many commands it won't run.
     */
    private static boolean allowed(Minecraft mc, String doing) {
        if (canPlace(mc)) return true;
        say(Component.literal(doing + " a placement needs operator permissions.").withStyle(ChatFormatting.RED));
        return false;
    }

    public static void undo() {
        Minecraft mc = Minecraft.getInstance();
        if (!allowed(mc, "Undoing")) return;
        Placed last = mc.level == null ? null : LAST.undo();
        if (last == null) { say(Component.literal("Nothing to undo.")); return; }
        List<BlockPos> pos = new ArrayList<>(last.pos);
        List<BlockState> states = new ArrayList<>(last.old);
        // A nearby block is only put back if its spot is empty now, so anything built there since is left alone.
        last.nearby.forEach((p, state) -> {
            if (free(mc.level.getBlockState(p))) { pos.add(p); states.add(state); }
        });
        send(pos, states, true, "undoing ");
    }

    /**
     * Puts back the placement that undo took away, so undo can take it back again. The blocks around it that need
     * support are looked for again, and the ones the first placement found are kept: one the placement broke may not
     * be back yet when this runs straight after an undo.
     */
    public static void redo() {
        Minecraft mc = Minecraft.getInstance();
        if (!allowed(mc, "Redoing")) return;
        Placed last = mc.level == null ? null : LAST.redo();
        if (last == null) { say(Component.literal("Nothing to redo.")); return; }
        Map<BlockPos, BlockState> nearby = new LinkedHashMap<>(last.nearby);
        dependents(mc, last.pos).forEach(nearby::putIfAbsent);
        place(new Placed(last.pos, last.states, last.old, nearby), "redoing ");
    }

    private static void send(List<BlockPos> pos, List<BlockState> states, boolean undo, String doing) {
        if (pos.isEmpty()) return;
        if (CurveGenClient.platform.canSendToServer()) {
            BlockPos origin = pos.get(0);
            int n = pos.size();
            for (int start = 0; start < n; start += PlaceBlocksPayload.MAX_PER_BATCH) {
                int end = Math.min(n, start + PlaceBlocksPayload.MAX_PER_BATCH);
                int[] off = new int[(end - start) * 3], st = new int[end - start];
                for (int i = start; i < end; i++) {
                    BlockPos p = pos.get(i);
                    off[3 * (i - start)] = p.getX() - origin.getX();
                    off[3 * (i - start) + 1] = p.getY() - origin.getY();
                    off[3 * (i - start) + 2] = p.getZ() - origin.getZ();
                    st[i - start] = Block.getId(states.get(i));
                }
                CurveGenClient.platform.sendToServer(new PlaceBlocksPayload(origin, off, st, end == n, n, undo));
            }
        } else {
            // The server doesn't have the mod: fall back to /setblock, which also needs operator rights.
            for (int i = 0; i < pos.size(); i++) {
                BlockPos p = pos.get(i);
                // Undo uses strict mode, which sets the block without block updates, as the server-side handler does first.
                // Commands can't run the handler's second step, so blocks around the restored ones keep their state.
                commandQueue.add(new Command(p, "setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " "
                        + BlockStateParser.serialize(states.get(i)) + (undo ? " strict" : "")));
            }
            int seconds = (int) Math.ceil(pos.size() / (COMMANDS_PER_TICK * 20.0));
            say(Component.literal("No server mod: " + doing + pos.size()
                    + " blocks with /setblock, about " + seconds + " s."));
        }
    }

    /**
     * Shows a message from the mod itself above the hotbar, and lets the narrator read it. It's one line that doesn't
     * wrap, so keep it short enough for a 427 px wide screen.
     */
    public static void say(Component t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.chatListener().handleOverlay(t);
    }
}
