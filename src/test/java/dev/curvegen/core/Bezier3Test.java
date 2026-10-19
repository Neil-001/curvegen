package dev.curvegen.core;

import dev.curvegen.core.edit.HandleMath;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The maths the editor uses on 3D Bézier curves and patches: adding and removing points, and finding where a look ray meets them. */
class Bezier3Test {
    private static List<double[]> curve(int n, long seed) {
        Random r = new Random(seed);
        List<double[]> p = new ArrayList<>();
        for (int k = 0; k < n; k++) p.add(new double[]{r.nextDouble() * 30, r.nextDouble() * 20, r.nextDouble() * 30});
        return p;
    }

    private static ShapeSettings surface(int rows, int cols, long seed) {
        Random r = new Random(seed);
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.SURFACE; s.sRows = rows; s.sCols = cols; s.sPts.clear();
        for (int k = 0; k < rows * cols; k++) s.sPts.add(new double[]{k % cols * 30.0 / (cols - 1), 2 + r.nextDouble() * 8, k / cols * 30.0 / (rows - 1)});
        return s;
    }

    private static double gap(double[] a, double[] b) { return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])); }

    private static double apart(List<double[]> a, List<double[]> b) {
        double most = 0;
        for (int k = 0; k <= 50; k++) most = Math.max(most, gap(Bezier3.point(a, k / 50.0), Bezier3.point(b, k / 50.0)));
        return most;
    }

    private static double apart(ShapeSettings a, ShapeSettings b) {
        double most = 0;
        for (int u = 0; u <= 12; u++)
            for (int v = 0; v <= 12; v++)
                most = Math.max(most, gap(Bezier3.patchPoint(a.sPts, a.sRows, a.sCols, u / 12.0, v / 12.0), Bezier3.patchPoint(b.sPts, b.sRows, b.sCols, u / 12.0, v / 12.0)));
        return most;
    }

    @Test
    void aCurveStartsAndEndsOnItsOuterPoints() {
        List<double[]> p = curve(7, 1);
        assertArrayEquals(p.get(0), Bezier3.point(p, 0), 1e-12);
        assertArrayEquals(p.get(6), Bezier3.point(p, 1), 1e-12);
        // Two points make a straight line, and three the familiar quadratic.
        assertArrayEquals(new double[]{2, 3, 4}, Bezier3.point(List.of(new double[]{0, 0, 0}, new double[]{8, 12, 16}), 0.25), 1e-12);
        assertArrayEquals(new double[]{1, 0.5, 0}, Bezier3.point(List.of(new double[]{0, 0, 0}, new double[]{1, 1, 0}, new double[]{2, 0, 0}), 0.5), 1e-12);
    }

    @Test
    void insertingAPointLeavesTheCurveAlone() {
        for (int n = 2; n <= 14; n++) {
            List<double[]> before = curve(n, n), after = new ArrayList<>(before);
            for (double u : new double[]{0, 0.3, 0.5, 0.97, 1}) {
                List<double[]> grown = new ArrayList<>(after);
                int index = Bezier3.insert(grown, u);
                assertEquals(after.size() + 1, grown.size());
                assertTrue(index >= 1 && index <= grown.size() - 2, "the new point is never an end");
                assertEquals(0, apart(before, grown), 1e-9, n + " points, u " + u);
                // It's the point whose pull on the curve peaks nearest u.
                assertTrue(Math.abs((double) index / (grown.size() - 1) - u) <= 1.0 / (grown.size() - 1) + 1e-9);
                after = grown;
            }
        }
        List<double[]> full = curve(Bezier3.MAX_POINTS, 3);
        assertEquals(-1, Bezier3.insert(full, 0.5));
        assertEquals(Bezier3.MAX_POINTS, full.size());
    }

    @Test
    void takingAPointOutUndoesPuttingOneIn() {
        for (int n = 2; n <= 12; n++) {
            List<double[]> p = curve(n, 40 + n), back = Bezier3.reduce(Bezier3.elevate(p));
            assertEquals(n, back.size());
            for (int k = 0; k < n; k++) assertArrayEquals(p.get(k), back.get(k), 1e-7, n + " points, point " + k);
        }
        // A curve that needs all its points can only be approximated, but the ends stay put and it's closer than dropping a point.
        List<double[]> p = curve(6, 9), less = Bezier3.reduce(p), dropped = new ArrayList<>(p);
        dropped.remove(3);
        assertEquals(5, less.size());
        assertArrayEquals(p.get(0), less.get(0), 0);
        assertArrayEquals(p.get(5), less.get(4), 0);
        assertTrue(apart(p, less) < apart(p, dropped), apart(p, less) + " against " + apart(p, dropped));
        assertThrows(IllegalArgumentException.class, () -> Bezier3.reduce(curve(2, 1)));
    }

    @Test
    void theRayFindsTheCurve() {
        Random r = new Random(5);
        for (int n = 2; n <= 14; n += 3) {
            List<double[]> p = curve(n, 70 + n);
            for (int trial = 0; trial < 20; trial++) {
                double u = r.nextDouble();
                double[] on = Bezier3.point(p, u), eye = {on[0] + r.nextGaussian() * 10, on[1] + 15 + r.nextDouble() * 10, on[2] + r.nextGaussian() * 10};
                // Looking straight at a point of the curve finds a point of the curve on the ray.
                double[] hit = Bezier3.nearestToRay(p, eye, new double[]{on[0] - eye[0], on[1] - eye[1], on[2] - eye[2]});
                assertEquals(0, hit[4], 1e-4, n + " points, u " + u);
                assertEquals(0, gap(Bezier3.point(p, hit[0]), new double[]{hit[1], hit[2], hit[3]}), 1e-9);
                assertEquals(0, HandleMath.toRay(eye, new double[]{on[0] - eye[0], on[1] - eye[1], on[2] - eye[2]}, new double[]{hit[1], hit[2], hit[3]})[0], 1e-4);
                // Looking a little to one side, nothing on the curve is nearer the ray than what it found.
                double[] dir = {on[0] - eye[0] + 0.7, on[1] - eye[1], on[2] - eye[2] - 0.4};
                hit = Bezier3.nearestToRay(p, eye, dir);
                for (int k = 0; k <= 2000; k++)
                    assertTrue(HandleMath.toRay(eye, dir, Bezier3.point(p, k / 2000.0))[0] >= hit[4] - 1e-3, n + " points, trial " + trial);
            }
        }
        assertNull(Bezier3.nearestToRay(curve(1, 1), new double[3], new double[]{0, 0, 1}));
        // A ray through a chord of a big curve misses the curve by a little. The answer is still a point of the curve.
        List<double[]> arch = List.of(new double[]{0, 0, 0}, new double[]{128, 256, 0}, new double[]{256, 0, 0});
        double[] eye = {125.33333333333334, 127.88888888888889, -10}, ahead = {0, 0, 1};
        double[] hit = Bezier3.nearestToRay(arch, eye, ahead);
        assertEquals(0, gap(Bezier3.point(arch, hit[0]), new double[]{hit[1], hit[2], hit[3]}), 1e-9);
        assertEquals(HandleMath.toRay(eye, ahead, new double[]{hit[1], hit[2], hit[3]})[0], hit[4], 1e-9);
        assertTrue(hit[4] > 0.01 && hit[4] < 0.06, "passes " + hit[4] + " from the curve");
    }

    @Test
    void aPatchPassesThroughItsCornersAndAlongItsEdges() {
        ShapeSettings s = surface(4, 5, 2);
        assertArrayEquals(s.sPts.get(0), Bezier3.patchPoint(s.sPts, 4, 5, 0, 0), 1e-12);
        assertArrayEquals(s.sPts.get(4), Bezier3.patchPoint(s.sPts, 4, 5, 1, 0), 1e-12);
        assertArrayEquals(s.sPts.get(15), Bezier3.patchPoint(s.sPts, 4, 5, 0, 1), 1e-12);
        assertArrayEquals(s.sPts.get(19), Bezier3.patchPoint(s.sPts, 4, 5, 1, 1), 1e-12);
        // An edge is the curve through that edge's own points.
        assertArrayEquals(Bezier3.point(s.sPts.subList(0, 5), 0.3), Bezier3.patchPoint(s.sPts, 4, 5, 0.3, 0), 1e-12);
    }

    @Test
    void addingARowOrColumnLeavesTheSurfaceAlone() {
        ShapeSettings start = surface(2, 3, 11), s = start.copy();
        for (int step = 0; step < 8; step++) {
            boolean row = step % 2 == 0;
            int rows = s.sRows, cols = s.sCols;
            int index = row ? Bezier3.addRow(s, 0.4) : Bezier3.addColumn(s, 0.9);
            if ((row ? rows : cols) == Bezier3.MAX_GRID) { assertEquals(-1, index); continue; }
            assertEquals(rows + (row ? 1 : 0), s.sRows);
            assertEquals(cols + (row ? 0 : 1), s.sCols);
            assertEquals(s.sRows * s.sCols, s.sPts.size());
            assertTrue(index >= 1 && index <= (row ? s.sRows : s.sCols) - 2);
            assertEquals(0, apart(start, s), 1e-9, "after step " + step);
        }
        assertEquals(Bezier3.MAX_GRID, s.sRows);
        assertEquals(-1, Bezier3.addRow(s, 0.5));
    }

    @Test
    void removingARowOrColumnKeepsTheSurfaceClose() {
        // Straight after adding one, removing it gives back exactly what there was.
        for (boolean row : new boolean[]{true, false}) {
            ShapeSettings start = surface(4, 3, 21), s = start.copy();
            assertTrue((row ? Bezier3.addRow(s, 0.5) : Bezier3.addColumn(s, 0.5)) > 0);
            assertTrue(row ? Bezier3.removeRow(s) : Bezier3.removeColumn(s));
            assertEquals(start.sRows, s.sRows); assertEquals(start.sCols, s.sCols);
            for (int k = 0; k < s.sPts.size(); k++) assertArrayEquals(start.sPts.get(k), s.sPts.get(k), 1e-7);
        }
        // Otherwise the corners and the edges that aren't shortened keep their shape, and the rest stays near.
        ShapeSettings start = surface(5, 4, 22), s = start.copy();
        assertTrue(Bezier3.removeRow(s));
        assertEquals(4, s.sRows); assertEquals(16, s.sPts.size());
        for (double[] uv : new double[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}, {0.4, 0}, {0.7, 1}})
            assertEquals(0, gap(Bezier3.patchPoint(start.sPts, 5, 4, uv[0], uv[1]), Bezier3.patchPoint(s.sPts, 4, 4, uv[0], uv[1])), 1e-9);
        assertTrue(apart(start, s) < 1.5, "moved " + apart(start, s));
        assertTrue(Bezier3.removeColumn(s) && Bezier3.removeColumn(s));
        assertFalse(Bezier3.removeColumn(s), "two columns is the fewest");
        assertEquals(2, s.sCols); assertEquals(8, s.sPts.size());
    }

    @Test
    void theRayFindsThePatch() {
        Random r = new Random(8);
        ShapeSettings s = surface(4, 4, 31);
        for (int trial = 0; trial < 40; trial++) {
            double u = r.nextDouble(), v = r.nextDouble();
            double[] on = Bezier3.patchPoint(s.sPts, 4, 4, u, v), eye = {on[0] + r.nextGaussian() * 4, on[1] + 20 + r.nextDouble() * 10, on[2] + r.nextGaussian() * 4};
            double[] hit = Bezier3.patchNearestToRay(s.sPts, 4, 4, eye, new double[]{on[0] - eye[0], on[1] - eye[1], on[2] - eye[2]});
            // From above, the first thing the ray meets is the point aimed at.
            assertEquals(0, hit[5], 1e-3, "trial " + trial);
            assertEquals(u, hit[0], 2e-3); assertEquals(v, hit[1], 2e-3);
            assertEquals(0, gap(on, new double[]{hit[2], hit[3], hit[4]}), 0.05);
            assertEquals(gap(on, eye), hit[6], 0.05);
        }
        // A patch that rises and falls steeply is crossed more than once. The first crossing is the one that counts.
        ShapeSettings peaks = surface(2, 6, 1);
        double[] heights = {0, 256, 0, 0, 256, 20};
        for (int k = 0; k < 12; k++) { peaks.sPts.get(k)[0] = 51.2 * (k % 6); peaks.sPts.get(k)[1] = heights[k % 6]; peaks.sPts.get(k)[2] = 256 * (k / 6); }
        double[] first = Bezier3.patchNearestToRay(peaks.sPts, 2, 6, new double[]{-10, 106.67, 128}, new double[]{1, 0, 0});
        assertEquals(0, first[5], 1e-3);
        assertEquals(53.60915, first[2], 0.01);
        assertEquals(63.60915, first[6], 0.01);
        // In and out of the top of one hump, under a block apart: still the way in.
        ShapeSettings hump = surface(2, 3, 1);
        double[][] over = {{0, 0}, {128, 256}, {256, 11}};
        for (int k = 0; k < 6; k++) { hump.sPts.get(k)[0] = over[k % 3][0]; hump.sPts.get(k)[1] = over[k % 3][1]; hump.sPts.get(k)[2] = 256 * (k / 3); }
        first = Bezier3.patchNearestToRay(hump.sPts, 2, 3, new double[]{-10, 65536.0 / 501 - .001, 128}, new double[]{1, 0, 0});
        assertEquals(0, first[5], 1e-6);
        assertEquals(130.448702066, first[2], 1e-3);
        // A ray that passes beside the patch finds the edge it comes closest to.
        double[] hit = Bezier3.patchNearestToRay(s.sPts, 4, 4, new double[]{-3, 40, 15}, new double[]{0, -1, 0});
        assertEquals(0, hit[0], 1e-6);
        assertEquals(3, hit[5], 0.05);
        for (int a = 0; a <= 40; a++)
            for (int b = 0; b <= 40; b++)
                assertTrue(HandleMath.toRay(new double[]{-3, 40, 15}, new double[]{0, -1, 0}, Bezier3.patchPoint(s.sPts, 4, 4, a / 40.0, b / 40.0))[0] >= hit[5] - 1e-3);
    }

    @Test
    void theBasisAddsUpAndItsSlopesMatch() {
        for (int n = 1; n <= 6; n++) {
            double[] b = new double[n + 1], d1 = new double[n + 1], d2 = new double[n + 1], lo = new double[n + 1], hi = new double[n + 1], x = new double[n + 1], y = new double[n + 1];
            double t = 0.37, e = 1e-4;
            Bezier3.basis(n, t, b, d1, d2);
            Bezier3.basis(n, t - e, lo, x, y);
            Bezier3.basis(n, t + e, hi, x, y);
            double sum = 0;
            for (int k = 0; k <= n; k++) {
                sum += b[k];
                assertEquals((hi[k] - lo[k]) / (2 * e), d1[k], 1e-6, "degree " + n);
                assertEquals((hi[k] - 2 * b[k] + lo[k]) / (e * e), d2[k], 1e-4, "degree " + n);
            }
            assertEquals(1, sum, 1e-12);
        }
    }
}
