package dev.curvegen.core.edit;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.Solver;
import org.junit.jupiter.api.Test;

class Edit2DTest {
    private static final boolean[] X = {true, false, false}, Y = {false, true, false}, Z = {false, false, true}, ALL = {true, true, true};

    private static ShapeSettings of(Gen gen) {
        ShapeSettings s = new ShapeSettings();
        s.gen = gen;
        return s;
    }

    @Test
    void anEllipsesBoxIsWidthHeightAndDepth() {
        ShapeSettings s = of(Gen.ELLIPSE);
        s.depth = 3;
        assertArrayEquals(new int[]{31, 19, 3}, Edit2D.size(s));
        Edit2D.resize(s, new int[]{40, 99, 9}, X);
        assertArrayEquals(new int[]{40, 19, 3}, Edit2D.size(s), "only the dragged axis changes");
        Edit2D.resize(s, new int[]{9999, 0, 9999}, ALL);
        assertArrayEquals(new int[]{Edit2D.MAX_SIZE, 1, Edit2D.MAX_DEPTH}, Edit2D.size(s));
    }

    /** What bump does: one more block on one axis. */
    @Test
    void bumpingGrowsOneAxisByOne() {
        ShapeSettings s = of(Gen.ELLIPSE);
        int[] want = Edit2D.size(s);
        want[1]++;
        Edit2D.resize(s, want, Y);
        assertEquals(20, s.eH);
        assertEquals(31, s.eW);
        want = Edit2D.size(s);
        want[2]++;
        Edit2D.resize(s, want, Z);
        assertEquals(2, s.depth);
    }

    @Test
    void aLockedEquationKeepsItsProportions() {
        ShapeSettings s = of(Gen.EQUATION);   // x from -2pi to 2pi, y from -3 to 3
        assertTrue(s.qLock);
        int h = Edit2D.size(s)[1];
        assertEquals(Solver.run(s.copy()).ny(), h, "the box matches the grid the solver builds");
        Edit2D.resize(s, new int[]{96, h, 1}, X);
        assertEquals(96, s.qW);
        assertEquals(46, Edit2D.size(s)[1], "twice as wide is twice as tall");
        // Dragging the height moves the width with it.
        Edit2D.resize(s, new int[]{96, 23, 1}, Y);
        assertEquals(48, s.qW);
        assertEquals(23, Edit2D.size(s)[1]);
        assertEquals(Solver.run(s.copy()).ny(), Edit2D.size(s)[1]);
    }

    @Test
    void anUnlockedEquationResizesFreely() {
        ShapeSettings s = of(Gen.EQUATION);
        s.qLock = false;
        Edit2D.resize(s, new int[]{60, 10, 1}, Y);
        assertArrayEquals(new int[]{48, 10, 1}, Edit2D.size(s));
    }

    @Test
    void anEquationWithBrokenRangesFallsBackToItsStoredHeight() {
        ShapeSettings s = of(Gen.EQUATION);
        s.xmax = "((";
        assertArrayEquals(new int[]{48, 23, 1}, Edit2D.size(s));
        Edit2D.resize(s, new int[]{48, 30, 1}, Y);
        assertEquals(30, s.qH);
    }

    @Test
    void aBeziersPointsStretchWithItsBox() {
        ShapeSettings s = of(Gen.BEZIER);   // 40 by 24
        Edit2D.resize(s, new int[]{80, 12, 1}, new boolean[]{true, true, false});
        assertArrayEquals(new int[]{80, 12, 1}, Edit2D.size(s));
        assertArrayEquals(new double[]{4, 1}, s.pts.get(0), 1e-12);
        assertArrayEquals(new double[]{76, 2}, s.pts.get(3), 1e-12);
    }

