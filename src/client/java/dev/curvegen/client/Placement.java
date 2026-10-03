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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
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

/** After "Place": a translucent preview follows the crosshair until the player confirms or cancels. */
public final class Placement {
    private Placement() {}

    private record Entry(int dx, int dy, int dz, BlockState state, List<AABB> boxes, float r, float g, float b) {}

    /** Above this many blocks the hologram shows only the bounding box, to keep frame rates sane. */
    private static final int HOLOGRAM_LIMIT = 30000;
    private static final int COMMANDS_PER_TICK = 40;

    private static Layout layout;
    private static int depth = 1, rotation = 0;
    /** How far the player has moved the shape from the block they're aiming at. */
    private static BlockPos nudge = BlockPos.ZERO;
    private static boolean active, overwrite = true, carve = false, floor = false;
    /** Carve offsets (relative to the anchor) for the current facing. */
    private static List<BlockPos> carveOffsets = List.of();
    /** Refreshed a few times a second: which offsets would really change the world right now. */
    private static List<Entry> toPlace = List.of();
    private static List<BlockPos> toBreak = List.of();
    /** The same two lists, split by whether the spot is under water. The hologram draws each half at a different time. */
    private static List<Entry> placeWet = List.of(), placeDry = List.of();
    private static List<BlockPos> breakWet = List.of(), breakDry = List.of();
    private static BlockPos viewAnchor;
    private static Direction viewFacing;
    private static int viewAge;
    private static BlockPos lockedAnchor, anchor;
    /** While locked, the direction the player was facing when they locked, so looking around doesn't turn the shape. */
    private static Direction lockedFacing;
    private static Direction cachedFacing;
    private static List<Entry> entries = List.of();
    private static int minDx, minDy, minDz, maxDx, maxDy, maxDz;

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

    public static boolean isActive() { return active; }

    public static void start(Layout l, int d, boolean overwriteBlocks, boolean carveSpace, boolean flat) {
        layout = l; depth = Math.max(1, d); overwrite = overwriteBlocks; carve = carveSpace; floor = flat;
        rotation = 0; nudge = BlockPos.ZERO; lockedAnchor = null; lockedFacing = null; cachedFacing = null; viewAnchor = null; active = true;
    }

    /** Where the shape may put a block when "Replace" is off: air and things like grass, water or snow layers. */
    private static boolean free(BlockState current) { return current.isAir() || current.canBeReplaced(); }

    public static void cancel() {
        if (!active) return;
        active = false;
        say(Component.literal("Placement cancelled."));
    }

