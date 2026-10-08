package dev.curvegen.core;

import dev.curvegen.core.edit.HandleMath;

import java.util.ArrayList;
import java.util.List;

/**
 * The maths of 3D Bézier curves and patches: evaluating them, changing their degree, and finding where a look ray
 * meets them. Points are {x, y, z}, and a ray is in whatever coordinates the control points are.
 *
 * A curve is a single Bézier through all its control points, so the only way to add a point without changing the
 * curve is to raise its degree, which moves every inner point. A patch's control points are listed row by row: u
 * runs along a row, from column to column, and v from row to row.
 */
public final class Bezier3 {
    private Bezier3() {}

    public static final int MAX_POINTS = 32;
    public static final int MIN_GRID = 2, MAX_GRID = 6;

    // ---------- curves ----------

    /** The curve's point at u, from 0 at the first control point to 1 at the last. */
    public static double[] point(List<double[]> p, double u) {
        int n = p.size();
        double[] x = new double[n], y = new double[n], z = new double[n];
        for (int i = 0; i < n; i++) { x[i] = p.get(i)[0]; y[i] = p.get(i)[1]; z[i] = p.get(i)[2]; }
        for (int k = n - 1; k > 0; k--)
            for (int i = 0; i < k; i++) { x[i] += (x[i + 1] - x[i]) * u; y[i] += (y[i + 1] - y[i]) * u; z[i] += (z[i + 1] - z[i]) * u; }
        return new double[]{x[0], y[0], z[0]};
    }

    /** The same curve with one more control point. */
    public static List<double[]> elevate(List<double[]> p) {
        int n = p.size();
        List<double[]> out = new ArrayList<>(n + 1);
        out.add(p.get(0).clone());
        for (int i = 1; i < n; i++) {
            double a = (double) i / n;
            double[] before = p.get(i - 1), here = p.get(i);
            out.add(new double[]{a * before[0] + (1 - a) * here[0], a * before[1] + (1 - a) * here[1], a * before[2] + (1 - a) * here[2]});
        }
        out.add(p.get(n - 1).clone());
        return out;
    }

    /**
     * The curve with one control point fewer that is closest to this one: it keeps both ends, and elevating it lands
     * as near these control points as it can (by least squares). It undoes {@link #elevate} exactly. Needs three points or more.
     */
    public static List<double[]> reduce(List<double[]> p) {
        int n = p.size() - 1, m = n - 1;            // from degree n to degree m
        if (m < 1) throw new IllegalArgumentException("a curve needs two control points");
        double[][] q = new double[m + 1][];
        q[0] = p.get(0).clone(); q[m] = p.get(n).clone();
        int unknowns = m - 1;
        if (unknowns > 0) {
            // Elevating q gives a·q[i-1] + (1-a)·q[i] at index i, with a = i/n. Solve the normal equations for the inner points.
            double[][] mat = new double[unknowns][unknowns + 3];
            for (int i = 0; i <= n; i++) {
                double a = (double) i / n;
                int[] col = {i - 2, i - 1};                 // the unknowns q[i-1] and q[i] multiply
                double[] w = {a, 1 - a};
                double[] rhs = p.get(i).clone();
                for (int t = 0; t < 2; t++) {
                    int qi = col[t] + 1;                    // index into q
                    if (w[t] == 0 || qi < 0 || qi > m) continue;
                    if (qi == 0 || qi == m) for (int c = 0; c < 3; c++) rhs[c] -= w[t] * q[qi][c];
                }
                for (int t = 0; t < 2; t++) {
                    if (col[t] < 0 || col[t] >= unknowns) continue;
                    for (int u = 0; u < 2; u++) if (col[u] >= 0 && col[u] < unknowns) mat[col[t]][col[u]] += w[t] * w[u];
                    for (int c = 0; c < 3; c++) mat[col[t]][unknowns + c] += w[t] * rhs[c];
                }
            }
            for (int r = 0; r < unknowns; r++) {            // the matrix is tridiagonal and positive definite: no pivoting needed
                for (int below = r + 1; below < Math.min(unknowns, r + 2); below++) {
                    double f = mat[below][r] / mat[r][r];
                    for (int c = r; c < unknowns + 3; c++) mat[below][c] -= f * mat[r][c];
                }
            }
            for (int r = unknowns - 1; r >= 0; r--) {
                q[r + 1] = new double[3];
                for (int c = 0; c < 3; c++) {
                    double v = mat[r][unknowns + c];
                    if (r + 1 < unknowns) v -= mat[r][r + 1] * q[r + 2][c];
                    q[r + 1][c] = v / mat[r][r];
                }
            }
        }
        return new ArrayList<>(List.of(q));
    }

