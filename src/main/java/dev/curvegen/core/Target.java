package dev.curvegen.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ideal shape on an nx×ny grid (local block coordinates, origin bottom-left, y up).
 * Each cell is empty (0), full (1) or mixed (2, with its pixel error against every piece state).
 */
public final class Target {
    public final int nx, ny;
    public final byte[] kind;
    public final short[][] errTab;
    public double area;
    public boolean symX, symY, hollow;
    /** Curve overlay: flat segment lists {x1,y1,x2,y2,...}; dashed ones mark wall edges. */
    public final List<double[]> overlay = new ArrayList<>(), dashed = new ArrayList<>();
    public Double axisX, axisY;
    /** Cells whose centre lies in the space the shape encloses; cleared to air when carving (null = never carve). */
    public boolean[] carve;
    public String error;
    /** For equations: block → math coordinates. */
    public double mx0, msx, my0, msy;
    public boolean hasMath;

    static final double R_CELL = 0.7082;

    public Target(int nx, int ny) {
        this.nx = nx; this.ny = ny;
        kind = new byte[nx * ny];
        errTab = new short[nx * ny][];
    }

    interface Inside { boolean test(double x, double y); }

    private final byte[] tg = new byte[256];

    void mixed(int idx, Inside in) {
        int i = idx % nx, j = idx / nx, cnt = 0;
        for (int py = 0; py < 16; py++) {
            double y = j + (py + .5) / 16;
            for (int px = 0; px < 16; px++) {
                byte v = in.test(i + (px + .5) / 16, y) ? (byte) 1 : 0;
                tg[py * 16 + px] = v; cnt += v;
            }
        }
        if (cnt == 0) { kind[idx] = 0; return; }
        if (cnt == 256) { kind[idx] = 1; area++; return; }
        short[] tab = new short[Pieces.COUNT];
        for (int s = 0; s < Pieces.COUNT; s++) {
            byte[] mk = Pieces.MASK[s]; int e = 0;
            for (int k = 0; k < 256; k++) e += mk[k] ^ tg[k];
            tab[s] = (short) e;
        }
        kind[idx] = 2; errTab[idx] = tab; area += cnt / 256.0;
    }

    void full(int idx) { kind[idx] = 1; area++; }

    // ================= builders =================

    public static Target build(ShapeSettings s) {
        return switch (s.gen) {
            case ELLIPSE -> ellipse(s);
            case EQUATION -> equation(s);
            case BEZIER -> bezier(s);
        };
    }

    /** Signed distance to an axis-aligned ellipse (negative inside). */
    public static double sdEllipse(double px, double py, double a, double b) {
        double x = Math.abs(px), y = Math.abs(py), tx = 0.7071067811865476, ty = tx, k2 = a * a - b * b;
        for (int it = 0; it < 5; it++) {
            double ex = k2 * tx * tx * tx / a, ey = -k2 * ty * ty * ty / b;
            double rx = a * tx - ex, ry = b * ty - ey, qx = x - ex, qy = y - ey;
            double r = Math.hypot(rx, ry), q = Math.hypot(qx, qy);
            if (q == 0) q = 1e-12;
            tx = Math.min(1, Math.max(0, (qx * r / q + ex) / a));
            ty = Math.min(1, Math.max(0, (qy * r / q + ey) / b));
            double t = Math.hypot(tx, ty);
            if (t == 0) t = 1;
            tx /= t; ty /= t;
        }
        double d = Math.hypot(x - a * tx, y - b * ty);
        return (x * x / (a * a) + y * y / (b * b) < 1) ? -d : d;
    }

