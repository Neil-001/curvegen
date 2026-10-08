package dev.curvegen.client.edit;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.core.Pieces;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.Solver;
import dev.curvegen.core.edit.Edit2D;
import dev.curvegen.core.edit.Option;
import dev.curvegen.core.edit.Orient;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The three 2D shapes in the in-world editor. The box is width × height × depth, and a Bézier curve's points stay in
 * the drawing's plane. Upright, own y points up and own z runs away from the player. Flat, own y runs away from the
 * player and own z points up, so layers stack upwards.
 */
final class Shape2D implements EditShape {
    private final ShapeSettings s;

    Shape2D(ShapeSettings s) { this.s = s; }

    /** A solver result with the box it was solved for, which the settings may since have moved on from. */
    private record Solved(Solver.Result result, int[] size, int depth) {
        /** How many cells the grid extends past the box on each side. Only an ellipse's does, for a wall that grows outwards. */
        int margin() { return (result.nx() - size[0]) / 2; }
    }

    @Override public int[] size() { return Edit2D.size(s); }
    @Override public void resize(int[] want, boolean[] dragged) { Edit2D.resize(s, want, dragged); }
    @Override public int[] bump(int axis, int side, int amount) { return Edit2D.bumpPoints(s, axis, side, amount); }
    @Override public List<double[]> points() { return Edit2D.points(s); }
    @Override public int pointPlane() { return 2; }
    @Override public int[] movePoint(int index, double[] to) { return Edit2D.movePoint(s, index, to); }
    @Override public boolean removePoint(int index) { return Edit2D.removePoint(s, index); }
    @Override public int insertPoint(double[] at) { return Edit2D.insertPoint(s, at); }
    @Override public int duplicatePoint(int index) { return Edit2D.duplicatePoint(s, index); }
    @Override public boolean solveUsesOrient() { return false; }
    @Override public List<Option> options() { return Edit2D.options(s); }

    @Override
    public Orient orient(Orient o) {
        // A 3D shape can be turned any way up before the menu swaps it for a drawing, so either axis may be the vertical one.
        int[] across = {o.x(), o.y(), o.z()}, away = {o.z(), o.y(), o.x()};
        int right = -1, forward = -1;
        for (int d : across) if (right < 0 && Orient.worldAxis(d) != 1) right = d;
        for (int d : away) if (forward < 0 && Orient.worldAxis(d) != 1 && Orient.worldAxis(d) != Orient.worldAxis(right)) forward = d;
        return s.floor ? new Orient(right, forward, Orient.UP) : new Orient(right, Orient.UP, forward);
    }

    /** Tipping a drawing switches it between upright and flat. */
    @Override
    public Orient tip(Orient current, int forward) {
        s.floor = !s.floor;
        return orient(current);
    }

    @Override
    public double[] curve(Object solved) {
        double[] direct = Edit2D.curve(s);
        if (direct != null || !(solved instanceof Solved v) || v.result.target().error != null) return direct;
        return Edit2D.overlay(v.result.target().overlay, v.margin(), v.size, size());
    }

    @Override
    public Object solve(ShapeSettings copy, Orient orient, BooleanSupplier cancelled) {
        Solver.Result r = Solver.run(copy);   // settles the equation's locked height in the copy
        return new Solved(r, Edit2D.size(copy), Math.max(1, copy.depth));
    }

    @Override
    public Content build(Object solved, Orient o) {
        Solved v = (Solved) solved;
        Solver.Result r = v.result;
        if (r.target().error != null) return new Content(List.of(), List.of(), r.floor(), r.target().error);
        boolean floor = r.floor();
        // The solve may be for the other of upright and flat than the settings are by now. Lay it out its own way.
        Orient at = floor == (Orient.worldAxis(o.z()) == 1) ? o : floor ? new Orient(o.x(), o.z(), Orient.UP) : new Orient(o.x(), Orient.UP, o.y());
        Direction right = Direction.from3DDataValue(at.x()), forward = Direction.from3DDataValue(floor ? at.y() : at.z());
        int m = v.margin(), depth = v.depth;
        int[] size = {v.size[0], r.ny() - 2 * m, depth};
        List<Placed> blocks = new ArrayList<>();
        List<BlockPos> carve = new ArrayList<>();
        boolean[] cv = r.target().carve;
        for (int j = 0; j < r.ny(); j++)
            for (int i = 0; i < r.nx(); i++) {
                int p = r.at(i, j);
                if (p == Pieces.EMPTY && (cv == null || !cv[j * r.nx() + i])) continue;
                int above = j + 1 < r.ny() ? r.at(i, j + 1) : Pieces.EMPTY;
                for (int k = 0; k < depth; k++) {
                    int[] w = at.cell(i - m, j - m, k, size);
                    if (p == Pieces.EMPTY) { carve.add(new BlockPos(w[0], w[1], w[2])); continue; }
                    BlockState state = BlockChoices.stateFor(p, above, right, forward, k, depth, floor);
                    blocks.add(new Placed(w[0], w[1], w[2], state));
                }
            }
        return new Content(blocks, carve, floor, null);
    }

    @Override
    public String describe() {
        int[] z = size();
        String name = s.gen == Gen.ELLIPSE ? "Ellipse" : s.gen == Gen.EQUATION ? "Equation" : "Bézier curve";
        return name + ", " + (s.floor ? "flat: " + z[0] + " by " + z[1] + ", " + z[2] + (z[2] == 1 ? " layer" : " layers") + " high"
                : z[0] + " wide, " + z[1] + " tall, " + z[2] + " deep");
    }
}
