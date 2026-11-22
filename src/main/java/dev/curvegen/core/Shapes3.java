package dev.curvegen.core;

import java.util.ArrayList;
import java.util.List;

public final class Shapes3 {
    private Shapes3() {}

    private static int size(int v) { return Math.max(1, Math.min(Shape3.MAX_SIZE, v)); }

    public static int scaled(int size, double range, double of) {
        return (int) Math.max(1, Math.min(Shape3.MAX_SIZE, Math.round(size * range / of)));
    }

    public static double sdEllipsoid(double px, double py, double pz, double a, double b, double c) { return sdEllipsoid(px, py, pz, a, b, c, null); }

    static double sdEllipsoid(double px, double py, double pz, double a, double b, double c, double[] near) {
        double x = Math.abs(px), y = Math.abs(py), z = Math.abs(pz);
        if (x < 1e-9 * a) x = 0;
        if (y < 1e-9 * b) y = 0;
        if (z < 1e-9 * c) z = 0;
        double a2 = a * a, b2 = b * b, c2 = c * c, ax = a * x, by = b * y, cz = c * z;
        boolean inside = x * x / a2 + y * y / b2 + z * z / c2 < 1;
        double t = Double.NEGATIVE_INFINITY, tz = Double.NEGATIVE_INFINITY;
        if (x > 0) t = Math.max(t, ax - a2); else tz = Math.max(tz, -a2);
        if (y > 0) t = Math.max(t, by - b2); else tz = Math.max(tz, -b2);
        if (z > 0) t = Math.max(t, cz - c2); else tz = Math.max(tz, -c2);
        boolean clamped = false;
        if (tz > t) {
            double u = ax / (tz + a2), v = by / (tz + b2), w = cz / (tz + c2);
            clamped = (x == 0 || tz + a2 > 0) && (y == 0 || tz + b2 > 0) && (z == 0 || tz + c2 > 0)
                    && (x == 0 ? 0 : u * u) + (y == 0 ? 0 : v * v) + (z == 0 ? 0 : w * w) <= 1;
        }
        if (clamped) t = tz;
        else
            for (int it = 0; it < 64; it++) {
                double u = x == 0 ? 0 : ax / (t + a2), v = y == 0 ? 0 : by / (t + b2), w = z == 0 ? 0 : cz / (t + c2);
                double g = u * u + v * v + w * w - 1;
                double dg = 2 * ((x == 0 ? 0 : u * u / (t + a2)) + (y == 0 ? 0 : v * v / (t + b2)) + (z == 0 ? 0 : w * w / (t + c2)));
                double dt = g / dg;
                if (!(dt > 1e-12 * (1 + Math.abs(t)))) break;
                t += dt;
            }
        double qx = x == 0 ? 0 : a2 * x / (t + a2), qy = y == 0 ? 0 : b2 * y / (t + b2), qz = z == 0 ? 0 : c2 * z / (t + c2);
        if (clamped) {
            double rest = Math.sqrt(Math.max(0, 1 - qx * qx / a2 - qy * qy / b2 - qz * qz / c2));
            if (x == 0 && tz == -a2) qx = a * rest;
            else if (y == 0 && tz == -b2) qy = b * rest;
            else qz = c * rest;
        }
        double d = Math.sqrt((qx - x) * (qx - x) + (qy - y) * (qy - y) + (qz - z) * (qz - z));
        if (near != null) { near[0] = Math.copySign(qx, px); near[1] = Math.copySign(qy, py); near[2] = Math.copySign(qz, pz); }
        return inside ? -d : d;
    }

    public static final class Ellipsoid implements Shape3 {
        private final double a, b, c, lo, hi, least;
        private final int nx, ny, nz, pad;
        private final boolean hollow, exact;
        private final double[] carve;

        public Ellipsoid(ShapeSettings s) {
            int w = size(s.e3W), h = size(s.e3H), d = size(s.e3D);
            double t = Math.max(0.0625, Math.min(50, s.e3T));
            a = w / 2.0; b = h / 2.0; c = d / 2.0; least = Math.min(a, Math.min(b, c));
            switch (s.e3Mode) {
                case OUTWARDS -> { lo = 0; hi = t; pad = (int) Math.ceil(t); }
                case INWARDS -> { lo = -t; hi = 0; pad = 0; }
                case MIDDLE -> { lo = -t / 2; hi = t / 2; pad = (int) Math.ceil(t / 2); }
                default -> { lo = Double.NEGATIVE_INFINITY; hi = 0; pad = 0; }
            }
            exact = lo != Double.NEGATIVE_INFINITY;
            hollow = s.e3Mode == ShapeSettings.EllipseMode.THIN;
            carve = s.e3Mode == ShapeSettings.EllipseMode.FILLED ? null : new double[]{Double.NEGATIVE_INFINITY, exact ? Math.min(lo, 0) : 0};
            nx = w + 2 * pad; ny = h + 2 * pad; nz = d + 2 * pad;
        }

        @Override public int nx() { return nx; }
        @Override public int ny() { return ny; }
        @Override public int nz() { return nz; }
        @Override public int pad() { return pad; }
        @Override public double lo() { return lo; }
        @Override public double hi() { return hi; }
        @Override public boolean hollow() { return hollow; }
        @Override public boolean smooth() { return least >= 2; }
        @Override public double[] carve() { return carve; }
        @Override public boolean symX() { return true; }
        @Override public boolean symY() { return true; }
        @Override public boolean symZ() { return true; }

        @Override public double field(double x, double y, double z) {
            double px = x - pad - a, py = y - pad - b, pz = z - pad - c;
            if (exact) return sdEllipsoid(px, py, pz, a, b, c);
            return (Math.sqrt(px * px / (a * a) + py * py / (b * b) + pz * pz / (c * c)) - 1) * least;
        }

        @Override public List<double[]> wireframe() {
            int n = (int) Math.max(48, Math.min(360, Math.round((a + b + c) * 4)));
            double[] xy = new double[(n + 1) * 3], xz = new double[(n + 1) * 3], yz = new double[(n + 1) * 3];
            double ox = pad + a, oy = pad + b, oz = pad + c;
            for (int k = 0; k <= n; k++) {
                double cos = Math.cos(k * 2 * Math.PI / n), sin = Math.sin(k * 2 * Math.PI / n);
                put(xy, k, ox + a * cos, oy + b * sin, oz);
                put(xz, k, ox + a * cos, oy, oz + c * sin);
                put(yz, k, ox, oy + b * cos, oz + c * sin);
            }
            return List.of(xy, xz, yz);
        }

        @Override public Wrapping wrapping() {
            double around = perimeter(a, c), up = (perimeter(a, b) + perimeter(c, b)) / 4;
            double ox = pad + a, oy = pad + b, oz = pad + c;
            return new Wrap(around, up, true, false, (x, y, z, out) -> {
                double[] q = new double[3];
                sdEllipsoid(x - ox, y - oy, z - oz, a, b, c, q);
                double px = q[0] / a, py = q[1] / b, pz = q[2] / c;
                out[0] = turn(Math.atan2(-px, pz)) * around;
                out[1] = (Math.atan2(py, Math.hypot(px, pz)) / Math.PI + 0.5) * up;
            });
        }
    }

    public static final class Torus implements Shape3 {
        private final int nx, ny, nz;
        private final double major, minor, sx, sy, sz, least;
        private final boolean hollow;

        public Torus(ShapeSettings s) {
            nx = size(s.tW); ny = size(s.tH); nz = size(s.tD);
            double ring = size(s.tRing), tube = Math.max(0.0625, Math.min(ring, s.tTube));
            minor = tube / 2; major = Math.max(0, ring / 2 - minor);
            sx = nx / ring; sy = ny / tube; sz = nz / ring; least = Math.min(sx, Math.min(sy, sz));
            hollow = s.tHollow;
        }

        @Override public int nx() { return nx; }
        @Override public int ny() { return ny; }
        @Override public int nz() { return nz; }
        @Override public boolean hollow() { return hollow; }
        @Override public boolean smooth() { return minor * least >= 2 && (major - minor) * Math.min(sx, sz) >= 1; }
        @Override public double[] carve() { return hollow ? new double[]{Double.NEGATIVE_INFINITY, 0} : null; }
        @Override public boolean symX() { return true; }
        @Override public boolean symY() { return true; }
        @Override public boolean symZ() { return true; }

