package dev.curvegen.core.edit;

import dev.curvegen.core.Bezier3;
import dev.curvegen.core.Expr;
import dev.curvegen.core.PresetData;
import dev.curvegen.core.Shape3;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.EllipseMode;
import dev.curvegen.core.ShapeSettings.Eq3Mode;
import dev.curvegen.core.ShapeSettings.Gen;
import java.util.ArrayList;
import java.util.List;

/**
 * What the in-world editor's handles and keys do to a 3D shape's {@link ShapeSettings}. A shape's own axes are the
 * ones its settings are written in: x its width, y its height and z its depth, before it's turned or tipped.
 *
 * <p>A torus has five numbers for a box that only needs three. Here the box (tW, tH, tD) is what the handles and the
 * bump keys change, and the ring and tube sizes stay as they are: they describe the round ring that is stretched to
 * fill the box, so stretching keeps the hole the same share of the ring. Changing the ring or the tube size itself,
 * with {@link #setRing} and {@link #setTube}, scales the box along with it, which keeps whatever stretch it has.
 *
 * <p>An equation's ranges stay as they are while its box stretches. With the same-scale lock the box keeps the
 * ranges' proportions, so dragging any one side sets all three sizes.
 *
 * <p>A Bézier curve's and a surface's control points are in blocks from the box's own minimum corner. They stretch
 * with the box, and the box grows to hold a point dragged out of it. A surface has no single points to add or
 * remove: those edits act on a whole row of its grid, or on a column when {@code alt} is set.
 */
public final class Edit3D {
    private Edit3D() {}

    private static final int MAX = Shape3.MAX_SIZE;

    private static int clamp(int v) { return Math.max(1, Math.min(MAX, v)); }

    /** The box the handles sit on, along the shape's own axes. It leaves out the room a shell or a thickness adds outside it. */
    public static int[] size(ShapeSettings s) {
        return switch (s.gen) {
            case ELLIPSOID -> new int[]{s.e3W, s.e3H, s.e3D};
            case TORUS -> new int[]{s.tW, s.tH, s.tD};
            case EQUATION3 -> {
                double[] r = ranges(s);
                yield s.q3Lock && r != null ? locked(s.q3W, r) : new int[]{clamp(s.q3W), clamp(s.q3H), clamp(s.q3D)};
            }
            case BEZIER3 -> new int[]{s.b3W, s.b3H, s.b3D};
            case SURFACE -> new int[]{s.sW, s.sH, s.sD};
            default -> throw new IllegalArgumentException("Not a 3D shape: " + s.gen);
        };
    }

    private static void setSize(ShapeSettings s, int[] z) {
        switch (s.gen) {
            case ELLIPSOID -> { s.e3W = z[0]; s.e3H = z[1]; s.e3D = z[2]; }
            case TORUS -> { s.tW = z[0]; s.tH = z[1]; s.tD = z[2]; }
            case EQUATION3 -> { s.q3W = z[0]; s.q3H = z[1]; s.q3D = z[2]; }
            case BEZIER3 -> { s.b3W = z[0]; s.b3H = z[1]; s.b3D = z[2]; }
            case SURFACE -> { s.sW = z[0]; s.sH = z[1]; s.sD = z[2]; }
            default -> throw new IllegalArgumentException("Not a 3D shape: " + s.gen);
        }
    }

    /** A 3D equation's ranges {x from, x to, y from, y to, z from, z to}, or null while they don't parse or run backwards. */
    private static double[] ranges(ShapeSettings s) {
        try {
            String[] src = {s.x3min, s.x3max, s.y3min, s.y3max, s.z3min, s.z3max};
            double[] r = new double[6];
            for (int k = 0; k < 6; k++) r[k] = Expr.constant(src[k], "range");
            return r[1] > r[0] && r[3] > r[2] && r[5] > r[4] ? r : null;
        } catch (Expr.ParseException e) {
            return null;
        }
    }

    /** The box the same-scale lock gives a width, as the shape itself works it out: x is the width, z the height and y the depth. */
    private static int[] locked(int width, double[] r) {
        int w = clamp(width);
        double x = r[1] - r[0];
        return new int[]{w, clamp((int) Math.round(w * (r[5] - r[4]) / x)), clamp((int) Math.round(w * (r[3] - r[2]) / x))};
    }

