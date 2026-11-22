package dev.curvegen.core.paint;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.Pieces;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.Solver;
import dev.curvegen.core.Target;
import dev.curvegen.core.paint.Pattern.Cell;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Wrap2DTest {
    @Test
    void anEllipseIsOneLoopRoundItsCentreLine() {
        ShapeSettings s = new ShapeSettings();
        s.eW = 41; s.eH = 41; s.eMode = ShapeSettings.EllipseMode.MIDDLE; s.eT = 3;
        Solver.Result r = Solver.run(s);
        Wrap2D w = Wrap2D.of(r, false, 1);
        assertTrue(w.line);
        assertEquals(1, w.strips.size());
        Tiling.Strip strip = w.strips.get(0);
        assertTrue(strip.closed());
        assertEquals(Math.PI * 41, strip.length(), 0.5);
        assertEquals(3, strip.width(), 1e-9);
        int nx = r.nx(), mid = r.ny() / 2;
        int c = mid * nx + nx - 3;
        assertEquals(Pieces.FULL, r.grid()[c]);
        assertEquals(strip.length() / 4, w.u[c], 1.5);
        assertTrue(w.v[mid * nx + nx - 2] < w.v[mid * nx + nx - 4]);
    }

    @Test
    void aFilledShapeIsProjected() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.BEZIER; s.bMode = ShapeSettings.BzMode.FILLED;
        Solver.Result r = Solver.run(s);
        Wrap2D w = Wrap2D.of(r, false, 1);
        assertFalse(w.line);
        int i0 = r.nx(), j0 = r.ny();
        for (int c = 0; c < r.nx() * r.ny(); c++)
            if (r.grid()[c] != Pieces.EMPTY) { i0 = Math.min(i0, c % r.nx()); j0 = Math.min(j0, c / r.nx()); }
        for (int j = 0; j < r.ny(); j++)
            for (int i = 0; i < r.nx(); i++) {
                int c = j * r.nx() + i;
                if (r.grid()[c] == Pieces.EMPTY) { assertEquals(-1, w.strip[c]); continue; }
                assertEquals(i - i0 + .5f, w.u[c], 1e-6);
                assertEquals(j - j0 + .5f, w.v[c], 1e-6);
            }
    }

    @Test
    void anEquationsPiecesAreChainedIntoLines() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.EQUATION;
        Solver.Result r = Solver.run(s);
        Wrap2D w = Wrap2D.of(r, false, 1);
        assertEquals(1, w.strips.size());
        assertFalse(w.strips.get(0).closed());
        int mid = r.ny() / 2, left = -1, right = -1;
        for (int i = 0; i < r.nx(); i++) if (r.grid()[mid * r.nx() + i] != Pieces.EMPTY) { if (left < 0) left = i; right = i; }
        assertTrue(w.u[mid * r.nx() + left] < w.u[mid * r.nx() + right]);

        s.src = "x^2 + y^2 = 16"; s.xmin = "-5"; s.xmax = "5"; s.ymin = "-5"; s.ymax = "5";
        w = Wrap2D.of(Solver.run(s), false, 1);
        assertEquals(1, w.strips.size());
        assertTrue(w.strips.get(0).closed());
    }

    @Test
    void aFilledEquationsEdgeIncludesWhereItsRangeCutsItOff() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.EQUATION; s.src = "y = 100"; s.qMode = ShapeSettings.EqMode.UNDER; s.qLock = false;
        s.xmin = "-10"; s.xmax = "10"; s.ymin = "-10"; s.ymax = "10"; s.qW = 20; s.qH = 20;
        Solver.Result r = Solver.run(s);
        Wrap2D w = Wrap2D.of(r, true, 3);
        for (int c = 0; c < r.nx() * r.ny(); c++) if (r.grid()[c] != Pieces.EMPTY) assertTrue(w.strip[c] >= 0);
        assertEquals(80, w.strips.stream().mapToDouble(Tiling.Strip::length).sum(), 1e-6);

        s.src = "y = 0.2";
        w = Wrap2D.of(Solver.run(s), true, 3);
        assertEquals(1, w.strips.size());
        assertTrue(w.strips.get(0).closed());
        assertEquals(60.4, w.strips.get(0).length(), 0.05);

        s.src = "y = 0"; s.qMode = ShapeSettings.EqMode.OVER; s.xmin = "0"; s.xmax = "20"; s.ymin = "0"; s.ymax = "20";
        w = Wrap2D.of(Solver.run(s), true, 3);
        assertEquals(1, w.strips.size());
        assertTrue(w.strips.get(0).closed());
        assertEquals(80, w.strips.get(0).length(), 0.01);
    }

    @Test
    void aFilledBeziersChordIsntCountedTwice() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.BEZIER; s.bMode = ShapeSettings.BzMode.FILLED; s.bW = 40; s.bH = 24;
        s.pts.clear();
        s.pts.add(new double[]{0, 0}); s.pts.add(new double[]{0, 24}); s.pts.add(new double[]{40, 24}); s.pts.add(new double[]{40, 0});
        Wrap2D w = Wrap2D.of(Solver.run(s), true, 3);
        assertEquals(1, w.strips.size());
        assertTrue(w.strips.get(0).closed());
    }

    @Test
    void cellsInsideAFilledShapeTakeTheNearestSpotOnItsOutline() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.BEZIER; s.bMode = ShapeSettings.BzMode.FILLED;
        assertNearestWithinSearchRadius(Solver.run(s));
        s = new ShapeSettings();
        s.gen = Gen.EQUATION; s.src = "min(x^2+y^2-16, (x-3)^2+(y-1)^2-9) = 0"; s.qMode = ShapeSettings.EqMode.UNDER; s.qLock = false;
        s.xmin = "-7"; s.xmax = "7"; s.ymin = "-7"; s.ymax = "7"; s.qW = 70; s.qH = 70;
        assertNearestWithinSearchRadius(Solver.run(s));
        s = new ShapeSettings();
        s.eW = s.eH = 128; s.eMode = ShapeSettings.EllipseMode.FILLED;
        assertNearestWithinSearchRadius(Solver.run(s));
    }

    @Test
    void aWrapKeepsEveryChainOfAFragmentedCurve() {
        int width = 64, height = 33;
        Target target = new Target(width, height);
        target.centre = new ArrayList<>();
        target.centreWidth = 1;
        byte[] grid = new byte[width * height];
        for (int j = 0, c = 0; j < height; j++)
            for (int i = 0; i < width; i++, c++) {
                target.centre.add(new double[]{i + .25, j + .5, i + .75, j + .5});
                grid[c] = Pieces.FULL;
            }
        int[] counts = new int[Pieces.COUNT];
        counts[Pieces.FULL] = grid.length;
        Solver.Result r = new Solver.Result(grid, width, height, 0, grid.length, counts, target, false);
        Wrap2D w = Wrap2D.of(r, false, 1);
        assertEquals(grid.length, w.strips.size());
        for (int c = 0; c < grid.length; c++) assertEquals(c, w.strip[c], "cell " + c);
    }

    @Test
    void aTinyEquationLoopStillGetsPainted() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.EQUATION; s.qMode = ShapeSettings.EqMode.LINE; s.qLock = false;
        s.qW = s.qH = 16; s.xmin = s.ymin = "0"; s.xmax = s.ymax = "16";
        s.src = "(x-8.5)^2+(y-8.5)^2 = .0001";
        s.paint.source = Paint.Source.PATTERN;
        String stone = TestWorld.m("stone_bricks");
        s.paint.pattern = Pattern.of("Stone", new Cell[][]{{Cell.block(stone)}});
        Solver.Result r = Solver.run(s);
        int centre = 8 * r.nx() + 8;
        assertNotEquals(Pieces.EMPTY, r.grid()[centre]);
        Wrap2D w = Wrap2D.of(r, false, 1);
        assertEquals(1, w.strips.size());
        assertTrue(w.strips.getFirst().closed());
        Painted painted = Painter2D.paint(r, 1, s.paint, s.fullConnects, TestWorld.catalog());
        assertTrue(painted.at(centre) >= 0, "the tiny contour should receive the pattern");
    }

    @Test
    void endpointRepairJoinsNearEndsInLargeInputs() {
        List<double[]> segments = new ArrayList<>();
        for (int j = 0; j < 600; j++) {
            segments.add(new double[]{-2, j, -1.02, j});
            segments.add(new double[]{2, j, 1.02, j});
            segments.add(new double[]{-.98, j, .98, j});
        }
        List<Wrap2D.Poly> chains = Wrap2D.Poly.chain(segments, false);
        assertEquals(600, chains.size());
        for (Wrap2D.Poly chain : chains) assertEquals(4, chain.length, 1e-9);
    }

    private static void assertNearestWithinSearchRadius(Solver.Result r) {
        List<double[]> segs = new ArrayList<>(r.target().overlay);
        segs.addAll(r.target().dashed);
        if (r.target().rim != null) segs.add(r.target().rim);
        List<Wrap2D.Poly> polys = Wrap2D.Poly.chain(segs, false);
        Wrap2D.Nearest nearest = new Wrap2D.Nearest(polys);
        int[] best = nearest.find(r.nx(), r.ny(), 1, null, null);
        assertFalse(polys.isEmpty());
        double worst = 0;
        for (int c = 0; c < best.length; c++) {
            double x = c % r.nx() + .5, y = c / r.nx() + .5, min = Double.MAX_VALUE;
            for (Wrap2D.Poly p : polys) for (int k = 0; k < p.segments(); k++) min = Math.min(min, p.distance2(k, x, y));
            double off = Math.sqrt(nearest.distance2(best[c], x, y)) - Math.sqrt(min);
            if (min <= 16 * 16) worst = Math.max(worst, off);
            else assertTrue(off < 1, "deep inside, at most a spot or so off");
        }
        assertEquals(0, worst, 1e-9);
    }
}
