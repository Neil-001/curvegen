package dev.curvegen.core.edit;

import dev.curvegen.core.Bezier3;
import dev.curvegen.core.Expr;
import dev.curvegen.core.PresetData;
import dev.curvegen.core.Message;
import dev.curvegen.core.Shape3;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.Shapes3;
import dev.curvegen.core.ShapeSettings.EllipseMode;
import dev.curvegen.core.ShapeSettings.Eq3Mode;
import dev.curvegen.core.ShapeSettings.Gen;
import java.util.ArrayList;
import java.util.List;

public final class Edit3D {
    private Edit3D() {}

    private static final int MAX = Shape3.MAX_SIZE;
    private static final double FIT_ROUNDOFF = 1e-9;

    private static int clamp(int v) { return Math.max(1, Math.min(MAX, v)); }

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
            default -> throw new IllegalArgumentException(Message.of("curvegen.diagnostic.edit_not_shape3", s.gen).toString());
        };
    }

    private static void setSize(ShapeSettings s, int[] z) {
        switch (s.gen) {
            case ELLIPSOID -> { s.e3W = z[0]; s.e3H = z[1]; s.e3D = z[2]; }
            case TORUS -> { s.tW = z[0]; s.tH = z[1]; s.tD = z[2]; }
            case EQUATION3 -> { s.q3W = z[0]; s.q3H = z[1]; s.q3D = z[2]; }
            case BEZIER3 -> { s.b3W = z[0]; s.b3H = z[1]; s.b3D = z[2]; }
            case SURFACE -> { s.sW = z[0]; s.sH = z[1]; s.sD = z[2]; }
            default -> throw new IllegalArgumentException(Message.of("curvegen.diagnostic.edit_not_shape3", s.gen).toString());
        }
    }

    private static double[] ranges(ShapeSettings s) {
        try {
            String[] src = {s.x3min, s.x3max, s.y3min, s.y3max, s.z3min, s.z3max};
            double[] r = new double[6];
            for (int k = 0; k < 6; k++) r[k] = Expr.constant(src[k], "curvegen.range.value");
            return r[1] > r[0] && r[3] > r[2] && r[5] > r[4] ? r : null;
        } catch (Expr.ParseException _) {
            return null;
        }
    }

    private static int[] locked(int width, double[] r) {
        int w = clamp(width);
        return new int[]{w, Shapes3.scaled(w, r[5] - r[4], r[1] - r[0]), Shapes3.scaled(w, r[3] - r[2], r[1] - r[0])};
    }

    private static List<double[]> pts(ShapeSettings s) { return s.gen == Gen.BEZIER3 ? s.pts3 : s.gen == Gen.SURFACE ? s.sPts : null; }

    public static void resize(ShapeSettings s, int[] want, boolean[] dragged) {
        int[] now = size(s), to = new int[3];
        for (int a = 0; a < 3; a++) to[a] = dragged[a] ? clamp(want[a]) : now[a];
        double[] r = s.gen == Gen.EQUATION3 && s.q3Lock ? ranges(s) : null;
        if (r != null) {
            double x = r[1] - r[0];
            int w = dragged[0] ? to[0] : dragged[1] ? Shapes3.scaled(to[1], x, r[5] - r[4]) : dragged[2] ? Shapes3.scaled(to[2], x, r[3] - r[2]) : now[0];
            to = locked(w, r);
        }
        List<double[]> pts = pts(s);
        if (pts != null)
            for (double[] p : pts)
                for (int a = 0; a < 3; a++) p[a] *= (double) to[a] / now[a];
        setSize(s, to);
    }

    private static int scaled(int size, int from, int to) {
        return size == from ? to : clamp((int) Math.round(size * (double) to / from));
    }

    public static void setRing(ShapeSettings s, int ring) {
        ring = clamp(ring);
        s.tW = scaled(s.tW, s.tRing, ring);
        s.tD = scaled(s.tD, s.tRing, ring);
        s.tRing = ring;
        if (s.tTube > ring) setTube(s, ring);
    }

    public static void setTube(ShapeSettings s, int tube) {
        tube = Math.min(clamp(tube), s.tRing);
        s.tH = scaled(s.tH, s.tTube, tube);
        s.tTube = tube;
    }

    public static List<double[]> points(ShapeSettings s) {
        List<double[]> pts = pts(s), out = new ArrayList<>();
        if (pts != null) for (double[] p : pts) out.add(p.clone());
        return out;
    }

    public static int columns(ShapeSettings s) { return s.gen == Gen.SURFACE ? s.sCols : 0; }

    private static double snap(ShapeSettings s, double v) { return s.snap ? Math.round(v * 2) / 2.0 : v; }

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

    private static double fitBoundary(double coordinate) {
        double integer = Math.rint(coordinate);
        return Math.abs(coordinate - integer) <= FIT_ROUNDOFF ? integer : coordinate;
    }

    public static int[] fit(ShapeSettings s) {
        List<double[]> pts = pts(s);
        if (pts == null) return null;
        int[] size = size(s), shift = new int[3];
        for (int a = 0; a < 3; a++) {
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (double[] p : pts) { lo = Math.min(lo, p[a]); hi = Math.max(hi, p[a]); }
            shift[a] = hold(pts, size, a, fitBoundary(lo), fitBoundary(hi));
            for (double[] p : pts) p[a] = Math.max(0, Math.min(size[a], p[a]));
        }
        setSize(s, size);
        return shift;
    }

    public static int[] movePoint(ShapeSettings s, int i, double[] to) {
        List<double[]> pts = pts(s);
        if (pts == null || i < 0 || i >= pts.size()) return null;
        int[] size = size(s), shift = new int[3];
        double[] p = pts.get(i);
        for (int a = 0; a < 3; a++) {
            p[a] = snap(s, to[a]);
            shift[a] = hold(pts, size, a, p[a], p[a]);
            p[a] = Math.max(0, Math.min(size[a], p[a]));
        }
        setSize(s, size);
        return shift;
    }

    public static int[] bumpPoints(ShapeSettings s, int axis, int side, int amount) {
        List<double[]> pts = pts(s);
        if (pts == null) return null;
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (double[] p : pts) { lo = Math.min(lo, p[axis]); hi = Math.max(hi, p[axis]); }
        double span = hi - lo;
        if (span < 1e-9) return null;
        int[] size = size(s), shift = new int[3];

        double min = lo, max = hi;
        if (side > 0) max = Math.min(MAX, Math.max(Math.min(hi, lo + 1), hi + amount));
        else min = Math.max(size[axis] - MAX, Math.min(Math.max(lo, hi - 1), lo - amount));
        double scale = (max - min) / span;
        for (double[] p : pts) p[axis] = p[axis] == hi ? max : min + (p[axis] - lo) * scale;
        shift[axis] = hold(pts, size, axis, min, max);
        for (double[] p : pts) p[axis] = Math.max(0, Math.min(size[axis], p[axis]));
        setSize(s, size);
        return shift;
    }

    public static boolean removePoint(ShapeSettings s, int i, boolean alt) {
        if (s.gen == Gen.SURFACE) return alt ? Bezier3.removeColumn(s) : Bezier3.removeRow(s);
        if (s.gen != Gen.BEZIER3 || s.pts3.size() <= 2 || i < 0 || i >= s.pts3.size()) return false;
        s.pts3.remove(i);
        return true;
    }

    private static double sq(double[] a, double[] b) {
        return (a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]);
    }

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

    public static void setGrid(ShapeSettings s, int rows, int cols) {
        rows = Math.max(Bezier3.MIN_GRID, Math.min(Bezier3.MAX_GRID, rows));
        cols = Math.max(Bezier3.MIN_GRID, Math.min(Bezier3.MAX_GRID, cols));
        while (s.sRows < rows && Bezier3.addRow(s, 0.5) >= 0) { }
        while (s.sRows > rows && Bezier3.removeRow(s)) { }
        while (s.sCols < cols && Bezier3.addColumn(s, 0.5) >= 0) { }
        while (s.sCols > cols && Bezier3.removeColumn(s)) { }
    }

    private static int addLine(ShapeSettings s, double u, double v, boolean column) {
        if (column) {
            int c = Bezier3.addColumn(s, u);
            return c < 0 ? -1 : (int) Math.round(v * (s.sRows - 1)) * s.sCols + c;
        }
        int r = Bezier3.addRow(s, v);
        return r < 0 ? -1 : r * s.sCols + (int) Math.round(u * (s.sCols - 1));
    }

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

    public static double[] lookAt(ShapeSettings s, double[] origin, double[] dir) {
        if (s.gen == Gen.BEZIER3) {
            double[] n = Bezier3.nearestToRay(s.pts3, origin, dir);
            return n == null ? null : new double[]{n[1], n[2], n[3], n[4], n[5]};
        }
        if (s.gen != Gen.SURFACE || s.sPts.size() != s.sRows * s.sCols) return null;
        double[] n = Bezier3.patchNearestToRay(s.sPts, s.sRows, s.sCols, origin, dir);
        return n == null ? null : new double[]{n[2], n[3], n[4], n[5], n[6]};
    }

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

    private static final double MIN_THICK = 0.0625, MAX_THICK = 50, THICK_STEP = 0.25;

    public static List<Option> options(ShapeSettings s) {
        List<Option> out = new ArrayList<>();
        switch (s.gen) {
            case ELLIPSOID -> {
                out.add(new Option.Cycler("curvegen.option.shape", null, List.of("curvegen.choice.thin", "curvegen.choice.filled", "curvegen.choice.thick_out", "curvegen.choice.thick_in", "curvegen.choice.thick_middle"),
                        () -> s.e3Mode.ordinal(), v -> s.e3Mode = EllipseMode.values()[v]));
                boolean thick = s.e3Mode == EllipseMode.OUTWARDS || s.e3Mode == EllipseMode.INWARDS || s.e3Mode == EllipseMode.MIDDLE;
                out.add(new Option.Number("curvegen.option.thickness", thick ? null : "curvegen.option.off.thick_shell",
                        () -> s.e3T, v -> s.e3T = v, MIN_THICK, MAX_THICK, THICK_STEP, false));
            }
            case TORUS -> {
                out.add(new Option.Number("curvegen.option.ring", null, () -> s.tRing, v -> setRing(s, (int) v), 1, MAX, 1, true));
                out.add(new Option.Number("curvegen.option.tube", null, () -> s.tTube, v -> setTube(s, (int) v), 1, MAX, 1, true));
                out.add(Option.toggle("curvegen.option.hollow", null, () -> s.tHollow, v -> s.tHollow = v));
            }
            case EQUATION3 -> {
                boolean inequality = Edit2D.isInequality(s.src3);
                out.add(new Option.Text("curvegen.option.equation", null, () -> s.src3, v -> s.src3 = v, v -> {
                    try { Expr.parseEquation3(v); return null; } catch (Expr.ParseException e) { return e.text(); }
                }));
                out.add(new Option.Cycler("curvegen.option.shape", inequality ? "curvegen.option.off.inequality" : null,
                        List.of("curvegen.choice.surface", "curvegen.choice.fill_below", "curvegen.choice.fill_above"), () -> s.q3Mode.ordinal(), v -> s.q3Mode = Eq3Mode.values()[v]));
                out.add(new Option.Number("curvegen.option.thickness", inequality || s.q3Mode != Eq3Mode.SURFACE ? "curvegen.option.off.filled_thickness" : null,
                        () -> s.q3T, v -> s.q3T = v, MIN_THICK, MAX_THICK, THICK_STEP, false));
                out.add(Option.toggle("curvegen.option.same_scale", null, () -> s.q3Lock, v -> s.q3Lock = v));
            }
            case BEZIER3 -> {
                out.add(new Option.Number("curvegen.option.thickness", null, () -> s.b3T, v -> s.b3T = v, MIN_THICK, MAX_THICK, THICK_STEP, false));
                boolean room = s.b3T >= ShapeSettings.HOLLOW_MIN;
                out.add(Option.toggle("curvegen.option.hollow", room ? null : "curvegen.option.off.hollow_thin", () -> s.b3Hollow, v -> s.b3Hollow = v));
                out.add(Option.toggle("curvegen.option.open_ends", !room ? "curvegen.option.off.hollow_thin" : s.b3Hollow ? null : "curvegen.option.off.open_ends",
                        () -> s.b3Open, v -> s.b3Open = v));
                out.add(Option.toggle("curvegen.option.snap", null, () -> s.snap, v -> s.snap = v));
            }
            case SURFACE -> {
                out.add(new Option.Number("curvegen.option.thickness", null, () -> s.sT, v -> s.sT = v, MIN_THICK, MAX_THICK, THICK_STEP, false));
                out.add(Option.toggle("curvegen.option.snap", null, () -> s.snap, v -> s.snap = v));
                out.add(new Option.Number("curvegen.option.rows", null, () -> s.sRows, v -> setGrid(s, (int) v, s.sCols), Bezier3.MIN_GRID, Bezier3.MAX_GRID, 1, true));
                out.add(new Option.Number("curvegen.option.columns", null, () -> s.sCols, v -> setGrid(s, s.sRows, (int) v), Bezier3.MIN_GRID, Bezier3.MAX_GRID, 1, true));
            }
            default -> { }
        }
        if (s.is3d()) out.add(new Option.Cycler("curvegen.option.colours_from", null, List.of("curvegen.choice.side", "curvegen.choice.top"), () -> s.topColours ? 1 : 0, v -> s.topColours = v == 1));
        return out;
    }

    public static Message describe(ShapeSettings s) {
        if (s.gen == Gen.TORUS && (s.tW != s.tRing || s.tD != s.tRing || s.tH != s.tTube))
            return Message.of(s.tHollow ? "curvegen.shape.torus.stretched_hollow" : "curvegen.shape.torus.stretched_filled", s.tW, s.tH, s.tD, s.tTube, s.tRing);
        return PresetData.defaultName(s, s.gen);
    }
}
