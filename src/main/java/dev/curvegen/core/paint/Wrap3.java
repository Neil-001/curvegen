package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces3;
import dev.curvegen.core.Shape3;
import dev.curvegen.core.Solver3;
import dev.curvegen.core.edit.Orient;
import dev.curvegen.core.paint.Paint.Mapping;
import dev.curvegen.core.paint.Tiling.Strip;
import java.util.stream.IntStream;

/**
 * Where each block of a solved 3D shape lies on the pattern's one strip. Wrap takes it from the shape's own
 * {@link Shape3.Wrapping}. The three projections lay the pattern flat on the box round the blocks, seen from above,
 * from the front or from the player's right, and carry it straight through: {@code u} runs to the right as seen from
 * there, and {@code v} up, or away from the player from above.
 *
 * <p>The solve is in the world's axes, and the shape's own axes point however {@link Orient} says. An editor's own
 * axes are x to the right, y up and z away from the player, which no turn of the shape changes, and the shapes wrap a
 * pattern unmirrored in those. A shape set out the other way round, as the menu's picture and an export without a
 * hologram are, gets its {@code u} reversed, so the pattern reads the same in the world either way.
 *
 * <p>It depends only on the solve and the mapping, so a repaint for another turn, slide or pattern can reuse it.
 */
public final class Wrap3 {
    /** Per block, in the solve's grid order with air left out: its place on the strip. */
    public final float[] u, v;
    public final Strip strip;
    public final Mapping mapping;
    /**
     * For a projection, the directions in the world, numbered as {@link Orient}'s, that the pattern's right and up
     * point: the pattern turns anticlockwise as seen from {@code right × up}. -1 for Wrap, which never turns a block.
     */
    public final int right, up;

    private Wrap3(int blocks, Strip strip, Mapping mapping, int right, int up) {
        u = new float[blocks]; v = new float[blocks];
        this.strip = strip; this.mapping = mapping; this.right = right; this.up = up;
    }

    /** How many blocks are in the solve, which is how many places there are. */
    public int blocks() { return u.length; }

    /**
     * Places every block of {@code r}, a solve of {@code own} turned by {@code o}. {@code mapping} must be one the
     * shape can use: Wrap needs {@link Shape3#wrapping}.
     */
    public static Wrap3 of(Solver3.Result r, Shape3 own, Orient o, Mapping mapping) {
        return of(r, own, mapping == Mapping.WRAP ? own.wrapping() : null, o, mapping);
    }

    /** The same, with the shape's wrapping already worked out, or null for a projection. */
    public static Wrap3 of(Solver3.Result r, Shape3 own, Shape3.Wrapping w, Orient o, Mapping mapping) {
        int nx = r.nx(), ny = r.ny(), nz = r.nz();
        short[] grid = r.grid();
        // Blocks before each level, so levels can be placed in parallel.
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
            double len = w.length();
            IntStream.range(0, ny).parallel().forEach(y -> {
                double[] world = new double[3], at = new double[2];
                int k = start[y];
                for (int z = 0, c = y * nz * nx; z < nz; z++)
                    for (int x = 0; x < nx; x++, c++) {
                        if (grid[c] == Pieces3.AIR) continue;
                        world[0] = x + .5; world[1] = y + .5; world[2] = z + .5;
                        w.place(own(world, 0, axis, back, size), own(world, 1, axis, back, size), own(world, 2, axis, back, size), at);
                        out.u[k] = (float) (flip ? len - at[0] : at[0]);
                        out.v[k] = (float) at[1];
                        k++;
                    }
            });
            return out;
        }

        // Projections: the two own axes the pattern lies along, and the box round the blocks along them.
        int ua = mapping == Mapping.SIDE ? 2 : 0, va = mapping == Mapping.ABOVE ? 2 : 1;
        double[] lo = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY}, hi = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double[] world = new double[3];
        for (int y = 0, c = 0; y < ny; y++)
            for (int z = 0; z < nz; z++)
                for (int x = 0; x < nx; x++, c++) {
                    if (grid[c] == Pieces3.AIR) continue;
                    world[0] = x; world[1] = y; world[2] = z;
                    // The block's own lowest corner along each of the two axes.
                    double pu = own(world, ua, axis, back, size) - (back[ua] ? 1 : 0), pv = own(world, va, axis, back, size) - (back[va] ? 1 : 0);
                    lo[0] = Math.min(lo[0], pu); hi[0] = Math.max(hi[0], pu + 1);
                    lo[1] = Math.min(lo[1], pv); hi[1] = Math.max(hi[1], pv + 1);
                }
        if (start[ny] == 0) { lo[0] = lo[1] = 0; hi[0] = hi[1] = 1; }
        double len = hi[0] - lo[0];
        int right = flip ? Orient.opposite(o.dir(ua)) : o.dir(ua), up = o.dir(va);
        Wrap3 out = new Wrap3(start[ny], new Strip(len, hi[1] - lo[1], false), mapping, right, up);
        IntStream.range(0, ny).parallel().forEach(y -> {
            double[] p = new double[3];
            int k = start[y];
            for (int z = 0, c = y * nz * nx; z < nz; z++)
                for (int x = 0; x < nx; x++, c++) {
                    if (grid[c] == Pieces3.AIR) continue;
                    p[0] = x + .5; p[1] = y + .5; p[2] = z + .5;
                    double pu = own(p, ua, axis, back, size) - lo[0];
                    out.u[k] = (float) (flip ? len - pu : pu);
                    out.v[k] = (float) (own(p, va, axis, back, size) - lo[1]);
                    k++;
                }
        });
        return out;
    }

    /** A point's coordinate along own axis {@code a}, from where it is in the world box. As {@link Orient#toOwn}. */
    private static double own(double[] world, int a, int[] axis, boolean[] back, int[] size) {
        double w = world[axis[a]];
        return back[a] ? size[a] - w : w;
    }

    /**
     * +1 when the own axes x, y, z make a right-handed set in the world, as the world's own axes do, and -1 when they
     * make a left-handed one, as an editor's x to the right, y up and z away from the player always do.
     */
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
