package dev.curvegen.core;

/**
 * A settled 2D shape as a 3D solve, for render.py's pictures, laid out as {@link Settle2D} lays it out: the drawing's
 * x runs east, and upright its y runs up and its layers south, while flat its y runs north and its layers stack up.
 * It lives in the core's package to read the settled states.
 */
public final class Built2D {
    private Built2D() {}

    public static Solver3.Result of(Solver.Result r, Settle2D s, int depth) {
        int nx = r.nx(), ny = r.ny(), d = Math.max(1, depth);
        boolean floor = r.floor();
        int sy = floor ? d : ny, sz = floor ? ny : d;
        short[] grid = new short[nx * sy * sz];
        int[] counts = new int[Pieces3.COUNT];
        for (int k = 0; k < d; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) grid[Settle2D.cell(i, j, k, nx, ny, sz, floor)] = (short) s.state(i, j, k);
        for (short v : grid) counts[v]++;
        return new Solver3.Result(grid, nx, sy, sz, r.err(), r.area(), counts, null, 0);
    }

    /** The drawing's column, row and layer of the block at x, y and z. */
    public static int[] drawing(int x, int y, int z, int nx, int ny, boolean floor) {
        return floor ? new int[]{x, ny - 1 - z, y} : new int[]{x, y, z};
    }
}
