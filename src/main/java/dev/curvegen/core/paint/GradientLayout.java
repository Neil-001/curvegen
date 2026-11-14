package dev.curvegen.core.paint;

/**
 * Which step of a gradient a block takes. The gradient spans the shape once: a block's place along the direction,
 * in blocks from 0 to {@code extent}, is split into as many equal bands as there are steps, first step first.
 *
 * <p>Ragged edges scatter neighbouring steps into each other as Chroma Studio's diorama bands do: each block's place is
 * nudged up or down at random before its band is read off, by up to a quarter of a band or a block, whichever is more,
 * but never more than a band, so a step only ever mixes with the ones beside it. The nudge comes from the block's
 * position alone, so the preview, the hologram and what gets placed agree, and the same shape always looks the same.
 */
public final class GradientLayout {
    private final int steps;
    private final double extent, band, nudge;
    private final boolean reverse;

    /**
     * @param steps  how many steps the gradient has, at least 1
     * @param extent how far the direction runs over the shape, in blocks. Places outside 0 to {@code extent} take the
     *               first or last step.
     */
    public GradientLayout(int steps, double extent, boolean reverse, boolean ragged) {
        this.steps = Math.max(1, steps);
        this.extent = extent > 0 ? extent : 1;
        this.reverse = reverse;
        band = this.extent / this.steps;
        nudge = ragged && this.steps > 1 ? Math.min(band, Math.max(1, band / 4)) : 0;
    }

    /** The step for a block whose place along the direction is {@code at}, and whose position, for the ragged nudge, is x, y, z. */
    public int at(double at, int x, int y, int z) {
        if (nudge > 0) at += nudge * (2 * unit(x, y, z) - 1);
        int k = (int) Math.floor(at / band);
        k = Math.max(0, Math.min(steps - 1, k));
        return reverse ? steps - 1 - k : k;
    }

    /** A repeatable value from 0 to 1 for a block, by SplitMix64's finaliser. */
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