    static Target ellipse(ShapeSettings s) {
        double a = s.eW / 2.0, b = s.eH / 2.0, T = s.eT, lo, hi;
        switch (s.eMode) {
            case OUTWARDS -> { lo = 0; hi = T; }
            case INWARDS -> { lo = -T; hi = 0; }
            case MIDDLE -> { lo = -T / 2; hi = T / 2; }
            default -> { lo = Double.NEGATIVE_INFINITY; hi = 0; }
        }
        int m = s.eMode == ShapeSettings.EllipseMode.OUTWARDS ? (int) Math.ceil(T) + 1
                : s.eMode == ShapeSettings.EllipseMode.MIDDLE ? (int) Math.ceil(T / 2) + 1 : 1;
        int nx = s.eW + 2 * m, ny = s.eH + 2 * m;
        double x0 = -a - m, y0 = -b - m;
        Target t = new Target(nx, ny);
        t.symX = t.symY = true;
        t.hollow = s.eMode == ShapeSettings.EllipseMode.THIN;
        final double flo = lo, fhi = hi;
        // Carving clears the hollow the wall surrounds: inside the ellipse, or inside the wall's inner edge.
        double carveBelow = switch (s.eMode) {
            case THIN, OUTWARDS -> 0;
            case INWARDS -> -T;
            case MIDDLE -> -T / 2;
            case FILLED -> Double.NEGATIVE_INFINITY;
        };
        if (s.eMode != ShapeSettings.EllipseMode.FILLED) t.carve = new boolean[nx * ny];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int idx = j * nx + i;
                double d = sdEllipse(x0 + i + .5, y0 + j + .5, a, b);
                if (t.carve != null) t.carve[idx] = d < carveBelow;
                if (d - R_CELL > hi || d + R_CELL < lo) continue;
                if (d - R_CELL >= lo && d + R_CELL <= hi) { t.full(idx); continue; }
                t.mixed(idx, (x, y) -> { double v = sdEllipse(x + x0, y + y0, a, b); return v >= flo && v <= fhi; });
            }
        t.axisX = -x0; t.axisY = -y0;
        int n = (int) Math.max(360, Math.round((a + b) * 24));
        t.overlay.add(ellipseCurve(a, b, 0, x0, y0, n));
        if (s.eMode == ShapeSettings.EllipseMode.OUTWARDS || s.eMode == ShapeSettings.EllipseMode.INWARDS || s.eMode == ShapeSettings.EllipseMode.MIDDLE) {
            if (hi != 0) t.dashed.add(ellipseCurve(a, b, hi, x0, y0, n));
            if (lo != 0) t.dashed.add(ellipseCurve(a, b, lo, x0, y0, n));
        }
        return t;
    }

    private static double[] ellipseCurve(double a, double b, double off, double x0, double y0, int n) {
        double[] out = new double[n * 4];
        double px = 0, py = 0;
        for (int k = 0; k <= n; k++) {
            double th = k * 2 * Math.PI / n, ex = a * Math.cos(th), ey = b * Math.sin(th);
            double ux = ex / (a * a), uy = ey / (b * b), ln = Math.hypot(ux, uy);
            if (ln == 0) ln = 1;
            double cx = ex + ux / ln * off - x0, cy = ey + uy / ln * off - y0;
            if (k > 0) { int o = (k - 1) * 4; out[o] = px; out[o + 1] = py; out[o + 2] = cx; out[o + 3] = cy; }
            px = cx; py = cy;
        }
        return out;
    }

    // ---------- segments ----------
    static final class Segs {
        double[] a = new double[256]; int n = 0;   // n = number of doubles
        void add(double x1, double y1, double x2, double y2) {
            if (n + 4 > a.length) a = Arrays.copyOf(a, a.length * 2);
            a[n++] = x1; a[n++] = y1; a[n++] = x2; a[n++] = y2;
        }
        /** Adds a segment split into pieces no longer than half a block. */
        void addSplit(double x1, double y1, double x2, double y2) {
            int k = Math.max(1, (int) Math.ceil(Math.hypot(x2 - x1, y2 - y1) / 0.5));
            for (int q = 0; q < k; q++)
                add(x1 + (x2 - x1) * q / k, y1 + (y2 - y1) * q / k, x1 + (x2 - x1) * (q + 1) / k, y1 + (y2 - y1) * (q + 1) / k);
        }
        int count() { return n / 4; }
        double[] toArray() { return Arrays.copyOf(a, n); }
    }

    static final class SegIndex {
        final int nx, ny; final double[] s; final int[][] buckets; final int[] stamp; int gen = 0;
        SegIndex(int nx, int ny, double[] s) {
            this.nx = nx; this.ny = ny; this.s = s;
            int n = s.length / 4;
            int[] cnt = new int[nx * ny];
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < n; k++) {
                    int i0 = ci(Math.min(s[4 * k], s[4 * k + 2])), i1 = ci(Math.max(s[4 * k], s[4 * k + 2]));
                    int j0 = cj(Math.min(s[4 * k + 1], s[4 * k + 3])), j1 = cj(Math.max(s[4 * k + 1], s[4 * k + 3]));
                    for (int j = j0; j <= j1; j++) for (int i = i0; i <= i1; i++) {
                        int b = j * nx + i;
                        if (pass == 0) cnt[b]++; else bucketsTmp[b][fill[b]++] = k;
                    }
                }
                if (pass == 0) {
                    bucketsTmp = new int[nx * ny][];
                    fill = new int[nx * ny];
                    for (int b = 0; b < cnt.length; b++) if (cnt[b] > 0) bucketsTmp[b] = new int[cnt[b]];
                }
            }
            buckets = bucketsTmp;
            stamp = new int[n];
        }
        private int[][] bucketsTmp; private int[] fill;
        int ci(double v) { return Math.min(nx - 1, Math.max(0, (int) Math.floor(v))); }
        int cj(double v) { return Math.min(ny - 1, Math.max(0, (int) Math.floor(v))); }
        int[] gather(double xa, double xb, double ya, double yb) {
            gen++;
            int[] out = new int[16]; int c = 0;
            for (int j = cj(ya); j <= cj(yb); j++) for (int i = ci(xa); i <= ci(xb); i++) {
                int[] b = buckets[j * nx + i];
                if (b == null) continue;
                for (int k : b) if (stamp[k] != gen) {
                    stamp[k] = gen;
                    if (c == out.length) out = Arrays.copyOf(out, c * 2);
                    out[c++] = k;
                }
            }
            return Arrays.copyOf(out, c);
        }
        double d2(double px, double py, int k) {
            double x1 = s[4 * k], y1 = s[4 * k + 1], dx = s[4 * k + 2] - x1, dy = s[4 * k + 3] - y1, L = dx * dx + dy * dy;
            double u = L > 0 ? ((px - x1) * dx + (py - y1) * dy) / L : 0;
            u = u < 0 ? 0 : u > 1 ? 1 : u;
            double ex = x1 + u * dx - px, ey = y1 + u * dy - py;
            return ex * ex + ey * ey;
        }
    }

    /** A line of width w centred on the segments. */
    void fillLine(double[] segs, double w) {
        SegIndex si = new SegIndex(nx, ny, segs);
        double hw = w / 2, reach = hw + R_CELL, hw2 = hw * hw;
        boolean[] cand = new boolean[nx * ny];
        for (int k = 0; k < segs.length / 4; k++) {
            int i0 = Math.max(0, (int) Math.floor(Math.min(segs[4 * k], segs[4 * k + 2]) - reach));
            int i1 = Math.min(nx - 1, (int) Math.floor(Math.max(segs[4 * k], segs[4 * k + 2]) + reach));
            int j0 = Math.max(0, (int) Math.floor(Math.min(segs[4 * k + 1], segs[4 * k + 3]) - reach));
            int j1 = Math.min(ny - 1, (int) Math.floor(Math.max(segs[4 * k + 1], segs[4 * k + 3]) + reach));
            for (int j = j0; j <= j1; j++) for (int i = i0; i <= i1; i++) cand[j * nx + i] = true;
        }
        for (int idx = 0; idx < nx * ny; idx++) {
            if (!cand[idx]) continue;
            int i = idx % nx, j = idx / nx;
            double cx = i + .5, cy = j + .5;
            int[] list = si.gather(cx - reach, cx + reach, cy - reach, cy + reach);
            double d2 = Double.POSITIVE_INFINITY;
            for (int k : list) d2 = Math.min(d2, si.d2(cx, cy, k));
            double d = Math.sqrt(d2);
            if (d > hw + R_CELL) continue;
            if (d <= hw - R_CELL) { full(idx); continue; }
            mixed(idx, (x, y) -> { for (int k : list) if (si.d2(x, y, k) <= hw2) return true; return false; });
        }
    }

    /** The inside of closed loop(s) of segments (even-odd rule). */
    void fillPolygon(double[] segs) {
        SegIndex si = new SegIndex(nx, ny, segs);
        int n = segs.length / 4;
        Map<Double, double[]> memo = new HashMap<>();
        java.util.function.DoubleFunction<double[]> cross = y -> memo.computeIfAbsent(y, yy -> {
            double[] arr = new double[16]; int c = 0;
            for (int k = 0; k < n; k++) {
                double y1 = segs[4 * k + 1], y2 = segs[4 * k + 3];
                if ((y1 > yy) != (y2 > yy)) {
                    double x1 = segs[4 * k];
                    if (c == arr.length) arr = Arrays.copyOf(arr, c * 2);
                    arr[c++] = x1 + (yy - y1) * (segs[4 * k + 2] - x1) / (y2 - y1);
                }
            }
            double[] r = Arrays.copyOf(arr, c); Arrays.sort(r); return r;
        });
        Inside inside = (x, y) -> {
            double[] a = cross.apply(y);
            int lo = 0, hi = a.length;
            while (lo < hi) { int m = (lo + hi) >>> 1; if (a[m] > x) hi = m; else lo = m + 1; }
            return ((a.length - lo) & 1) == 1;
        };
        double rr = R_CELL * R_CELL;
        for (int idx = 0; idx < nx * ny; idx++) {
            int i = idx % nx, j = idx / nx;
            double cx = i + .5, cy = j + .5;
            boolean near = false;
            for (int k : si.gather(cx - R_CELL, cx + R_CELL, cy - R_CELL, cy + R_CELL))
                if (si.d2(cx, cy, k) <= rr) { near = true; break; }
            if (!near) { if (inside.test(cx, cy)) full(idx); continue; }
            mixed(idx, inside);
        }
    }

    static Target equation(ShapeSettings s) {
        double xmin, xmax, ymin, ymax; Expr.Equation eq;
        try {
            xmin = Expr.constant(s.xmin, "x from"); xmax = Expr.constant(s.xmax, "x to");
            ymin = Expr.constant(s.ymin, "y from"); ymax = Expr.constant(s.ymax, "y to");
            if (!(xmax > xmin)) throw new Expr.ParseException("The x range must end higher than it starts.");
            if (!(ymax > ymin)) throw new Expr.ParseException("The y range must end higher than it starts.");
            eq = Expr.parseEquation(s.src);
        } catch (Expr.ParseException e) {
            Target t = new Target(s.qW, Math.max(1, s.qH));
            t.error = e.getMessage();
            return t;
        }
        ShapeSettings.EqMode mode = eq.rel() == null || eq.rel().equals("=") ? s.qMode
                : eq.rel().startsWith("<") ? ShapeSettings.EqMode.UNDER : ShapeSettings.EqMode.OVER;
        int W = s.qW;
        int H = s.qLock ? (int) Math.min(400, Math.max(1, Math.round(W * (ymax - ymin) / (xmax - xmin)))) : s.qH;
        if (s.qLock) s.qH = H;
        Target t = new Target(W, H);
        double sx = (xmax - xmin) / W, sy = (ymax - ymin) / H;
        final double fxmin = xmin, fymin = ymin;
        Expr.Fn F = eq.f();
        Expr.Fn Fl = (bx, by) -> F.eval(fxmin + bx * sx, fymin + by * sy);

        int r = Math.max(2, Math.min(16, (int) Math.floor(Math.sqrt(1.2e6 / ((double) W * H)))));
        int cols = W * r + 1, rows = H * r + 1;
        double[] V = new double[cols * rows];
        for (int b = 0; b < rows; b++) for (int a = 0; a < cols; a++) V[b * cols + a] = Fl.eval((double) a / r, (double) b / r);
        Segs segs = new Segs();
        for (int b = 0; b < rows - 1; b++)
            for (int a = 0; a < cols - 1; a++) {
                double v00 = V[b * cols + a], v10 = V[b * cols + a + 1], v01 = V[(b + 1) * cols + a], v11 = V[(b + 1) * cols + a + 1];
                boolean sg = v00 <= 0;
                if ((v10 <= 0) == sg && (v01 <= 0) == sg && (v11 <= 0) == sg) continue;
                double x0 = (double) a / r, x1 = (double) (a + 1) / r, y0 = (double) b / r, y1 = (double) (b + 1) / r;
                double[][] e = {root(Fl, x0, y0, v00, x1, y0, v10), root(Fl, x1, y0, v10, x1, y1, v11),
                        root(Fl, x0, y1, v01, x1, y1, v11), root(Fl, x0, y0, v00, x0, y1, v01)};
                List<double[]> f = new ArrayList<>(4);
                for (double[] p : e) if (p != null) f.add(p);
                if (f.size() == 2) segs.add(f.get(0)[0], f.get(0)[1], f.get(1)[0], f.get(1)[1]);
                else if (f.size() == 4) {
                    if ((Fl.eval((x0 + x1) / 2, (y0 + y1) / 2) <= 0) == sg) {
                        segs.add(e[0][0], e[0][1], e[1][0], e[1][1]); segs.add(e[2][0], e[2][1], e[3][0], e[3][1]);
                    } else {
                        segs.add(e[0][0], e[0][1], e[3][0], e[3][1]); segs.add(e[1][0], e[1][1], e[2][0], e[2][1]);
                    }
                }
            }
        double[] sa = segs.toArray();
        if (mode == ShapeSettings.EqMode.LINE) t.fillLine(sa, s.qLW);
        else {
            boolean under = mode == ShapeSettings.EqMode.UNDER;
            // Carving clears the other side: above the curve for "fill under", below it for "fill over".
            t.carve = new boolean[W * H];
            for (int j = 0; j < H; j++)
                for (int i = 0; i < W; i++) {
                    double v = Fl.eval(i + .5, j + .5);
                    t.carve[j * W + i] = !Double.isNaN(v) && (under ? v > 0 : v < 0);
                }
            SegIndex si = new SegIndex(W, H, sa);
            for (int j = 0; j < H; j++)
                for (int i = 0; i < W; i++) {
                    int idx = j * W + i;
                    boolean uni = si.buckets[idx] == null;
                    int s0 = st(V[j * r * cols + i * r], under);
                    for (int b = j * r; uni && b <= (j + 1) * r; b++)
                        for (int a = i * r; a <= (i + 1) * r; a++) if (st(V[b * cols + a], under) != s0) { uni = false; break; }
                    if (uni) { if (s0 == 1) t.full(idx); continue; }
                    t.mixed(idx, (x, y) -> { double v = Fl.eval(x, y); return under ? v <= 0 : v >= 0; });
                }
        }
        t.axisX = xmin <= 0 && xmax >= 0 ? -xmin / sx : null;
        t.axisY = ymin <= 0 && ymax >= 0 ? -ymin / sy : null;
        t.overlay.add(sa);
        t.hasMath = true; t.mx0 = xmin; t.msx = sx; t.my0 = ymin; t.msy = sy;
        return t;
    }

    private static int st(double v, boolean under) { return Double.isNaN(v) ? 2 : (under ? v <= 0 : v >= 0) ? 1 : 0; }

    /** Crossing on an edge, refined by bisection; null if there's none or it's a jump (asymptote). */
    private static double[] root(Expr.Fn F, double ax, double ay, double fa, double bx, double by, double fb) {
        if (Double.isNaN(fa) || Double.isNaN(fb) || (fa <= 0) == (fb <= 0)) return null;
        double A = Math.abs(fa), B = Math.abs(fb);
        double M = Double.isFinite(A) ? (Double.isFinite(B) ? Math.max(A, B) : A) : (Double.isFinite(B) ? B : Double.NaN);
        if (Double.isNaN(M)) return null;
        double lx = ax, ly = ay, hx = bx, hy = by, fl = fa;
        for (int it = 0; it < 24; it++) {
            double mx = (lx + hx) / 2, my = (ly + hy) / 2, fm = F.eval(mx, my);
            if (Double.isNaN(fm)) return null;
            if ((fm <= 0) == (fl <= 0)) { lx = mx; ly = my; fl = fm; } else { hx = mx; hy = my; }
        }
        double mx = (lx + hx) / 2, my = (ly + hy) / 2, fm = F.eval(mx, my);
        return Math.abs(fm) <= 0.02 * M ? new double[]{mx, my} : null;
    }

    public static double[] bezierPoint(List<double[]> P, double u) {
        int n = P.size();
        double[] qx = new double[n], qy = new double[n];
        for (int i = 0; i < n; i++) { qx[i] = P.get(i)[0]; qy[i] = P.get(i)[1]; }
        for (int k = n - 1; k > 0; k--)
            for (int i = 0; i < k; i++) { qx[i] += (qx[i + 1] - qx[i]) * u; qy[i] += (qy[i + 1] - qy[i]) * u; }
        return new double[]{qx[0], qy[0]};
    }

    static Target bezier(ShapeSettings s) {
        List<double[]> P = s.pts;
        Target t = new Target(s.bW, s.bH);
        double L = 0;
        for (int k = 1; k < P.size(); k++) L += Math.hypot(P.get(k)[0] - P.get(k - 1)[0], P.get(k)[1] - P.get(k - 1)[1]);
        int N = (int) Math.max(24, Math.min(4000, Math.ceil(L * 12)));
        double[][] pts = new double[N + 1][];
        for (int k = 0; k <= N; k++) pts[k] = bezierPoint(P, (double) k / N);
        Segs curve = new Segs(), chord = new Segs();
        for (int k = 1; k <= N; k++) curve.addSplit(pts[k - 1][0], pts[k - 1][1], pts[k][0], pts[k][1]);
        chord.addSplit(pts[N][0], pts[N][1], pts[0][0], pts[0][1]);
        double[] ca = curve.toArray(), ch = chord.toArray();
        if (s.bMode == ShapeSettings.BzMode.LINE) t.fillLine(ca, s.bLW);
        else {
            double[] all = Arrays.copyOf(ca, ca.length + ch.length);
            System.arraycopy(ch, 0, all, ca.length, ch.length);
            t.fillPolygon(all);
            t.dashed.add(ch);
        }
        t.overlay.add(ca);
        return t;
    }
}