        @Override public double field(double x, double y, double z) {
            double px = (x - nx / 2.0) / sx, py = (y - ny / 2.0) / sy, pz = (z - nz / 2.0) / sz;
            return (Math.hypot(Math.hypot(px, pz) - major, py) - minor) * least;
        }

        @Override public List<double[]> wireframe() {
            int n = (int) Math.max(48, Math.min(360, Math.round((nx + nz) * 2.0)));
            List<double[]> out = new ArrayList<>();
            double ox = nx / 2.0, oy = ny / 2.0, oz = nz / 2.0;
            double[][] rings = {{major + minor, 0}, {major - minor, 0}, {major, minor}, {major, -minor}};
            for (double[] r : rings) {
                double[] line = new double[(n + 1) * 3];
                for (int k = 0; k <= n; k++) {
                    double th = k * 2 * Math.PI / n;
                    put(line, k, ox + r[0] * Math.cos(th) * sx, oy + r[1] * sy, oz + r[0] * Math.sin(th) * sz);
                }
                out.add(line);
            }
            int m = Math.max(24, n / 4);
            for (int q = 0; q < 4; q++) {
                double cos = Math.cos(q * Math.PI / 2), sin = Math.sin(q * Math.PI / 2);
                double[] line = new double[(m + 1) * 3];
                for (int k = 0; k <= m; k++) {
                    double th = k * 2 * Math.PI / m, rad = major + minor * Math.cos(th);
                    put(line, k, ox + rad * cos * sx, oy + minor * Math.sin(th) * sy, oz + rad * sin * sz);
                }
                out.add(line);
            }
            return out;
        }

        @Override public Wrapping wrapping() {
            double around = perimeter(major * sx, major * sz), tube = perimeter(minor * (sx + sz) / 2, minor * sy);
            double ox = nx / 2.0, oy = ny / 2.0, oz = nz / 2.0;
            return new Wrap(around, tube, true, true, (x, y, z, out) -> {
                double px = (x - ox) / sx, py = (y - oy) / sy, pz = (z - oz) / sz;
                out[0] = turn(Math.atan2(-px, pz)) * around;
                out[1] = turn(Math.atan2(py, Math.hypot(px, pz) - major) + Math.PI / 2) * tube;
            });
        }
    }

    public static final class Equation implements Shape3 {
        private static final double INSET = 1e-6, NUDGE = 1e-7;

        private final int nx, ny, nz;
        private final Expr.Fn3 f;
        private final Message error;
        private final ShapeSettings.Eq3Mode mode;
        private final double x0, y1, z0, sx, sy, sz, half, lo, hi;
        private volatile List<double[]> wires;

        public Equation(ShapeSettings s) {
            int w = size(s.q3W), d = size(s.q3D), h = size(s.q3H);
            double[] r = new double[6];
            Expr.Fn3 fn = null;
            Message err = null;
            String rel = null;
            try {
                String[] src = {s.x3min, s.x3max, s.y3min, s.y3max, s.z3min, s.z3max};
                for (int k = 0; k < 6; k++) r[k] = Expr.constant(src[k], "curvegen.range." + "xyz".charAt(k / 2) + (k % 2 == 0 ? "_from" : "_to"));
                for (int k = 0; k < 3; k++)
                    if (!(r[2 * k + 1] > r[2 * k])) throw new Expr.ParseException(Message.of("curvegen.error.range_order", String.valueOf("xyz".charAt(k))));
                Expr.Equation3 eq = Expr.parseEquation3(s.src3);
                fn = eq.f(); rel = eq.rel();
                if (s.q3Lock) {
                    d = scaled(w, r[3] - r[2], r[1] - r[0]);
                    h = scaled(w, r[5] - r[4], r[1] - r[0]);
                }
            } catch (Expr.ParseException e) {
                err = e.text();
            }
            nx = w; ny = h; nz = d; f = fn; error = err;
            mode = rel == null || rel.equals("=") ? s.q3Mode : rel.startsWith("<") ? ShapeSettings.Eq3Mode.BELOW : ShapeSettings.Eq3Mode.ABOVE;
            x0 = r[0]; y1 = r[3]; z0 = r[4];
            sx = (r[1] - r[0]) / nx; sy = (r[3] - r[2]) / nz; sz = (r[5] - r[4]) / ny;
            half = thickness(s.q3T) / 2;
            lo = mode == ShapeSettings.Eq3Mode.BELOW ? Double.NEGATIVE_INFINITY : mode == ShapeSettings.Eq3Mode.ABOVE ? 0 : -half;
            hi = mode == ShapeSettings.Eq3Mode.BELOW ? 0 : mode == ShapeSettings.Eq3Mode.ABOVE ? Double.POSITIVE_INFINITY : half;
        }

        @Override public int nx() { return nx; }
        @Override public int ny() { return ny; }
        @Override public int nz() { return nz; }
        @Override public double lo() { return lo; }
        @Override public double hi() { return hi; }
        @Override public Message error() { return error; }
        @Override public double inset() { return INSET; }
        public ShapeSettings.Eq3Mode mode() { return mode; }

        @Override public double[] carve() {
            return switch (mode) {
                case BELOW -> new double[]{0, Double.POSITIVE_INFINITY};
                case ABOVE -> new double[]{Double.NEGATIVE_INFINITY, 0};
                case SURFACE -> null;
            };
        }

        private double raw(double x, double y, double z) { return f.eval(x0 + x * sx, y1 - z * sy, z0 + y * sz); }

        @Override public double field(double x, double y, double z) {
            return mode == ShapeSettings.Eq3Mode.SURFACE ? distance(x, y, z, half + 0.5) : raw(x, y, z);
        }

        @Override public void lattice(int i, int j, int k, double[] lattice) {
            if (mode != ShapeSettings.Eq3Mode.SURFACE) { Shape3.super.lattice(i, j, k, lattice); return; }
            double[] touch = new double[4], first = null;
            for (int q = 0; q < 125; q++) {
                int a = q % 5, b = q / 25, c = q / 5 % 5;
                double v = distance(i + (a == 0 ? INSET : a == 4 ? 1 - INSET : a * .25), j + (b == 0 ? INSET : b == 4 ? 1 - INSET : b * .25), k + (c == 0 ? INSET : c == 4 ? 1 - INSET : c * .25), half + 0.5, touch);
                if (touch[3] != 0 && v == v) {
                    if (first == null) first = touch.clone();
                    v = touch[0] * first[0] + touch[1] * first[1] + touch[2] * first[2] < 0 ? -Math.abs(v) : Math.abs(v);
                }
                lattice[q] = v;
            }
        }

        private double slope(double x, double y, double z, double v, int axis) {
            for (double e = NUDGE; ; e = -e) {
                double g = (raw(x + (axis == 0 ? e : 0), y + (axis == 1 ? e : 0), z + (axis == 2 ? e : 0)) - v) / e;
                if ((Double.isFinite(g) && g != 0) || e < 0) return g;
            }
        }

        private double distance(double x0, double y0, double z0, double reach) { return distance(x0, y0, z0, reach, null); }