    /** The control points a shape is drawn through, or null for a shape without any. */
    private static List<double[]> pts(ShapeSettings s) { return s.gen == Gen.BEZIER3 ? s.pts3 : s.gen == Gen.SURFACE ? s.sPts : null; }

    /**
     * Asks for a new box, within the size limit. {@code dragged} says which own axes the player moved. A locked
     * equation takes its scale from the first of those, and control points stretch with their box.
     */
    public static void resize(ShapeSettings s, int[] want, boolean[] dragged) {
        int[] now = size(s), to = new int[3];
        for (int a = 0; a < 3; a++) to[a] = dragged[a] ? clamp(want[a]) : now[a];
        double[] r = s.gen == Gen.EQUATION3 && s.q3Lock ? ranges(s) : null;
        if (r != null) {
            // Height is the z range and depth the y range.
            double x = r[1] - r[0];
            int w = dragged[0] ? to[0] : dragged[1] ? (int) Math.round(to[1] * x / (r[5] - r[4])) : dragged[2] ? (int) Math.round(to[2] * x / (r[3] - r[2])) : now[0];
            to = locked(w, r);
        }
        List<double[]> pts = pts(s);
        if (pts != null)
            for (double[] p : pts)
                for (int a = 0; a < 3; a++) p[a] *= (double) to[a] / now[a];
        setSize(s, to);
    }

    // ---------- torus ----------

    /** A box size after the ring or tube it was stretched from changed. An unstretched size follows exactly. */
    private static int scaled(int size, int from, int to) {
        return size == from ? to : clamp((int) Math.round(size * (double) to / from));
    }

    /** Sets a torus's ring size, the distance across it, and scales its width and depth to match. */
    public static void setRing(ShapeSettings s, int ring) {
        ring = clamp(ring);
        s.tW = scaled(s.tW, s.tRing, ring);
        s.tD = scaled(s.tD, s.tRing, ring);
        s.tRing = ring;
        if (s.tTube > ring) setTube(s, ring);
    }

    /** Sets a torus's tube thickness and scales its height to match. The tube can't be thicker than the ring is across. */
    public static void setTube(ShapeSettings s, int tube) {
        tube = Math.min(clamp(tube), s.tRing);
        s.tH = scaled(s.tH, s.tTube, tube);
        s.tTube = tube;
    }

    // ---------- control points ----------

    /** A curve's or a surface's control points. Other shapes have none. */
    public static List<double[]> points(ShapeSettings s) {
        List<double[]> pts = pts(s), out = new ArrayList<>();
        if (pts != null) for (double[] p : pts) out.add(p.clone());
        return out;
    }

    /** How many control points make a row of a surface's grid, or 0 for a shape whose points are one line. */
    public static int columns(ShapeSettings s) { return s.gen == Gen.SURFACE ? s.sCols : 0; }

    private static double snap(ShapeSettings s, double v) { return s.snap ? Math.round(v * 2) / 2.0 : v; }

    /** Grows the box by whole blocks until a coordinate along an axis fits, and returns how far its minimum corner moved (zero or less). */
    private static int hold(List<double[]> pts, int[] size, int axis, double min, double max) {
        int shift = 0;
        if (min < 0) {
            int grow = Math.min(MAX - size[axis], (int) Math.ceil(-min));
            for (double[] p : pts) p[axis] += grow;
            size[axis] += grow; max += grow; shift = -grow;
        }
        if (max > size[axis]) size[axis] = Math.min(MAX, (int) Math.ceil(max));
        return shift;
    }

    /**
     * Moves a point, in half blocks when Snap is on. Dragging it past a side grows the box by whole blocks to keep it
     * inside, up to the size limit. Returns how many cells the box's own minimum corner moved along each axis (zero
     * or negative), or null for a shape without points.
     */
    public static int[] movePoint(ShapeSettings s, int i, double[] to) {
        List<double[]> pts = pts(s);
        if (pts == null || i < 0 || i >= pts.size()) return null;
        int[] size = size(s), shift = new int[3];
        double[] p = pts.get(i);
        for (int a = 0; a < 3; a++) {
            p[a] = snap(s, to[a]);
            // The point is in the list, so it moves along with the rest when the box grows on its low side.
            shift[a] = hold(pts, size, a, p[a], p[a]);
            p[a] = Math.max(0, Math.min(size[a], p[a]));
        }
        setSize(s, size);
        return shift;
    }