    @Test
    void bumpingABezierStretchesItsPointsNotItsGrid() {
        ShapeSettings s = of(Gen.BEZIER);
        s.pts.clear();
        s.pts.add(new double[]{10, 4});
        s.pts.add(new double[]{10.5, 9});
        s.pts.add(new double[]{11, 4});
        // The points span 1 block across. Pushing the right side out by one makes that 2, and the left stays at 10.
        assertArrayEquals(new int[3], Edit2D.bumpPoints(s, 0, 1, 1));
        assertArrayEquals(new double[]{10, 4}, s.pts.get(0), 1e-12);
        assertArrayEquals(new double[]{11, 9}, s.pts.get(1), 1e-12);
        assertArrayEquals(new double[]{12, 4}, s.pts.get(2), 1e-12);
        assertEquals(40, s.bW);
        // Pushing the left side out keeps the right at 12.
        Edit2D.bumpPoints(s, 0, -1, 2);
        assertArrayEquals(new double[]{8, 4}, s.pts.get(0), 1e-12);
        assertArrayEquals(new double[]{12, 4}, s.pts.get(2), 1e-12);
        // Pulling in stops at one block.
        Edit2D.bumpPoints(s, 0, 1, -50);
        assertArrayEquals(new double[]{9, 4}, s.pts.get(2), 1e-12);
        // Past the bottom of the grid, the grid grows and reports how far its corner moved.
        assertArrayEquals(new int[]{0, -6, 0}, Edit2D.bumpPoints(s, 1, -1, 10));
        assertEquals(30, s.bH);
        assertEquals(0, s.pts.get(0)[1], 1e-12);
        assertEquals(15, s.pts.get(1)[1], 1e-12);
        // At the size limit there's no room, and the points keep their proportions instead of piling up at the edge.
        ShapeSettings full = of(Gen.BEZIER);
        full.bW = Edit2D.MAX_SIZE;
        full.pts.clear();
        full.pts.add(new double[]{0, 2});
        full.pts.add(new double[]{200, 2});
        full.pts.add(new double[]{400, 2});
        for (int side : new int[]{1, -1}) {
            Edit2D.bumpPoints(full, 0, side, 5);
            assertEquals(Edit2D.MAX_SIZE, full.bW);
            assertEquals(200, full.pts.get(1)[0], 1e-9);
            assertEquals(400, full.pts.get(2)[0], 1e-9);
        }
        // Depth and other shapes leave it to the box.
        assertNull(Edit2D.bumpPoints(s, 2, 1, 1));
        assertNull(Edit2D.bumpPoints(of(Gen.ELLIPSE), 0, 1, 1));
    }

    @Test
    void pointsSitHalfwayThroughTheDepth() {
        ShapeSettings s = of(Gen.BEZIER);
        s.depth = 3;
        assertEquals(4, Edit2D.points(s).size());
        assertArrayEquals(new double[]{2, 2, 1.5}, Edit2D.points(s).get(0), 0);
        assertTrue(Edit2D.points(of(Gen.ELLIPSE)).isEmpty());
    }

    @Test
    void aPointMovesFreelyOrInHalfBlocks() {
        ShapeSettings s = of(Gen.BEZIER);
        assertArrayEquals(new int[3], Edit2D.movePoint(s, 1, new double[]{11.3, 7.8, 0}));
        assertArrayEquals(new double[]{11.3, 7.8}, s.pts.get(1), 0);
        s.snap = true;
        Edit2D.movePoint(s, 1, new double[]{11.3, 7.8, 0});
        assertArrayEquals(new double[]{11.5, 8}, s.pts.get(1), 0);
    }

    @Test
    void draggingAPointPastTheEdgeGrowsTheGrid() {
        ShapeSettings s = of(Gen.BEZIER);
        // Past the right and the top: the grid grows that way and nothing else moves.
        assertArrayEquals(new int[3], Edit2D.movePoint(s, 3, new double[]{43.2, 30, 0}));
        assertEquals(44, s.bW);
        assertEquals(30, s.bH);
        assertArrayEquals(new double[]{43.2, 30}, s.pts.get(3), 0);
        assertArrayEquals(new double[]{2, 2}, s.pts.get(0), 0);
        // Past the left and the bottom: the corner moves out and every point shifts to stay where it was.
        assertArrayEquals(new int[]{-3, -1, 0}, Edit2D.movePoint(s, 0, new double[]{-2.5, -1, 0}));
        assertEquals(47, s.bW);
        assertEquals(31, s.bH);
        assertArrayEquals(new double[]{0.5, 0}, s.pts.get(0), 1e-12);
        assertArrayEquals(new double[]{46.2, 31}, s.pts.get(3), 1e-12);
    }

