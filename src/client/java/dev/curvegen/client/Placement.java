package dev.curvegen.client;

import dev.curvegen.core.Layout;
import dev.curvegen.net.PlaceBlocksPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** After "Place": a translucent preview follows the crosshair until the player confirms or cancels. */
public final class Placement {
    private Placement() {}

    private record Entry(int dx, int dy, int dz, BlockState state, List<Box> boxes, float r, float g, float b) {}

    /** Above this many blocks the hologram shows only the bounding box, to keep frame rates sane. */
    private static final int HOLOGRAM_LIMIT = 30000;
    private static final int COMMANDS_PER_TICK = 40;

    private static Layout layout;
    private static int depth = 1, rotation = 0, yOffset = 0;
    private static boolean active;
    private static BlockPos lockedAnchor, anchor;
    private static Direction cachedFacing;
    private static List<Entry> entries = List.of();
    private static int minDx, minDz, maxDx, maxDz;

    private static List<BlockPos> undoPositions;
    private static List<BlockState> undoStates;
    private static final ArrayDeque<String> commandQueue = new ArrayDeque<>();

    public static boolean isActive() { return active; }

    public static void start(Layout l, int d) {
        layout = l; depth = Math.max(1, d);
        rotation = 0; yOffset = 0; lockedAnchor = null; cachedFacing = null; active = true;
    }

    public static void cancel() {
        if (!active) return;
        active = false;
        say(Text.literal("Placement cancelled."));
    }

    public static void rotate() { rotation = (rotation + 1) & 3; }
    public static void raise(int dy) { yOffset += dy; }
    public static void toggleLock() { lockedAnchor = lockedAnchor == null ? baseTarget(MinecraftClient.getInstance()) : null; }

    private static Direction facing(MinecraftClient mc) {
        Direction f = mc.player.getHorizontalFacing();
        for (int i = 0; i < rotation; i++) f = f.rotateYClockwise();
        return f;
    }

