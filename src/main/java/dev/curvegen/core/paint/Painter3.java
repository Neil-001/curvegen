package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.Pieces3;
import dev.curvegen.core.Shape3;
import dev.curvegen.core.Solver3;
import dev.curvegen.core.edit.Orient;
import dev.curvegen.core.paint.Ladder.Why;
import dev.curvegen.core.paint.Paint.Mapping;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Painter3 {
    private Painter3() {}

    public static Painted paint(Solver3.Result r, Wrap3 w, Paint paint, boolean fullConnects, Catalog catalog) {
        if (!paint.on()) return null;
        Tiling tiling = new Tiling(paint.pattern, paint, paint.fitFor(false), w.strip);
        Ladder ladder = new Ladder(catalog, paint.pattern, paint.oneMaterial(), fullConnects, used(r.counts()));
        int quarter = w.mapping == Mapping.WRAP ? 0 : tiling.quarters();
        boolean[] holes = paint.holes();
        long open = 0;
        short[] grid = r.grid();
        int[] slots = new int[w.blocks()];
        Map<Integer, Integer> entries = new HashMap<>();
        List<String> names = new ArrayList<>();
        long[][] why = new long[Family.values().length][Why.values().length];
        List<long[]> perEntry = new ArrayList<>();
        long[] base = new long[Family.values().length];
        for (int c = 0, k = 0; c < grid.length; c++) {
            int piece = grid[c];
            if (piece == Pieces3.AIR) continue;
            Family f = Pieces3.FAMILY[piece];
            int cell = tiling.sample(w.u[k], w.v[k]);
            if (holes != null && holes[cell]) { slots[k++] = Painted.OPEN; open++; continue; }
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
            } else base[f.ordinal()]++;
            slots[k++] = e;
            why[f.ordinal()][ladder.why(cell, f).ordinal()]++;
        }
        long[][] counts = new long[Family.values().length][names.size() + 1];
        int[] quarters = new int[names.size()];
        Arrays.fill(quarters, quarter);
        for (Family f : Family.values()) {
            counts[f.ordinal()][0] = base[f.ordinal()];
            for (int e = 0; e < names.size(); e++) counts[f.ordinal()][e + 1] = perEntry.get(e)[f.ordinal()];
        }
        return new Painted(slots, 1, List.copyOf(names), quarters, why, counts, open, null);
    }

    public static Painted mix(Solver3.Result r, Shape3 own, Orient o, Paint paint, boolean fullConnects, Catalog catalog) {
        if (!paint.on()) return null;
        Ladder ladder = new Ladder(catalog, paint.mix, paint.oneMaterial(), fullConnects, used(r.counts()));
        MixLayout layout = new MixLayout(paint.mix, paint.arrange, paint.patch, paint.seed);
        int nx = r.nx(), ny = r.ny(), nz = r.nz(), pad = own.pad();
        int[] axis = new int[3], size = {own.nx(), own.ny(), own.nz()};
        boolean[] back = new boolean[3];
        for (int a = 0; a < 3; a++) { axis[a] = Orient.worldAxis(o.dir(a)); back[a] = Orient.sign(o.dir(a)) < 0; }
        short[] grid = r.grid();
        int blocks = 0;
        for (short c : grid) if (c != Pieces3.AIR) blocks++;
        int[] slots = new int[blocks];
        long[][] why = new long[Family.values().length][Why.values().length];
        int[] world = new int[3], at = new int[3];
        for (int y = 0, c = 0, k = 0; y < ny; y++)
            for (int z = 0; z < nz; z++)
                for (int x = 0; x < nx; x++, c++) {
                    int piece = grid[c];
                    if (piece == Pieces3.AIR) continue;
                    Family f = Pieces3.FAMILY[piece];
                    world[0] = x; world[1] = y; world[2] = z;
                    for (int a = 0; a < 3; a++) at[a] = (back[a] ? size[a] - 1 - world[axis[a]] : world[axis[a]]) - pad;
                    int cell = layout.at(at[0], at[1], at[2]);
                    slots[k++] = ladder.pick(cell, f);
                    why[f.ordinal()][ladder.why(cell, f).ordinal()]++;
                }
        int n = ladder.blocks().size();
        long[][] counts = new long[why.length][n + 1];
        for (int c = 0, k = 0; c < grid.length; c++)
            if (grid[c] != Pieces3.AIR) counts[Pieces3.FAMILY[grid[c]].ordinal()][slots[k++] + 1]++;
        return new Painted(slots, 1, List.copyOf(ladder.blocks()), new int[n], why, counts);
    }

    public static Painted gradient(Solver3.Result r, Shape3 own, Orient o, Wrap3 w, Paint paint, boolean fullConnects, Catalog catalog) {
        if (!paint.on()) return null;
        Gradient g = paint.gradient;
        Ladder ladder = new Ladder(catalog, g, paint.oneMaterial(), fullConnects, used(r.counts()));
        Paint.Flow flow = paint.flowFor(w != null);
        boolean wrapped = flow == Paint.Flow.ALONG || flow == Paint.Flow.ACROSS;
        int nx = r.nx(), ny = r.ny(), nz = r.nz(), pad = own.pad();
        int[] axis = new int[3], size = {own.nx(), own.ny(), own.nz()};
        boolean[] back = new boolean[3];
        for (int a = 0; a < 3; a++) { axis[a] = Orient.worldAxis(o.dir(a)); back[a] = Orient.sign(o.dir(a)) < 0; }
        short[] grid = r.grid();
        int blocks = 0;
        for (short c : grid) if (c != Pieces3.AIR) blocks++;
        int[] own3 = new int[blocks * 3];
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        int[] world = new int[3];
        for (int y = 0, c = 0, k = 0; y < ny; y++)
            for (int z = 0; z < nz; z++)
                for (int x = 0; x < nx; x++, c++) {
                    if (grid[c] == Pieces3.AIR) continue;
                    world[0] = x; world[1] = y; world[2] = z;
                    for (int a = 0; a < 3; a++) {
                        int at = (back[a] ? size[a] - 1 - world[axis[a]] : world[axis[a]]) - pad;
                        own3[k * 3 + a] = at;
                        lo[a] = Math.min(lo[a], at); hi[a] = Math.max(hi[a], at);
                    }
                    k++;
                }
        double[] mid = new double[3];
        for (int a = 0; a < 3; a++) mid[a] = (lo[a] + hi[a] + 1) / 2.0;
        double extent;
        if (wrapped) extent = flow == Paint.Flow.ALONG ? w.strip.length() : w.strip.width();
        else if (flow == Paint.Flow.UP) extent = blocks == 0 ? 1 : hi[1] - lo[1] + 1;
        else {
            extent = 0;
            for (int k = 0; k < blocks; k++) extent = Math.max(extent, out(own3, k, mid) + .5);
        }
        GradientLayout layout = new GradientLayout(g.paletteSize(), extent, paint.reverse, paint.edges == Paint.Edges.RAGGED);
        int[] slots = new int[blocks];
        long[][] why = new long[Family.values().length][Why.values().length];
        for (int c = 0, k = 0; c < grid.length; c++) {
            if (grid[c] == Pieces3.AIR) continue;
            Family f = Pieces3.FAMILY[grid[c]];
            double at = switch (flow) {
                case ALONG -> w.u[k];
                case ACROSS -> w.v[k];
                case UP -> own3[k * 3 + 1] - lo[1] + .5;
                case OUT -> out(own3, k, mid);
            };
            int cell = layout.at(at, own3[k * 3], own3[k * 3 + 1], own3[k * 3 + 2]);
            slots[k++] = ladder.pick(cell, f);
            why[f.ordinal()][ladder.why(cell, f).ordinal()]++;
        }
        int n = ladder.blocks().size();
        long[][] counts = new long[why.length][n + 1];
        for (int c = 0, k = 0; c < grid.length; c++)
            if (grid[c] != Pieces3.AIR) counts[Pieces3.FAMILY[grid[c]].ordinal()][slots[k++] + 1]++;
        return new Painted(slots, 1, List.copyOf(ladder.blocks()), new int[n], why, counts);
    }

    private static double out(int[] own3, int k, double[] mid) {
        double dx = own3[k * 3] + .5 - mid[0], dy = own3[k * 3 + 1] + .5 - mid[1], dz = own3[k * 3 + 2] + .5 - mid[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static Set<Family> used(int[] counts) {
        Set<Family> out = EnumSet.noneOf(Family.class);
        for (int s = 1; s < Pieces3.COUNT; s++) if (counts[s] > 0) out.add(Pieces3.FAMILY[s]);
        return out;
    }
}