    @Test
    void theGridStopsGrowingAtTheSizeLimit() {
        ShapeSettings s = of(Gen.BEZIER);
        assertArrayEquals(new int[]{-360, 0, 0}, Edit2D.movePoint(s, 0, new double[]{-5000, 2, 0}));
        assertEquals(Edit2D.MAX_SIZE, s.bW);
        assertEquals(0, s.pts.get(0)[0], 0);
        Edit2D.movePoint(s, 3, new double[]{5000, 2, 0});
        assertEquals(Edit2D.MAX_SIZE, s.pts.get(3)[0], 0);
    }

    @Test
    void removingStopsAtTwoPoints() {
        ShapeSettings s = of(Gen.BEZIER);
        assertTrue(Edit2D.removePoint(s, 1));
        assertTrue(Edit2D.removePoint(s, 0));
        assertEquals(2, s.pts.size());
        assertFalse(Edit2D.removePoint(s, 0));
        assertFalse(Edit2D.removePoint(of(Gen.ELLIPSE), 0));
    }

    @Test
    void insertingPutsThePointWhereTheCurvePasses() {
        ShapeSettings s = of(Gen.BEZIER);
        s.pts.clear();
        s.pts.add(new double[]{0, 0});
        s.pts.add(new double[]{10, 0});
        s.pts.add(new double[]{20, 0});
        s.pts.add(new double[]{30, 0});
        // Two thirds of the way along a straight curve is between the third and fourth points.
        assertEquals(2, Edit2D.insertPoint(s, new double[]{14, 0.2, 0}));
        // Near the end of what are now five points, it goes just before the last.
        assertEquals(4, Edit2D.insertPoint(s, new double[]{28, 0, 0}));
        assertEquals(6, s.pts.size());
        assertArrayEquals(new double[]{14, 0.2}, s.pts.get(2), 0);
        // The ends stay the ends.
        assertEquals(1, Edit2D.insertPoint(s, new double[]{0, 0, 0}));
        assertEquals(s.pts.size() - 1, Edit2D.insertPoint(s, new double[]{30, 0, 0}));
        while (s.pts.size() < Edit2D.MAX_POINTS) assertTrue(Edit2D.insertPoint(s, new double[]{5, 0, 0}) > 0);
        assertEquals(-1, Edit2D.insertPoint(s, new double[]{5, 0, 0}));
    }

    @Test
    void duplicatingPutsTheCopyHalfABlockTowardsTheNextPoint() {
        ShapeSettings s = of(Gen.BEZIER);
        s.pts.clear();
        s.pts.add(new double[]{2, 2});
        s.pts.add(new double[]{2, 10});
        s.pts.add(new double[]{12, 10});
        assertEquals(1, Edit2D.duplicatePoint(s, 0));
        assertArrayEquals(new double[]{2, 2.5}, s.pts.get(1), 1e-12);
        // The last point's copy goes towards the previous one, and the end stays the end.
        assertEquals(3, Edit2D.duplicatePoint(s, 3));
        assertArrayEquals(new double[]{11.5, 10}, s.pts.get(3), 1e-12);
        assertArrayEquals(new double[]{12, 10}, s.pts.get(4), 0);
    }

    @Test
    void theCurveRunsThroughTheShape() {
        ShapeSettings s = of(Gen.ELLIPSE);
        s.depth = 4;
        double[] c = Edit2D.curve(s);
        assertEquals(0, c.length % 6);
        for (int k = 0; k < c.length; k += 3) {
            double x = (c[k] - 15.5) / 15.5, y = (c[k + 1] - 9.5) / 9.5;
            assertEquals(1, x * x + y * y, 1e-9);
            assertEquals(2, c[k + 2], 0);
        }
        s = of(Gen.BEZIER);
        c = Edit2D.curve(s);
        assertArrayEquals(new double[]{2, 2, 0.5}, new double[]{c[0], c[1], c[2]}, 1e-12);
        assertArrayEquals(new double[]{38, 4, 0.5}, new double[]{c[c.length - 3], c[c.length - 2], c[c.length - 1]}, 1e-12);
        assertNull(Edit2D.curve(of(Gen.EQUATION)));
    }

    @Test
    void aSolvedOverlayStretchesToTheBox() {
        double[] o = Edit2D.overlay(java.util.List.of(new double[]{2, 2, 12, 7}), 2, new int[]{10, 5, 1}, new int[]{20, 5, 4});
        assertArrayEquals(new double[]{0, 0, 2, 20, 5, 2}, o, 1e-12);
    }
}