    private static BlockPos baseTarget(MinecraftClient mc) {
        if (mc.player == null) return null;
        HitResult hit = mc.player.raycast(128, 1f, false);
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) return b.getBlockPos().offset(b.getSide());
        return BlockPos.ofFloored(hit.getPos());
    }

    private static void rebuild(Direction forward) {
        Direction right = forward.rotateYClockwise();
        int half = layout.width() / 2;
        List<Entry> out = new ArrayList<>(layout.cells().size() * depth);
        minDx = minDz = Integer.MAX_VALUE; maxDx = maxDz = Integer.MIN_VALUE;
        for (Layout.Cell c : layout.cells())
            for (int k = 0; k < depth; k++) {
                int u = c.x() - half;
                int dx = right.getOffsetX() * u + forward.getOffsetX() * k;
                int dz = right.getOffsetZ() * u + forward.getOffsetZ() * k;
                BlockState st = BlockChoices.stateFor(c.piece(), right, forward, k, depth);
                List<Box> boxes = out.size() < HOLOGRAM_LIMIT
                        ? st.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getBoundingBoxes() : List.of();
                int rgb = ColorIndex.of(st.getBlock());
                out.add(new Entry(dx, c.y(), dz, st, boxes,
                        ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f));
                minDx = Math.min(minDx, dx); maxDx = Math.max(maxDx, dx);
                minDz = Math.min(minDz, dz); maxDz = Math.max(maxDz, dz);
            }
        entries = out;
        cachedFacing = forward;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player != null && mc.getNetworkHandler() != null)
            for (int i = 0; i < COMMANDS_PER_TICK && !commandQueue.isEmpty(); i++)
                mc.getNetworkHandler().sendChatCommand(commandQueue.poll());
        if (!active || mc.player == null) return;
        Direction f = facing(mc);
        if (f != cachedFacing) rebuild(f);
        BlockPos base = lockedAnchor != null ? lockedAnchor : baseTarget(mc);
        anchor = base == null ? null : base.up(yOffset);
    }

    // ---------- rendering ----------

    public static void render(WorldRenderContext ctx) {
        if (!active || anchor == null || entries.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        MatrixStack ms = ctx.matrixStack();
        Vec3d cam = ctx.camera().getPos();
        VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();
        ms.push();
        ms.translate(anchor.getX() - cam.x, anchor.getY() - cam.y, anchor.getZ() - cam.z);

        if (entries.size() <= HOLOGRAM_LIMIT) {
            VertexConsumer fill = imm.getBuffer(RenderLayer.getDebugFilledBox());
            for (Entry e : entries)
                for (Box b : e.boxes)
                    WorldRenderer.renderFilledBox(ms, fill,
                            e.dx + b.minX, e.dy + b.minY, e.dz + b.minZ, e.dx + b.maxX, e.dy + b.maxY, e.dz + b.maxZ,
                            e.r, e.g, e.b, 0.45f);
            imm.draw(RenderLayer.getDebugFilledBox());
        }
        VertexConsumer lines = imm.getBuffer(RenderLayer.getLines());
        Box bounds = new Box(minDx, 0, minDz, maxDx + 1, layout.height(), maxDz + 1);
        WorldRenderer.drawBox(ms, lines, bounds, 1f, 1f, 1f, 0.9f);
        WorldRenderer.drawBox(ms, lines, new Box(0, 0, 0, 1, 1, 1).expand(0.02), 1f, 0.3f, 0.45f, 1f);  // the anchor block
        imm.draw(RenderLayer.getLines());
        ms.pop();
    }

    public static void renderHud(DrawContext dc) {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        var tr = mc.textRenderer;
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("Curve Generator: " + entries.size() + " blocks, " + layout.width() + " wide, " + layout.height() + " tall, " + depth + " deep").formatted(Formatting.WHITE));
        lines.add(Text.literal(key(CurveGenClient.CONFIRM) + " place, " + key(CurveGenClient.ROTATE) + " rotate, "
                + key(CurveGenClient.RAISE) + "/" + key(CurveGenClient.LOWER) + " move up or down, "
                + key(CurveGenClient.LOCK) + (lockedAnchor != null ? " unlock position" : " lock position") + ", "
                + key(CurveGenClient.CANCEL) + " cancel").formatted(Formatting.GRAY));
        if (entries.size() > HOLOGRAM_LIMIT) lines.add(Text.literal("Large shape: only its outline is previewed.").formatted(Formatting.YELLOW));
        if (!mc.player.hasPermissionLevel(2))
            lines.add(Text.literal("You need operator permissions to place blocks here. Cancel and use Export instead.").formatted(Formatting.RED));
        int y = 6;
        for (Text t : lines) {
            dc.fill(4, y - 2, 8 + tr.getWidth(t), y + 10, 0x90000000);
            dc.drawTextWithShadow(tr, t, 6, y, 0xFFFFFF);
            y += 13;
        }
    }

    private static String key(net.minecraft.client.option.KeyBinding k) { return k.getBoundKeyLocalizedText().getString(); }

    // ---------- placing ----------

    public static void confirm() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!active || anchor == null || mc.player == null || mc.world == null) return;
        if (!mc.player.hasPermissionLevel(2)) {
            say(Text.literal("You need operator permissions (level 2) to place shapes. Use Export to make a Litematica schematic instead.").formatted(Formatting.RED));
            return;
        }
        List<BlockPos> pos = new ArrayList<>(entries.size());
        List<BlockState> states = new ArrayList<>(entries.size()), old = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            BlockPos p = anchor.add(e.dx, e.dy, e.dz);
            pos.add(p); states.add(e.state); old.add(mc.world.getBlockState(p));
        }
        undoPositions = pos; undoStates = old;
        send(pos, states, false);
        active = false;
    }

    public static void undo() {
        if (undoPositions == null) { say(Text.literal("Nothing to undo.")); return; }
        send(undoPositions, undoStates, true);
        undoPositions = null; undoStates = null;
    }

    private static void send(List<BlockPos> pos, List<BlockState> states, boolean undo) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (pos.isEmpty()) return;
        if (ClientPlayNetworking.canSend(PlaceBlocksPayload.ID)) {
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
                    st[i - start] = Block.getRawIdFromState(states.get(i));
                }
                ClientPlayNetworking.send(new PlaceBlocksPayload(origin, off, st, end == n, n, undo));
            }
        } else {
            // The server doesn't have the mod: fall back to /setblock, which also needs operator rights.
            for (int i = 0; i < pos.size(); i++) {
                BlockPos p = pos.get(i);
                commandQueue.add("setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " "
                        + BlockArgumentParser.stringifyBlockState(states.get(i)));
            }
            int seconds = (int) Math.ceil(pos.size() / (COMMANDS_PER_TICK * 20.0));
            say(Text.literal("This server doesn't have Curve Generator, so " + (undo ? "undo" : "placement")
                    + " uses /setblock: " + pos.size() + " blocks, about " + seconds + " s."));
        }
    }

    private static void say(Text t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(t, false);
    }
}
