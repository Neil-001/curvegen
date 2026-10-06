package dev.curvegen.core.edit;

import dev.curvegen.core.Expr;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.Target;
import java.util.ArrayList;
import java.util.List;

/**
 * What the in-world editor's handles and keys do to the settings of a 2D shape (ellipse, equation or Bézier curve).
 * The shape's own axes are x across the drawing, y up it and z through its depth, in blocks from the drawing's
 * bottom-left corner at the front.
 */
public final class Edit2D {
    private Edit2D() {}

    public static final int MAX_SIZE = 400, MAX_DEPTH = 64, MAX_POINTS = 10;

    /** The box the handles sit on: width, height and depth. */
    public static int[] size(ShapeSettings s) {
        return switch (s.gen) {
            case ELLIPSE -> new int[]{s.eW, s.eH, s.depth};
            case EQUATION -> new int[]{s.qW, equationHeight(s), s.depth};
            case BEZIER -> new int[]{s.bW, s.bH, s.depth};
        };
    }

    /** An equation's ranges {x from, x to, y from, y to}, or null while they don't parse or run backwards. */
    private static double[] ranges(ShapeSettings s) {
        try {
            double[] r = {Expr.constant(s.xmin, "x from"), Expr.constant(s.xmax, "x to"), Expr.constant(s.ymin, "y from"), Expr.constant(s.ymax, "y to")};
            return r[1] > r[0] && r[3] > r[2] ? r : null;
        } catch (Expr.ParseException e) {
            return null;
        }
    }

    /** The equation's height, which the same-scale lock derives from its width just as the solver does. */
    private static int equationHeight(ShapeSettings s) {
        double[] r = ranges(s);
        return s.qLock && r != null ? Target.lockedHeight(s.qW, r[0], r[1], r[2], r[3]) : Math.max(1, s.qH);
    }

    private static int clamp(int v, int max) { return Math.max(1, Math.min(max, v)); }

    /**
     * Asks for a new box. {@code dragged} says which axes the player moved. Sizes stay within the limits. An equation
     * with the same-scale lock keeps its proportions, so its width follows a dragged height. A Bézier curve's points
     * stretch with its box.
     */
    public static void resize(ShapeSettings s, int[] want, boolean[] dragged) {
        if (dragged[2]) s.depth = clamp(want[2], MAX_DEPTH);
        switch (s.gen) {
            case ELLIPSE -> {
                if (dragged[0]) s.eW = clamp(want[0], MAX_SIZE);
                if (dragged[1]) s.eH = clamp(want[1], MAX_SIZE);
            }
            case EQUATION -> {
                double[] r = ranges(s);
                if (dragged[0]) s.qW = clamp(want[0], MAX_SIZE);
                if (s.qLock && r != null) {
                    if (dragged[1] && !dragged[0]) s.qW = clamp((int) Math.round(want[1] * (r[1] - r[0]) / (r[3] - r[2])), MAX_SIZE);
                    s.qH = equationHeight(s);
                } else if (dragged[1]) s.qH = clamp(want[1], MAX_SIZE);
            }
            case BEZIER -> {
                int w = dragged[0] ? clamp(want[0], MAX_SIZE) : s.bW, h = dragged[1] ? clamp(want[1], MAX_SIZE) : s.bH;
                double fx = (double) w / s.bW, fy = (double) h / s.bH;
                for (double[] p : s.pts) { p[0] *= fx; p[1] *= fy; }
                s.bW = w; s.bH = h;
            }
        }
    }

    /** A Bézier curve's control points, halfway through its depth. Other shapes have none. */
    public static List<double[]> points(ShapeSettings s) {
        List<double[]> out = new ArrayList<>();
        if (s.gen == Gen.BEZIER) for (double[] p : s.pts) out.add(new double[]{p[0], p[1], s.depth / 2.0});
        return out;
    }

    private static double snap(ShapeSettings s, double v) { return s.snap ? Math.round(v * 2) / 2.0 : v; }

    /**
     * Moves a point, in half blocks when Snap is on. Dragging it past an edge grows the grid by whole blocks to keep it
     * inside, up to the size limit. Returns how many cells the grid's bottom-left corner moved along each own axis
     * (zero or negative), so the caller can keep the rest of the curve where it was.
     */
    public static int[] movePoint(ShapeSettings s, int i, double[] to) {
        int[] shift = new int[3];
        double x = snap(s, to[0]), y = snap(s, to[1]);
        if (x < 0) {
            int grow = Math.min(MAX_SIZE - s.bW, (int) Math.ceil(-x));
            for (double[] p : s.pts) p[0] += grow;
            s.bW += grow; x += grow; shift[0] = -grow;
        } else if (x > s.bW) s.bW = Math.min(MAX_SIZE, (int) Math.ceil(x));
        if (y < 0) {
            int grow = Math.min(MAX_SIZE - s.bH, (int) Math.ceil(-y));
            for (double[] p : s.pts) p[1] += grow;
            s.bH += grow; y += grow; shift[1] = -grow;
        } else if (y > s.bH) s.bH = Math.min(MAX_SIZE, (int) Math.ceil(y));
        s.pts.set(i, new double[]{Math.max(0, Math.min(s.bW, x)), Math.max(0, Math.min(s.bH, y))});
        return shift;
    }