    /**
     * Bumps a curve or a surface: the control points' bounding box grows or shrinks by {@code amount} blocks along
     * an own axis, on its low side ({@code side} -1) or high side (1), and the points stretch with it while the
     * opposite side stays put. The box grows to hold them, as in {@link #movePoint}, whose kind of shift this returns.
     * Null for a shape without points, or when they have no extent along the axis to stretch.
     */
    public static int[] bumpPoints(ShapeSettings s, int axis, int side, int amount) {
        List<double[]> pts = pts(s);
        if (pts == null) return null;
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (double[] p : pts) { lo = Math.min(lo, p[axis]); hi = Math.max(hi, p[axis]); }
        double span = hi - lo;
        if (span < 1e-9) return null;
        int[] size = size(s), shift = new int[3];
        // Pulling in stops at one block, or at the span it already has if that's smaller. Pushing out stops where
        // the box can't grow any further, so the points keep their proportions.
        double room = side > 0 ? MAX - lo : hi + MAX - size[axis];
        double f = Math.min(room, Math.max(Math.min(span, 1), span + amount)) / span;
        for (double[] p : pts) p[axis] = side > 0 ? lo + (p[axis] - lo) * f : hi - (hi - p[axis]) * f;
        shift[axis] = hold(pts, size, axis, side > 0 ? lo : hi - span * f, side > 0 ? lo + span * f : hi);
        for (double[] p : pts) p[axis] = Math.max(0, Math.min(size[axis], p[axis]));
        setSize(s, size);
        return shift;
    }

    /** Removes a curve's point, which keeps at least two, or a row of a surface's grid (a column with {@code alt}). */
    public static boolean removePoint(ShapeSettings s, int i, boolean alt) {
        if (s.gen == Gen.SURFACE) return alt ? Bezier3.removeColumn(s) : Bezier3.removeRow(s);
        if (s.gen != Gen.BEZIER3 || s.pts3.size() <= 2 || i < 0 || i >= s.pts3.size()) return false;
        s.pts3.remove(i);
        return true;
    }

