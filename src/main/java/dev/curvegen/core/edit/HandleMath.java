package dev.curvegen.core.edit;

import java.util.List;

/**
 * The geometry of grabbing and dragging handles with a look ray. Points and rays are {@code double[3]} in any one
 * coordinate system. Nothing here knows about Minecraft, so the in-world editor and the menu's 3D preview share it.
 */
public final class HandleMath {
    private HandleMath() {}

    /** Rays closer to parallel than this (as the sine of the angle) don't count as crossing a plane or passing an axis. */
    private static final double PARALLEL = 0.02;

    public static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    public static double[] normalize(double[] v) {
        double l = Math.sqrt(dot(v, v));
        return l == 0 ? new double[]{0, 0, 1} : new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    /**
     * Where the ray passes closest to the line through {@code p} along a world axis, as the coordinate along that axis.
     * {@code Double.NaN} when the ray runs nearly along the axis or the closest pass is behind the ray's start.
     */
    public static double alongAxis(double[] origin, double[] dir, double[] p, int axis) {
        double[] d = normalize(dir);
        double b = d[axis];                         // the cosine between the two lines
        double denom = 1 - b * b;
        if (denom < PARALLEL * PARALLEL) return Double.NaN;
        double[] w = {origin[0] - p[0], origin[1] - p[1], origin[2] - p[2]};
        double dw = dot(d, w), ew = w[axis];
        double s = (b * ew - dw) / denom;           // distance along the ray
        if (s < 0) return Double.NaN;
        return p[axis] + (ew - b * dw) / denom;
    }

    /**
     * Where the ray crosses the plane that has one world axis as its normal and sits at {@code coord} along it.
     * Null when the ray runs nearly along the plane, points away from it, or would cross further than {@code reach}.
     */
    public static double[] onPlane(double[] origin, double[] dir, int normalAxis, double coord, double reach) {
        double[] d = normalize(dir);
        if (Math.abs(d[normalAxis]) < PARALLEL) return null;
        double s = (coord - origin[normalAxis]) / d[normalAxis];
        if (s < 0 || s > reach) return null;
        double[] hit = {origin[0] + d[0] * s, origin[1] + d[1] * s, origin[2] + d[2] * s};
        hit[normalAxis] = coord;
        return hit;
    }

    /** The world axis the ray runs most nearly along: the normal of the axis-aligned plane that faces the camera most directly. */
    public static int facingAxis(double[] dir) {
        double ax = Math.abs(dir[0]), ay = Math.abs(dir[1]), az = Math.abs(dir[2]);
        return ax >= ay && ax >= az ? 0 : ay >= az ? 1 : 2;
    }

    /** How far a point is from the ray, and how far along the ray its nearest point is. Behind the start counts from the start. */
    public static double[] toRay(double[] origin, double[] dir, double[] p) {
        double[] d = normalize(dir);
        double[] w = {p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]};
        double s = Math.max(0, dot(d, w));
        double ex = w[0] - d[0] * s, ey = w[1] - d[1] * s, ez = w[2] - d[2] * s;
        return new double[]{Math.sqrt(ex * ex + ey * ey + ez * ez), s};
    }

    /** How big something {@code size} across at arm's length should be at this distance to look the same size. */
    public static double scaled(double size, double distance) { return size * Math.max(1, distance / NEAR); }
    /** Handles keep their real size up to this far away and grow with distance beyond it. */
    public static final double NEAR = 6;

    /**
     * The handle the ray points at: the one it passes within {@code radius} of (scaled with distance like the handles
     * themselves), nearest first. Returns -1 when it points at none. Handles behind the ray's start don't count.
     */
    public static int pick(double[] origin, double[] dir, List<double[]> handles, double radius) {
        int best = -1;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int k = 0; k < handles.size(); k++) {
            double[] r = toRay(origin, dir, handles.get(k));
            if (r[1] <= 0) continue;
            double allowed = scaled(radius, r[1]);
            if (r[0] > allowed) continue;
            // Mostly the one nearest the crosshair, with a nudge towards the one nearer the player.
            double score = r[0] / allowed + r[1] * 1e-3;
            if (score < bestScore) { bestScore = score; best = k; }
        }
        return best;
    }

    /** How much of its opacity a handle keeps while another one is dragged. */
    public static final double DIMMED = 0.3;

    /** A handle's ARGB colour while another handle is dragged: the same colour, fainter. */
    public static int dimmed(int argb) { return (int) Math.round((argb >>> 24) * DIMMED) << 24 | argb & 0xFFFFFF; }

    /**
     * The point on a set of line segments {x1,y1,z1,x2,y2,z2,...} that the ray passes closest to.
     * Returns {x, y, z, distance from the ray, distance along the ray, segment index}, or null without segments.
     */
    public static double[] nearestOnSegments(double[] origin, double[] dir, double[] segs) {
        double[] d = normalize(dir), best = null;
        for (int k = 0; k + 5 < segs.length; k += 6) {
            double ux = segs[k + 3] - segs[k], uy = segs[k + 4] - segs[k + 1], uz = segs[k + 5] - segs[k + 2];
            double wx = segs[k] - origin[0], wy = segs[k + 1] - origin[1], wz = segs[k + 2] - origin[2];
            double a = ux * ux + uy * uy + uz * uz, b = ux * d[0] + uy * d[1] + uz * d[2];
            double c = ux * wx + uy * wy + uz * wz, e = d[0] * wx + d[1] * wy + d[2] * wz;
            double denom = a - b * b;
            // t runs along the segment, s along the ray.
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
