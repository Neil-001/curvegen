package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces3;
import dev.curvegen.core.Shape3;
import dev.curvegen.core.Solver3;
import dev.curvegen.core.edit.Orient;
import dev.curvegen.core.paint.Paint.Mapping;
import dev.curvegen.core.paint.Tiling.Strip;
import java.util.stream.IntStream;

public final class Wrap3 {
    public final float[] u, v;
    public final Strip strip;
    public final Mapping mapping;
    public final int right, up;

    private Wrap3(int blocks, Strip strip, Mapping mapping, int right, int up) {
        u = new float[blocks]; v = new float[blocks];
        this.strip = strip; this.mapping = mapping; this.right = right; this.up = up;
    }

    public int blocks() { return u.length; }

    public static Wrap3 of(Solver3.Result r, Shape3 own, Orient o, Mapping mapping) {
        return of(r, own, mapping == Mapping.WRAP ? own.wrapping() : null, o, mapping);
    }

    public static Wrap3 of(Solver3.Result r, Shape3 own, Shape3.Wrapping w, Orient o, Mapping mapping) {
        int nx = r.nx(), ny = r.ny(), nz = r.nz();
        short[] grid = r.grid();
        int[] start = new int[ny + 1];
        for (int y = 0; y < ny; y++) {
            int n = 0;
            for (int c = y * nz * nx, e = c + nz * nx; c < e; c++) if (grid[c] != Pieces3.AIR) n++;
            start[y + 1] = start[y] + n;
        }
        int[] axis = new int[3], size = {own.nx(), own.ny(), own.nz()};
        boolean[] back = new boolean[3];
        for (int a = 0; a < 3; a++) { axis[a] = Orient.worldAxis(o.dir(a)); back[a] = Orient.sign(o.dir(a)) < 0; }
        boolean flip = handedness(o) > 0;

        if (mapping == Mapping.WRAP) {
            Wrap3 out = new Wrap3(start[ny], new Strip(w.length(), w.width(), w.closedAlong(), w.closedAcross()), mapping, -1, -1);
            out.fill(r, start, flip, (p, at) ->
                    w.place(own(p, 0, axis, back, size), own(p, 1, axis, back, size), own(p, 2, axis, back, size), at));
            return out;
        }

        int ua = mapping == Mapping.SIDE ? 2 : 0, va = mapping == Mapping.ABOVE ? 2 : 1;
        double[] lo = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY}, hi = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double[] world = new double[3];
        for (int y = 0, c = 0; y < ny; y++)
            for (int z = 0; z < nz; z++)
                for (int x = 0; x < nx; x++, c++) {
                    if (grid[c] == Pieces3.AIR) continue;
                    world[0] = x; world[1] = y; world[2] = z;
                    double pu = own(world, ua, axis, back, size) - (back[ua] ? 1 : 0), pv = own(world, va, axis, back, size) - (back[va] ? 1 : 0);
                    lo[0] = Math.min(lo[0], pu); hi[0] = Math.max(hi[0], pu + 1);
                    lo[1] = Math.min(lo[1], pv); hi[1] = Math.max(hi[1], pv + 1);
                }
        if (start[ny] == 0) { lo[0] = lo[1] = 0; hi[0] = hi[1] = 1; }
        double len = hi[0] - lo[0];
        int right = flip ? Orient.opposite(o.dir(ua)) : o.dir(ua), up = o.dir(va);
        Wrap3 out = new Wrap3(start[ny], new Strip(len, hi[1] - lo[1], false), mapping, right, up);
        out.fill(r, start, flip, (p, at) -> {
            at[0] = own(p, ua, axis, back, size) - lo[0];
            at[1] = own(p, va, axis, back, size) - lo[1];
        });
        return out;
    }

    private interface Placer { void place(double[] world, double[] at); }

    private void fill(Solver3.Result r, int[] start, boolean flip, Placer placer) {
        int nx = r.nx(), nz = r.nz();
        short[] grid = r.grid();
        double len = strip.length();
        IntStream.range(0, r.ny()).parallel().forEach(y -> {
            double[] p = new double[3], at = new double[2];
            int k = start[y];
            for (int z = 0, c = y * nz * nx; z < nz; z++)
                for (int x = 0; x < nx; x++, c++) {
                    if (grid[c] == Pieces3.AIR) continue;
                    p[0] = x + .5; p[1] = y + .5; p[2] = z + .5;
                    placer.place(p, at);
                    u[k] = (float) (flip ? len - at[0] : at[0]);
                    v[k] = (float) at[1];
                    k++;
                }
        });
    }

    private static double own(double[] world, int a, int[] axis, boolean[] back, int[] size) {
        double w = world[axis[a]];
        return back[a] ? size[a] - w : w;
    }

    public static int handedness(Orient o) {
        double[][] m = new double[3][];
        for (int a = 0; a < 3; a++) {
            double[] d = new double[3];
            d[Orient.worldAxis(o.dir(a))] = Orient.sign(o.dir(a));
            m[a] = d;
        }
        double det = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
        return det > 0 ? 1 : -1;
    }
}
