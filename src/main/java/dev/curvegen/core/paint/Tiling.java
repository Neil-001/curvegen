package dev.curvegen.core.paint;

import dev.curvegen.core.paint.Paint.Fit;

/**
 * Lays a pattern on a strip: which pattern cell a point takes, from Fit, Size, Turn, Mirror and Slide. A shape works
 * out where each of its blocks lies on its strips (2D in {@link Wrap2D}); this is the part every shape shares.
 *
 * <p>A strip runs {@code u} from 0 to its length along the shape and {@code v} from 0 to its width across it, in
 * blocks. A closed strip's end meets its start. Round a closed strip with a repeating fit, the size, and with a turn
 * also the angle, are nudged so a whole number of repeats meets itself without a seam: a tilted stripe round a loop
 * becomes a spiral. With Stretch the seam stays where the strip starts.
 *
 * <p>Round a strip closed across, as a tube is, and more so round one closed both ways, as a torus is, each closed
 * loop must be a whole number of repeats, which a turn and a size alone can't always give. There the pattern is also
 * sheared a little: each closed loop is rounded to the nearest whole number of repeats along and across the pattern
 * as it would lie, and the pattern is fitted to those exactly.
 */
public final class Tiling {
    /** The space a pattern is laid on. {@code closed} is along it; a torus's tube is closed across it as well. */
    public record Strip(double length, double width, boolean closed, boolean closedAcross) {
        public Strip(double length, double width, boolean closed) { this(length, width, closed, false); }
    }

    private final Pattern pattern;
    private final Fit fit;
    private final boolean mirror, wrapAcross, sheared;
    private final int w, h;
    /** Blocks per cell along and across, the turn's cosine and sine, where it turns about, and the slide. */
    private final double su, sv, cos, sin, ou, ov, slideU, slideV;
    /** Round a strip closed both ways: from (u, v) to pattern cells, as a matrix. */
    private final double m00, m01, m10, m11;
    /** The angle in degrees after any nudge, for turning blocks with the pattern. */
    public final double angle;

    public Tiling(Pattern pattern, Paint paint, Fit fit, Strip strip) {
        this.pattern = pattern;
        this.fit = fit;
        this.mirror = paint.mirror;
        w = pattern.width; h = pattern.height;
        double L = Math.max(1e-9, strip.length()), A = Math.max(1e-9, strip.width());
        double theta = Math.toRadians(paint.turn), s = paint.size;
        double u = s, v = s;
        boolean turned = paint.turn % 360 != 0;
        switch (fit) {
            case REPEAT -> { ou = 0; ov = 0; }
            case REPEAT_ALONG -> { v = A / h; ou = 0; ov = A / 2; }
            default -> { u = L / w; v = A / h; ou = L / 2; ov = A / 2; }
        }
        // Across a "Repeat along" strip the pattern is stretched once, unless it's turned or the strip is closed across:
        // then it repeats that way too.
        wrapAcross = fit == Fit.REPEAT || turned || strip.closedAcross() && fit != Fit.STRETCH;
        sheared = strip.closedAcross() && fit != Fit.STRETCH;
        double a00 = 0, a01 = 0, a10 = 0, a11 = 0;
        if (sheared) {
            // As the pattern would lie, from (u, v) to pattern cells, and the loop across, (r w, t h) in pattern cells, rounded.
            double c = Math.cos(theta), sn = Math.sin(theta);
            a00 = c / u; a10 = -sn / v;
            long r = Math.round(A * sn / (u * w)), t = Math.round(A * c / (v * h));
            if (r == 0 && t == 0) t = 1;
            if (strip.closed()) {
                // Round a strip closed both ways the loop along is (p w, q h) too.
                long p = Math.round(L * c / (u * w)), q = Math.round(-L * sn / (v * h));
                if (p == 0 && q == 0) p = c < 0 ? -1 : 1;
                // Both loops running the same way round the pattern would squash it flat. Then the loop across goes square to the other.
                if (p * t - q * r == 0) { r = -q; t = p; }
                a00 = (double) p * w / L; a10 = (double) q * h / L;
            }
            a01 = (double) r * w / A; a11 = (double) t * h / A;
            // A loop across that lands along the pattern's own way along would squash it flat: then it goes square to that.
            if (Math.abs(a00 * a11 - a01 * a10) < 1e-9 * (Math.abs(a00 * a11) + Math.abs(a01 * a10) + 1e-12)) { a01 = 0; a11 = h / A; }
            theta = Math.atan2(a01, a00);
        } else if (strip.closed() && fit != Fit.STRETCH) {
            // The loop, (L, 0), seen in pattern cells must be a whole number of repeats each way: (p w, q h).
            double cx = L * Math.cos(theta) / (u * w), cy = -L * Math.sin(theta) / (v * h);
            long p = Math.round(cx), q = wrapAcross ? Math.round(cy) : 0;
            if (p == 0 && q == 0) p = cx < 0 ? -1 : 1;
            double sinT = -q * h * v / L, cosT = Math.copySign(Math.sqrt(Math.max(0, 1 - sinT * sinT)), p), along = L * cosT / (p * w);
            if (p != 0 && Math.abs(sinT) <= 1 && along > u / 2 && along < u * 2) {
                // Keep the size across and nudge the angle and the size along, as long as that's only a nudge.
                theta = Math.atan2(sinT, cosT);
                u = along;
            } else {
                // Otherwise the same size both ways, turned so the loop is exactly p repeats one way and q the other.
                theta = -Math.atan2((double) q * h, (double) p * w);
                double scale = L / Math.hypot((double) p * w, (double) q * h);
                u = scale; v = scale;
            }
        }
        m00 = a00; m01 = a01; m10 = a10; m11 = a11;
        su = u; sv = v;
        cos = Math.cos(theta); sin = Math.sin(theta);
        angle = Math.toDegrees(theta);
        slideU = paint.along; slideV = paint.across;
    }

    /** The pattern's palette index at {@code u} along and {@code v} across the strip. */
    public int sample(double u, double v) {
        double a = u - slideU - ou, b = v - slideV - ov;
        double px, py;
        if (sheared) {
            px = m00 * a + m01 * b; py = m10 * a + m11 * b;
        } else {
            double x = ou + a * cos + b * sin, y = ov - a * sin + b * cos;
            px = x / su; py = y / sv;
        }
        int ix, iy;
        if (fit == Fit.STRETCH) {
            ix = clamp((int) Math.floor(px), w);
            iy = clamp((int) Math.floor(py), h);
            if (mirror) ix = w - 1 - ix;
        } else {
            ix = Math.floorMod((long) Math.floor(px), w);
            if (mirror) ix = w - 1 - ix;
            iy = wrapAcross ? Math.floorMod((long) Math.floor(py), h) : clamp((int) Math.floor(py), h);
        }
        return pattern.at(ix, iy);
    }

    /** Quarter turns anticlockwise nearest the angle, for blocks such as logs that turn with the pattern. */
    public int quarters() { return Math.floorMod(Math.round(angle / 90), 4); }

    private static int clamp(int k, int n) { return k < 0 ? 0 : k >= n ? n - 1 : k; }
}
