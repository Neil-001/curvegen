package dev.curvegen.core.paint;

import dev.curvegen.core.paint.Paint.Arrange;

/**
 * Where a {@link Mix}'s blocks go: which entry the block at a position takes. It needs only the block's position in
 * the shape's own box, not a wrap, so 2D and 3D shapes call it alike, and the same settings always give the same
 * blocks: the preview, the hologram and what gets placed agree.
 *
 * <p>Scatter picks every block on its own, weighted. Patches splits space into lumps about {@code patch} blocks
 * across, the same in every direction, and each lump takes one weighted pick, so a mix's shares hold on average either
 * way. The lumps are cells round points jittered off a grid, with their edges roughened a little so they don't run
 * straight.
 */
public final class MixLayout {
    public static final int MIN_PATCH = 2, MAX_PATCH = 16;

    private final boolean patches;
    private final long seed;
    private final double patch, rough;
    /** Running totals of the weights, for a weighted pick. */
    private final long[] upTo;

    public MixLayout(Palette mix, Arrange arrange, int patch, int seed) {
        this.patches = arrange == Arrange.PATCHES;
        this.seed = mix(seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L);
        this.patch = Math.max(MIN_PATCH, Math.min(MAX_PATCH, patch));
        rough = Math.min(1, this.patch / 4) * .5;
        upTo = new long[mix.paletteSize()];
        long t = 0;
        for (int k = 0; k < upTo.length; k++) upTo[k] = t += Math.max(0, mix.count(k));
        if (t <= 0) throw new IllegalArgumentException();
    }

    /** The entry the block at x, y, z takes. */
    public int at(int x, int y, int z) {
        if (!patches) return pick(hash(x, y, z, 1));
        // The block's centre, nudged a little at random so the lumps' edges aren't straight lines.
        double px = x + .5 + rough * (unit(hash(x, y, z, 2)) - .5) * 2;
        double py = y + .5 + rough * (unit(hash(x, y, z, 3)) - .5) * 2;
        double pz = z + .5 + rough * (unit(hash(x, y, z, 4)) - .5) * 2;
        int gx = (int) Math.floor(px / patch), gy = (int) Math.floor(py / patch), gz = (int) Math.floor(pz / patch);
        double best = Double.MAX_VALUE;
        long owner = 0;
        for (int dz = -1; dz <= 1; dz++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = gx + dx, cy = gy + dy, cz = gz + dz;
                    double fx = (cx + unit(hash(cx, cy, cz, 5))) * patch - px;
                    double fy = (cy + unit(hash(cx, cy, cz, 6))) * patch - py;
                    double fz = (cz + unit(hash(cx, cy, cz, 7))) * patch - pz;
                    double d = fx * fx + fy * fy + fz * fz;
                    if (d < best) { best = d; owner = hash(cx, cy, cz, 8); }
                }
        return pick(owner);
    }

    private int pick(long h) {
        long r = Math.floorMod(h >>> 1, upTo[upTo.length - 1]);
        int lo = 0, hi = upTo.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (upTo[mid] > r) hi = mid; else lo = mid + 1;
        }
        return lo;
    }

    private long hash(int x, int y, int z, int salt) {
        long h = seed ^ salt * 0xD1B54A32D192ED03L;
        h = mix(h + x * 0x9E3779B97F4A7C15L);
        h = mix(h + y * 0xC2B2AE3D27D4EB4FL);
        return mix(h + z * 0x165667B19E3779F9L);
    }

    private static double unit(long h) { return (h >>> 11) * 0x1.0p-53; }

    /** SplitMix64's finaliser. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
