package dev.curvegen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.curvegen.core.Layout;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Puts blocks into the world and takes the last placement back. The server does it when it has the mod; otherwise the
 * client sends {@code /setblock} commands. The in-world editor ({@link dev.curvegen.client.edit.Editor}) decides what to place.
 */
public final class Placement {
    private Placement() {}

    private static final int COMMANDS_PER_TICK = 40;

    private static List<BlockPos> undoPositions;
    private static List<BlockState> undoStates;
    /**
     * Blocks next to the last placement that could break because of it, such as a torch on a carved wall or sand above
     * a carved hole. Undo puts back the ones that are gone.
     */
    private static Map<BlockPos, BlockState> undoNearby = Map.of();
    /** A cap on that list, so a placement beside a huge field of plants stays cheap. */
    private static final int NEARBY_LIMIT = 20000;
    private static final ArrayDeque<String> commandQueue = new ArrayDeque<>();

    /** Where the shape may put a block when "Replace" is off: air and things like grass, water or snow layers. */
    private static boolean free(BlockState current) { return current.isAir() || current.canBeReplaced(); }

    public static boolean canPlace(Minecraft mc) {
        return mc.player != null && mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static void tick(Minecraft mc) {
        if (mc.player != null && mc.getConnection() != null)
            for (int i = 0; i < COMMANDS_PER_TICK && !commandQueue.isEmpty(); i++)
                mc.getConnection().sendCommand(commandQueue.poll());
    }

    /** Sets {@code states} at {@code pos} and remembers {@code old}, the states there now, for undo. */
    public static void place(Minecraft mc, List<BlockPos> pos, List<BlockState> states, List<BlockState> old) {
        undoPositions = pos; undoStates = old;
        undoNearby = dependents(mc, pos);
        send(pos, states, false);
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

    public static void undo() {
        Minecraft mc = Minecraft.getInstance();
        if (undoPositions == null || mc.level == null) { say(Component.literal("Nothing to undo.")); return; }
        List<BlockPos> pos = new ArrayList<>(undoPositions);
        List<BlockState> states = new ArrayList<>(undoStates);
        // A nearby block is only put back if its spot is empty now, so anything built there since is left alone.
        undoNearby.forEach((p, state) -> {
            if (free(mc.level.getBlockState(p))) { pos.add(p); states.add(state); }
        });
        send(pos, states, true);
        undoPositions = null; undoStates = null; undoNearby = Map.of();
    }

    private static void send(List<BlockPos> pos, List<BlockState> states, boolean undo) {
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
                commandQueue.add("setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " "
                        + BlockStateParser.serialize(states.get(i)) + (undo ? " strict" : ""));
            }
            int seconds = (int) Math.ceil(pos.size() / (COMMANDS_PER_TICK * 20.0));
            say(Component.literal("No server mod: " + (undo ? "undoing " : "placing ") + pos.size()
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
