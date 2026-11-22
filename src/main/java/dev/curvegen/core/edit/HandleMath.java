package dev.curvegen.core.edit;

import java.util.List;

public final class HandleMath {
    private HandleMath() {}

    private static final double PARALLEL = 0.02;

    public static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    public static double[] normalize(double[] v) {
        double l = Math.sqrt(dot(v, v));
        return l == 0 ? new double[]{0, 0, 1} : new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    public static double alongAxis(double[] origin, double[] dir, double[] p, int axis) {
        double[] d = normalize(dir);
        double b = d[axis];
        double denom = 1 - b * b;
        if (denom < PARALLEL * PARALLEL) return Double.NaN;
        double[] w = {origin[0] - p[0], origin[1] - p[1], origin[2] - p[2]};
        double dw = dot(d, w), ew = w[axis];
        double s = (b * ew - dw) / denom;
        if (s < 0) return Double.NaN;
        return p[axis] + (ew - b * dw) / denom;
    }

    public static double[] onPlane(double[] origin, double[] dir, int normalAxis, double coord, double reach) {
        double[] d = normalize(dir);
        if (Math.abs(d[normalAxis]) < PARALLEL) return null;
        double s = (coord - origin[normalAxis]) / d[normalAxis];
        if (s < 0 || s > reach) return null;
        double[] hit = {origin[0] + d[0] * s, origin[1] + d[1] * s, origin[2] + d[2] * s};
        hit[normalAxis] = coord;
        return hit;
    }

    public static int facingAxis(double[] dir) {
        double ax = Math.abs(dir[0]), ay = Math.abs(dir[1]), az = Math.abs(dir[2]);
        return ax >= ay && ax >= az ? 0 : ay >= az ? 1 : 2;
    }

    public static double[] toRay(double[] origin, double[] dir, double[] p) {
        double[] d = normalize(dir);
        double[] w = {p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]};
        double s = Math.max(0, dot(d, w));
        double ex = w[0] - d[0] * s, ey = w[1] - d[1] * s, ez = w[2] - d[2] * s;
        return new double[]{Math.sqrt(ex * ex + ey * ey + ez * ez), s};
    }

    public static double scaled(double size, double distance) { return size * Math.max(1, distance / NEAR); }
    public static final double NEAR = 6;

    public static int pick(double[] origin, double[] dir, List<double[]> handles, double radius) {
        int best = -1;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int k = 0; k < handles.size(); k++) {
            double[] r = toRay(origin, dir, handles.get(k));
            if (r[1] <= 0) continue;
            double allowed = scaled(radius, r[1]);
            if (r[0] > allowed) continue;
            double score = r[0] / allowed + r[1] * 1e-3;
            if (score < bestScore) { bestScore = score; best = k; }
        }
        return best;
    }

    public static final double DIMMED = 0.3;

    public static int dimmed(int argb) { return (int) Math.round((argb >>> 24) * DIMMED) << 24 | argb & 0xFFFFFF; }

    public static double[] nearestOnSegments(double[] origin, double[] dir, double[] segs) {
        double[] d = normalize(dir), best = null;
        for (int k = 0; k + 5 < segs.length; k += 6) {
            double ux = segs[k + 3] - segs[k], uy = segs[k + 4] - segs[k + 1], uz = segs[k + 5] - segs[k + 2];
            double wx = segs[k] - origin[0], wy = segs[k + 1] - origin[1], wz = segs[k + 2] - origin[2];
            double a = ux * ux + uy * uy + uz * uz, b = ux * d[0] + uy * d[1] + uz * d[2];
            double c = ux * wx + uy * wy + uz * wz, e = d[0] * wx + d[1] * wy + d[2] * wz;
            double denom = a - b * b;
            double t = denom > 1e-12 ? Math.max(0, Math.min(1, (b * e - c) / denom)) : 0;
            double s = Math.max(0, e + b * t);
            t = a > 1e-12 ? Math.max(0, Math.min(1, (b * s - c) / a)) : 0;
            double px = segs[k] + ux * t, py = segs[k + 1] + uy * t, pz = segs[k + 2] + uz * t;
            double ex = px - origin[0] - d[0] * s, ey = py - origin[1] - d[1] * s, ez = pz - origin[2] - d[2] * s;
            double dist = Math.sqrt(ex * ex + ey * ey + ez * ez);
            if (best == null || dist < best[3]) best = new double[]{px, py, pz, dist, s, k / 6};
        }
        return best;
    }
}