    /**
     * Adds a control point without changing the curve, by raising its degree. Returns the index of the new polygon's
     * point that sits nearest u along the curve, which is never an end, or -1 when the curve is full.
     */
    public static int insert(List<double[]> pts, double u) {
        if (pts.size() < 2 || pts.size() >= MAX_POINTS) return -1;
        List<double[]> up = elevate(pts);
        pts.clear(); pts.addAll(up);
        return inner(u, pts.size());
    }

    /** The inner control point of n whose share of the curve is centred nearest u. */
    private static int inner(double u, int n) { return (int) Math.max(1, Math.min(n - 2, Math.round(u * (n - 1)))); }

    /**
     * The point on the curve the ray passes closest to: {u, x, y, z, distance from the ray, distance along the ray}.
     * Null for fewer than two control points.
     */
    public static double[] nearestToRay(List<double[]> pts, double[] origin, double[] dir) {
        if (pts.size() < 2) return null;
        int n = Math.max(32, Math.min(512, pts.size() * 16));
        double[] segs = new double[n * 6];
        double[] a = point(pts, 0);
        for (int k = 0; k < n; k++) {
            double[] b = point(pts, (k + 1.0) / n);
            System.arraycopy(a, 0, segs, k * 6, 3); System.arraycopy(b, 0, segs, k * 6 + 3, 3);
            a = b;
        }
        double[] hit = HandleMath.nearestOnSegments(origin, dir, segs);
        int k = (int) hit[5];
        // The curve bends between samples: narrow down to the best u around the segment found.
        double lo = Math.max(0, (k - 1.0) / n), hi = Math.min(1, (k + 2.0) / n);
        for (int it = 0; it < 40; it++) {
            double m1 = lo + (hi - lo) / 3, m2 = hi - (hi - lo) / 3;
            if (HandleMath.toRay(origin, dir, point(pts, m1))[0] <= HandleMath.toRay(origin, dir, point(pts, m2))[0]) hi = m2; else lo = m1;
        }
        double u = (lo + hi) / 2;
        double[] p = point(pts, u), r = HandleMath.toRay(origin, dir, p);
        // A curve that crosses itself, or a ray along it, has other bends the search can't see. If the curve is
        // nearer the ray where the sampled answer was, take the curve's point there.
        double len = Math.sqrt(sq(segs[k * 6 + 3] - segs[k * 6], segs[k * 6 + 4] - segs[k * 6 + 1], segs[k * 6 + 5] - segs[k * 6 + 2]));
        double t = len == 0 ? 0 : Math.sqrt(sq(hit[0] - segs[k * 6], hit[1] - segs[k * 6 + 1], hit[2] - segs[k * 6 + 2])) / len;
        double[] q = point(pts, (k + t) / n), rq = HandleMath.toRay(origin, dir, q);
        if (rq[0] < r[0]) { u = (k + t) / n; p = q; r = rq; }
        return new double[]{u, p[0], p[1], p[2], r[0], r[1]};
    }

    private static double sq(double x, double y, double z) { return x * x + y * y + z * z; }

    // ---------- patches ----------

    /** A column of the grid, or a row when {@code alongRow}, as a curve's control points. */
    private static List<double[]> line(List<double[]> pts, int rows, int cols, int index, boolean alongRow) {
        List<double[]> out = new ArrayList<>();
        if (alongRow) for (int c = 0; c < cols; c++) out.add(pts.get(index * cols + c));
        else for (int r = 0; r < rows; r++) out.add(pts.get(r * cols + index));
        return out;
    }

    /** The patch's point at (u, v): u runs along a row, v from the first row to the last. */
    public static double[] patchPoint(List<double[]> pts, int rows, int cols, double u, double v) {
        List<double[]> across = new ArrayList<>(rows);
        for (int r = 0; r < rows; r++) across.add(point(line(pts, rows, cols, r, true), u));
        return point(across, v);
    }

    /** The same patch with one more row. */
    public static List<double[]> elevateRows(List<double[]> pts, int rows, int cols) { return changeRows(pts, rows, cols, true); }

    /** The patch with one row fewer that is closest to this one: every column is reduced as {@link #reduce} does a curve. */
    public static List<double[]> reduceRows(List<double[]> pts, int rows, int cols) { return changeRows(pts, rows, cols, false); }

