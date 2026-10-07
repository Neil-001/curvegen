package dev.curvegen.core.edit;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.Shape3;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;
import org.junit.jupiter.api.Test;

class Edit3DTest {
    private static final boolean[] ALL = {true, true, true};

    private static ShapeSettings of(Gen gen) {
        ShapeSettings s = new ShapeSettings();
        s.gen = gen;
        return s;
    }

    @Test
    void anEllipsoidTakesTheBoxItIsGiven() {
        ShapeSettings s = of(Gen.ELLIPSOID);
        assertArrayEquals(new int[]{21, 21, 21}, Edit3D.size(s));
        Edit3D.resize(s, new int[]{30, 8, 1}, ALL);
        assertArrayEquals(new int[]{30, 8, 1}, new int[]{s.e3W, s.e3H, s.e3D});
        Edit3D.resize(s, new int[]{1000, 0, -5}, ALL);
        assertArrayEquals(new int[]{Shape3.MAX_SIZE, 1, 1}, Edit3D.size(s));
    }

    @Test
    void aThickEllipsoidsBoxLeavesOutTheRoomItsShellAdds() {
        ShapeSettings s = of(Gen.ELLIPSOID);
        s.e3Mode = ShapeSettings.EllipseMode.OUTWARDS; s.e3T = 3;
        assertArrayEquals(new int[]{21, 21, 21}, Edit3D.size(s));
        assertEquals(27, Shape3.of(s).nx());
    }

    @Test
    void stretchingATorusLeavesItsRingAndTubeAlone() {
        ShapeSettings s = of(Gen.TORUS);
        Edit3D.resize(s, new int[]{40, 12, 25}, ALL);
        assertArrayEquals(new int[]{40, 12, 25}, Edit3D.size(s));
        assertEquals(25, s.tRing);
        assertEquals(9, s.tTube);
        assertTrue(Edit3D.describe(s).contains("stretched from a 25 ring"), Edit3D.describe(s));
        Edit3D.resize(s, new int[]{999, 12, 25}, ALL);
        assertEquals(Shape3.MAX_SIZE, s.tW);
    }

    @Test
    void aRoundTorusFollowsItsRingAndTubeExactly() {
        ShapeSettings s = of(Gen.TORUS);
        Edit3D.setRing(s, 31);
        assertArrayEquals(new int[]{31, 9, 31}, Edit3D.size(s));
        Edit3D.setTube(s, 11);
        assertArrayEquals(new int[]{31, 11, 31}, Edit3D.size(s));
        assertFalse(Edit3D.describe(s).contains("stretched"));
    }

    @Test
    void aStretchedTorusKeepsItsStretchWhenTheRingOrTubeChanges() {
        ShapeSettings s = of(Gen.TORUS);
        Edit3D.resize(s, new int[]{50, 18, 25}, ALL);   // twice as wide and twice as tall as round
        Edit3D.setRing(s, 30);
        assertArrayEquals(new int[]{60, 18, 30}, Edit3D.size(s));
        Edit3D.setTube(s, 6);
        assertArrayEquals(new int[]{60, 12, 30}, Edit3D.size(s));
    }

    @Test
    void theTubeIsNeverThickerThanTheRing() {
        ShapeSettings s = of(Gen.TORUS);
        Edit3D.setTube(s, 80);
        assertEquals(25, s.tTube);
        Edit3D.setRing(s, 10);
        assertEquals(10, s.tTube);
        assertEquals(10, s.tH);
        Edit3D.setRing(s, 0);
        assertEquals(1, s.tRing);
        assertEquals(1, s.tTube);
    }

    // ---------- equation ----------

    @Test
    void aLockedEquationKeepsItsRangesProportionsWhicheverSideIsDragged() {
        ShapeSettings s = of(Gen.EQUATION3);   // x and y run -pi to pi, z -1.5 to 1.5
        assertArrayEquals(new int[]{32, 15, 32}, Edit3D.size(s));
        Shape3 shape = Shape3.of(s);
        assertArrayEquals(new int[]{shape.nx(), shape.ny(), shape.nz()}, Edit3D.size(s), "the box is the one the solver uses");

        Edit3D.resize(s, new int[]{64, 15, 32}, new boolean[]{true, false, false});
        assertArrayEquals(new int[]{64, 31, 64}, Edit3D.size(s));
        Edit3D.resize(s, new int[]{64, 10, 64}, new boolean[]{false, true, false});   // height 10 means a width of 10 * 2pi / 3
        assertArrayEquals(new int[]{21, 10, 21}, Edit3D.size(s));
        Edit3D.resize(s, new int[]{21, 10, 40}, new boolean[]{false, false, true});
        assertArrayEquals(new int[]{40, 19, 40}, Edit3D.size(s));
        shape = Shape3.of(s);
        assertArrayEquals(new int[]{shape.nx(), shape.ny(), shape.nz()}, Edit3D.size(s));
        assertArrayEquals(new int[]{s.q3W, s.q3H, s.q3D}, Edit3D.size(s), "the settings hold the locked sizes too");
        assertEquals("-pi", s.x3min, "ranges stay as they are");
    }

