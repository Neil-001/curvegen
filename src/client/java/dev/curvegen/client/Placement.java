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

public final class Placement {
    private Placement() {}

    private static final int COMMANDS_PER_TICK = 40;

    private record Placed(List<BlockPos> pos, List<BlockState> states, List<BlockState> old, Map<BlockPos, BlockState> nearby) {}
    private static final LastPlacement<Placed> LAST = new LastPlacement<>();
    private static final int NEARBY_LIMIT = 20000;

    private record Command(BlockPos pos, String text) {}
    private static final ArrayDeque<Command> commandQueue = new ArrayDeque<>();
    private static final CommandReplies REPLIES = new CommandReplies();
    private static boolean sending;

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

    public static void commandSent(String command) {
        if (!sending) REPLIES.other(command);
    }

    public static boolean hidesFeedback(Component message, boolean overlay) { return !overlay && hides(REPLIES, message); }

    static boolean hides(CommandReplies replies, Component message) {
        if (message.getContents() instanceof TranslatableContents t) {
            int[] at = position(t);
            return message.getSiblings().isEmpty() && at != null && replies.hidesSuccess(t.getKey(), at[0], at[1], at[2]);
        }
        if (!empty(message) || message.getSiblings().size() != 1) return false;
        Component error = message.getSiblings().get(0);
        if (error.getContents() instanceof TranslatableContents t) return error.getSiblings().isEmpty() && replies.hidesFailure(t.getKey());
        List<Component> parts = error.getSiblings();
        return empty(error) && !parts.isEmpty() && parts.get(parts.size() - 1).getContents() instanceof TranslatableContents t
                && t.getKey().equals(CommandReplies.HERE) && error.getStyle().getClickEvent() instanceof ClickEvent.SuggestCommand(String command)
                && command.startsWith("/setblock ") && replies.hidesFailure(CommandReplies.HERE);
    }

    private static boolean empty(Component c) { return c.getContents() instanceof PlainTextContents plain && plain.text().isEmpty(); }

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

    public static void place(Minecraft mc, List<BlockPos> pos, List<BlockState> states, List<BlockState> old) {
        place(new Placed(pos, states, old, dependents(mc, pos)), "place");
    }

    private static void place(Placed p, String doing) {
        LAST.placed(p);
        send(p.pos, p.states, false, doing);
    }

    private static boolean needsSupport(Minecraft mc, BlockPos p, BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return false;
        return state.getBlock() instanceof FallingBlock || state.getBlock() instanceof SnowLayerBlock
                || !state.isCollisionShapeFullBlock(mc.level, p);
    }

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

    private static boolean allowed(Minecraft mc, String doing) {
        if (canPlace(mc)) return true;
        say(Component.translatable(doing).withStyle(ChatFormatting.RED));
        return false;
    }

    public static void undo() {
        Minecraft mc = Minecraft.getInstance();
        if (!allowed(mc, "curvegen.placement.undo_permission")) return;
        Placed last = mc.level == null ? null : LAST.undo();
        if (last == null) { say(Component.translatable("curvegen.placement.nothing_undo")); return; }
        List<BlockPos> pos = new ArrayList<>(last.pos);
        List<BlockState> states = new ArrayList<>(last.old);
        last.nearby.forEach((p, state) -> {
            if (free(mc.level.getBlockState(p))) { pos.add(p); states.add(state); }
        });
        send(pos, states, true, "undo");
    }

    public static void redo() {
        Minecraft mc = Minecraft.getInstance();
        if (!allowed(mc, "curvegen.placement.redo_permission")) return;
        Placed last = mc.level == null ? null : LAST.redo();
        if (last == null) { say(Component.translatable("curvegen.placement.nothing_redo")); return; }
        Map<BlockPos, BlockState> nearby = new LinkedHashMap<>(last.nearby);
        dependents(mc, last.pos).forEach(nearby::putIfAbsent);
        place(new Placed(last.pos, last.states, last.old, nearby), "redo");
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
            for (int i = 0; i < pos.size(); i++) {
                BlockPos p = pos.get(i);
                commandQueue.add(new Command(p, "setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " "
                        + BlockStateParser.serialize(states.get(i)) + (undo ? " strict" : "")));
            }
            int seconds = (int) Math.ceil(pos.size() / (COMMANDS_PER_TICK * 20.0));
            say(Component.translatable("curvegen.placement.fallback." + doing, pos.size(), seconds));
        }
    }

    public static void say(Component t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.chatListener().handleOverlay(t);
    }
}