    private static List<double[]> changeRows(List<double[]> pts, int rows, int cols, boolean up) {
        int now = rows + (up ? 1 : -1);
        double[][] out = new double[now * cols][];
        for (int c = 0; c < cols; c++) {
            List<double[]> column = line(pts, rows, cols, c, false);
            column = up ? elevate(column) : reduce(column);
            for (int r = 0; r < now; r++) out[r * cols + c] = column.get(r);
        }
        return new ArrayList<>(List.of(out));
    }

    /** The grid with rows and columns swapped. */
    public static List<double[]> transpose(List<double[]> pts, int rows, int cols) {
        List<double[]> out = new ArrayList<>(pts.size());
        for (int c = 0; c < cols; c++) for (int r = 0; r < rows; r++) out.add(pts.get(r * cols + c));
        return out;
    }

    /**
     * Adds a row to the surface without changing its shape. Returns the index of the new grid's row that sits
     * nearest v, which is never an edge, or -1 when the surface has all the rows it may.
     */
    public static int addRow(ShapeSettings s, double v) {
        if (s.sRows >= MAX_GRID) return -1;
        List<double[]> up = elevateRows(s.sPts, s.sRows, s.sCols);
        s.sPts.clear(); s.sPts.addAll(up);
        return inner(v, ++s.sRows);
    }

    /** Adds a column the same way, nearest u. */
    public static int addColumn(ShapeSettings s, double u) {
        if (s.sCols >= MAX_GRID) return -1;
        List<double[]> up = transpose(elevateRows(transpose(s.sPts, s.sRows, s.sCols), s.sCols, s.sRows), s.sCols + 1, s.sRows);
        s.sPts.clear(); s.sPts.addAll(up);
        return inner(u, ++s.sCols);
    }

    /**
     * Takes a row out of the surface, keeping its shape as close as one row fewer allows. The edges stay where they
     * are and the inner rows all move, so it doesn't matter which row the player picked. False at the fewest rows.
     */
    public static boolean removeRow(ShapeSettings s) {
        if (s.sRows <= MIN_GRID) return false;
        List<double[]> down = reduceRows(s.sPts, s.sRows, s.sCols);
        s.sPts.clear(); s.sPts.addAll(down);
        s.sRows--;
        return true;
    }

    public static boolean removeColumn(ShapeSettings s) {
        if (s.sCols <= MIN_GRID) return false;
        List<double[]> down = transpose(reduceRows(transpose(s.sPts, s.sRows, s.sCols), s.sCols, s.sRows), s.sCols - 1, s.sRows);
        s.sPts.clear(); s.sPts.addAll(down);
        s.sCols--;
        return true;
    }

    /**
     * Where the ray first hits the patch, or failing that the point on the patch it passes closest to:
     * {u, v, x, y, z, distance from the ray, distance along the ray}. The distance from the ray is 0 for a hit,
     * and passing within a twentieth of a block counts as one, so that a ray just clipping a hump stops there.
     */
    public static double[] patchNearestToRay(List<double[]> pts, int rows, int cols, double[] origin, double[] dir) {
        double[] d = HandleMath.normalize(dir);
        // A mesh fine enough for the bends the control points can make: a patch with more of them can turn more often.
        int g = 24 * (cols - 1), gv = 24 * (rows - 1);
        double[] found = scan(pts, rows, cols, origin, d, 0, 1, 0, 1, g, gv);
        double u = found[1], v = found[2], du = 1.0 / g, dv = 1.0 / gv;
        if (found[0] > 0) {
            // One facet of that mesh can hide two crossings, where the ray goes in and out of a hump. Look again
            // around the hit with a finer mesh, twice, and then close in on the real crossing nearest what it finds.
            for (int level = 0; level < 2; level++) {
                double[] finer = scan(pts, rows, cols, origin, d, Math.max(0, u - du), Math.min(1, u + du), Math.max(0, v - dv), Math.min(1, v + dv), 16, 16);
                if (finer[0] == 0) break;
                found = finer; u = finer[1]; v = finer[2]; du /= 8; dv /= 8;
            }
            double[] at = crossing(pts, rows, cols, origin, d, u, v, found[3]);
            if (at != null) { u = at[0]; v = at[1]; }
        }
        // Then, or when there's no crossing, walk to the best point nearby on the patch itself.
        double here = HandleMath.toRay(origin, d, patchPoint(pts, rows, cols, u, v))[0];
        for (double step = Math.min(du, dv) / 2; step > 1e-7 && here > 1e-9; ) {
            boolean moved = false;
            for (int k = 0; k < 4; k++) {
                double tu = Math.max(0, Math.min(1, u + (k == 0 ? step : k == 1 ? -step : 0)));
                double tv = Math.max(0, Math.min(1, v + (k == 2 ? step : k == 3 ? -step : 0)));
                double dist = HandleMath.toRay(origin, d, patchPoint(pts, rows, cols, tu, tv))[0];
                if (dist < here) { here = dist; u = tu; v = tv; moved = true; }
            }
            if (!moved) step /= 2;
        }
        double[] p = patchPoint(pts, rows, cols, u, v), r = HandleMath.toRay(origin, d, p);
        return new double[]{u, v, p[0], p[1], p[2], r[0], r[1]};
    }

