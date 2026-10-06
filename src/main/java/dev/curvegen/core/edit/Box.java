package dev.curvegen.core.edit;

/** A box of whole blocks in world axes: the minimum corner's block and the size along x, y and z. */
public record Box(int x, int y, int z, int sx, int sy, int sz) {
    public int min(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
    public int size(int axis) { return axis == 0 ? sx : axis == 1 ? sy : sz; }
    public int max(int axis) { return min(axis) + size(axis); }
    public int[] size() { return new int[]{sx, sy, sz}; }

    public static Box of(int[] min, int[] size) { return new Box(min[0], min[1], min[2], size[0], size[1], size[2]); }

    public Box moved(int dx, int dy, int dz) { return new Box(x + dx, y + dy, z + dz, sx, sy, sz); }

    /** Sitting on the anchor block and centred on it sideways, which is where a new shape appears. */
    public static Box spawn(int ax, int ay, int az, int[] size) {
        return new Box(ax - size[0] / 2, ay, az - size[2] / 2, size[0], size[1], size[2]);
    }

    /** The same place with a new size: it keeps its centre sideways and stays on its bottom. For turning and tipping. */
    public Box refit(int[] size) { return fit(new int[3], false, size); }

    /**
     * The 26 handles are numbered by which side they sit on along each axis: -1, 0 or 1. One non-zero sign is a face
     * centre, two an edge midpoint and three a corner.
     */
    public static int[][] handles() {
        int[][] out = new int[26][];
        int n = 0;
        for (int a = -1; a <= 1; a++)
            for (int b = -1; b <= 1; b++)
                for (int c = -1; c <= 1; c++)
                    if (a != 0 || b != 0 || c != 0) out[n++] = new int[]{a, b, c};
        return out;
    }

    /** Where a handle sits, in world coordinates. */
    public double[] handle(int[] sign) {
        double[] p = new double[3];
        for (int a = 0; a < 3; a++) p[a] = min(a) + size(a) * (sign[a] + 1) / 2.0;
        return p;
    }

    /**
     * The size a drag asks for. {@code target} is where the handle should be on each axis it moves along, and
     * {@code Double.NaN} on the others. Sizes move in whole blocks and never go below one. The opposite side stays
     * put, or with {@code symmetric} the centre does and the far side mirrors the handle.
     */
    public int[] dragSize(int[] sign, double[] target, boolean symmetric) {
        int[] out = size();
        for (int a = 0; a < 3; a++) {
            if (sign[a] == 0 || Double.isNaN(target[a])) continue;
            long face = Math.round(target[a]);
            long grow = sign[a] > 0 ? face - max(a) : min(a) - face;
            long size = size(a) + (symmetric ? 2 * grow : grow);
            // A symmetric drag changes the size two blocks at a time, so it stops at 1 or 2 without flipping parity.
            long least = symmetric ? 2 - (size(a) & 1) : 1;
            out[a] = (int) Math.max(least, Math.min(Integer.MAX_VALUE / 4, size));
        }
        return out;
    }

    /**
     * This box resized to {@code size} after a drag on the handle {@code sign}. Along an axis the handle moves on, the
     * opposite side stays put (or the centre, when symmetric). An axis that changed without being dragged, as an
     * equation's height does under the same-scale lock, keeps its centre sideways and its bottom vertically.
     */
    public Box fit(int[] sign, boolean symmetric, int[] size) {
        int[] min = new int[3];
        for (int a = 0; a < 3; a++) {
            int old = size(a), now = size[a];
            if (now == old) min[a] = min(a);
            else if (sign[a] != 0 && !symmetric) min[a] = sign[a] > 0 ? min(a) : max(a) - now;
            else if (sign[a] == 0 && a == 1) min[a] = min(a);
            else min[a] = min(a) - Math.floorDiv(now - old, 2);
        }
        return of(min, size);
    }

    /**
     * This box after its shape's own minimum corner moved by {@code ownShift} cells and its own size became
     * {@code ownSize}. A Bézier curve does this when a point is dragged past the edge of its grid.
     */
    public Box regrown(Orient o, int[] ownShift, int[] ownSize) {
        int[] min = new int[3], size = new int[3];
        for (int a = 0; a < 3; a++) {
            int w = Orient.worldAxis(o.dir(a));
            size[w] = ownSize[a];
            min[w] = Orient.sign(o.dir(a)) > 0 ? min(w) + ownShift[a] : max(w) - ownShift[a] - ownSize[a];
        }
        return of(min, size);
    }
}
