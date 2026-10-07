package dev.curvegen.client.edit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.curvegen.client.ColorIndex;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The translucent blocks of one solved shape. It takes block offsets and states from whatever solved them, works out
 * once which faces can be seen from outside, and after that only checks the world: which spots placing would change,
 * and which are under water.
 */
public final class Hologram {
    /** A block and the faces of it worth drawing: 12 coordinates a quad, with the side each quad faces. */
    private record Entry(int dx, int dy, int dz, BlockState state, float[] quads, byte[] sides, int rgb) {}

    private final List<Entry> entries;
    private final List<BlockPos> carve;
    /** False above the block limit, when only the wireframe shows. */
    private final boolean drawn;
    private final int blockLimit;
    public final String error;

    /** Refreshed a few times a second: what placing right now would change, given the blocks already in the world. */
    private List<Entry> toPlace = List.of();
    private List<BlockPos> toBreak = List.of();
    /** The same two lists, split by whether the spot is under water. Each half draws at a different time in the frame. */
    private List<Entry> placeWet = List.of(), placeDry = List.of();
    private List<BlockPos> breakWet = List.of(), breakDry = List.of();

    public Hologram(EditShape.Content content, int blockLimit) {
        error = content.error();
        this.blockLimit = blockLimit;
        carve = content.carve();
        drawn = content.blocks().size() <= blockLimit;
        List<Entry> out = new ArrayList<>(content.blocks().size());
        // A face against a neighbour that is a whole cube can't be seen, so it isn't drawn.
        Map<BlockState, VoxelShape> shapes = new IdentityHashMap<>();
        LongOpenHashSet cubes = new LongOpenHashSet();
        if (drawn)
            for (EditShape.Placed p : content.blocks()) {
                VoxelShape shape = shapes.computeIfAbsent(p.state(), st -> st.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
                if (Block.isShapeFullBlock(shape)) cubes.add(BlockPos.asLong(p.dx(), p.dy(), p.dz()));
            }
        List<float[]> quads = new ArrayList<>();
        List<Byte> sides = new ArrayList<>();
        for (EditShape.Placed p : content.blocks()) {
            quads.clear(); sides.clear();
            if (drawn) {
                int x = p.dx(), y = p.dy(), z = p.dz();
                boolean[] hidden = {cubes.contains(BlockPos.asLong(x, y - 1, z)), cubes.contains(BlockPos.asLong(x, y + 1, z)),
                        cubes.contains(BlockPos.asLong(x, y, z - 1)), cubes.contains(BlockPos.asLong(x, y, z + 1)),
                        cubes.contains(BlockPos.asLong(x - 1, y, z)), cubes.contains(BlockPos.asLong(x + 1, y, z))};
                for (AABB b : shapes.get(p.state()).toAabbs()) {
                    float a = (float) (x + b.minX), c = (float) (y + b.minY), d = (float) (z + b.minZ);
                    float e = (float) (x + b.maxX), f = (float) (y + b.maxY), g = (float) (z + b.maxZ);
                    if (!(hidden[0] && b.minY <= 0)) { quads.add(new float[]{a, c, d, e, c, d, e, c, g, a, c, g}); sides.add((byte) 0); }
                    if (!(hidden[1] && b.maxY >= 1)) { quads.add(new float[]{a, f, d, a, f, g, e, f, g, e, f, d}); sides.add((byte) 1); }
                    if (!(hidden[2] && b.minZ <= 0)) { quads.add(new float[]{a, c, d, a, f, d, e, f, d, e, c, d}); sides.add((byte) 2); }
                    if (!(hidden[3] && b.maxZ >= 1)) { quads.add(new float[]{a, c, g, e, c, g, e, f, g, a, f, g}); sides.add((byte) 3); }
                    if (!(hidden[4] && b.minX <= 0)) { quads.add(new float[]{a, c, d, a, c, g, a, f, g, a, f, d}); sides.add((byte) 4); }
                    if (!(hidden[5] && b.maxX >= 1)) { quads.add(new float[]{e, c, d, e, f, d, e, f, g, e, c, g}); sides.add((byte) 5); }
                }
            }
            float[] flat = new float[quads.size() * 12];
            byte[] side = new byte[quads.size()];
            for (int q = 0; q < side.length; q++) {
                System.arraycopy(quads.get(q), 0, flat, q * 12, 12);
                side[q] = sides.get(q);
            }
            out.add(new Entry(p.dx(), p.dy(), p.dz(), p.state(), flat, side, ColorIndex.of(p.state().getBlock(), content.topColours())));
        }
        entries = out;
    }

    public int size() { return entries.size(); }

    /** Every block of the shape, whatever is in the world: what an export holds. */
    public List<EditShape.Placed> blocks() {
        List<EditShape.Placed> out = new ArrayList<>(entries.size());
        for (Entry e : entries) out.add(new EditShape.Placed(e.dx, e.dy, e.dz, e.state));
        return out;
    }
    public boolean isDrawn() { return drawn; }
    public int placing() { return toPlace.size(); }
    public int clearing() { return toBreak.size(); }

    /** Where the shape may put a block when Replace is off: air and things like grass, water or snow layers. */
    public static boolean free(BlockState current) { return current.isAir() || current.canBeReplaced(); }

    private static boolean underWater(Minecraft mc, BlockPos p) { return mc.level.getFluidState(p).is(FluidTags.WATER); }

    /** Works out what placing at {@code origin} right now would actually do. */
    public void refresh(Minecraft mc, BlockPos origin, boolean overwrite, boolean carving) {
        List<Entry> place = new ArrayList<>(), wet = new ArrayList<>(), dry = new ArrayList<>();
        for (Entry e : entries) {
            BlockPos p = origin.offset(e.dx, e.dy, e.dz);
            if (!overwrite && !free(mc.level.getBlockState(p))) continue;
            place.add(e);
            if (drawn && e.sides.length > 0) (underWater(mc, p) ? wet : dry).add(e);
        }
        List<BlockPos> brk = new ArrayList<>(), brkWet = new ArrayList<>(), brkDry = new ArrayList<>();
        if (carving)
            for (BlockPos o : carve) {
                BlockPos p = origin.offset(o);
                if (mc.level.getBlockState(p).isAir()) continue;
                brk.add(o);
                if (drawn) (underWater(mc, p) ? brkWet : brkDry).add(o);
            }
        toPlace = place; toBreak = brk;
        placeWet = wet; placeDry = dry; breakWet = brkWet; breakDry = brkDry;
    }

    /**
     * Lists what placing at {@code origin} changes: positions, the states to set and the states there now. Carving
     * comes first, so it never removes anything the shape itself places.
     */
    public void collect(Minecraft mc, BlockPos origin, boolean overwrite, boolean carving, List<BlockPos> pos, List<BlockState> states, List<BlockState> old) {
        if (carving)
            for (BlockPos o : carve) {
                BlockPos p = origin.offset(o);
                BlockState cur = mc.level.getBlockState(p);
                if (!cur.isAir()) { pos.add(p); states.add(Blocks.AIR.defaultBlockState()); old.add(cur); }
            }
        for (Entry e : entries) {
            BlockPos p = origin.offset(e.dx, e.dy, e.dz);
            BlockState cur = mc.level.getBlockState(p);
            if (!overwrite && !free(cur)) continue;
            pos.add(p); states.add(e.state); old.add(cur);
        }
    }

    /**
     * Submits the boxes. {@code ms} is already moved to the hologram's origin and (cx, cy, cz) is the camera from there.
     * Water is drawn between the two phases. Boxes on the camera's side of the surface go after it, so they draw over
     * water behind them. Boxes on the far side go before it, so they show through the surface.
     */
    public void submit(SubmitNodeCollector out, PoseStack ms, boolean camWet, float opacity, double cx, double cy, double cz) {
        if (!drawn) return;
        boolean clear = toBreak.size() <= blockLimit;
        submitBoxes(out, ms, camWet ? placeWet : placeDry, !clear ? List.of() : camWet ? breakWet : breakDry, true, opacity, cx, cy, cz);
        submitBoxes(out, ms, camWet ? placeDry : placeWet, !clear ? List.of() : camWet ? breakDry : breakWet, false, opacity, cx, cy, cz);
    }

    private static void submitBoxes(SubmitNodeCollector out, PoseStack ms, List<Entry> place, List<BlockPos> clear, boolean afterWater,
                                    float opacity, double cx, double cy, double cz) {
        if (place.isEmpty() && clear.isEmpty()) return;
        submit(out, ms, RenderTypes.debugFilledBox(), afterWater, (pose, fill) -> {
            for (Entry e : place) {
                int color = ARGB.color((int) (opacity * 255), e.rgb);
                float[] q = e.quads;
                for (int k = 0; k < e.sides.length; k++) {
                    int o = k * 12;
                    // Only the faces turned towards the camera: the render type doesn't cull the others.
                    boolean seen = switch (e.sides[k]) {
                        case 0 -> cy < q[o + 1]; case 1 -> cy > q[o + 1];
                        case 2 -> cz < q[o + 2]; case 3 -> cz > q[o + 2];
                        case 4 -> cx < q[o]; default -> cx > q[o];
                    };
                    if (seen) for (int v = 0; v < 12; v += 3) fill.addVertex(pose, q[o + v], q[o + v + 1], q[o + v + 2]).setColor(color);
                }
            }
            // Blocks carving will remove, in red. Slightly larger than the block, or a full block would hide its own box.
            int red = ARGB.colorFromFloat(0.28f, 1f, 0.2f, 0.25f);
            for (BlockPos o : clear)
                filledBox(pose, fill, o.getX() - .005, o.getY() - .005, o.getZ() - .005, o.getX() + 1.005, o.getY() + 1.005, o.getZ() + 1.005, red, cx, cy, cz);
        });
    }

    /**
     * Submits custom geometry either after translucent terrain or before it. The after-terrain phase is only reachable
     * on the game's own collector, so a collector from another mod gets everything before the water.
     */
    static void submit(SubmitNodeCollector out, PoseStack ms, RenderType type, boolean afterTerrain, SubmitNodeCollector.CustomGeometryRenderer geometry) {
        if (afterTerrain && out instanceof SubmitNodeStorage storage)
            storage.order(0).afterTerrain.submit(new CustomFeatureRenderer.Submit(ms.last().copy(), type, geometry));
        else out.submitCustomGeometry(ms, type, geometry);
    }

    /**
     * The faces of a box that the camera at (cx, cy, cz) can see. The filled-box render type no longer culls back faces,
     * and drawing them too would blend every box twice.
     */
    static void filledBox(PoseStack.Pose pose, VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1,
                          int color, double cx, double cy, double cz) {
        float a = (float) x0, b = (float) y0, c = (float) z0, d = (float) x1, e = (float) y1, f = (float) z1;
        if (cy < y0) quad(pose, vc, color, a, b, c, d, b, c, d, b, f, a, b, f);   // down
        if (cy > y1) quad(pose, vc, color, a, e, c, a, e, f, d, e, f, d, e, c);   // up
        if (cz < z0) quad(pose, vc, color, a, b, c, a, e, c, d, e, c, d, b, c);   // north
        if (cz > z1) quad(pose, vc, color, a, b, f, d, b, f, d, e, f, a, e, f);   // south
        if (cx < x0) quad(pose, vc, color, a, b, c, a, b, f, a, e, f, a, e, c);   // west
        if (cx > x1) quad(pose, vc, color, d, b, c, d, e, c, d, e, f, d, b, f);   // east
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int color, float... xyz) {
        for (int i = 0; i < 12; i += 3) vc.addVertex(pose, xyz[i], xyz[i + 1], xyz[i + 2]).setColor(color);
    }

    /**
     * A render type that draws plain coloured quads over everything, whatever is in front. The game has no such
     * type for untextured boxes, so this is the one it draws see-through name tags with, on a white texture.
     */
    static RenderType throughWalls() { return RenderTypes.textSeeThrough(WHITE); }
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("curvegen", "textures/white.png");

    /** A vertex consumer for {@link #throughWalls}, which wants a texture coordinate and a light level on every vertex. */
    static VertexConsumer lit(VertexConsumer vc) { return new Lit(vc); }

    /** Completes each vertex as soon as it has its colour. */
    private record Lit(VertexConsumer vc) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { vc.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { vc.setColor(r, g, b, a).setUv(0.5f, 0.5f).setLight(0xF000F0); return this; }
        @Override public VertexConsumer setColor(int argb) { vc.setColor(argb).setUv(0.5f, 0.5f).setLight(0xF000F0); return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setUv3(float u, float v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float w) { return this; }
    }
}
