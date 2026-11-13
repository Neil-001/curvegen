package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.Pieces3;
import dev.curvegen.core.Shape3;
import dev.curvegen.core.Solver3;
import dev.curvegen.core.edit.Orient;
import dev.curvegen.core.paint.Paint.Mapping;

/**
 * One 3D solve, ready to be painted again and again. It keeps the shape's wrapping and the last {@link Wrap3}, so a
 * repaint for another turn, slide, size or pattern only runs {@link Painter3}. Safe to share between threads.
 * The tests' {@code Paint3Timing} has the figures.
 */
public final class Solve3Painter {
    /** Above this many blocks a {@link Wrap3} isn't kept: it takes 8 bytes a block, and placing again is quick. */
    private static final int KEEP_UP_TO = 2_000_000;

    /** A painted solve: its blocks, and which way the pattern's right and up point, as in {@link Wrap3}. */
    public record Done(Painted painted, int right, int up) {}

    public final Solver3.Result result;
    private final Shape3 own;
    private final Orient orient;
    private Shape3.Wrapping wrapping;
    private boolean wrapped;
    private Wrap3 last;

    /** {@code result} is a solve of {@code own} turned by {@code orient}. */
    public Solve3Painter(Solver3.Result result, Shape3 own, Orient orient) {
        this.result = result; this.own = own; this.orient = orient;
    }

    /** Whether a pattern can wrap round the shape, which a 3D equation can't. */
    public synchronized boolean wraps() {
        if (!wrapped) { wrapping = own.error() == null ? own.wrapping() : null; wrapped = true; }
        return wrapping != null;
    }

    /** Paints the solve, or returns null while paint is off or the shape has an error. */
    public Done paint(Paint paint, boolean fullConnects, Catalog catalog) {
        if (!paint.on() || own.error() != null) return null;
        Wrap3 w = wrap(paint.mappingFor(wraps()));
        return new Done(Painter3.paint(result, w, paint, fullConnects, catalog), w.right, w.up);
    }

    private synchronized Wrap3 wrap(Mapping m) {
        if (last != null && last.mapping == m) return last;
        Wrap3 w = Wrap3.of(result, own, m == Mapping.WRAP ? wrapping : null, orient, m);
        last = w.blocks() <= KEEP_UP_TO ? w : null;
        return w;
    }

    /**
     * Per block, in the order of {@link Painted}'s slots, an index into a palette that holds a colour per piece type
     * by ordinal and then one per painted entry: what the picture colours each block by.
     */
    public static int[] colourIndex(Solver3.Result r, Painted p) {
        int families = Family.values().length;
        int[] out = new int[p.slots().length];
        short[] grid = r.grid();
        for (int c = 0, k = 0; c < grid.length; c++) {
            if (grid[c] == Pieces3.AIR) continue;
            int e = p.slots()[k];
            out[k++] = e >= 0 ? families + e : Pieces3.FAMILY[grid[c]].ordinal();
        }
        return out;
    }
}
