package dev.curvegen.core;

import java.util.Arrays;

final class Near3 {
    @FunctionalInterface interface Dist { double from(int item, double x, double y, double z); }

    static final int SIZE = 2;
    private static final double R = SIZE * Shape3.R_CELL;

    private final int gx, gy, gz;
    private final int[][] lists;
    private final int[] counts;

    Near3(int nx, int ny, int nz, int items, double[] bounds, double reach, Dist dist) {
        gx = (nx + SIZE - 1) / SIZE; gy = (ny + SIZE - 1) / SIZE; gz = (nz + SIZE - 1) / SIZE;
        lists = new int[gx * gy * gz][];
        counts = new int[lists.length];
        float[] least = new float[lists.length];
        Arrays.fill(least, Float.POSITIVE_INFINITY);
        double grow = reach + R;
        for (int pass = 0; pass < 2; pass++)
            for (int it = 0; it < items; it++) {
                int o = it * 6;
                int x0 = cell(bounds[o] - grow, gx), y0 = cell(bounds[o + 1] - grow, gy), z0 = cell(bounds[o + 2] - grow, gz);
                int x1 = cell(bounds[o + 3] + grow, gx), y1 = cell(bounds[o + 4] + grow, gy), z1 = cell(bounds[o + 5] + grow, gz);
                for (int y = y0; y <= y1; y++)
                    for (int z = z0; z <= z1; z++)
                        for (int x = x0; x <= x1; x++) {
                            int b = (y * gz + z) * gx + x;
                            double d = dist.from(it, (x + .5) * SIZE, (y + .5) * SIZE, (z + .5) * SIZE);
                            if (pass == 0) { if (d < least[b]) least[b] = (float) d; continue; }
                            if (d > grow || d > least[b] + 2 * R + 1e-3) continue;
                            int n = counts[b];
                            if (n == 0) lists[b] = new int[4];
                            else if (n == lists[b].length) lists[b] = Arrays.copyOf(lists[b], n * 2);
                            lists[b][counts[b]++] = it;
                        }
            }
    }

    private static int cell(double v, int n) { return Math.max(0, Math.min(n - 1, (int) Math.floor(v / SIZE))); }

    int bucket(double x, double y, double z) { return (cell(y, gy) * gz + cell(z, gz)) * gx + cell(x, gx); }
    int[] items(int bucket) { return lists[bucket]; }
    int count(int bucket) { return counts[bucket]; }
}
