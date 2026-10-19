package dev.curvegen.core.edit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrbitTest {
    private static final double EPS = 1e-9;

    private static Orbit some() {
        Orbit o = new Orbit();
        o.yaw = 0.7; o.pitch = 0.4; o.scale = 6; o.tx = 10; o.ty = 5; o.tz = 8; o.ox = 120; o.oy = 90;
        return o;
    }

    @Test
    void theCameraStartsSouthOfTheTargetWithEastOnTheRight() {
        Orbit o = new Orbit();
        o.yaw = 0; o.pitch = 0; o.scale = 10;
        assertArrayEquals(new double[]{10, 0, 0}, o.project(1, 0, 0), EPS);       // east is right
        assertArrayEquals(new double[]{0, -10, 0}, o.project(0, 1, 0), EPS);      // up is up the screen, which is -y
        assertArrayEquals(new double[]{0, 0, 1}, o.project(0, 0, -1), EPS);       // north is further away
    }

    @Test
    void theScreenAxesAndTheLookDirectionAreSquareToEachOther() {
        Orbit o = some();
        double[] r = o.right(), u = o.up(), f = o.forward();
        assertEquals(0, HandleMath.dot(r, u), EPS);
        assertEquals(0, HandleMath.dot(r, f), EPS);
        assertEquals(0, HandleMath.dot(u, f), EPS);
        for (double[] v : new double[][]{r, u, f}) assertEquals(1, HandleMath.dot(v, v), EPS);
        // Right-handed: right × up points back at the camera.
        double[] b = o.back();
        assertEquals(b[0], r[1] * u[2] - r[2] * u[1], EPS);
        assertEquals(b[1], r[2] * u[0] - r[0] * u[2], EPS);
        assertEquals(b[2], r[0] * u[1] - r[1] * u[0], EPS);
    }

    @Test
    void unprojectUndoesProject() {
        Orbit o = some();
        double[] s = o.project(3.5, -2, 17.25);
        assertArrayEquals(new double[]{3.5, -2, 17.25}, o.unproject(s[0], s[1], s[2]), 1e-9);
    }

    @Test
    void aLookRayPassesThroughEveryPointDrawnAtItsSpot() {
        Orbit o = some();
        double[] p = {14, 2, 3};
        double[] s = o.project(p[0], p[1], p[2]);
        double[][] ray = o.ray(s[0], s[1], 1000);
        double[] near = HandleMath.toRay(ray[0], ray[1], p);
        assertEquals(0, near[0], 1e-9);
        assertTrue(near[1] > 0, "the ray starts in front of everything in the box");
    }

    @Test
    void aDraggedPointStaysUnderTheCursorAndInThePlaneFacingTheCamera() {
        Orbit o = some();
        double[] p = {4, 6, 9};
        double[] moved = o.drag(p, 200, 33);
        double[] s = o.project(moved[0], moved[1], moved[2]);
        assertEquals(200, s[0], EPS);
        assertEquals(33, s[1], EPS);
        assertEquals(o.project(p[0], p[1], p[2])[2], s[2], EPS, "it keeps its depth");
        // The move is square to the look direction.
        double[] step = {moved[0] - p[0], moved[1] - p[1], moved[2] - p[2]};
        assertEquals(0, HandleMath.dot(step, o.forward()), EPS);
        // Dragged to where it already is, it doesn't move.
        double[] at = o.project(p[0], p[1], p[2]);
        assertArrayEquals(p, o.drag(p, at[0], at[1]), EPS);
    }

    @Test
    void lookingStraightAtAnAxisPlaneDragsWithinIt() {
        Orbit o = new Orbit();
        o.yaw = 0; o.pitch = 0; o.scale = 4;
        double[] moved = o.drag(new double[]{1, 2, 3}, 40, -20);
        assertArrayEquals(new double[]{10, 5, 3}, moved, EPS);
    }

    @Test
    void orbitingTurnsAboutTheTargetAndStopsShortOfThePoles() {
        Orbit o = some();
        double[] before = o.project(o.tx, o.ty, o.tz);
        o.orbit(40, -25);
        assertArrayEquals(before, o.project(o.tx, o.ty, o.tz), EPS);
        assertEquals(0.7 - 40 * Orbit.TURN, o.yaw, EPS);
        assertEquals(0.4 - 25 * Orbit.TURN, o.pitch, EPS);
        o.orbit(0, 1e6);
        assertEquals(Orbit.MAX_PITCH, o.pitch, EPS);
        o.orbit(0, -1e6);
        assertEquals(-Orbit.MAX_PITCH, o.pitch, EPS);
    }

    @Test
    void zoomingKeepsWhatIsUnderTheCursor() {
        Orbit o = some();
        double[] under = o.unproject(77, 140, 2);
        o.zoom(77, 140, 1.7);
        assertEquals(6 * 1.7, o.scale, EPS);
        double[] s = o.project(under[0], under[1], under[2]);
        assertEquals(77, s[0], 1e-9);
        assertEquals(140, s[1], 1e-9);
        o.zoom(0, 0, 1e9);
        assertEquals(Orbit.MAX_SCALE, o.scale, EPS);
        o.zoom(0, 0, 1e-12);
        assertEquals(Orbit.MIN_SCALE, o.scale, EPS);
    }

    @Test
    void panningSlidesThePicture() {
        Orbit o = some();
        double[] a = o.project(1, 2, 3);
        o.pan(5, -7);
        double[] b = o.project(1, 2, 3);
        assertArrayEquals(new double[]{a[0] + 5, a[1] - 7, a[2]}, b, EPS);
    }

    @Test
    void aBoxFitsTheScreenFromWhereverTheCameraIs() {
        Orbit o = new Orbit();
        int[] n = {30, 12, 50};
        for (double yaw = 0; yaw < 6.3; yaw += 0.37)
            for (double pitch = -1.5; pitch <= 1.5; pitch += 0.3) {
                o.yaw = yaw; o.pitch = pitch;
                o.fit(n[0], n[1], n[2], 162, 44, 421, 188, 6);
                for (int c = 0; c < 8; c++) {
                    double[] s = o.project((c & 1) * n[0], (c >> 1 & 1) * n[1], (c >> 2) * n[2]);
                    assertTrue(s[0] >= 162 + 6 - 1e-6 && s[0] <= 421 - 6 + 1e-6 && s[1] >= 44 + 6 - 1e-6 && s[1] <= 188 - 6 + 1e-6,
                            "corner " + c + " at " + s[0] + ", " + s[1]);
                }
            }
        // It's no smaller than it has to be: from the south, 30 wide and 12 high in a room of 247 by 132.
        o.yaw = 0; o.pitch = 0;
        o.fit(n[0], n[1], n[2], 162, 44, 421, 188, 6);
        assertEquals(247.0 / 30, o.scale, 1e-9);
    }

    @Test
    void pickingTakesThePointNearestTheCursorAndThenTheOneInFront() {
        Orbit o = new Orbit();
        o.yaw = 0; o.pitch = 0; o.scale = 10;
        List<double[]> pts = List.of(new double[]{0, 0, 0}, new double[]{1, 0, 0}, new double[]{1, 0, 5}, new double[]{4, 4, 0});
        assertEquals(0, o.pick(pts, 1, 1, 6));
        assertEquals(2, o.pick(pts, 10, 0, 6), "two points drawn on the same spot: the one nearer the camera");
        assertEquals(1, o.pick(List.of(pts.get(0), pts.get(1)), 7, 0, 6), "nearest the cursor of two within reach");
        assertEquals(-1, o.pick(pts, 25, -20, 6));
        assertEquals(3, o.pick(pts, 45, -45, 6));
        assertEquals(-1, o.pick(pts, 47, -40, 6), "the reach is a square: 7 pixels out on one axis is too far");
    }

    @Test
    void aCopyTimesTwoDrawsEverythingTwiceAsFarOut() {
        Orbit o = some(), big = o.times(2);
        double[] a = o.project(3, 4, 5), b = big.project(3, 4, 5);
        assertArrayEquals(new double[]{a[0] * 2, a[1] * 2, a[2]}, b, EPS);
        assertTrue(o.same(o.copy()));
        assertFalse(o.same(big));
    }
}