    /**
     * Meshes part of the patch, nu by nv facets, and finds where the ray first hits the mesh:
     * {1, u, v, distance along the ray}. Without a hit it's {0, u, v, 0} for the mesh point nearest the ray.
     */
    private static double[] scan(List<double[]> pts, int rows, int cols, double[] o, double[] d, double u0, double u1, double v0, double v1, int nu, int nv) {
        double[][] mesh = new double[(nu + 1) * (nv + 1)][];
        for (int b = 0; b <= nv; b++) {
            List<double[]> across = new ArrayList<>(cols);      // the curve of constant v, evaluated along u
            for (int c = 0; c < cols; c++) across.add(point(line(pts, rows, cols, c, false), v0 + (v1 - v0) * b / nv));
            for (int a = 0; a <= nu; a++) mesh[b * (nu + 1) + a] = point(across, u0 + (u1 - u0) * a / nu);
        }
        double bestS = Double.POSITIVE_INFINITY, hitA = 0, hitB = 0;
        for (int b = 0; b < nv; b++)
            for (int a = 0; a < nu; a++) {
                double[] p00 = mesh[b * (nu + 1) + a], p10 = mesh[b * (nu + 1) + a + 1], p01 = mesh[(b + 1) * (nu + 1) + a], p11 = mesh[(b + 1) * (nu + 1) + a + 1];
                double[] h = hitTriangle(o, d, p00, p10, p11);
                if (h != null && h[0] < bestS) { bestS = h[0]; hitA = a + h[1] + h[2]; hitB = b + h[2]; }
                h = hitTriangle(o, d, p00, p11, p01);
                if (h != null && h[0] < bestS) { bestS = h[0]; hitA = a + h[1]; hitB = b + h[1] + h[2]; }
            }
        // The mesh cuts the tops off the patch's humps, so a ray that only just clips one misses the mesh there.
        // Passing within a twentieth of a block of a mesh line counts as a hit too.
        for (int b = 0; b <= nv; b++)
            for (int a = 0; a <= nu; a++)
                for (int way = 0; way < 2; way++) {
                    if (way == 0 ? a == nu : b == nv) continue;
                    double[] near = pass(o, d, mesh[b * (nu + 1) + a], mesh[way == 0 ? b * (nu + 1) + a + 1 : (b + 1) * (nu + 1) + a]);
                    if (near[0] > GRAZE || near[1] >= bestS) continue;
                    bestS = near[1]; hitA = a + (way == 0 ? near[2] : 0); hitB = b + (way == 0 ? 0 : near[2]);
                }
        if (bestS == Double.POSITIVE_INFINITY) {
            int best = 0;
            double bestD = Double.POSITIVE_INFINITY;
            for (int k = 0; k < mesh.length; k++) {
                double dist = HandleMath.toRay(o, d, mesh[k])[0];
                if (dist < bestD) { bestD = dist; best = k; }
            }
            return new double[]{0, u0 + (u1 - u0) * (best % (nu + 1)) / nu, v0 + (v1 - v0) * (best / (nu + 1)) / nv, 0};
        }
        return new double[]{1, u0 + (u1 - u0) * hitA / nu, v0 + (v1 - v0) * hitB / nv, bestS};
    }

    /** Newton's method for where the ray meets the patch, from a guess at (u, v) and s along the ray. Null if it doesn't settle on the patch. */
    private static double[] crossing(List<double[]> pts, int rows, int cols, double[] o, double[] d, double u, double v, double s) {
        final double e = 1e-6;
        for (int it = 0; it < 12; it++) {
            double[] p = patchPoint(pts, rows, cols, u, v), pu = patchPoint(pts, rows, cols, u + e, v), pv = patchPoint(pts, rows, cols, u, v + e);
            double[] r = {p[0] - o[0] - s * d[0], p[1] - o[1] - s * d[1], p[2] - o[2] - s * d[2]};
            if (Math.sqrt(sq(r[0], r[1], r[2])) < 1e-9) return new double[]{u, v};
            // Solve [Su Sv -d] (du dv ds) = -r by Cramer's rule.
            double[] a = {(pu[0] - p[0]) / e, (pu[1] - p[1]) / e, (pu[2] - p[2]) / e}, b = {(pv[0] - p[0]) / e, (pv[1] - p[1]) / e, (pv[2] - p[2]) / e}, c = {-d[0], -d[1], -d[2]};
            double det = det(a, b, c);
            if (Math.abs(det) < 1e-12) return null;
            double[] m = {-r[0], -r[1], -r[2]};
            u += det(m, b, c) / det; v += det(a, m, c) / det; s += det(a, b, m) / det;
            if (u < 0 || u > 1 || v < 0 || v > 1 || s < 0 || u != u) return null;
        }
        return null;
    }