        private double distance(double x0, double y0, double z0, double reach, double[] touch) {
            if (touch != null) touch[3] = 0;
            double x = x0, y = y0, z = z0, v = raw(x, y, z);
            if (v == 0) return 0;
            if (!Double.isFinite(v)) return Double.NaN;
            double sign = v < 0 ? -1 : 1, gone = 0;
            reach = 1.5 * reach + 0.5;
            for (int leg = 0; leg < 8; leg++) {
                double gx = slope(x, y, z, v, 0), gy = slope(x, y, z, v, 1), gz = slope(x, y, z, v, 2);
                double g = Math.sqrt(gx * gx + gy * gy + gz * gz), left = reach - gone;
                if (!(Math.abs(v) / g <= 4 * left)) {
                    gx = (raw(x + .25, y, z) - v) * 4; gy = (raw(x, y + .25, z) - v) * 4; gz = (raw(x, y, z + .25) - v) * 4;
                    g = Math.sqrt(gx * gx + gy * gy + gz * gz);
                }
                if (!(g > 0) || g == Double.POSITIVE_INFINITY) return Double.NaN;
                double guess = Math.abs(v) / g;
                if (guess > 4 * left) return Double.NaN;
                double ux = -sign * gx / g, uy = -sign * gy / g, uz = -sign * gz / g;
                if (guess < 2e-3) {
                    if (guess < 1e-9 || Math.abs(raw(x + guess * ux, y + guess * uy, z + guess * uz)) <= 0.5 * Math.abs(v)) {
                        double ex = x + guess * ux - x0, ey = y + guess * uy - y0, ez = z + guess * uz - z0;
                        return sign * Math.sqrt(ex * ex + ey * ey + ez * ez);
                    }
                }
                double a = 0, fa = v, b = Math.min(1.5 * guess + 0.02, left), fb = raw(x + b * ux, y + b * uy, z + b * uz);
                boolean crossed = fb == 0 || (fb == fb && (fb < 0) != (v < 0));
                boolean walk = false;
                if (!crossed && !(Math.abs(fb - v * (1 - b / guess)) <= 0.5 * Math.abs(v))) {
                    double mid = raw(x + b / 2 * ux, y + b / 2 * uy, z + b / 2 * uz);
                    if (mid == 0 || (mid == mid && (mid < 0) != (v < 0))) { b /= 2; fb = mid; crossed = true; }
                    else walk = !(Math.abs(fb) <= Math.abs(mid) && Math.abs(mid) <= Math.abs(v));
                }
                if (!crossed && guess < 0.1) {
                    double tx = x, ty = y, tz = z, tv = v, step = guess, wx = ux, wy = uy, wz = uz;
                    for (int it = 0; it < 40; it++) {
                        tx += step * wx; ty += step * wy; tz += step * wz;
                        double nv = raw(tx, ty, tz);
                        if (!(Math.abs(nv) <= 0.75 * Math.abs(tv))) break;
                        tv = nv;
                        if (tv == 0 || step < 1e-4) {
                            double ex = tx - x0, ey = ty - y0, ez = tz - z0;
                            if (touch != null) {
                                double past = raw(tx + 2e-3 * wx, ty + 2e-3 * wy, tz + 2e-3 * wz);
                                if (past == past && past != 0 && (past < 0) == (v < 0)) { touch[0] = ex; touch[1] = ey; touch[2] = ez; touch[3] = 1; }
                            }
                            return sign * Math.sqrt(ex * ex + ey * ey + ez * ez);
                        }
                        double hx = slope(tx, ty, tz, tv, 0), hy = slope(tx, ty, tz, tv, 1), hz = slope(tx, ty, tz, tv, 2), h = Math.sqrt(hx * hx + hy * hy + hz * hz);
                        if (!(h > 0) || h == Double.POSITIVE_INFINITY) break;
                        double side = tv < 0 ? -1 : 1;
                        step = Math.abs(tv) / h; wx = -side * hx / h; wy = -side * hy / h; wz = -side * hz / h;
                    }
                }
                if (walk) {
                    double end = b;
                    for (b = 0, fb = v; !crossed && b < end; ) {
                        if (fb == fb) { a = b; fa = fb; }
                        b = Math.min(b + 0.25, end);
                        fb = raw(x + b * ux, y + b * uy, z + b * uz);
                        crossed = fb == 0 || (fb == fb && (fb < 0) != (v < 0));
                    }
                    for (int it = 0; fb != fb && !crossed && it < 14; it++) {
                        double m = (a + b) / 2, fm = raw(x + m * ux, y + m * uy, z + m * uz);
                        if (fm != fm) b = m;
                        else if (fm == 0 || (fm < 0) != (v < 0)) { b = m; fb = fm; crossed = true; }
                        else { a = m; fa = fm; }
                    }
                }
                if (fb != fb || fb == Double.POSITIVE_INFINITY || fb == Double.NEGATIVE_INFINITY) return Double.NaN;
                if (crossed) {
                    double near = 1e-4 * g, m = b, fm = fb;
                    for (int it = 0, kept = 0; it < 5 && Math.abs(fm) > near; it++) {
                        m = (a * fb - b * fa) / (fb - fa);
                        fm = raw(x + m * ux, y + m * uy, z + m * uz);
                        if (fm != fm) return Double.NaN;
                        if ((fm < 0) == (fa < 0)) { a = m; fa = fm; if (kept == 1) fb /= 2; kept = 1; }
                        else { b = m; fb = fm; if (kept == -1) fa /= 2; kept = -1; }
                    }
                    if (Math.abs(fm) > near) {
                        fa = raw(x + a * ux, y + a * uy, z + a * uz); fb = raw(x + b * ux, y + b * uy, z + b * uz);
                        double earlier = 0;
                        for (int it = 0; it < 12; it++) {
                            if (it == 8) earlier = Math.abs(fa) + Math.abs(fb);
                            m = (a + b) / 2;
                            fm = raw(x + m * ux, y + m * uy, z + m * uz);
                            if (fm != fm) return Double.NaN;
                            if ((fm < 0) == (fa < 0)) { a = m; fa = fm; } else { b = m; fb = fm; }
                        }
                        if (!(Math.abs(fa) + Math.abs(fb) <= 0.5 * earlier)) return Double.NaN;
                    }
                    double ex = x + m * ux - x0, ey = y + m * uy - y0, ez = z + m * uz - z0;
                    return sign * Math.sqrt(ex * ex + ey * ey + ez * ez);
                }
                gone += b;
                if (gone >= reach) return Double.NaN;
                x += b * ux; y += b * uy; z += b * uz; v = fb;
            }
            return Double.NaN;
        }

        @Override public int uniform(int i, int j, int k) {
            boolean wall = mode == ShapeSettings.Eq3Mode.SURFACE;
            double v0 = raw(i + .5, j + .5, k + .5);
            if (v0 != 0 && Double.isFinite(v0)) {
                double gx = slope(i + .5, j + .5, k + .5, v0, 0), gy = slope(i + .5, j + .5, k + .5, v0, 1), gz = slope(i + .5, j + .5, k + .5, v0, 2);
                double g = Math.sqrt(gx * gx + gy * gy + gz * gz);
                if (g > 0 && Math.abs(v0) / g > 4 * ((wall ? half + R_CELL : 0) + 2)) {
                    int n = 0;
                    for (double before = v0; n < 32 && i + n < nx && straight(i + n, j, k, i + .5, v0, gx, gy, gz); n++) {
                        double v = raw(i + n + .5, j + .5, k + .5);
                        if ((v < 0) != (v0 < 0) || !(Math.abs(v) >= Math.abs(v0) / 2) || !(Math.abs(v - before) <= 2 * g)) break;
                        before = v;
                    }
                    if (n > 0) return !wall && v0 >= lo && v0 <= hi ? n : -n;
                }
            }
            double least = Double.POSITIVE_INFINITY, most = Double.NEGATIVE_INFINITY, margin = Double.POSITIVE_INFINITY;
            double closest = Double.POSITIVE_INFINITY;
            int in = 0, out = 0, none = 0, undefined = 0;
            for (int c = 0; c < 9; c++) {
                double x = c == 8 ? i + .5 : i + ((c & 1) == 0 ? INSET : 1 - INSET), y = c == 8 ? j + .5 : j + ((c & 2) == 0 ? INSET : 1 - INSET),
                        z = c == 8 ? k + .5 : k + ((c & 4) == 0 ? INSET : 1 - INSET);
                double v = raw(x, y, z);
                if (v != v) undefined++;
                else if (wall) v = distance(x, y, z, half + R_CELL + 0.05);
                if (v != v) { none++; continue; }
                if (v == Double.POSITIVE_INFINITY || v == Double.NEGATIVE_INFINITY) return 0;
                if (v >= lo && v <= hi) in++; else out++;
                least = Math.min(least, v); most = Math.max(most, v);
                margin = Math.min(margin, wall ? half - Math.abs(v) : Math.abs(v));
                closest = Math.min(closest, Math.abs(v));
            }
            if (undefined > 0 && undefined < 9) return 0;
            if (undefined == 9) {
                for (int q = 0; q < 125; q++) {
                    int a = q % 5, b = q / 25, c = q / 5 % 5;
                    double v = raw(i + (a == 0 ? INSET : a == 4 ? 1 - INSET : a * .25), j + (b == 0 ? INSET : b == 4 ? 1 - INSET : b * .25), k + (c == 0 ? INSET : c == 4 ? 1 - INSET : c * .25));
                    if (v == v) return 0;
                }
                return -1;
            }
            if (wall) {
                if (in == 0) return closest > half + R_CELL ? -1 : 0;
                if (in < 9 || !(margin > R_CELL)) return 0;
                double gentlest = Double.POSITIVE_INFINITY, steepest = 0;
                for (int c = 0; c < 9; c++) {
                    double x = c == 8 ? i + .5 : i + ((c & 1) == 0 ? INSET : 1 - INSET), y = c == 8 ? j + .5 : j + ((c & 2) == 0 ? INSET : 1 - INSET),
                            z = c == 8 ? k + .5 : k + ((c & 4) == 0 ? INSET : 1 - INSET);
                    double v = raw(x, y, z), gx = slope(x, y, z, v, 0), gy = slope(x, y, z, v, 1), gz = slope(x, y, z, v, 2), g = Math.sqrt(gx * gx + gy * gy + gz * gz);
                    gentlest = Math.min(gentlest, g); steepest = Math.max(steepest, g);
                }
                return steepest <= 2 * gentlest ? 1 : 0;
            }
            if (none == 9) return -1;
            if (none > 0 || (in > 0 && out > 0)) return 0;
            return margin > 0.5 * (most - least) ? (in > 0 ? 1 : -1) : 0;
        }