    public static void rotate() { rotation = (rotation + 1) & 3; }
    public static void move(Direction d) { nudge = nudge.relative(d); }
    /** Moves one block along whichever of the six directions the player is looking closest to, or the opposite way. */
    public static void moveWithView(boolean forwards) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Direction d = mc.player.getNearestViewDirection();
        move(forwards ? d : d.getOpposite());
    }
    /** Freezes (or releases) both where the shape is and which way it faces. R still rotates it while locked. */
    public static void toggleLock() {
        Minecraft mc = Minecraft.getInstance();
        if (lockedAnchor == null && mc.player != null) {
            lockedAnchor = baseTarget(mc);
            lockedFacing = mc.player.getDirection();
        } else {
            lockedAnchor = null;
            lockedFacing = null;
        }
    }

    private static Direction facing(Minecraft mc) {
        Direction f = lockedFacing != null ? lockedFacing : mc.player.getDirection();
        for (int i = 0; i < rotation; i++) f = f.getClockWise();
        return f;
    }

    private static BlockPos baseTarget(Minecraft mc) {
        if (mc.player == null) return null;
        HitResult hit = mc.player.pick(128, 1f, false);
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) return b.getBlockPos().relative(b.getDirection());
        return BlockPos.containing(hit.getLocation());
    }

    /**
     * Upright: the drawing stands facing the player, its bottom-middle on the targeted block, extruded away from them.
     * Flat: the drawing lies on the ground centred on the targeted block, its "up" pointing away from the player,
     * with layers stacking upwards.
     */
    private static BlockPos offset(int x, int y, int k, Direction right, Direction forward) {
        int u = x - layout.width() / 2;
        if (floor) {
            int v = y - layout.height() / 2;
            return new BlockPos(right.getStepX() * u + forward.getStepX() * v, k, right.getStepZ() * u + forward.getStepZ() * v);
        }
        return new BlockPos(right.getStepX() * u + forward.getStepX() * k, y, right.getStepZ() * u + forward.getStepZ() * k);
    }

    private static void rebuild(Direction forward) {
        Direction right = forward.getClockWise();
        List<Entry> out = new ArrayList<>(layout.cells().size() * depth);
        minDx = minDy = minDz = Integer.MAX_VALUE; maxDx = maxDy = maxDz = Integer.MIN_VALUE;
        for (Layout.Cell c : layout.cells())
            for (int k = 0; k < depth; k++) {
                BlockPos o = offset(c.x(), c.y(), k, right, forward);
                BlockState st = BlockChoices.stateFor(c.piece(), right, forward, k, depth, floor);
                List<AABB> boxes = out.size() < HOLOGRAM_LIMIT
                        ? st.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs() : List.of();
                int rgb = ColorIndex.of(st.getBlock(), floor);
                out.add(new Entry(o.getX(), o.getY(), o.getZ(), st, boxes,
                        ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f));
                minDx = Math.min(minDx, o.getX()); maxDx = Math.max(maxDx, o.getX());
                minDy = Math.min(minDy, o.getY()); maxDy = Math.max(maxDy, o.getY());
                minDz = Math.min(minDz, o.getZ()); maxDz = Math.max(maxDz, o.getZ());
            }
        entries = out;
        List<BlockPos> cv = new ArrayList<>();
        if (carve)
            for (Layout.Cell c : layout.carve())
                for (int k = 0; k < depth; k++) cv.add(offset(c.x(), c.y(), k, right, forward));
        carveOffsets = cv;
        cachedFacing = forward;
        viewAnchor = null;
    }

    /** Works out what placing right now would actually do, given the blocks already in the world. */
    private static void refreshView(Minecraft mc) {
        boolean drawn = entries.size() <= HOLOGRAM_LIMIT;   // the split is only for drawing the boxes
        List<Entry> place = new ArrayList<>(), wet = new ArrayList<>(), dry = new ArrayList<>();
        for (Entry e : entries) {
            BlockPos p = anchor.offset(e.dx, e.dy, e.dz);
            if (!overwrite && !free(mc.level.getBlockState(p))) continue;
            place.add(e);
            if (drawn) (underWater(mc, p) ? wet : dry).add(e);
        }
        List<BlockPos> brk = new ArrayList<>(), brkWet = new ArrayList<>(), brkDry = new ArrayList<>();
        for (BlockPos o : carveOffsets) {
            BlockPos p = anchor.offset(o);
            if (mc.level.getBlockState(p).isAir()) continue;
            brk.add(o);
            if (drawn) (underWater(mc, p) ? brkWet : brkDry).add(o);
        }
        toPlace = place; toBreak = brk;
        placeWet = wet; placeDry = dry; breakWet = brkWet; breakDry = brkDry;
        viewAnchor = anchor; viewFacing = cachedFacing; viewAge = 0;
    }

    private static boolean underWater(Minecraft mc, BlockPos p) { return mc.level.getFluidState(p).is(FluidTags.WATER); }

    public static void tick(Minecraft mc) {
        if (mc.player != null && mc.getConnection() != null)
            for (int i = 0; i < COMMANDS_PER_TICK && !commandQueue.isEmpty(); i++)
                mc.getConnection().sendCommand(commandQueue.poll());
        if (!active || mc.player == null) return;
        Direction f = facing(mc);
        if (f != cachedFacing) rebuild(f);
        BlockPos base = lockedAnchor != null ? lockedAnchor : baseTarget(mc);
        anchor = base == null ? null : base.offset(nudge);
        if (anchor != null && mc.level != null && (!anchor.equals(viewAnchor) || viewFacing != cachedFacing || ++viewAge >= 10))
            refreshView(mc);
    }

    // ---------- rendering ----------

    /**
     * Draws the hologram. {@code ms} is the world's pose stack and {@code cam} the camera position. Each loader calls
     * this twice a frame: once before the game draws water and other translucent blocks, and once after. Boxes on the
     * far side of a water surface are drawn before it, so they show through it. The rest are drawn after it, so they
     * draw over water behind them.
     */
    public static void render(PoseStack ms, Vec3 cam, boolean afterWater) {
        if (!active || anchor == null || entries.isEmpty() || !anchor.equals(viewAnchor)) return;
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource imm = mc.renderBuffers().bufferSource();
        ms.pushPose();
        ms.translate(anchor.getX() - cam.x, anchor.getY() - cam.y, anchor.getZ() - cam.z);

        if (entries.size() <= HOLOGRAM_LIMIT) {
            boolean camWet = mc.gameRenderer.getMainCamera().getFluidInCamera() == FogType.WATER;
            boolean wet = camWet == afterWater;   // which half is on the camera's side decides which one this pass draws
            VertexConsumer fill = imm.getBuffer(RenderTypes.debugFilledBox());
            PoseStack.Pose pose = ms.last();
            for (Entry e : wet ? placeWet : placeDry) {
                int color = ARGB.colorFromFloat(0.45f, e.r, e.g, e.b);
                for (AABB b : e.boxes)
                    filledBox(pose, fill, e.dx + b.minX, e.dy + b.minY, e.dz + b.minZ, e.dx + b.maxX, e.dy + b.maxY, e.dz + b.maxZ, color);
            }
            if (toBreak.size() <= HOLOGRAM_LIMIT) {
                // Blocks carving will remove, in red. Slightly larger than the block, or a full block would hide its own box.
                int red = ARGB.colorFromFloat(0.28f, 1f, 0.2f, 0.25f);
                for (BlockPos o : wet ? breakWet : breakDry)
                    filledBox(pose, fill, o.getX() - .005, o.getY() - .005, o.getZ() - .005, o.getX() + 1.005, o.getY() + 1.005, o.getZ() + 1.005, red);
            }
            imm.endBatch(RenderTypes.debugFilledBox());
        }
        if (afterWater) {
            VertexConsumer lines = imm.getBuffer(RenderTypes.lines());
            float width = mc.getWindow().getAppropriateLineWidth();
            AABB bounds = new AABB(minDx, minDy, minDz, maxDx + 1, maxDy + 1, maxDz + 1);
            ShapeRenderer.renderShape(ms, lines, Shapes.create(bounds), 0, 0, 0, ARGB.colorFromFloat(0.9f, 1f, 1f, 1f), width);
            ShapeRenderer.renderShape(ms, lines, Shapes.create(new AABB(0, 0, 0, 1, 1, 1).inflate(0.02)), 0, 0, 0,
                    ARGB.colorFromFloat(1f, 1f, 0.3f, 0.45f), width);   // the anchor block
            imm.endBatch(RenderTypes.lines());
        }
        ms.popPose();
    }

    /** The six faces of a box as quads, each wound to face outwards so back faces are culled. */
    private static void filledBox(PoseStack.Pose pose, VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1, int color) {
        float a = (float) x0, b = (float) y0, c = (float) z0, d = (float) x1, e = (float) y1, f = (float) z1;
        quad(pose, vc, color, a, b, c, d, b, c, d, b, f, a, b, f);   // down
        quad(pose, vc, color, a, e, c, a, e, f, d, e, f, d, e, c);   // up
        quad(pose, vc, color, a, b, c, a, e, c, d, e, c, d, b, c);   // north
        quad(pose, vc, color, a, b, f, d, b, f, d, e, f, a, e, f);   // south
        quad(pose, vc, color, a, b, c, a, b, f, a, e, f, a, e, c);   // west
        quad(pose, vc, color, d, b, c, d, e, c, d, e, f, d, b, f);   // east
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int color, float... xyz) {
        for (int i = 0; i < 12; i += 3) vc.addVertex(pose, xyz[i], xyz[i + 1], xyz[i + 2]).setColor(color);
    }

    public static void renderHud(GuiGraphicsExtractor dc) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var tr = mc.font;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Curve Generator: " + (floor
                ? "floor, " + layout.width() + " by " + layout.height() + ", " + depth + (depth == 1 ? " layer" : " layers") + " high"
                : layout.width() + " wide, " + layout.height() + " tall, " + depth + " deep")).withStyle(ChatFormatting.WHITE));
        String what = toPlace.size() + " blocks to place" + (toPlace.size() < entries.size() ? " (" + (entries.size() - toPlace.size()) + " skipped, Replace is off)" : "");
        if (carve) what += ", " + toBreak.size() + " to clear (shown in red)";
        lines.add(Component.literal(what).withStyle(ChatFormatting.WHITE));
        lines.add(Component.literal(key(CurveGenClient.CONFIRM) + " place, " + key(CurveGenClient.ROTATE) + " rotate, "
                + key(CurveGenClient.FORWARD) + "/" + key(CurveGenClient.BACK) + " move forwards or back, "
                + key(CurveGenClient.LOCK) + (lockedAnchor != null ? " unlock (follow your view again)" : " lock position and direction") + ", "
                + key(CurveGenClient.CANCEL) + " cancel").withStyle(ChatFormatting.GRAY));
        if (entries.size() > HOLOGRAM_LIMIT) lines.add(Component.literal("Large shape: only its outline is previewed.").withStyle(ChatFormatting.YELLOW));
        if (!mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            lines.add(Component.literal("You need operator permissions to place blocks here. Cancel and use Export instead.").withStyle(ChatFormatting.RED));
        int y = 6;
        for (Component t : lines) {
            dc.fill(4, y - 2, 8 + tr.width(t), y + 10, 0x90000000);
            dc.text(tr, t, 6, y, 0xFFFFFFFF);
            y += 13;
        }
    }

    private static String key(net.minecraft.client.KeyMapping k) { return k.getTranslatedKeyMessage().getString(); }

    // ---------- placing ----------

    public static void confirm() {
        Minecraft mc = Minecraft.getInstance();
        if (!active || anchor == null || mc.player == null || mc.level == null) return;
        if (!mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            say(Component.literal("You need operator permissions (level 2) to place shapes. Use Export to make a Litematica schematic instead.").withStyle(ChatFormatting.RED));
            return;
        }
        List<BlockPos> pos = new ArrayList<>(entries.size());
        List<BlockState> states = new ArrayList<>(entries.size()), old = new ArrayList<>(entries.size());
        // Clear first, so carving never removes anything the shape itself places.
        for (BlockPos o : carveOffsets) {
            BlockPos p = anchor.offset(o);
            BlockState cur = mc.level.getBlockState(p);
            if (!cur.isAir()) { pos.add(p); states.add(Blocks.AIR.defaultBlockState()); old.add(cur); }
        }
        for (Entry e : entries) {
            BlockPos p = anchor.offset(e.dx, e.dy, e.dz);
            BlockState cur = mc.level.getBlockState(p);
            if (!overwrite && !free(cur)) continue;
            pos.add(p); states.add(e.state); old.add(cur);
        }
        if (pos.isEmpty()) { say(Component.literal("Nothing to change here: every spot is already taken and Replace is off.")); return; }
        undoPositions = pos; undoStates = old;
        undoNearby = dependents(mc, pos);
        send(pos, states, false);
        active = false;
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
            say(Component.literal("This server doesn't have Curve Generator, so " + (undo ? "undo" : "placement")
                    + " uses /setblock: " + pos.size() + " blocks, about " + seconds + " s."));
        }
    }

    /** Shows a message from the mod itself in chat, and lets the narrator read it. */
    public static void say(Component t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.getChatListener().handleSystemMessage(t, false);
    }
}