    @Test
    void anUnlockedEquationStretchesOneAxisAtATime() {
        ShapeSettings s = of(Gen.EQUATION3);
        s.q3Lock = false;
        Edit3D.resize(s, new int[]{50, 99, 99}, new boolean[]{true, false, false});
        assertArrayEquals(new int[]{50, 15, 32}, Edit3D.size(s));
        Edit3D.resize(s, new int[]{50, 400, 7}, new boolean[]{false, true, true});
        assertArrayEquals(new int[]{50, Shape3.MAX_SIZE, 7}, Edit3D.size(s));
    }

    @Test
    void anEquationWithBrokenRangesStillHasABox() {
        ShapeSettings s = of(Gen.EQUATION3);
        s.x3max = "oops";
        assertArrayEquals(new int[]{32, 15, 32}, Edit3D.size(s));
        Edit3D.resize(s, new int[]{40, 15, 32}, new boolean[]{true, false, false});
        assertArrayEquals(new int[]{40, 15, 32}, Edit3D.size(s));
    }

    // ---------- curve ----------

    @Test
    void aCurvesPointsStretchWithItsBox() {
        ShapeSettings s = of(Gen.BEZIER3);   // 32 x 16 x 32
        Edit3D.resize(s, new int[]{64, 16, 16}, new boolean[]{true, false, true});
        assertArrayEquals(new int[]{64, 16, 16}, Edit3D.size(s));
        assertArrayEquals(new double[]{4, 2, 1}, s.pts3.get(0), 1e-9);
        assertArrayEquals(new double[]{60, 2, 15}, s.pts3.get(3), 1e-9);
    }

    @Test
    void draggingAPointOutOfTheBoxGrowsIt() {
        ShapeSettings s = of(Gen.BEZIER3);
        int[] shift = Edit3D.movePoint(s, 1, new double[]{-2.3, 20.2, 4});
        assertArrayEquals(new int[]{-3, 0, 0}, shift);
        assertArrayEquals(new int[]{35, 21, 32}, Edit3D.size(s));
        assertArrayEquals(new double[]{0.7, 20.2, 4}, s.pts3.get(1), 1e-9);
        assertArrayEquals(new double[]{5, 2, 2}, s.pts3.get(0), 1e-9, "the others stay where they were in the world");

        s.snap = true;
        Edit3D.movePoint(s, 2, new double[]{10.3, 3.8, 7.1});
        assertArrayEquals(new double[]{10.5, 4, 7}, s.pts3.get(2), 1e-9);
        assertNull(Edit3D.movePoint(of(Gen.ELLIPSOID), 0, new double[3]));
    }

    @Test
    void aPointStopsAtTheSizeLimit() {
        ShapeSettings s = of(Gen.BEZIER3);
        Edit3D.movePoint(s, 3, new double[]{999, 2, -999});
        assertArrayEquals(new int[]{Shape3.MAX_SIZE, 16, Shape3.MAX_SIZE}, Edit3D.size(s));
        for (double[] p : s.pts3)
            for (int a = 0; a < 3; a++) assertTrue(p[a] >= 0 && p[a] <= Edit3D.size(s)[a]);
    }

    @Test
    void bumpStretchesTheControlPointsAlongAnyAxis() {
        ShapeSettings s = of(Gen.BEZIER3);   // z runs from 2 to 30 in a box 32 deep
        assertArrayEquals(new int[]{0, 0, 0}, Edit3D.bumpPoints(s, 2, 1, 4));
        assertEquals(34, s.pts3.get(3)[2], 1e-9);
        assertEquals(2, s.pts3.get(0)[2], 1e-9, "the far side stays put");
        assertEquals(34, s.b3D);
        assertArrayEquals(new int[]{0, -3, 0}, Edit3D.bumpPoints(s, 1, -1, 5));   // y ran from 2 to 14, in 16
        assertEquals(19, s.b3H);
        assertEquals(0, s.pts3.get(0)[1], 1e-9);
        assertEquals(17, s.pts3.get(1)[1], 1e-9);
        assertNull(Edit3D.bumpPoints(of(Gen.TORUS), 0, 1, 1), "a shape without points resizes its box instead");
    }

