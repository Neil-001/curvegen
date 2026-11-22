package dev.curvegen.core.paint;

import dev.curvegen.core.paint.Paint.Fit;

public final class Tiling {
    public record Strip(double length, double width, boolean closed, boolean closedAcross) {
        public Strip(double length, double width, boolean closed) { this(length, width, closed, false); }
    }

    private final Pattern pattern;
    private final Fit fit;
    private final boolean mirror, wrapAcross, sheared;
    private final int w, h;
    private final double su, sv, cos, sin, ou, ov, slideU, slideV;
    private final double m00, m01, m10, m11;
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
        wrapAcross = fit == Fit.REPEAT || turned || strip.closedAcross() && fit != Fit.STRETCH;
        sheared = strip.closedAcross() && fit != Fit.STRETCH;
        double a00 = 0, a01 = 0, a10 = 0, a11 = 0;
        if (sheared) {
            double c = Math.cos(theta), sn = Math.sin(theta);
            a00 = c / u; a10 = -sn / v;
            double ir = A * sn / (u * w), it = A * c / (v * h);
            long r = Math.round(ir), t = Math.round(it);
            if (r == 0 && t == 0) { if (Math.abs(ir) > Math.abs(it)) r = ir < 0 ? -1 : 1; else t = it < 0 ? -1 : 1; }
            if (strip.closed()) {
                long p = Math.round(L * c / (u * w)), q = Math.round(-L * sn / (v * h));
                if (p == 0 && q == 0) p = c < 0 ? -1 : 1;
                if (p * t - q * r == 0) { r = -q; t = p; }
                a00 = (double) p * w / L; a10 = (double) q * h / L;
            }
            a01 = (double) r * w / A; a11 = (double) t * h / A;
            if (!(a00 * a11 - a01 * a10 > 1e-9 * (Math.abs(a00 * a11) + Math.abs(a01 * a10) + 1e-12))) {
                double best = 0;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    double b01 = (double) d[0] * w / A, b11 = (double) d[1] * h / A, det = a00 * b11 - b01 * a10;
                    if (det > best) { best = det; a01 = b01; a11 = b11; }
                }
            }
            theta = Math.atan2(a01, a00);
        } else if (strip.closed() && fit != Fit.STRETCH) {
            double cx = L * Math.cos(theta) / (u * w), cy = -L * Math.sin(theta) / (v * h);
            long p = Math.round(cx), q = wrapAcross ? Math.round(cy) : 0;
            if (p == 0 && q == 0) p = cx < 0 ? -1 : 1;
            double sinT = -q * h * v / L, cosT = Math.copySign(Math.sqrt(Math.max(0, 1 - sinT * sinT)), p), along = L * cosT / (p * w);
            if (p != 0 && Math.abs(sinT) <= 1 && along > u / 2 && along < u * 2) {
                theta = Math.atan2(sinT, cosT);
                u = along;
            } else {
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

    public int quarters() { return Math.floorMod(Math.round(angle / 90), 4); }

    private static int clamp(int k, int n) { return k < 0 ? 0 : k >= n ? n - 1 : k; }
}