        private boolean straight(int i, int j, int k, double x0, double v0, double gx, double gy, double gz) {
            for (int c = 0; c < 8; c++) {
                double x = i + ((c & 1) == 0 ? INSET : 1 - INSET), dy = (c & 2) == 0 ? INSET - .5 : .5 - INSET, dz = (c & 4) == 0 ? INSET - .5 : .5 - INSET;
                if (!(Math.abs(raw(x, j + .5 + dy, k + .5 + dz) - (v0 + gx * (x - x0) + gy * dy + gz * dz)) <= 0.25 * Math.abs(v0))) return false;
            }
            return true;
        }

        @Override public boolean follows(double[] t) {
            if (mode == ShapeSettings.Eq3Mode.SURFACE) return true;
            int[] step = {1, 25, 5};
            for (int q = 0; q < 125; q++) {
                int[] at = {q % 5, q / 25, q / 5 % 5};
                for (int axis = 0; axis < 3; axis++) {
                    if (at[axis] == 0 || at[axis] == 4) continue;
                    double before = t[q - step[axis]], here = t[q], after = t[q + step[axis]];
                    boolean inB = before >= lo && before <= hi, inH = here >= lo && here <= hi, inA = after >= lo && after <= hi;
                    if (inB == inH && inH == inA && before == before && here == here && after == after) continue;
                    if (!Double.isFinite(before) || !Double.isFinite(here) || !Double.isFinite(after)) return false;
                    double g2 = 0;
                    for (int ax = 0; ax < 3; ax++) {
                        double lower = t[at[ax] == 0 ? q : q - step[ax]], upper = t[at[ax] == 4 ? q : q + step[ax]];
                        if (!Double.isFinite(lower) || !Double.isFinite(upper)) return false;
                        double g = (upper - lower) / (at[ax] == 0 || at[ax] == 4 ? 1 : 2);
                        g2 += g * g;
                    }
                    double bend = before - 2 * here + after;
                    if (bend * bend > g2) return false;
                }
            }
            return true;
        }

        @Override public List<double[]> wireframe() {
            List<double[]> w = wires;
            if (w != null) return w;
            w = new ArrayList<>();
            for (int top = 0; top < 2; top++) {
                double y = top * ny;
                w.add(new double[]{0, y, 0, nx, y, 0, nx, y, nz, 0, y, nz, 0, y, 0});
            }
            for (int c = 0; c < 4; c++) w.add(new double[]{(c & 1) * nx, 0, (c >> 1) * nz, (c & 1) * nx, ny, (c >> 1) * nz});
            if (error == null) {
                int[] n = {nx, ny, nz};
                for (int axis = 0; axis < 3; axis++)
                    for (int cut = 0; cut <= 4; cut++) {
                        if (axis == 1 && (cut == 0 || cut == 4)) continue;
                        slice(w, axis, Math.max(INSET, Math.min(n[axis] - INSET, n[axis] * cut / 4.0)));
                    }
            }
            return wires = w;
        }

        private void slice(List<double[]> out, int axis, double at) {
            int a1 = axis == 0 ? 1 : 0, a2 = axis == 2 ? 1 : 2;
            int[] n = {nx, ny, nz};
            int cols = Math.min(n[a1], 96), rows = Math.min(n[a2], 96);
            double w1 = (double) n[a1] / cols, w2 = (double) n[a2] / rows;
            double[] v = new double[(cols + 1) * (rows + 1)];
            double[] p = new double[3], q = new double[3];
            p[axis] = q[axis] = at;
            for (int b = 0; b <= rows; b++)
                for (int a = 0; a <= cols; a++) {
                    p[a1] = a * w1; p[a2] = b * w2;
                    v[b * (cols + 1) + a] = raw(p[0], p[1], p[2]);
                }
            double[][] cross = new double[4][];
            for (int b = 0; b < rows; b++)
                for (int a = 0; a < cols; a++) {
                    int o = b * (cols + 1) + a, found = 0;
                    int[][] ends = {{a, b, a + 1, b}, {a + 1, b, a + 1, b + 1}, {a, b + 1, a + 1, b + 1}, {a, b, a, b + 1}};
                    double[][] vals = {{v[o], v[o + 1]}, {v[o + 1], v[o + cols + 2]}, {v[o + cols + 1], v[o + cols + 2]}, {v[o], v[o + cols + 1]}};
                    for (int e = 0; e < 4; e++) {
                        p[a1] = ends[e][0] * w1; p[a2] = ends[e][1] * w2; q[a1] = ends[e][2] * w1; q[a2] = ends[e][3] * w2;
                        double[] c = crossing(p, vals[e][0], q, vals[e][1]);
                        if (c != null) cross[found++] = c;
                    }
                    if (found == 2 || found == 4)
                        for (int e = 0; e < found; e += 2)
                            out.add(new double[]{cross[e][0], cross[e][1], cross[e][2], cross[e + 1][0], cross[e + 1][1], cross[e + 1][2]});
                }
        }

        private double[] crossing(double[] p, double fp, double[] q, double fq) {
            if (!Double.isFinite(fp) || !Double.isFinite(fq) || (fp <= 0) == (fq <= 0)) return null;
            double a = 0, b = 1, fm = fq, most = Math.max(Math.abs(fp), Math.abs(fq));
            for (int it = 0; it < 8; it++) {
                double m = (a + b) / 2;
                fm = raw(p[0] + (q[0] - p[0]) * m, p[1] + (q[1] - p[1]) * m, p[2] + (q[2] - p[2]) * m);
                if (fm != fm) return null;
                if ((fm <= 0) == (fp <= 0)) a = m; else b = m;
            }
            double m = (a + b) / 2;
            return Math.abs(fm) <= 0.1 * most ? new double[]{p[0] + (q[0] - p[0]) * m, p[1] + (q[1] - p[1]) * m, p[2] + (q[2] - p[2]) * m} : null;
        }
    }

    private static final double FAR = 2;

    public static final class Curve implements Shape3 {
        private static final double FLAT = 0.01, LONGEST = 2, PAST = 3;

        private final int nx, ny, nz, pad;
        private final double hi, cap;
        private final boolean hollow, open;
        private final List<double[]> pts = new ArrayList<>();
        private final Message error;
        private volatile double[] segs;
        private volatile Near3 near;

        public Curve(ShapeSettings s) {
            hi = thickness(s.b3T) / 2; cap = hi + FAR;
            hollow = s.b3Hollow && 2 * hi >= ShapeSettings.HOLLOW_MIN;
            open = hollow && s.b3Open;
            pad = (int) Math.ceil(hi);
            nx = size(s.b3W) + 2 * pad; ny = size(s.b3H) + 2 * pad; nz = size(s.b3D) + 2 * pad;
            for (double[] p : s.pts3) pts.add(new double[]{p[0] + pad, p[1] + pad, p[2] + pad});
            error = pts.size() < 2 ? Message.of("curvegen.error.curve_min_points") : pts.size() > Bezier3.MAX_POINTS ? Message.of("curvegen.error.curve_max_points", Bezier3.MAX_POINTS) : null;
        }

