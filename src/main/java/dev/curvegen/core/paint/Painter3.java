package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.Pieces3;
import dev.curvegen.core.Solver3;
import dev.curvegen.core.paint.Ladder.Why;
import dev.curvegen.core.paint.Paint.Mapping;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Paints a solved 3D shape. Like {@link Painter2D} it reads the solve and never changes it. {@link Wrap3} says where
 * each block lies on the pattern, and {@link Ladder} picks each block.
 *
 * <p>A 3D shape's {@link Painted} has one layer and one slot per block of the solve, in the grid's order
 * ({@code (y * nz + z) * nx + x}) with air left out. Wrap never turns a full block; a projection turns it with the
 * pattern, as seen from {@link Wrap3#right} × {@link Wrap3#up}.
 */
public final class Painter3 {
    private Painter3() {}

    /** The blocks for {@code r} under {@code paint}, placed by {@code w}, or null when paint is off. */
    public static Painted paint(Solver3.Result r, Wrap3 w, Paint paint, boolean fullConnects, Catalog catalog) {
        if (!paint.on()) return null;
        Tiling tiling = new Tiling(paint.pattern, paint, paint.fitFor(false), w.strip);
        Ladder ladder = new Ladder(catalog, paint.pattern, paint.oneMaterial(), fullConnects, used(r.counts()));
        int quarter = w.mapping == Mapping.WRAP ? 0 : tiling.quarters();
        short[] grid = r.grid();
        int[] slots = new int[w.blocks()];
        // Entries are the ladder's picks in the order first seen. Every full block turns the same way, so a pick is one entry.
        Map<Integer, Integer> entries = new HashMap<>();
        List<String> names = new ArrayList<>();
        long[][] why = new long[Family.values().length][Why.values().length];
        List<long[]> perEntry = new ArrayList<>();
        long[][] base = new long[Family.values().length][1];
        for (int c = 0, k = 0; c < grid.length; c++) {
            int piece = grid[c];
            if (piece == Pieces3.AIR) continue;
            Family f = Pieces3.FAMILY[piece];
            int cell = tiling.sample(w.u[k], w.v[k]);
            int pick = ladder.pick(cell, f), e = -1;
            if (pick >= 0) {
                Integer known = entries.get(pick);
                if (known == null) {
                    known = names.size();
                    entries.put(pick, known);
                    names.add(ladder.blocks().get(pick));
                    perEntry.add(new long[Family.values().length]);
                }
                e = known;
                perEntry.get(e)[f.ordinal()]++;
            } else base[f.ordinal()][0]++;
            slots[k++] = e;
            why[f.ordinal()][ladder.why(cell, f).ordinal()]++;
        }
        long[][] counts = new long[Family.values().length][names.size() + 1];
        int[] quarters = new int[names.size()];
        java.util.Arrays.fill(quarters, quarter);
        for (Family f : Family.values()) {
            counts[f.ordinal()][0] = base[f.ordinal()][0];
            for (int e = 0; e < names.size(); e++) counts[f.ordinal()][e + 1] = perEntry.get(e)[f.ordinal()];
        }
        return new Painted(slots, 1, List.copyOf(names), quarters, why, counts);
    }

    /** Piece types the solve placed at least one of. */
    static Set<Family> used(int[] counts) {
        Set<Family> out = EnumSet.noneOf(Family.class);
        for (int s = 1; s < Pieces3.COUNT; s++) if (counts[s] > 0) out.add(Pieces3.FAMILY[s]);
        return out;
    }
}