    @Test
    void pointsComeAndGoOnACurve() {
        ShapeSettings s = of(Gen.BEZIER3);
        double[] middle = dev.curvegen.core.Bezier3.point(s.pts3, 0.5);
        int added = Edit3D.insertPoint(s, middle, false);
        assertEquals(5, s.pts3.size());
        assertEquals(2, added);
        assertArrayEquals(middle, dev.curvegen.core.Bezier3.point(s.pts3, 0.5), 1e-9, "adding a point leaves the curve alone");

        int copy = Edit3D.duplicatePoint(s, 4, false);
        assertEquals(4, copy, "the last point's copy goes before it");
        assertEquals(6, s.pts3.size());
        double[] a = s.pts3.get(4), b = s.pts3.get(5);
        assertEquals(0.5, Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])), 1e-9);

        assertTrue(Edit3D.removePoint(s, 1, false));
        while (s.pts3.size() > 2) assertTrue(Edit3D.removePoint(s, 0, false));
        assertFalse(Edit3D.removePoint(s, 0, false), "a curve keeps two points");
        while (Edit3D.duplicatePoint(s, 0, false) >= 0) { }
        assertEquals(dev.curvegen.core.Bezier3.MAX_POINTS, s.pts3.size());
        assertEquals(-1, Edit3D.insertPoint(s, middle, false));
    }

    @Test
    void theRayFindsTheCurveAndTheSurface() {
        ShapeSettings s = of(Gen.BEZIER3);
        double[] on = dev.curvegen.core.Bezier3.point(s.pts3, 0.3);
        double[] hit = Edit3D.lookAt(s, new double[]{on[0], on[1] + 20, on[2]}, new double[]{0, -1, 0});
        assertEquals(0, hit[3], 1e-3);
        assertEquals(20, hit[4], 1e-3);
        assertArrayEquals(on, new double[]{hit[0], hit[1], hit[2]}, 1e-2);

        ShapeSettings f = of(Gen.SURFACE);
        hit = Edit3D.lookAt(f, new double[]{12, 40, 17}, new double[]{0, -1, 0});
        assertEquals(0, hit[3], 0.06);
        assertEquals(12, hit[0], 0.06);
        assertEquals(17, hit[2], 0.06);
        assertNull(Edit3D.lookAt(of(Gen.ELLIPSOID), new double[3], new double[]{0, 1, 0}));
    }

    // ---------- surface ----------

    @Test
    void aSurfaceGainsAndLosesWholeRowsAndColumns() {
        ShapeSettings s = of(Gen.SURFACE);   // 4 by 4 points over 30 x 12 x 30; rows run along x and stack along z
        double[] at = dev.curvegen.core.Bezier3.patchPoint(s.sPts, 4, 4, 0.2, 0.7);
        int p = Edit3D.insertPoint(s, at, false);
        assertEquals(5, s.sRows);
        assertEquals(4, s.sCols);
        assertEquals(20, s.sPts.size());
        assertEquals(3 * 4 + 1, p, "the new row nearest v = 0.7, at the column nearest u = 0.2");
        assertArrayEquals(at, dev.curvegen.core.Bezier3.patchPoint(s.sPts, 5, 4, 0.2, 0.7), 1e-9, "the surface keeps its shape");

        p = Edit3D.insertPoint(s, at, true);
        assertEquals(5, s.sCols);
        assertEquals(3 * 5 + 1, p);

        assertTrue(Edit3D.removePoint(s, 7, true));
        assertEquals(4, s.sCols);
        assertTrue(Edit3D.removePoint(s, 7, false));
        assertEquals(4, s.sRows);
        assertArrayEquals(at, dev.curvegen.core.Bezier3.patchPoint(s.sPts, 4, 4, 0.2, 0.7), 1e-9, "taking them out again undoes it");
        assertEquals(4, Edit3D.columns(s));
        assertEquals(0, Edit3D.columns(of(Gen.BEZIER3)));
    }

    @Test
    void middleClickOnASurfaceAddsALineBesideThePoint() {
        ShapeSettings s = of(Gen.SURFACE);
        int p = Edit3D.duplicatePoint(s, 2 * 4 + 3, false);   // row 2, column 3
        assertEquals(5, s.sRows);
        assertEquals(3, p % 4, "the same column");
        assertEquals(3, p / 4, "the new row nearest the old row 2");
        p = Edit3D.duplicatePoint(s, 0, true);                // a corner: the new column is the first inner one
        assertEquals(5, s.sCols);
        assertEquals(1, p);
        while (Edit3D.duplicatePoint(s, 0, false) >= 0) { }
        assertEquals(dev.curvegen.core.Bezier3.MAX_GRID, s.sRows);
        while (Edit3D.removePoint(s, 0, true)) { }
        assertEquals(dev.curvegen.core.Bezier3.MIN_GRID, s.sCols);
    }

    @Test
    void aSurfaceStretchesAndBumpsLikeACurve() {
        ShapeSettings s = of(Gen.SURFACE);
        Edit3D.resize(s, new int[]{60, 12, 30}, new boolean[]{true, false, false});
        assertEquals(60, s.sPts.get(3)[0], 1e-9);
        assertNotNull(Edit3D.bumpPoints(s, 1, 1, 3));
        assertEquals(14, s.sH);
        double[] top = s.sPts.get(5);
        assertEquals(14, top[1], 1e-9);
        assertEquals(18 * 24, Edit3D.curve(s).length / 6);
        assertEquals(64, Edit3D.curve(of(Gen.BEZIER3)).length / 6);
        assertNull(Edit3D.curve(of(Gen.EQUATION3)));
    }

    // ---------- options ----------

    @Test
    void everyShapeOffersItsOwnOptionsAndTheColourFace() {
        for (Gen gen : Gen.values()) {
            if (!ShapeSettings.is3d(gen)) continue;
            ShapeSettings s = of(gen);
            java.util.List<Option> options = Edit3D.options(s);
            assertTrue(options.size() >= 3 && options.size() <= Radial.MAX_WEDGES - 5, gen + ": " + options.size());
            for (Option o : options) assertTrue(o.label().length() <= 13, o.label());
            Option.Cycler face = (Option.Cycler) options.get(options.size() - 1);
            face.set().accept(1);
            assertTrue(s.topColours, gen.toString());
        }
    }

    @Test
    void aSurfacesRowsAndColumnsAreNumbers() {
        ShapeSettings s = of(Gen.SURFACE);
        double[] at = dev.curvegen.core.Bezier3.patchPoint(s.sPts, 4, 4, 0.3, 0.6);
        java.util.List<Option> options = Edit3D.options(s);
        ((Option.Number) options.get(2)).set().accept(6);
        ((Option.Number) options.get(3)).set().accept(5);
        assertEquals(6, s.sRows);
        assertEquals(5, s.sCols);
        assertEquals(30, s.sPts.size());
        assertArrayEquals(at, dev.curvegen.core.Bezier3.patchPoint(s.sPts, 6, 5, 0.3, 0.6), 1e-9);
        Edit3D.setGrid(s, 1, 99);
        assertEquals(2, s.sRows);
        assertEquals(6, s.sCols);
        assertEquals(12, s.sPts.size());
    }

    @Test
    void torusOptionsGoThroughTheRingAndTubeRules() {
        ShapeSettings s = of(Gen.TORUS);
        java.util.List<Option> options = Edit3D.options(s);
        ((Option.Number) options.get(0)).set().accept(31);
        assertArrayEquals(new int[]{31, 9, 31}, Edit3D.size(s));
        ((Option.Number) options.get(1)).set().accept(40);
        assertEquals(31, s.tTube);
        ((Option.Cycler) options.get(2)).set().accept(1);
        assertTrue(s.tHollow);
    }

    @Test
    void optionsThatDontApplySayWhy() {
        ShapeSettings s = of(Gen.EQUATION3);
        Option.Text text = (Option.Text) Edit3D.options(s).get(0);
        assertNull(text.check().apply("x^2 + y^2 + z^2 = 9"));
        assertNotNull(text.check().apply("z = ("));
        assertNull(Edit3D.options(s).get(2).off());
        s.q3Mode = ShapeSettings.Eq3Mode.BELOW;
        assertNotNull(Edit3D.options(s).get(2).off());
        s.src3 = "z < x";
        assertNotNull(Edit3D.options(s).get(1).off());
        assertNotNull(Edit3D.options(of(Gen.ELLIPSOID)).get(1).off(), "a thin ellipsoid has no thickness");
    }
}