    private static double sq(double[] a, double[] b) {
        return (a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]);
    }

    /**
     * Adds a control point to a curve where {@code at} is on it, or a row to a surface (a column with {@code alt}).
     * Neither changes the shape: the other points move to make room. Returns the index of a new point nearest
     * {@code at}, or -1 when the shape has all the points it may.
     */
    public static int insertPoint(ShapeSettings s, double[] at, boolean alt) {
        final int steps = 64;
        if (s.gen == Gen.BEZIER3) {
            double best = Double.POSITIVE_INFINITY, bestU = 0.5;
            for (int k = 0; k <= steps * 4; k++) {
                double d = sq(Bezier3.point(s.pts3, k / (steps * 4.0)), at);
                if (d < best) { best = d; bestU = k / (steps * 4.0); }
            }
            return Bezier3.insert(s.pts3, bestU);
        }
        if (s.gen != Gen.SURFACE) return -1;
        double best = Double.POSITIVE_INFINITY, u = 0.5, v = 0.5;
        for (int j = 0; j <= steps; j++)
            for (int k = 0; k <= steps; k++) {
                double d = sq(Bezier3.patchPoint(s.sPts, s.sRows, s.sCols, (double) k / steps, (double) j / steps), at);
                if (d < best) { best = d; u = (double) k / steps; v = (double) j / steps; }
            }
        return addLine(s, u, v, alt);
    }

    /** Adds a row nearest v, or a column nearest u, and returns the index of its point nearest the other of the two. */
    private static int addLine(ShapeSettings s, double u, double v, boolean column) {
        if (column) {
            int c = Bezier3.addColumn(s, u);
            return c < 0 ? -1 : (int) Math.round(v * (s.sRows - 1)) * s.sCols + c;
        }
        int r = Bezier3.addRow(s, v);
        return r < 0 ? -1 : r * s.sCols + (int) Math.round(u * (s.sCols - 1));
    }

    /**
     * Copies a curve's point: the copy sits half a block along the way to the next point (or the previous one, for
     * the last) and goes between the two. On a surface it adds a row beside the point's own (a column with
     * {@code alt}) without changing the shape, and returns the new line's point beside it. Returns the new point's
     * index, or -1 when the shape has all the points it may.
     */
    public static int duplicatePoint(ShapeSettings s, int i, boolean alt) {
        if (s.gen == Gen.SURFACE) {
            if (i < 0 || i >= s.sPts.size()) return -1;
            int r = i / s.sCols, c = i % s.sCols;
            if (alt) {
                int nc = Bezier3.addColumn(s, (double) c / (s.sCols - 1));
                return nc < 0 ? -1 : r * s.sCols + nc;
            }
            int nr = Bezier3.addRow(s, (double) r / (s.sRows - 1));
            return nr < 0 ? -1 : nr * s.sCols + c;
        }
        if (s.gen != Gen.BEZIER3 || s.pts3.size() >= Bezier3.MAX_POINTS || i < 0 || i >= s.pts3.size()) return -1;
        boolean last = i == s.pts3.size() - 1;
        double[] p = s.pts3.get(i), q = s.pts3.get(last ? i - 1 : i + 1), d = {q[0] - p[0], q[1] - p[1], q[2] - p[2]};
        double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        if (len < 1e-9) { d[0] = 1; len = 1; }
        int[] size = size(s);
        double[] copy = new double[3];
        for (int a = 0; a < 3; a++) copy[a] = Math.max(0, Math.min(size[a], p[a] + d[a] / len * 0.5));
        int index = last ? i : i + 1;
        s.pts3.add(index, copy);
        return index;
    }

    /**
     * Where a look ray, given in own axes, meets a curve or a surface: {x, y, z, distance from the ray, distance along
     * the ray}. Null for a shape without points.
     */
    public static double[] lookAt(ShapeSettings s, double[] origin, double[] dir) {
        if (s.gen == Gen.BEZIER3) {
            double[] n = Bezier3.nearestToRay(s.pts3, origin, dir);
            return n == null ? null : new double[]{n[1], n[2], n[3], n[4], n[5]};
        }
        if (s.gen != Gen.SURFACE || s.sPts.size() != s.sRows * s.sCols) return null;
        double[] n = Bezier3.patchNearestToRay(s.sPts, s.sRows, s.sCols, origin, dir);
        return n == null ? null : new double[]{n[2], n[3], n[4], n[5], n[6]};
    }

    /**
     * A curve or a surface as line segments {x1,y1,z1,x2,y2,z2,...}: the curve itself, or nine lines each way across
     * the surface. It's cheap enough to redo every frame. Null for any other shape.
     */
    public static double[] curve(ShapeSettings s) {
        if (s.gen == Gen.BEZIER3) {
            if (s.pts3.size() < 2) return null;
            int n = Math.max(32, Math.min(512, s.pts3.size() * 16));
            double[] out = new double[n * 6];
            double[] a = Bezier3.point(s.pts3, 0);
            for (int k = 0; k < n; k++) {
                double[] b = Bezier3.point(s.pts3, (k + 1.0) / n);
                System.arraycopy(a, 0, out, k * 6, 3); System.arraycopy(b, 0, out, k * 6 + 3, 3);
                a = b;
            }
            return out;
        }
        if (s.gen != Gen.SURFACE || s.sPts.size() != s.sRows * s.sCols || s.sRows < 2 || s.sCols < 2) return null;
        final int lines = 8, steps = 24;
        double[] out = new double[2 * (lines + 1) * steps * 6];
        int o = 0;
        for (int way = 0; way < 2; way++)
            for (int l = 0; l <= lines; l++) {
                double across = (double) l / lines;
                double[] a = way == 0 ? Bezier3.patchPoint(s.sPts, s.sRows, s.sCols, 0, across) : Bezier3.patchPoint(s.sPts, s.sRows, s.sCols, across, 0);
                for (int k = 1; k <= steps; k++) {
                    double along = (double) k / steps;
                    double[] b = way == 0 ? Bezier3.patchPoint(s.sPts, s.sRows, s.sCols, along, across) : Bezier3.patchPoint(s.sPts, s.sRows, s.sCols, across, along);
                    System.arraycopy(a, 0, out, o, 3); System.arraycopy(b, 0, out, o + 3, 3);
                    o += 6;
                    a = b;
                }
            }
        return out;
    }

    // ---------- options ----------

    private static final double MIN_THICK = 0.0625, MAX_THICK = 50, THICK_STEP = 0.25;

    /** What the radial menu's "Shape options" offers for a 3D shape. Sizes are the box's, and an equation's ranges stay in the full menu. */
    public static List<Option> options(ShapeSettings s) {
        List<Option> out = new ArrayList<>();
        switch (s.gen) {
            case ELLIPSOID -> {
                out.add(new Option.Cycler("Shape", null, List.of("Thin", "Filled", "Thick out", "Thick in", "Thick middle"),
                        () -> s.e3Mode.ordinal(), v -> s.e3Mode = EllipseMode.values()[v]));
                boolean thick = s.e3Mode == EllipseMode.OUTWARDS || s.e3Mode == EllipseMode.INWARDS || s.e3Mode == EllipseMode.MIDDLE;
                out.add(new Option.Number("Thickness", thick ? null : "Only a thick shell has a thickness. Change Shape first.",
                        () -> s.e3T, v -> s.e3T = v, MIN_THICK, MAX_THICK, THICK_STEP, false));
            }
            case TORUS -> {
                out.add(new Option.Number("Ring", null, () -> s.tRing, v -> setRing(s, (int) v), 1, MAX, 1, true));
                out.add(new Option.Number("Tube", null, () -> s.tTube, v -> setTube(s, (int) v), 1, MAX, 1, true));
                out.add(Option.toggle("Hollow", null, () -> s.tHollow, v -> s.tHollow = v));
            }
            case EQUATION3 -> {
                boolean inequality = Edit2D.isInequality(s.src3);
                out.add(new Option.Text("Equation", null, () -> s.src3, v -> s.src3 = v, v -> {
                    try { Expr.parseEquation3(v); return null; } catch (Expr.ParseException e) { return e.getMessage(); }
                }));
                out.add(new Option.Cycler("Shape", inequality ? "Your inequality sets the shape. Use = to choose it here." : null,
                        List.of("Surface", "Fill below", "Fill above"), () -> s.q3Mode.ordinal(), v -> s.q3Mode = Eq3Mode.values()[v]));
                out.add(new Option.Number("Thickness", inequality || s.q3Mode != Eq3Mode.SURFACE ? "Filled shapes don't use a thickness." : null,
                        () -> s.q3T, v -> s.q3T = v, MIN_THICK, MAX_THICK, THICK_STEP, false));
                out.add(Option.toggle("Same scale", null, () -> s.q3Lock, v -> s.q3Lock = v));
            }
            case BEZIER3 -> {
                out.add(new Option.Number("Thickness", null, () -> s.b3T, v -> s.b3T = v, MIN_THICK, MAX_THICK, THICK_STEP, false));
                out.add(Option.toggle("Snap", null, () -> s.snap, v -> s.snap = v));
            }
            case SURFACE -> {
                out.add(new Option.Number("Thickness", null, () -> s.sT, v -> s.sT = v, MIN_THICK, MAX_THICK, THICK_STEP, false));
                out.add(Option.toggle("Snap", null, () -> s.snap, v -> s.snap = v));
            }
            default -> { }
        }
        if (s.is3d()) out.add(new Option.Cycler("Colours from", null, List.of("Side", "Top"), () -> s.topColours ? 1 : 0, v -> s.topColours = v == 1));
        return out;
    }

    /** One line that names the shape and its size. */
    public static String describe(ShapeSettings s) {
        String name = PresetData.defaultName(s, s.gen);
        if (s.gen == Gen.TORUS && (s.tW != s.tRing || s.tD != s.tRing || s.tH != s.tTube)) name += ", stretched from a " + s.tRing + " ring";
        return name;
    }
}
