package dev.curvegen.core.edit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class HandleMathTest {
    private static final double EPS = 1e-9;

    @Test
    void rayFindsTheNearestSpotOnAnAxis() {
        // Looking north (-z) from x = 3, at an x axis line 5 blocks ahead: the closest spot is straight ahead.
        assertEquals(3, HandleMath.alongAxis(new double[]{3, 1, 0}, new double[]{0, 0, -1}, new double[]{10, 1, -5}, 0), EPS);
        // A diagonal ray that crosses the vertical line through (2, ?, 2) at height 4.
        assertEquals(4, HandleMath.alongAxis(new double[]{0, 2, 0}, new double[]{1, 1, 1}, new double[]{2, 0, 2}, 1), 1e-9);
        // A ray that misses the line still gives the height where it passes closest.
        assertEquals(1, HandleMath.alongAxis(new double[]{0, 0, 0}, new double[]{1, 1, 0}, new double[]{1, 7, 4}, 1), EPS);
    }

    @Test
    void rayAlongOrAwayFromAnAxisGivesNothing() {
        assertTrue(Double.isNaN(HandleMath.alongAxis(new double[]{0, 0, 0}, new double[]{1, 0.001, 0}, new double[]{0, 3, 0}, 0)));
        // The closest pass is behind the player.
        assertTrue(Double.isNaN(HandleMath.alongAxis(new double[]{0, 0, 0}, new double[]{0, 0, 1}, new double[]{0, 0, -5}, 0)));
    }

    @Test
    void rayCrossesAPlane() {
        double[] hit = HandleMath.onPlane(new double[]{1, 10, 1}, new double[]{1, -2, 0}, 1, 4, 100);
        assertArrayEquals(new double[]{4, 4, 1}, hit, EPS);
        assertNull(HandleMath.onPlane(new double[]{0, 10, 0}, new double[]{1, 0.001, 0}, 1, 4, 100), "nearly parallel");
        assertNull(HandleMath.onPlane(new double[]{0, 10, 0}, new double[]{0, 1, 0}, 1, 4, 100), "behind the player");
        assertNull(HandleMath.onPlane(new double[]{0, 10, 0}, new double[]{1, -0.05, 0}, 1, 4, 100), "further than the reach");
    }

    @Test
    void facingAxisIsTheOneTheRayRunsAlongMost() {
        assertEquals(0, HandleMath.facingAxis(new double[]{-0.9, 0.3, 0.2}));
        assertEquals(1, HandleMath.facingAxis(new double[]{0.3, -0.9, 0.2}));
        assertEquals(2, HandleMath.facingAxis(new double[]{0.3, 0.2, 0.9}));
    }

    @Test
    void pickTakesTheHandleNearestTheCrosshair() {
        double[] o = {0, 0, 0}, d = {0, 0, 1};
        List<double[]> handles = List.of(new double[]{0.3, 0, 5}, new double[]{0.1, 0, 5}, new double[]{0, 0, -3}, new double[]{2, 0, 5});
        assertEquals(1, HandleMath.pick(o, d, handles, 0.35));
        assertEquals(-1, HandleMath.pick(o, d, handles.subList(2, 4), 0.35), "one is behind and one is too far to the side");
    }

    @Test
    void pickReachesFurtherAtADistance() {
        double[] o = {0, 0, 0}, d = {0, 0, 1};
        assertEquals(-1, HandleMath.pick(o, d, List.of(new double[]{1, 0, 5}), 0.35));
        // Far handles are drawn bigger, so the same angle off the crosshair still counts.
        assertEquals(0, HandleMath.pick(o, d, List.of(new double[]{1, 0, 60}), 0.35));
        // Of two handles in line, the nearer wins.
        assertEquals(1, HandleMath.pick(o, d, List.of(new double[]{0, 0, 9}, new double[]{0, 0, 4}), 0.35));
    }

    @Test
    void nearestPointOnACurve() {
        // Two segments along x at z = 5. Looking at x = 1.5 lands on the second.
        double[] segs = {0, 0, 5, 1, 0, 5, 1, 0, 5, 2, 0, 5};
        double[] r = HandleMath.nearestOnSegments(new double[]{1.5, 0.2, 0}, new double[]{0, 0, 1}, segs);
        assertArrayEquals(new double[]{1.5, 0, 5}, new double[]{r[0], r[1], r[2]}, 1e-9);
        assertEquals(0.2, r[3], 1e-9);
        assertEquals(5, r[4], 1e-9);
        assertEquals(1, (int) r[5]);
        // Past the end, it clamps to the last point.
        r = HandleMath.nearestOnSegments(new double[]{4, 0, 0}, new double[]{0, 0, 1}, segs);
        assertEquals(2, r[0], 1e-9);
        assertEquals(2, r[3], 1e-9);
        assertNull(HandleMath.nearestOnSegments(new double[]{0, 0, 0}, new double[]{0, 0, 1}, new double[0]));
    }
}
