package dev.curvegen.core;

import java.util.Arrays;

/**
 * Finds what is near a point in a 3D shape's box without looking at everything. The box is cut into buckets
 * {@link #SIZE} blocks across, and each lists the items (a curve's segments, a patch's sample points) that can be the
 * nearest one to some point inside it. Items further than {@code reach} from a point may be missing from its bucket,
 * and a bucket with nothing within reach of any of its points is empty.
 */
final class Near3 {
    @FunctionalInterface interface Dist { double from(int item, double x, double y, double z); }

    static final int SIZE = 2;
    /** Half a bucket's diagonal. */
    private static final double R = SIZE * Shape3.R_CELL;

    private final int gx, gy, gz;
    private final int[][] lists;
    private final int[] counts;

    /** {@code bounds} holds each item's box as {x0, y0, z0, x1, y1, z1}, and {@code dist} its true distance from a point. */
    Near3(int nx, int ny, int nz, int items, double[] bounds, double reach, Dist dist) {
        gx = (nx + SIZE - 1) / SIZE; gy = (ny + SIZE - 1) / SIZE; gz = (nz + SIZE - 1) / SIZE;
        lists = new int[gx * gy * gz][];
        counts = new int[lists.length];
        float[] least = new float[lists.length];
        Arrays.fill(least, Float.POSITIVE_INFINITY);
        double grow = reach + R;
        // An item can be nearest to a point in a bucket only if it's within the bucket's diagonal of whatever is nearest its centre.
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

    /** The bucket holding a point. A point outside the box counts as in the nearest bucket. */
    int bucket(double x, double y, double z) { return (cell(y, gy) * gz + cell(z, gz)) * gx + cell(x, gx); }
    /** The bucket's items: the first {@link #count} entries. Null when it has none. */
    int[] items(int bucket) { return lists[bucket]; }
    int count(int bucket) { return counts[bucket]; }
}
