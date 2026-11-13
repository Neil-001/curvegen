package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.Solver;
import dev.curvegen.core.paint.Ladder.Why;
import dev.curvegen.core.paint.Paint.Fit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Paints a solved 2D shape. It reads the solve and never changes it: every piece stays where the solver put it.
 * Lines and outlines wrap the pattern, filled shapes project it, from {@link Wrap2D}, and {@link Ladder} picks each
 * block. Fast enough to run again on every change of a paint setting without solving again.
 */
public final class Painter2D {
    private Painter2D() {}

    /**
     * The blocks for {@code r} under {@code paint}, or null when paint is off or the shape has an error.
     *
     * @param depth         how many layers deep the shape is built
     * @param fullConnects  the setting the shape was solved with
     */
    public static Painted paint(Solver.Result r, int depth, Paint paint, boolean fullConnects, Catalog catalog) {
        if (!paint.on() || r.target().error != null) return null;
        int nx = r.nx(), ny = r.ny(), d = Math.max(1, depth);
        boolean edge = paint.onFor(d) == Paint.On.EDGE;
        Wrap2D w = Wrap2D.of(r, edge, d);
        Fit fit = paint.fitFor(w.line);
        List<Tiling> tilings = new ArrayList<>();
        for (Tiling.Strip s : w.strips) tilings.add(new Tiling(paint.pattern, paint, fit, s));
        Ladder ladder = new Ladder(catalog, paint.pattern, paint.oneMaterial(), fullConnects, used(r.counts()));

        int layers = edge ? d : 1, per = edge ? 1 : d;
        int[] slots = new int[layers * nx * ny];
        java.util.Arrays.fill(slots, -1);
        long[][] why = new long[Family.values().length][Why.values().length];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int piece = r.at(i, j), c = j * nx + i;
                if (piece == Pieces.EMPTY) continue;
                Family f = Pieces.FAMILY[piece];
                int s = w.strip[c];
                for (int k = 0; k < layers; k++) {
                    Why reason = Why.BLOCKS_TAB;
                    if (s >= 0) {
                        int cell = tilings.get(s).sample(w.u[c], edge ? k + .5 : w.v[c]);
                        slots[(k * ny + j) * nx + i] = ladder.pick(cell, f);
                        reason = ladder.why(cell, f);
                    }
                    why[f.ordinal()][reason.ordinal()] += per;
                }
            }
        long[][] counts = new long[Family.values().length][ladder.blocks().size() + 1];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int piece = r.at(i, j);
                if (piece == Pieces.EMPTY) continue;
                for (int k = 0; k < layers; k++) counts[Pieces.FAMILY[piece].ordinal()][slots[(k * ny + j) * nx + i] + 1] += per;
            }
        // Blocks turn with the pattern as it was laid, nudge included. Pieces of an equation each nudge on their own; the longest decides.
        int quarters = Math.floorMod(Math.round(paint.turn / 90f), 4);
        double longest = -1;
        for (int k = 0; k < tilings.size(); k++)
            if (w.strips.get(k).length() > longest) { longest = w.strips.get(k).length(); quarters = tilings.get(k).quarters(); }
        return new Painted(slots, layers, List.copyOf(ladder.blocks()), quarters, why, counts);
    }

    /** Piece types the solve placed at least one of. */
    static Set<Family> used(int[] counts) {
        Set<Family> out = EnumSet.noneOf(Family.class);
        for (int s = 1; s < Pieces.COUNT; s++) if (counts[s] > 0) out.add(Pieces.FAMILY[s]);
        return out;
    }
}