        @Override public int nx() { return nx; }
        @Override public int ny() { return ny; }
        @Override public int nz() { return nz; }
        @Override public int pad() { return pad; }
        @Override public double hi() { return hi; }
        @Override public Message error() { return error; }
        @Override public boolean smooth() { return hi >= 0.5; }
        @Override public boolean hollow() { return hollow; }
        @Override public double[] carve() { return hollow ? new double[]{Double.NEGATIVE_INFINITY, hi} : null; }
        @Override public boolean cuts() { return open && error == null; }

        @Override public boolean cut(double x, double y, double z) {
            Near3 n = near();
            int b = n.bucket(x, y, z), count = n.count(b);
            int[] items = n.items(b);
            double[] g = segs;
            int last = g.length / 7 - 1;
            double beyond = Double.POSITIVE_INFINITY, curve = Double.POSITIVE_INFINITY;
            for (int q = 0; q < count; q++) {
                double d = off(g, items[q], x, y, z);
                if (items[q] == 0 || items[q] == last) beyond = Math.min(beyond, d);
                else curve = Math.min(curve, d);
            }
            return beyond < curve - 1e-9 * (1 + beyond);
        }

        private double[] segs() {
            double[] g = segs;
            if (g != null) return g;
            List<double[]> line = new ArrayList<>();
            int spans = 2 * (pts.size() - 1);
            double[] a = Bezier3.point(pts, 0);
            line.add(a);
            for (int k = 0; k < spans; k++) {
                double[] b = Bezier3.point(pts, (k + 1.0) / spans);
                flatten(line, (double) k / spans, a, (k + 1.0) / spans, b, 0);
                a = b;
            }
            if (open) {
                double[] before = beyond(line.getFirst(), pts, 0, 1), after = beyond(line.getLast(), pts, pts.size() - 1, -1);
                line.add(0, before);
                line.add(after);
            }
            g = new double[(line.size() - 1) * 7];
            for (int k = 0; k + 1 < line.size(); k++) {
                double[] p = line.get(k), q = line.get(k + 1);
                double dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2], l2 = dx * dx + dy * dy + dz * dz;
                System.arraycopy(p, 0, g, k * 7, 3);
                g[k * 7 + 3] = dx; g[k * 7 + 4] = dy; g[k * 7 + 5] = dz; g[k * 7 + 6] = l2 > 0 ? 1 / l2 : 0;
            }
            return segs = g;
        }

        private static double[] beyond(double[] end, List<double[]> pts, int at, int step) {
            double[] e = pts.get(at);
            for (int k = at + step; k >= 0 && k < pts.size(); k += step) {
                double[] p = pts.get(k);
                double dx = e[0] - p[0], dy = e[1] - p[1], dz = e[2] - p[2], l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (l > 1e-9) return new double[]{end[0] + dx / l * PAST, end[1] + dy / l * PAST, end[2] + dz / l * PAST};
            }
            return end;
        }

