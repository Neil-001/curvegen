package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.Settle2D;
import dev.curvegen.core.Solver;
import dev.curvegen.core.paint.Ladder.Why;
import dev.curvegen.core.paint.Paint.Fit;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Paints a solved 2D shape. It reads the solve and never changes it: every piece stays where the solver put it.
 * Lines and outlines wrap the pattern, filled shapes project it, from {@link Wrap2D}, and {@link Ladder} picks each
 * block. A mix needs no wrap: {@link MixLayout} places its blocks by position, through the depth too. Fast enough
 * to run again on every change of a paint setting without solving again. With "Empty spots: Open" a pattern's empty
 * cells become holes, and {@link Settle2D} settles the blocks beside them.
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
        if (paint.source == Paint.Source.MIX) return mix(r, depth, paint, fullConnects, catalog);
        if (paint.source == Paint.Source.GRADIENT) return gradient(r, depth, paint, fullConnects, catalog);
        int nx = r.nx(), ny = r.ny(), d = Math.max(1, depth);
        boolean edge = paint.onFor(d) == Paint.On.EDGE;
        Wrap2D w = Wrap2D.of(r, edge, d);
        Fit fit = paint.fitFor(w.line);
        List<Tiling> tilings = new ArrayList<>();
        for (Tiling.Strip s : w.strips) tilings.add(new Tiling(paint.pattern, paint, fit, s));
        Ladder ladder = new Ladder(catalog, paint.pattern, paint.oneMaterial(), fullConnects, used(r.counts()));

        int layers = edge ? d : 1, per = edge ? 1 : d;
        boolean[] holes = paint.holes();
        long open = 0;
        // Entries are the ladder's blocks, full blocks as each strip turns them: an equation's loops can each be nudged their own way.
        java.util.Map<Long, Integer> entries = new java.util.HashMap<>();
        List<Integer> quarters = new ArrayList<>();
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
                        if (holes != null && holes[cell]) { slots[(k * ny + j) * nx + i] = Painted.OPEN; open += per; continue; }
                        slots[(k * ny + j) * nx + i] = entry(ladder.pick(cell, f), f == Family.FULL ? tilings.get(s).quarters() : 0, entries, quarters);
                        reason = ladder.why(cell, f);
                    }
                    why[f.ordinal()][reason.ordinal()] += per;
                }
            }
        long[][] counts = new long[Family.values().length][quarters.size() + 1];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int piece = r.at(i, j);
                if (piece == Pieces.EMPTY) continue;
                for (int k = 0; k < layers; k++) {
                    int slot = slots[(k * ny + j) * nx + i];
                    if (slot != Painted.OPEN) counts[Pieces.FAMILY[piece].ordinal()][slot + 1] += per;
                }
            }
        String[] names = new String[quarters.size()];
        int[] turns = new int[quarters.size()];
        for (var e : entries.entrySet()) {
            names[e.getValue()] = ladder.blocks().get((int) (e.getKey() >> 2));
            turns[e.getValue()] = (int) (e.getKey() & 3);
        }
        return new Painted(slots, layers, List.of(names), turns, why, counts, open, open == 0 ? null : settle(r, d, slots, layers, fullConnects));
    }

    /** The shape with its holes taken out, every layer of a single-layer paint opening alike. */
    private static Settle2D settle(Solver.Result r, int depth, int[] slots, int layers, boolean fullConnects) {
        int n = r.nx() * r.ny();
        BitSet gone = new BitSet(depth * n);
        for (int k = 0; k < depth; k++)
            for (int c = 0; c < n; c++) if (slots[(layers == 1 ? 0 : k) * n + c] == Painted.OPEN) gone.set(k * n + c);
        return Settle2D.of(r, depth, gone, fullConnects, () -> false);
    }

    /**
     * A mix's blocks. Each layer has its own, so patches are lumps through the depth as well as across the face: the
     * block in column i, row j and layer k sits at (i, j, k) in the shape's box, less the grid's margin round it, so
     * the arrangement stays put when the margin changes. Nothing is turned.
     */
    private static Painted mix(Solver.Result r, int depth, Paint paint, boolean fullConnects, Catalog catalog) {
        int nx = r.nx(), ny = r.ny(), d = Math.max(1, depth), m = r.target().margin;
        Ladder ladder = new Ladder(catalog, paint.mix, paint.oneMaterial(), fullConnects, used(r.counts()));
        MixLayout layout = new MixLayout(paint.mix, paint.arrange, paint.patch, paint.seed);
        int[] slots = new int[d * nx * ny];
        java.util.Arrays.fill(slots, -1);
        long[][] why = new long[Family.values().length][Why.values().length];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int piece = r.at(i, j);
                if (piece == Pieces.EMPTY) continue;
                Family f = Pieces.FAMILY[piece];
                for (int k = 0; k < d; k++) {
                    int cell = layout.at(i - m, j - m, k);
                    slots[(k * ny + j) * nx + i] = ladder.pick(cell, f);
                    why[f.ordinal()][ladder.why(cell, f).ordinal()]++;
                }
            }
        int n = ladder.blocks().size();
        long[][] byEntry = new long[why.length][n + 1];
        for (int k = 0; k < d; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int piece = r.at(i, j);
                    if (piece != Pieces.EMPTY) byEntry[Pieces.FAMILY[piece].ordinal()][slots[(k * ny + j) * nx + i] + 1]++;
                }
        return new Painted(slots, d, List.copyOf(ladder.blocks()), new int[n], why, byEntry);
    }

    /**
     * A gradient's blocks, spanning the shape once. Bottom to top runs up the picture, Along and Across run along a
     * line's centre line and across its width, or across the picture and up it for a filled shape, and Outwards runs
     * from the middle of the box round the blocks. Every layer takes the same blocks, so the steps run straight through
     * the depth. Nothing is turned.
     */
    private static Painted gradient(Solver.Result r, int depth, Paint paint, boolean fullConnects, Catalog catalog) {
        int nx = r.nx(), ny = r.ny(), per = Math.max(1, depth);
        Gradient g = paint.gradient;
        Ladder ladder = new Ladder(catalog, g, paint.oneMaterial(), fullConnects, used(r.counts()));
        Paint.Flow flow = paint.flowFor(true);
        int i0 = nx, j0 = ny, i1 = -1, j1 = -1;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++)
                if (r.at(i, j) != Pieces.EMPTY) { i0 = Math.min(i0, i); i1 = Math.max(i1, i); j0 = Math.min(j0, j); j1 = Math.max(j1, j); }
        Wrap2D w = flow == Paint.Flow.ALONG || flow == Paint.Flow.ACROSS ? Wrap2D.of(r, false, 1) : null;
        double cx = (i0 + i1 + 1) / 2.0, cy = (j0 + j1 + 1) / 2.0, far = 0;
        if (flow == Paint.Flow.OUT)
            for (int j = j0; j <= j1; j++)
                for (int i = i0; i <= i1; i++)
                    if (r.at(i, j) != Pieces.EMPTY) far = Math.max(far, Math.hypot(i + .5 - cx, j + .5 - cy) + .5);
        // One layout per strip for Along and Across, as each of an equation's loops spans the gradient on its own.
        List<GradientLayout> layouts = new ArrayList<>();
        if (w != null) for (Tiling.Strip st : w.strips)
            layouts.add(new GradientLayout(g.paletteSize(), flow == Paint.Flow.ALONG ? st.length() : st.width(), paint.reverse, paint.edges == Paint.Edges.RAGGED));
        else layouts.add(new GradientLayout(g.paletteSize(), flow == Paint.Flow.OUT ? far : j1 - j0 + 1, paint.reverse, paint.edges == Paint.Edges.RAGGED));

        int[] slots = new int[nx * ny];
        java.util.Arrays.fill(slots, -1);
        long[][] why = new long[Family.values().length][Why.values().length];
        int m = r.target().margin;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int piece = r.at(i, j), c = j * nx + i;
                if (piece == Pieces.EMPTY) continue;
                Family f = Pieces.FAMILY[piece];
                double at;
                GradientLayout layout;
                if (w != null) {
                    int s = w.strip[c];
                    if (s < 0) { why[f.ordinal()][Why.BLOCKS_TAB.ordinal()] += per; continue; }
                    layout = layouts.get(s);
                    at = flow == Paint.Flow.ALONG ? w.u[c] : w.v[c];
                } else {
                    layout = layouts.get(0);
                    at = flow == Paint.Flow.OUT ? Math.hypot(i + .5 - cx, j + .5 - cy) : j - j0 + .5;
                }
                int cell = layout.at(at, i - m, j - m, 0);
                slots[c] = ladder.pick(cell, f);
                why[f.ordinal()][ladder.why(cell, f).ordinal()] += per;
            }
        int n = ladder.blocks().size();
        long[][] counts = new long[why.length][n + 1];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int piece = r.at(i, j);
                if (piece != Pieces.EMPTY) counts[Pieces.FAMILY[piece].ordinal()][slots[j * nx + i] + 1] += per;
            }
        return new Painted(slots, 1, List.copyOf(ladder.blocks()), new int[n], why, counts);
    }

    /** The entry for a ladder pick turned by {@code quarter} quarter turns, adding it the first time. -1 stays -1. */
    private static int entry(int pick, int quarter, java.util.Map<Long, Integer> entries, List<Integer> quarters) {
        if (pick < 0) return -1;
        return entries.computeIfAbsent((long) pick << 2 | quarter, unused -> { quarters.add(quarter); return quarters.size() - 1; });
    }

    /** Piece types the solve placed at least one of. */
    static Set<Family> used(int[] counts) {
        Set<Family> out = EnumSet.noneOf(Family.class);
        for (int s = 1; s < Pieces.COUNT; s++) if (counts[s] > 0) out.add(Pieces.FAMILY[s]);
        return out;
    }
}