    private static double det(double[] a, double[] b, double[] c) {
        return a[0] * (b[1] * c[2] - b[2] * c[1]) - b[0] * (a[1] * c[2] - a[2] * c[1]) + c[0] * (a[1] * b[2] - a[2] * b[1]);
    }

    /** A ray that passes this close to the patch counts as hitting it. */
    private static final double GRAZE = 0.05;

    /** How a ray passes the piece from a to b: {distance at the closest, how far along the ray, how far along the piece from 0 to 1}. */
    private static double[] pass(double[] o, double[] d, double[] a, double[] b) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = a[0] - o[0], wy = a[1] - o[1], wz = a[2] - o[2];
        double aa = ux * ux + uy * uy + uz * uz, ad = ux * d[0] + uy * d[1] + uz * d[2], aw = ux * wx + uy * wy + uz * wz, dw = d[0] * wx + d[1] * wy + d[2] * wz;
        double denom = aa - ad * ad;
        double t = denom > 1e-12 ? Math.max(0, Math.min(1, (ad * dw - aw) / denom)) : 0;
        double s = Math.max(0, dw + ad * t);
        t = aa > 1e-12 ? Math.max(0, Math.min(1, (ad * s - aw) / aa)) : 0;
        double ex = wx + ux * t - d[0] * s, ey = wy + uy * t - d[1] * s, ez = wz + uz * t - d[2] * s;
        return new double[]{Math.sqrt(ex * ex + ey * ey + ez * ez), s, t};
    }

    /** Where a ray crosses a triangle: {distance along the ray, weight of b, weight of c}, or null. */
    private static double[] hitTriangle(double[] o, double[] d, double[] a, double[] b, double[] c) {
        double e1x = b[0] - a[0], e1y = b[1] - a[1], e1z = b[2] - a[2], e2x = c[0] - a[0], e2y = c[1] - a[1], e2z = c[2] - a[2];
        double px = d[1] * e2z - d[2] * e2y, py = d[2] * e2x - d[0] * e2z, pz = d[0] * e2y - d[1] * e2x;
        double det = e1x * px + e1y * py + e1z * pz;
        if (Math.abs(det) < 1e-12) return null;
        double tx = o[0] - a[0], ty = o[1] - a[1], tz = o[2] - a[2];
        // A ray along the seam between two triangles must hit one of them, so each reaches a hair past its edges.
        final double seam = 1e-9;
        double wb = (tx * px + ty * py + tz * pz) / det;
        if (wb < -seam || wb > 1 + seam) return null;
        double qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
        double wc = (d[0] * qx + d[1] * qy + d[2] * qz) / det;
        if (wc < -seam || wb + wc > 1 + seam) return null;
        double s = (e2x * qx + e2y * qy + e2z * qz) / det;
        return s < 0 ? null : new double[]{s, wb, wc};
    }

    // ---------- for the shapes ----------

    /** The Bernstein polynomials of degree n at t, with their first and second derivatives. */
    static void basis(int n, double t, double[] b, double[] d1, double[] d2) {
        double[] low = new double[n + 1];           // degree n-2, then n-1, then n
        double[] b2 = new double[n + 1], b1 = new double[n + 1];
        low[0] = 1;
        for (int deg = 1; deg <= n; deg++) {
            if (deg == n - 1) System.arraycopy(low, 0, b2, 0, deg);
            if (deg == n) System.arraycopy(low, 0, b1, 0, deg);
            for (int i = deg; i > 0; i--) low[i] = low[i] * (1 - t) + low[i - 1] * t;
            low[0] *= 1 - t;
        }
        for (int i = 0; i <= n; i++) {
            b[i] = low[i];
            d1[i] = n * ((i > 0 ? b1[i - 1] : 0) - (i < n ? b1[i] : 0));
            d2[i] = n < 2 ? 0 : n * (n - 1) * ((i > 1 ? b2[i - 2] : 0) - 2 * (i > 0 && i < n ? b2[i - 1] : 0) + (i < n - 1 ? b2[i] : 0));
        }
    }
}
