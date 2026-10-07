package dev.curvegen.client.edit;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.core.Pieces3;
import dev.curvegen.core.Shape3;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.Solver3;
import dev.curvegen.core.edit.Edit3D;
import dev.curvegen.core.edit.Option;
import dev.curvegen.core.edit.Orient;
import dev.curvegen.core.edit.Turned;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The 3D shapes in the in-world editor. The volumetric solver works in the world's orientation, so turning or
 * tipping a shape changes which way its own axes point and solves it again.
 */
final class Shape3D implements EditShape {
    private final ShapeSettings s;

    Shape3D(ShapeSettings s) { this.s = s; }

    /**
     * A solve, with the cells Carve clears (null while Carve was off) and whether colours match the top texture.
     * {@code own} is the shape before it was turned, for its wireframe, with the kind and box it was solved for.
     */
    private record Solved(Solver3.Result result, BitSet carve, boolean topColours, Shape3 own, Gen gen, int[] size) {}

    @Override public int[] size() { return Edit3D.size(s); }
    @Override public void resize(int[] want, boolean[] dragged) { Edit3D.resize(s, want, dragged); }
    @Override public int[] bump(int axis, int side, int amount) { return Edit3D.bumpPoints(s, axis, side, amount); }
    @Override public List<double[]> points() { return Edit3D.points(s); }
    @Override public int pointColumns() { return Edit3D.columns(s); }
    @Override public int[] movePoint(int index, double[] to) { return Edit3D.movePoint(s, index, to); }
    @Override public boolean removePoint(int index) { return removePoint(index, false); }
    @Override public int insertPoint(double[] at) { return insertPoint(at, false); }
    @Override public int duplicatePoint(int index) { return duplicatePoint(index, false); }
    @Override public boolean removePoint(int index, boolean alt) { return Edit3D.removePoint(s, index, alt); }
    @Override public int insertPoint(double[] at, boolean alt) { return Edit3D.insertPoint(s, at, alt); }
    @Override public int duplicatePoint(int index, boolean alt) { return Edit3D.duplicatePoint(s, index, alt); }
    @Override public double[] lookAt(double[] origin, double[] dir) { return Edit3D.lookAt(s, origin, dir); }
    @Override public List<Option> options() { return Edit3D.options(s); }
    @Override public boolean solveUsesCarve() { return true; }
    @Override public String describe() { return Edit3D.describe(s); }

    @Override
    public double[] curve(Object solved) {
        double[] direct = Edit3D.curve(s);
        if (direct != null) return direct;
        if (s.gen == Gen.BEZIER3 || s.gen == Gen.SURFACE) return null;
        // Tracing an equation's surface takes too long for every frame. The last solve's is stretched to the box as it is now.
        if (s.gen == Gen.EQUATION3) {
            if (!(solved instanceof Solved v) || v.gen != Gen.EQUATION3 || v.own.error() != null) return null;
            int[] size = size();
            return segments(v.own.wireframe(), 0, new double[]{(double) size[0] / v.size[0], (double) size[1] / v.size[1], (double) size[2] / v.size[2]});
        }
        Shape3 shape = Shape3.of(s);
        if (shape.error() != null) return null;
        // The shape's box includes the room an outwards shell adds, and the handles' box doesn't.
        return segments(shape.wireframe(), shape.pad(), new double[]{1, 1, 1});
    }

    /** Polylines as separate segments, moved in by {@code pad} and then stretched. */
    private static double[] segments(List<double[]> lines, int pad, double[] scale) {
        int n = 0;
        for (double[] line : lines) n += Math.max(0, line.length / 3 - 1);
        double[] out = new double[n * 6];
        int o = 0;
        for (double[] line : lines)
            for (int p = 0; p + 5 < line.length; p += 3)
                for (int k = 0; k < 6; k++) out[o++] = (line[p + k] - pad) * scale[k % 3];
        return out;
    }

    @Override
    public Object solve(ShapeSettings copy, Orient orient, BooleanSupplier cancelled) {
        Shape3 own = Shape3.of(copy), shape = Turned.of(own, orient);
        Solver3.Result r = Solver3.solve(shape, copy, cancelled);
        if (r == null) return null;
        if (own.error() == null) own.wireframe();   // traced here, off the client thread, and kept by the shape
        return new Solved(r, copy.carve ? Solver3.carve(shape) : null, copy.topColours, own, copy.gen, Edit3D.size(copy));
    }

    @Override
    public Content build(Object solved, Orient o) {
        Solved v = (Solved) solved;
        Solver3.Result r = v.result;
        String error = r.shape().error();
        if (error != null) return new Content(List.of(), List.of(), v.topColours, error);
        BlockState[] states = BlockChoices.statesFor3();
        int pad = r.shape().pad(), nx = r.nx(), ny = r.ny(), nz = r.nz();
        List<Placed> blocks = new ArrayList<>();
        List<BlockPos> carve = new ArrayList<>();
        for (int y = 0, c = 0; y < ny; y++)
            for (int z = 0; z < nz; z++)
                for (int x = 0; x < nx; x++, c++) {
                    int p = r.grid()[c];
                    if (p != Pieces3.AIR) blocks.add(new Placed(x - pad, y - pad, z - pad, states[p]));
                    else if (v.carve != null && v.carve.get(c)) carve.add(new BlockPos(x - pad, y - pad, z - pad));
                }
        return new Content(blocks, carve, v.topColours, null);
    }
}
