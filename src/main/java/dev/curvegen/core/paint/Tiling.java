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
 */
public final class Tiling {
    /** The space a pattern is laid on. */
    public record Strip(double length, double width, boolean closed) {}

    private final Pattern pattern;
    private final Fit fit;
    private final boolean mirror, wrapAcross;
    private final int w, h;
    /** Blocks per cell along and across, the turn's cosine and sine, where it turns about, and the slide. */
    private final double su, sv, cos, sin, ou, ov, slideU, slideV;
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
        // Across a "Repeat along" strip the pattern is stretched once, unless it's turned: then it repeats that way too.
        wrapAcross = fit == Fit.REPEAT || turned;
        if (strip.closed() && fit != Fit.STRETCH) {
            // The loop, (L, 0), seen in pattern cells must be a whole number of repeats each way: (p w, q h).
            double cx = L * Math.cos(theta) / (u * w), cy = -L * Math.sin(theta) / (v * h);
            long p = Math.round(cx), q = wrapAcross ? Math.round(cy) : 0;
            if (p == 0 && q == 0) p = cx < 0 ? -1 : 1;
            double sinT = -q * h * v / L;
            if (p != 0 && Math.abs(sinT) <= 1) {
                // Keep the size across and nudge the angle and the size along.
                double cosT = Math.copySign(Math.sqrt(1 - sinT * sinT), p);
                theta = Math.atan2(sinT, cosT);
                u = L * cosT / (p * w);
            } else {
                theta = -Math.atan2((double) q * h, (double) p * w);
                double scale = L / Math.hypot((double) p * w, (double) q * h);
                u = scale; v = scale;
            }
        }
        su = u; sv = v;
        cos = Math.cos(theta); sin = Math.sin(theta);
        angle = Math.toDegrees(theta);
        slideU = paint.along; slideV = paint.across;
    }

    /** The pattern's palette index at {@code u} along and {@code v} across the strip. */
    public int sample(double u, double v) {
        double a = u - slideU - ou, b = v - slideV - ov;
        double x = ou + a * cos + b * sin, y = ov - a * sin + b * cos;
        double px = x / su, py = y / sv;
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