        private void flatten(List<double[]> line, double u0, double[] a, double u1, double[] b, int depth) {
            double um = (u0 + u1) / 2;
            double[] m = Bezier3.point(pts, um);
            boolean flat = depth >= 14 || (Math.sqrt(off(a, b, m)) <= FLAT && Math.sqrt(off(a, b, Bezier3.point(pts, (u0 + um) / 2))) <= FLAT
                    && Math.sqrt(off(a, b, Bezier3.point(pts, (um + u1) / 2))) <= FLAT
                    && (b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]) <= LONGEST * LONGEST);
            if (flat) { line.add(b); return; }
            flatten(line, u0, a, um, m, depth + 1);
            flatten(line, um, m, u1, b, depth + 1);
        }

        private static double off(double[] a, double[] b, double[] p) {
            double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2], l2 = dx * dx + dy * dy + dz * dz;
            double t = l2 > 0 ? Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy + (p[2] - a[2]) * dz) / l2)) : 0;
            double ex = a[0] + dx * t - p[0], ey = a[1] + dy * t - p[1], ez = a[2] + dz * t - p[2];
            return ex * ex + ey * ey + ez * ez;
        }

        private static double off(double[] g, int k, double x, double y, double z) {
            int o = k * 7;
            double px = x - g[o], py = y - g[o + 1], pz = z - g[o + 2], dx = g[o + 3], dy = g[o + 4], dz = g[o + 5];
            double t = (px * dx + py * dy + pz * dz) * g[o + 6];
            t = t < 0 ? 0 : t > 1 ? 1 : t;
            double ex = px - dx * t, ey = py - dy * t, ez = pz - dz * t;
            return ex * ex + ey * ey + ez * ez;
        }

        private Near3 near() {
            Near3 n = near;
            if (n != null) return n;
            synchronized (this) {
                if (near != null) return near;
                double[] g = segs(), box = new double[g.length / 7 * 6];
                for (int k = 0; k < g.length / 7; k++)
                    for (int c = 0; c < 3; c++) {
                        box[k * 6 + c] = Math.min(g[k * 7 + c], g[k * 7 + c] + g[k * 7 + 3 + c]);
                        box[k * 6 + 3 + c] = Math.max(g[k * 7 + c], g[k * 7 + c] + g[k * 7 + 3 + c]);
                    }
                return near = new Near3(nx, ny, nz, g.length / 7, box, cap, (k, x, y, z) -> Math.sqrt(off(g, k, x, y, z)));
            }
        }

        @Override public double field(double x, double y, double z) {
            Near3 n = near();
            int b = n.bucket(x, y, z), count = n.count(b);
            int[] items = n.items(b);
            double[] g = segs;
            double best = cap * cap;
            for (int q = 0; q < count; q++) best = Math.min(best, off(g, items[q], x, y, z));
            return Math.sqrt(best);
        }

        @Override public int uniform(int i, int j, int k) {
            Near3 n = near();
            if (n.count(n.bucket(i + .5, j + .5, k + .5)) == 0) return -(Near3.SIZE - i % Near3.SIZE);
            return Shape3.super.uniform(i, j, k);
        }

        @Override public List<double[]> wireframe() {
            double[] polygon = new double[pts.size() * 3];
            for (int k = 0; k < pts.size(); k++) System.arraycopy(pts.get(k), 0, polygon, k * 3, 3);
            if (error != null) return List.of(polygon);
            double[] g = segs();
            int first = open ? 1 : 0, last = g.length / 7 - 1 - first;
            double[] line = new double[(last - first + 2) * 3];
            for (int k = first; k <= last; k++) System.arraycopy(g, k * 7, line, (k - first) * 3, 3);
            for (int c = 0; c < 3; c++) line[(last - first + 1) * 3 + c] = g[last * 7 + c] + g[last * 7 + 3 + c];
            return List.of(line, polygon);
        }

        @Override public Wrapping wrapping() {
            if (error != null) return null;
            double[] g = segs();
            int first = open ? 1 : 0, last = g.length / 7 - 1 - first, n = last - first + 1;
            double[] at = new double[n + 1], frame = new double[n * 9];
            double[] nn = null;
            for (int k = 0; k < n; k++) {
                int o = (first + k) * 7;
                double l = Math.sqrt(g[o + 3] * g[o + 3] + g[o + 4] * g[o + 4] + g[o + 5] * g[o + 5]);
                at[k + 1] = at[k] + l;
                double[] t = l > 0 ? new double[]{g[o + 3] / l, g[o + 4] / l, g[o + 5] / l} : k > 0 ? new double[]{frame[(k - 1) * 9], frame[(k - 1) * 9 + 1], frame[(k - 1) * 9 + 2]} : new double[]{1, 0, 0};
                double[] m = nn == null ? new double[]{0, -1, 0} : nn;
                double d = m[0] * t[0] + m[1] * t[1] + m[2] * t[2];
                double[] q = {m[0] - d * t[0], m[1] - d * t[1], m[2] - d * t[2]};
                double ql = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
                if (ql < 1e-6) {
                    q = Math.abs(t[0]) < 0.9 ? new double[]{1 - t[0] * t[0], -t[0] * t[1], -t[0] * t[2]} : new double[]{-t[1] * t[0], 1 - t[1] * t[1], -t[1] * t[2]};
                    ql = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
                }
                nn = new double[]{q[0] / ql, q[1] / ql, q[2] / ql};
                setFrame(frame, k, t, nn);
            }
            double sc = 0, ss = 0;
            for (int k = 0; k < n; k++) {
                double w = at[k + 1] - at[k];
                sc -= w * frame[k * 9 + 4];
                ss -= w * frame[k * 9 + 7];
            }
            if (sc * sc + ss * ss > 1e-12 * at[n] * at[n]) {
                double len = Math.hypot(sc, ss), cos = sc / len, sin = ss / len;
                for (int k = 0; k < n; k++) {
                    int o = k * 9;
                    double[] t = {frame[o], frame[o + 1], frame[o + 2]};
                    double[] m = {cos * frame[o + 3] + sin * frame[o + 6], cos * frame[o + 4] + sin * frame[o + 7], cos * frame[o + 5] + sin * frame[o + 8]};
                    setFrame(frame, k, t, m);
                }
            }
            double length = at[n], around = 2 * Math.PI * hi;
            Near3 near = near();
            return new Wrap(length, around, false, true, (x, y, z, out) -> {
                int b = near.bucket(x, y, z), count = near.count(b);
                int[] items = near.items(b);
                int best = -1;
                double bestD = Double.POSITIVE_INFINITY;
                for (int q = 0; q < count; q++) {
                    int k = items[q] - first;
                    if (k < 0 || k >= n) continue;
                    double d = off(g, items[q], x, y, z);
                    if (d < bestD) { bestD = d; best = k; }
                }
                if (best < 0)
                    for (int k = 0; k < n; k++) {
                        double d = off(g, k + first, x, y, z);
                        if (d < bestD) { bestD = d; best = k; }
                    }
                int o = (best + first) * 7, f = best * 9;
                double px = x - g[o], py = y - g[o + 1], pz = z - g[o + 2];
                double t = Math.max(0, Math.min(1, (px * g[o + 3] + py * g[o + 4] + pz * g[o + 5]) * g[o + 6]));
                double rx = px - g[o + 3] * t, ry = py - g[o + 4] * t, rz = pz - g[o + 5] * t;
                out[0] = at[best] + t * (at[best + 1] - at[best]);
                double cn = rx * frame[f + 3] + ry * frame[f + 4] + rz * frame[f + 5], cb = rx * frame[f + 6] + ry * frame[f + 7] + rz * frame[f + 8];
                out[1] = turn(Math.atan2(cb, cn)) * around;
            });
        }

        private static void setFrame(double[] frame, int k, double[] t, double[] m) {
            int o = k * 9;
            frame[o] = t[0]; frame[o + 1] = t[1]; frame[o + 2] = t[2];
            frame[o + 3] = m[0]; frame[o + 4] = m[1]; frame[o + 5] = m[2];
            frame[o + 6] = t[1] * m[2] - t[2] * m[1]; frame[o + 7] = t[2] * m[0] - t[0] * m[2]; frame[o + 8] = t[0] * m[1] - t[1] * m[0];
        }
    }

    public static final class Patch implements Shape3 {
        private static final double TRUST = 1.5;
        private static final int MOST = 384;

        private final int nx, ny, nz, pad, rows, cols;
        private final double hi, cap;
        private final List<double[]> pts = new ArrayList<>();
        private final Message error;
        private volatile Built built;
        private volatile List<double[]> wires;

        private record Built(int nu, int nv, double[] at, int[] coarse, Near3 near, double span, double apart, double acrossU, double acrossV) {
            double d2(int k, double x, double y, double z) {
                double ex = at[k * 18] - x, ey = at[k * 18 + 1] - y, ez = at[k * 18 + 2] - z;
                return ex * ex + ey * ey + ez * ez;
            }

            int walk(int k, double x, double y, double z) {
                int w = nu + 1, a = k % w, v = k / w;
                double d = d2(k, x, y, z);
                for (boolean moved = true; moved; ) {
                    moved = false;
                    int a0 = a, v0 = v;
                    for (int dv = -1; dv <= 1; dv++)
                        for (int da = -1; da <= 1; da++) {
                            int na = a0 + da, nv2 = v0 + dv;
                            if (na < 0 || nv2 < 0 || na > nu || nv2 > nv || (da == 0 && dv == 0)) continue;
                            double e = d2(nv2 * w + na, x, y, z);
                            if (e < d) { d = e; a = na; v = nv2; moved = true; }
                        }
                }
                return v * w + a;
            }
        }

        public Patch(ShapeSettings s) {
            hi = thickness(s.sT) / 2; cap = hi + FAR;
            pad = (int) Math.ceil(hi);
            nx = size(s.sW) + 2 * pad; ny = size(s.sH) + 2 * pad; nz = size(s.sD) + 2 * pad;
            rows = s.sRows; cols = s.sCols;
            for (double[] p : s.sPts) pts.add(new double[]{p[0] + pad, p[1] + pad, p[2] + pad});
            error = rows < Bezier3.MIN_GRID || cols < Bezier3.MIN_GRID || rows > Bezier3.MAX_GRID || cols > Bezier3.MAX_GRID || pts.size() != rows * cols
                    ? Message.of("curvegen.error.surface_grid", Bezier3.MIN_GRID, Bezier3.MAX_GRID) : null;
        }

        @Override public int nx() { return nx; }
        @Override public int ny() { return ny; }
        @Override public int nz() { return nz; }
        @Override public int pad() { return pad; }
        @Override public double lo() { return -hi; }
        @Override public double hi() { return hi; }
        @Override public Message error() { return error; }

        private double[] p(int r, int c) { return pts.get(r * cols + c); }

        private Built built() {
            Built b = built;
            if (b != null) return b;
            synchronized (this) {
                if (built != null) return built;
                double longU = 0, longV = 0;
                for (int r = 0; r < rows; r++) {
                    double l = 0;
                    for (int c = 1; c < cols; c++) l += dist(p(r, c - 1), p(r, c));
                    longU = Math.max(longU, l);
                }
                for (int c = 0; c < cols; c++) {
                    double l = 0;
                    for (int r = 1; r < rows; r++) l += dist(p(r - 1, c), p(r, c));
                    longV = Math.max(longV, l);
                }
                int nu = (int) Math.max(4 * (cols - 1), Math.min(MOST, Math.ceil(longU))), nv = (int) Math.max(4 * (rows - 1), Math.min(MOST, Math.ceil(longV)));
                double[] bv = new double[rows], bv1 = new double[rows], bv2 = new double[rows], bu = new double[cols], bu1 = new double[cols], bu2 = new double[cols];
                double[][] basisU = new double[nu + 1][cols * 3];
                for (int a = 0; a <= nu; a++) {
                    Bezier3.basis(cols - 1, (double) a / nu, bu, bu1, bu2);
                    for (int c = 0; c < cols; c++) { basisU[a][c] = bu[c]; basisU[a][cols + c] = bu1[c] / nu; basisU[a][2 * cols + c] = bu2[c] / ((double) nu * nu); }
                }
                double[] at = new double[(nu + 1) * (nv + 1) * 18], mid = new double[cols * 9];
                for (int v = 0; v <= nv; v++) {
                    Bezier3.basis(rows - 1, (double) v / nv, bv, bv1, bv2);
                    java.util.Arrays.fill(mid, 0);
                    for (int c = 0; c < cols; c++)
                        for (int r = 0; r < rows; r++) {
                            double[] q = p(r, c);
                            for (int d = 0; d < 3; d++) {
                                mid[c * 9 + d] += bv[r] * q[d];
                                mid[c * 9 + 3 + d] += bv1[r] / nv * q[d];
                                mid[c * 9 + 6 + d] += bv2[r] / ((double) nv * nv) * q[d];
                            }
                        }
                    for (int a = 0; a <= nu; a++) {
                        int o = (v * (nu + 1) + a) * 18;
                        double[] w = basisU[a];
                        for (int c = 0; c < cols; c++)
                            for (int d = 0; d < 3; d++) {
                                double m = mid[c * 9 + d], mv = mid[c * 9 + 3 + d], mvv = mid[c * 9 + 6 + d];
                                at[o + d] += w[c] * m;
                                at[o + 3 + d] += w[cols + c] * m;
                                at[o + 6 + d] += w[c] * mv;
                                at[o + 9 + d] += w[2 * cols + c] * m;
                                at[o + 12 + d] += w[cols + c] * mv;
                                at[o + 15 + d] += w[c] * mvv;
                            }
                    }
                }
                double span = 0, acrossU = Double.POSITIVE_INFINITY, acrossV = Double.POSITIVE_INFINITY;
                for (int v = 0; v <= nv; v++)
                    for (int a = 0; a <= nu; a++) {
                        int o = (v * (nu + 1) + a) * 18;
                        if (a < nu && v < nv) span = Math.max(span, Math.sqrt(Math.max(d2(at, o, o + 18 * (nu + 2)), d2(at, o + 18, o + 18 * (nu + 1)))));
                        double cx = at[o + 4] * at[o + 8] - at[o + 5] * at[o + 7], cy = at[o + 5] * at[o + 6] - at[o + 3] * at[o + 8], cz = at[o + 3] * at[o + 7] - at[o + 4] * at[o + 6];
                        double area = Math.sqrt(cx * cx + cy * cy + cz * cz);
                        double lu = Math.sqrt(at[o + 3] * at[o + 3] + at[o + 4] * at[o + 4] + at[o + 5] * at[o + 5]), lv = Math.sqrt(at[o + 6] * at[o + 6] + at[o + 7] * at[o + 7] + at[o + 8] * at[o + 8]);
                        acrossU = Math.min(acrossU, lv > 0 ? area / lv : 0);
                        acrossV = Math.min(acrossV, lu > 0 ? area / lu : 0);
                    }
                int cu = (nu + 1) / 2 + 1, cv = (nv + 1) / 2 + 1;
                int[] coarse = new int[cu * cv];
                double[] box = new double[coarse.length * 6];
                for (int q = 0; q < coarse.length; q++) {
                    int k = coarse[q] = Math.min(nv, q / cu * 2) * (nu + 1) + Math.min(nu, q % cu * 2);
                    for (int d = 0; d < 3; d++) box[q * 6 + d] = box[q * 6 + 3 + d] = at[k * 18 + d];
                }
                Near3 near = new Near3(nx, ny, nz, coarse.length, box, cap + 2 * span, (q, x, y, z) -> {
                    int o = coarse[q] * 18;
                    double ex = at[o] - x, ey = at[o + 1] - y, ez = at[o + 2] - z;
                    return Math.sqrt(ex * ex + ey * ey + ez * ez);
                });
                return built = new Built(nu, nv, at, coarse, near, span / 2, span, acrossU, acrossV);
            }
        }

        private static double dist(double[] a, double[] b) { return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])); }
        private static double d2(double[] at, int a, int b) {
            return (at[a] - at[b]) * (at[a] - at[b]) + (at[a + 1] - at[b + 1]) * (at[a + 1] - at[b + 1]) + (at[a + 2] - at[b + 2]) * (at[a + 2] - at[b + 2]);
        }

        private int foot(Built b, double x, double y, double z, double[] out) {
            int bucket = b.near.bucket(x, y, z), count = b.near.count(bucket), stretches = 1;
            if (count == 0) return 0;
            int[] items = b.near.items(bucket);
            int w = b.nu + 1, best = -1;
            double bestD = Double.POSITIVE_INFINITY;
            for (int q = 0; q < count; q++) {
                double d = b.d2(b.coarse[items[q]], x, y, z);
                if (d < bestD) { bestD = d; best = b.coarse[items[q]]; }
            }
            int k = b.walk(best, x, y, z);
            step(b, k % w, k / w, x, y, z, out);
            out[3] = Math.sqrt(b.d2(k, x, y, z));
            double[] seenU = new double[4], seenV = new double[4], again = null;
            seenU[0] = out[5]; seenV[0] = out[6];
            for (int seen = 1; seen < 4; seen++) {
                double within = Math.abs(out[0]) + 2 * b.apart, around = within + 2 * R_CELL;
                int other = -1;
                double otherD = around * around;
                for (int q = 0; q < count; q++) {
                    int c = b.coarse[items[q]];
                    double d = b.d2(c, x, y, z);
                    if (d >= otherD) continue;
                    boolean tried = false;
                    for (int t = 0; t < seen && !tried; t++) {
                        int su = (int) Math.max(0, Math.min(b.nu, Math.round(seenU[t]))), sv = (int) Math.max(0, Math.min(b.nv, Math.round(seenV[t])));
                        tried = (Math.abs(c % w - su) <= 4 && Math.abs(c / w - sv) <= 4)
                                || b.d2((c / w + sv) / 2 * w + (c % w + su) / 2, x, y, z) <= Math.max(d, b.d2(sv * w + su, x, y, z));
                    }
                    if (!tried) { otherD = d; other = c; }
                }
                if (other < 0) break;
                stretches = 2;
                if (otherD > within * within) break;
                if (again == null) again = new double[7];
                k = b.walk(other, x, y, z);
                step(b, k % w, k / w, x, y, z, again);
                seenU[seen] = again[5]; seenV[seen] = again[6];
                again[3] = out[3] = Math.min(out[3], Math.sqrt(b.d2(k, x, y, z)));
                if (Math.abs(again[0]) < Math.abs(out[0])) System.arraycopy(again, 0, out, 0, 7);
            }
            return stretches;
        }

        private void step(Built b, double u, double v, double x, double y, double z, double[] out) {
            double[] at = b.at;
            double s = 0, t = 0, mx = 0, my = 0, mz = 0, ax = 0, ay = 0, az = 0, bx = 0, by = 0, bz = 0;
            for (int hop = 0; ; hop++) {
                int ku = (int) Math.max(0, Math.min(b.nu, Math.round(u))), kv = (int) Math.max(0, Math.min(b.nv, Math.round(v))), o = (kv * (b.nu + 1) + ku) * 18;
                s = Math.max(-TRUST, Math.min(TRUST, u - ku)); t = Math.max(-TRUST, Math.min(TRUST, v - kv));
                for (int it = 0; ; it++) {
                    mx = at[o] + at[o + 3] * s + at[o + 6] * t + .5 * at[o + 9] * s * s + at[o + 12] * s * t + .5 * at[o + 15] * t * t;
                    my = at[o + 1] + at[o + 4] * s + at[o + 7] * t + .5 * at[o + 10] * s * s + at[o + 13] * s * t + .5 * at[o + 16] * t * t;
                    mz = at[o + 2] + at[o + 5] * s + at[o + 8] * t + .5 * at[o + 11] * s * s + at[o + 14] * s * t + .5 * at[o + 17] * t * t;
                    ax = at[o + 3] + at[o + 9] * s + at[o + 12] * t; ay = at[o + 4] + at[o + 10] * s + at[o + 13] * t; az = at[o + 5] + at[o + 11] * s + at[o + 14] * t;
                    bx = at[o + 6] + at[o + 12] * s + at[o + 15] * t; by = at[o + 7] + at[o + 13] * s + at[o + 16] * t; bz = at[o + 8] + at[o + 14] * s + at[o + 17] * t;
                    if (it == 5) break;
                    double rx = x - mx, ry = y - my, rz = z - mz;
                    double g1 = ax * rx + ay * ry + az * rz, g2 = bx * rx + by * ry + bz * rz;
                    double aa = ax * ax + ay * ay + az * az, ab = ax * bx + ay * by + az * bz, bb = bx * bx + by * by + bz * bz;
                    double h11 = aa - (rx * at[o + 9] + ry * at[o + 10] + rz * at[o + 11]), h12 = ab - (rx * at[o + 12] + ry * at[o + 13] + rz * at[o + 14]),
                            h22 = bb - (rx * at[o + 15] + ry * at[o + 16] + rz * at[o + 17]);
                    double det = h11 * h22 - h12 * h12;
                    if (!(h11 > 0 && det > 1e-9 * aa * bb)) { h11 = aa; h12 = ab; h22 = bb; det = aa * bb - ab * ab; }
                    if (!(det > 1e-12 * aa * bb)) break;
                    double ns = Math.max(-TRUST, Math.min(TRUST, s + (g1 * h22 - g2 * h12) / det)), nt = Math.max(-TRUST, Math.min(TRUST, t + (g2 * h11 - g1 * h12) / det));
                    double ds = ns - s, dt = nt - t;
                    s = ns; t = nt;
                    if (Math.abs(ds) + Math.abs(dt) < 0.02) {
                        mx += ax * ds + bx * dt; my += ay * ds + by * dt; mz += az * ds + bz * dt;
                        break;
                    }
                }
                u = ku + s; v = kv + t;
                boolean nearer = Math.max(0, Math.min(b.nu, Math.round(u))) != ku && Math.abs(s) > 1 || Math.max(0, Math.min(b.nv, Math.round(v))) != kv && Math.abs(t) > 1;
                if (!nearer || hop == 3) break;
            }
            double rx = x - mx, ry = y - my, rz = z - mz, d = Math.sqrt(rx * rx + ry * ry + rz * rz);
            double cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
            out[0] = rx * cx + ry * cy + rz * cz < 0 ? -d : d;
            out[5] = u; out[6] = v;
            double pastU = Math.max(-u, u - b.nu), pastV = Math.max(-v, v - b.nv);
            out[1] = out[2] = Double.NEGATIVE_INFINITY;
            if (pastU > 0 || pastV > 0) {
                double aa = ax * ax + ay * ay + az * az, ab = ax * bx + ay * by + az * bz, bb = bx * bx + by * by + bz * bz;
                if (pastU > 0) rim(out, pastU, ax - ab / bb * bx, ay - ab / bb * by, az - ab / bb * bz);
                if (pastV > 0) rim(out, pastV, bx - ab / aa * ax, by - ab / aa * ay, bz - ab / aa * az);
            }
            out[4] = Math.min(Math.min(u, b.nu - u) * b.acrossU, Math.min(v, b.nv - v) * b.acrossV);
        }

        private static void rim(double[] out, double past, double ex, double ey, double ez) {
            double l = Math.sqrt(ex * ex + ey * ey + ez * ez);
            if (!(l > 0)) { out[1] = out[2] = Double.POSITIVE_INFINITY; return; }
            out[2] = Math.max(out[2], past * l);
            out[1] = Math.max(out[1], past * l - 0.125 * (Math.abs(ex) + Math.abs(ey) + Math.abs(ez)) / l);
        }

        @Override public double field(double x, double y, double z) {
            double[] r = new double[7];
            return foot(built(), x, y, z, r) > 0 ? value(r) : cap;
        }

        private double value(double[] r) { return r[1] > 0 ? Double.NaN : Math.max(-cap, Math.min(cap, r[0])); }

        @Override public void lattice(int i, int j, int k, double[] lattice) {
            Built b = built();
            double[] r = new double[7];
            for (int c = 1; c < 9; c++)
                if (foot(b, i + (c == 8 ? .5 : c & 1), j + (c == 8 ? .5 : c >> 1 & 1), k + (c == 8 ? .5 : c >> 2), r) != 1) { Shape3.super.lattice(i, j, k, lattice); return; }
            if (foot(b, i, j, k, r) != 1) { Shape3.super.lattice(i, j, k, lattice); return; }
            double rowU = r[5], rowV = r[6], levelU = rowU, levelV = rowV;
            for (int y = 0, q = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    double u = z == 0 ? levelU : rowU, v = z == 0 ? levelV : rowV;
                    for (int x = 0; x < 5; x++) {
                        step(b, u, v, i + x * .25, j + y * .25, k + z * .25, r);
                        u = r[5]; v = r[6];
                        if (x == 0) { rowU = u; rowV = v; if (z == 0) { levelU = u; levelV = v; } }
                        lattice[q++] = value(r);
                    }
                }
            }
        }

        @Override public int uniform(int i, int j, int k) {
            Built b = built();
            if (b.near.count(b.near.bucket(i + .5, j + .5, k + .5)) == 0) return -(Near3.SIZE - i % Near3.SIZE);
            double[] r = new double[7];
            foot(b, i + .5, j + .5, k + .5, r);
            double d = Math.abs(r[0]);
            if (r[2] > 0 || d > hi) {
                double out = r[2] > 0 ? r[3] - b.span - hi - 0.25 : d - hi - (r[4] < 2 ? 0.25 : 0);
                return out > R_CELL ? -(1 + (int) Math.floor(out - R_CELL)) : 0;
            }
            double in = Math.min(hi - d, r[4] / 2 - 0.25);
            return in > R_CELL ? 1 + (int) Math.floor(in - R_CELL) : 0;
        }

        @Override public Wrapping wrapping() {
            if (error != null) return null;
            Built b = built();
            double[] at = b.at;
            int w = b.nu + 1;
            double along = 0, across = 0, nx = 0, ny = 0, nz = 0;
            for (int a = 0; a <= b.nu; a++)
                for (int v = 0; v < b.nv; v++) along += Math.sqrt(d2(at, (v * w + a) * 18, ((v + 1) * w + a) * 18));
            for (int v = 0; v <= b.nv; v++)
                for (int a = 0; a < b.nu; a++) across += Math.sqrt(d2(at, (v * w + a) * 18, (v * w + a + 1) * 18));
            along /= b.nu + 1; across /= b.nv + 1;
            for (int k = 0; k < w * (b.nv + 1); k++) {
                int o = k * 18;
                nx -= at[o + 7] * at[o + 5] - at[o + 8] * at[o + 4];
                ny -= at[o + 8] * at[o + 3] - at[o + 6] * at[o + 5];
                nz -= at[o + 6] * at[o + 4] - at[o + 7] * at[o + 3];
            }
            boolean flip = Math.abs(ny) > Math.abs(nz) ? ny < 0 : nz > 0;
            double length = along, width = across;
            return new Wrap(length, width, false, false, (x, y, z, out) -> {
                double[] r = new double[7];
                if (foot(b, x, y, z, r) == 0) { out[0] = 0; out[1] = 0; return; }
                double u = Math.max(0, Math.min(1, r[6] / b.nv)), v = Math.max(0, Math.min(1, r[5] / b.nu));
                out[0] = (flip ? 1 - u : u) * length;
                out[1] = v * width;
            });
        }

        @Override public List<double[]> wireframe() {
            List<double[]> w = wires;
            if (w != null) return w;
            w = new ArrayList<>();
            if (error == null) {
                final int lines = 8, steps = 32;
                for (int way = 0; way < 2; way++)
                    for (int l = 0; l <= lines; l++) {
                        double[] line = new double[(steps + 1) * 3];
                        for (int k = 0; k <= steps; k++) {
                            double along = (double) k / steps, across = (double) l / lines;
                            System.arraycopy(Bezier3.patchPoint(pts, rows, cols, way == 0 ? along : across, way == 0 ? across : along), 0, line, k * 3, 3);
                        }
                        w.add(line);
                    }
                for (int r = 0; r < rows; r++) {
                    double[] line = new double[cols * 3];
                    for (int c = 0; c < cols; c++) System.arraycopy(p(r, c), 0, line, c * 3, 3);
                    w.add(line);
                }
                for (int c = 0; c < cols; c++) {
                    double[] line = new double[rows * 3];
                    for (int r = 0; r < rows; r++) System.arraycopy(p(r, c), 0, line, r * 3, 3);
                    w.add(line);
                }
            }
            return wires = w;
        }
    }

    private record Wrap(double length, double width, boolean closedAlong, boolean closedAcross, Placer placer) implements Shape3.Wrapping {
        @Override public void place(double x, double y, double z, double[] out) { placer.place(x, y, z, out); }
    }

    private interface Placer { void place(double x, double y, double z, double[] out); }

    private static double turn(double angle) {
        double t = angle / (2 * Math.PI);
        t -= Math.floor(t);
        return t >= 1 ? 0 : t;
    }

    static double perimeter(double a, double b) {
        if (a + b <= 0) return 0;
        double h = (a - b) * (a - b) / ((a + b) * (a + b));
        return Math.PI * (a + b) * (1 + 3 * h / (10 + Math.sqrt(4 - 3 * h)));
    }

    private static double thickness(double t) { return Math.max(0.0625, Math.min(50, t)); }

    private static void put(double[] line, int k, double x, double y, double z) {
        line[k * 3] = x; line[k * 3 + 1] = y; line[k * 3 + 2] = z;
    }
}
