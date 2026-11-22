package dev.curvegen.core.paint;

public final class GradientLayout {
    private final int steps;
    private final double extent, band, nudge;
    private final boolean reverse;

    public GradientLayout(int steps, double extent, boolean reverse, boolean ragged) {
        this.steps = Math.max(1, steps);
        this.extent = extent > 0 ? extent : 1;
        this.reverse = reverse;
        band = this.extent / this.steps;
        nudge = ragged && this.steps > 1 ? Math.min(band, Math.max(1, band / 4)) : 0;
    }

    public int at(double at, int x, int y, int z) {
        if (nudge > 0) at += nudge * (2 * unit(x, y, z) - 1);
        int k = (int) Math.floor(at / band);
        k = Math.max(0, Math.min(steps - 1, k));
        return reverse ? steps - 1 - k : k;
    }

    private static double unit(int x, int y, int z) {
        long h = mix(0x5DEECE66DL + x * 0x9E3779B97F4A7C15L);
        h = mix(h + y * 0xC2B2AE3D27D4EB4FL);
        h = mix(h + z * 0x165667B19E3779F9L);
        return (h >>> 11) * 0x1.0p-53;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