    /** Removes a point. A curve keeps at least two. */
    public static boolean removePoint(ShapeSettings s, int i) {
        if (s.gen != Gen.BEZIER || s.pts.size() <= 2 || i < 0 || i >= s.pts.size()) return false;
        s.pts.remove(i);
        return true;
    }

    /**
     * Adds a control point at a spot on the curve, between the two points that part of the curve runs between.
     * Returns the new point's index, or -1 when the curve already has as many points as it can.
     */
    public static int insertPoint(ShapeSettings s, double[] at) {
        if (s.gen != Gen.BEZIER || s.pts.size() >= MAX_POINTS) return -1;
        int n = s.pts.size(), steps = 200;
        double bestU = 0.5, best = Double.POSITIVE_INFINITY;
        for (int k = 0; k <= steps; k++) {
            double u = (double) k / steps;
            double[] p = Target.bezierPoint(s.pts, u);
            double d = Math.hypot(p[0] - at[0], p[1] - at[1]);
            if (d < best) { best = d; bestU = u; }
        }
        int index = Math.max(1, Math.min(n - 1, (int) Math.ceil(bestU * (n - 1))));
        s.pts.add(index, new double[]{Math.max(0, Math.min(s.bW, snap(s, at[0]))), Math.max(0, Math.min(s.bH, snap(s, at[1])))});
        return index;
    }

    /**
     * Copies a point. The copy sits half a block along the way to the next point (or the previous one, for the last)
     * and goes between the two. Returns the copy's index, or -1 when the curve already has as many points as it can.
     */
    public static int duplicatePoint(ShapeSettings s, int i) {
        if (s.gen != Gen.BEZIER || s.pts.size() >= MAX_POINTS || i < 0 || i >= s.pts.size()) return -1;
        boolean last = i == s.pts.size() - 1;
        double[] p = s.pts.get(i), q = s.pts.get(last ? i - 1 : i + 1);
        double dx = q[0] - p[0], dy = q[1] - p[1], len = Math.hypot(dx, dy);
        if (len < 1e-9) { dx = 1; dy = 0; len = 1; }
        double[] copy = {Math.max(0, Math.min(s.bW, p[0] + dx / len * 0.5)), Math.max(0, Math.min(s.bH, p[1] + dy / len * 0.5))};
        int index = last ? i : i + 1;
        s.pts.add(index, copy);
        return index;
    }

    /**
     * The ideal curve as line segments {x1,y1,z1,x2,y2,z2,...}, halfway through the depth. It's cheap enough to redo
     * every frame. Null for an equation, whose curve only the solver can trace.
     */
    public static double[] curve(ShapeSettings s) {
        double z = s.depth / 2.0;
        int n;
        double[][] p;
        switch (s.gen) {
            case ELLIPSE -> {
                n = 96;
                p = new double[n + 1][];
                for (int k = 0; k <= n; k++) {
                    double th = k * 2 * Math.PI / n;
                    p[k] = new double[]{s.eW / 2.0 * (1 + Math.cos(th)), s.eH / 2.0 * (1 + Math.sin(th))};
                }
            }
            case BEZIER -> {
                n = Math.max(24, Math.min(200, s.pts.size() * 16));
                p = new double[n + 1][];
                for (int k = 0; k <= n; k++) p[k] = Target.bezierPoint(s.pts, (double) k / n);
            }
            default -> { return null; }
        }
        double[] out = new double[n * 6];
        for (int k = 0; k < n; k++) {
            out[6 * k] = p[k][0]; out[6 * k + 1] = p[k][1]; out[6 * k + 2] = z;
            out[6 * k + 3] = p[k + 1][0]; out[6 * k + 4] = p[k + 1][1]; out[6 * k + 5] = z;
        }
        return out;
    }

    /**
     * A solved curve's overlay (flat lists {x1,y1,x2,y2,...} in grid cells) as segments in own axes, stretched from the
     * grid it was solved on to the current box. {@code margin} is how many cells the grid extends past the box.
     */
    public static double[] overlay(List<double[]> overlay, int margin, int[] solvedSize, int[] size) {
        int n = 0;
        for (double[] o : overlay) n += o.length / 4;
        double[] out = new double[n * 6];
        double fx = (double) size[0] / solvedSize[0], fy = (double) size[1] / solvedSize[1], z = size[2] / 2.0;
        int k = 0;
        for (double[] o : overlay)
            for (int q = 0; q + 3 < o.length; q += 4) {
                out[k++] = (o[q] - margin) * fx; out[k++] = (o[q + 1] - margin) * fy; out[k++] = z;
                out[k++] = (o[q + 2] - margin) * fx; out[k++] = (o[q + 3] - margin) * fy; out[k++] = z;
            }
        return out;
    }
}
