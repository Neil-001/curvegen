package dev.curvegen.core;

import java.util.ArrayList;
import java.util.List;

/** The built-in 3D shapes. */
public final class Shapes3 {
    private Shapes3() {}

    private static int size(int v) { return Math.max(1, Math.min(Shape3.MAX_SIZE, v)); }

    /**
     * Signed distance to an axis-aligned ellipsoid with semi-axes a, b and c (negative inside). The nearest surface
     * point is e²y/(t + e²) on each axis, for the largest root t of Σ(e·y/(t + e²))² = 1. Newton's method reaches
     * it from the left without overshooting, because the sum is convex and falling there.
     */
    public static double sdEllipsoid(double px, double py, double pz, double a, double b, double c) {
        double x = Math.abs(px), y = Math.abs(py), z = Math.abs(pz);
        // Too close to a plane of symmetry to tell from being on it, and the sums below would divide by nearly nothing.
        if (x < 1e-9 * a) x = 0;
        if (y < 1e-9 * b) y = 0;
        if (z < 1e-9 * c) z = 0;
        double a2 = a * a, b2 = b * b, c2 = c * c, ax = a * x, by = b * y, cz = c * z;
        boolean inside = x * x / a2 + y * y / b2 + z * z / c2 < 1;
        // A coordinate of exactly 0 drops out of the sum. Deep inside, the nearest point can still lie off that
        // plane: that happens when the root falls left of the dropped term's pole, the largest of which is tz.
        double t = Double.NEGATIVE_INFINITY, tz = Double.NEGATIVE_INFINITY;
        if (x > 0) t = Math.max(t, ax - a2); else tz = Math.max(tz, -a2);
        if (y > 0) t = Math.max(t, by - b2); else tz = Math.max(tz, -b2);
        if (z > 0) t = Math.max(t, cz - c2); else tz = Math.max(tz, -c2);
        if (t == Double.NEGATIVE_INFINITY) return -Math.min(a, Math.min(b, c));
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
        return inside ? -d : d;
    }

    /** An ellipsoid filling the box, solid or as a shell. The shell modes are the 2D ellipse's. */
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
            // A shell needs the true distance. A solid only needs to know which side of the surface a point is on.
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
        // Both fields crease at the middle, which is within reach of the surface in a small ellipsoid.
        @Override public boolean smooth() { return least >= 2; }
        @Override public double[] carve() { return carve; }
        @Override public boolean symX() { return true; }
        @Override public boolean symY() { return true; }
        @Override public boolean symZ() { return true; }

        @Override public double field(double x, double y, double z) {
            double px = x - pad - a, py = y - pad - b, pz = z - pad - c;
            if (exact) return sdEllipsoid(px, py, pz, a, b, c);
            // Never steeper than the true distance, and 0 on the same surface.
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
    }

    /**
     * A ring lying flat. It's round, tRing across and tTube thick, when its box is tRing × tTube × tRing; any
     * other box stretches it.
     */
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
        // The field creases along the tube's core and the ring's axis, which a thin tube or a small hole brings near the surface.
        @Override public boolean smooth() { return minor * least >= 2 && (major - minor) * Math.min(sx, sz) >= 1; }
        @Override public double[] carve() { return hollow ? new double[]{Double.NEGATIVE_INFINITY, 0} : null; }
        @Override public boolean symX() { return true; }
        @Override public boolean symY() { return true; }
        @Override public boolean symZ() { return true; }

        @Override public double field(double x, double y, double z) {
            // The round ring's distance, measured before stretching and scaled so it's never steeper than the real one.
            double px = (x - nx / 2.0) / sx, py = (y - ny / 2.0) / sy, pz = (z - nz / 2.0) / sz;
            return (Math.hypot(Math.hypot(px, pz) - major, py) - minor) * least;
        }

        @Override public List<double[]> wireframe() {
            int n = (int) Math.max(48, Math.min(360, Math.round((nx + nz) * 2.0)));
            List<double[]> out = new ArrayList<>();
            double ox = nx / 2.0, oy = ny / 2.0, oz = nz / 2.0;
            // Rings around the outside, the hole, the top and the bottom, then four cuts through the tube.
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
    }

    private static void put(double[] line, int k, double x, double y, double z) {
        line[k * 3] = x; line[k * 3 + 1] = y; line[k * 3 + 2] = z;
    }
}
