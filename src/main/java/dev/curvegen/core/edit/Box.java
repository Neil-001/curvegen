package dev.curvegen.core.edit;

public record Box(int x, int y, int z, int sx, int sy, int sz) {
    public int min(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
    public int size(int axis) { return axis == 0 ? sx : axis == 1 ? sy : sz; }
    public int max(int axis) { return min(axis) + size(axis); }
    public int[] size() { return new int[]{sx, sy, sz}; }

    public static Box of(int[] min, int[] size) { return new Box(min[0], min[1], min[2], size[0], size[1], size[2]); }

    public Box moved(int dx, int dy, int dz) { return new Box(x + dx, y + dy, z + dz, sx, sy, sz); }

    public static Box spawn(int ax, int ay, int az, int[] size) {
        return new Box(ax - size[0] / 2, ay, az - size[2] / 2, size[0], size[1], size[2]);
    }

    public int[] pivot() { return new int[]{2 * x + sx, 2 * z + sz}; }

    public Box about(int[] pivot, int[] size) {
        return new Box(Math.floorDiv(pivot[0] - size[0], 2), y, Math.floorDiv(pivot[1] - size[2], 2), size[0], size[1], size[2]);
    }

    public Box refit(int[] size) { return fit(new int[3], false, size); }

    public static int[][] handles() {
        int[][] out = new int[26][];
        int n = 0;
        for (int a = -1; a <= 1; a++)
            for (int b = -1; b <= 1; b++)
                for (int c = -1; c <= 1; c++)
                    if (a != 0 || b != 0 || c != 0) out[n++] = new int[]{a, b, c};
        return out;
    }

    public static int[][] handles(int[] size, double gap) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int[] h : handles()) {
            boolean keep = true;
            for (int a = 0; a < 3 && keep; a++)
                if (h[a] != 0 && size[a] / 2.0 < gap) keep = h[(a + 1) % 3] == 0 && h[(a + 2) % 3] == 0;
            if (keep) out.add(h);
        }
        return out.toArray(new int[0][]);
    }

    public double[] handle(int[] sign) {
        double[] p = new double[3];
        for (int a = 0; a < 3; a++) p[a] = min(a) + size(a) * (sign[a] + 1) / 2.0;
        return p;
    }

    public int[] dragSize(int[] sign, double[] target, boolean symmetric) {
        int[] out = size();
        for (int a = 0; a < 3; a++) {
            if (sign[a] == 0 || Double.isNaN(target[a])) continue;
            long face = Math.round(target[a]);
            long grow = sign[a] > 0 ? face - max(a) : min(a) - face;
            long size = size(a) + (symmetric ? 2 * grow : grow);
            long least = symmetric ? 2 - (size(a) & 1) : 1;
            out[a] = (int) Math.max(least, Math.min(Integer.MAX_VALUE / 4, size));
        }
        return out;
    }

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
