package dev.curvegen.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the solver's results for the default shapes. If a change moves these numbers, it changed what gets built:
 * make sure that's intended, then update them. (Walls-off numbers also match the web version.)
 */
class SolverRegressionTest {
    private static final double EPS = 1e-3;

    private static double err(ShapeSettings s) { return Solver.run(s).err(); }

    @Test
    void uprightEllipses() {
        ShapeSettings s = new ShapeSettings();
        double[] withWalls = {8.2031, 8.2031, 18.4844, 15.5000, 18.1094};
        double[] withoutWalls = {8.2813, 8.2813, 18.5938, 15.5156, 18.1094};
        ShapeSettings.EllipseMode[] modes = ShapeSettings.EllipseMode.values();
        for (int k = 0; k < modes.length; k++) {
            s.eMode = modes[k];
            s.wall = true;
            assertEquals(withWalls[k], err(s), EPS, "walls on, " + modes[k]);
            s.wall = false;
            assertEquals(withoutWalls[k], err(s), EPS, "walls off, " + modes[k]);
        }
        s.eMode = ShapeSettings.EllipseMode.FILLED;
        assertEquals(462.6563, Solver.run(s).area(), EPS);
    }

    @Test
    void flatEllipses() {
        ShapeSettings s = new ShapeSettings();
        s.floor = true;
        double[] expected = {13.5000, 13.5000, 26.0625, 24.5156, 29.7969};
        ShapeSettings.EllipseMode[] modes = ShapeSettings.EllipseMode.values();
        for (int k = 0; k < modes.length; k++) {
            s.eMode = modes[k];
            assertEquals(expected[k], err(s), EPS, "flat " + modes[k]);
        }
    }

    @Test
    void equationAndBezier() {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.EQUATION;
        Solver.Result r = Solver.run(s);
        assertEquals(48, r.nx());
        assertEquals(23, r.ny());                   // "same scale" height for y = 2sin(x) over ±2π, ±3
        assertEquals(21.3438, r.err(), EPS);

        s.gen = ShapeSettings.Gen.BEZIER;
        assertEquals(12.2695, err(s), EPS);
        s.bMode = ShapeSettings.BzMode.FILLED;
        assertEquals(9.4492, err(s), EPS);
    }

    @Test
    void ellipsesAreMirrorSymmetric() {
        ShapeSettings s = new ShapeSettings();
        for (boolean floor : new boolean[]{false, true})
            for (ShapeSettings.EllipseMode m : ShapeSettings.EllipseMode.values()) {
                s.floor = floor; s.eMode = m;
                Solver.Result r = Solver.run(s);
                for (int j = 0; j < r.ny(); j++)
                    for (int i = 0; i < r.nx(); i++) {
                        int p = r.at(i, j);
                        if (Pieces.isConnector(p)) continue;   // connectors take their shape from neighbours
                        assertEquals(Pieces.MX[p], r.at(r.nx() - 1 - i, j), m + " at " + i + "," + j);
                    }
            }
    }

    @Test
    void asymptotesAndUndefinedRegions() {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.EQUATION;
        // y = tan(x): no false vertical lines at the asymptotes, so the columns there stay (nearly) empty.
        s.src = "y = tan(x)"; s.xmin = "-4"; s.xmax = "4"; s.ymin = "-4"; s.ymax = "4"; s.qW = 32;
        Solver.Result r = Solver.run(s);
        int col = (int) Math.floor((Math.PI / 2 + 4) / 8 * 32);
        int filled = 0;
        for (int j = 0; j < r.ny(); j++) if (r.at(col, j) != Pieces.EMPTY) filled++;
        assertTrue(filled <= 4, "asymptote column has " + filled + " pieces");

        // y = sqrt(x), fill under: nothing where sqrt is undefined (x < 0).
        s.src = "y = sqrt(x)"; s.xmin = "-2"; s.xmax = "6"; s.ymin = "-1"; s.ymax = "3"; s.qMode = ShapeSettings.EqMode.UNDER;
        r = Solver.run(s);
        int negCols = (int) Math.floor(2.0 / 8 * r.nx()) - 1;
        for (int j = 0; j < r.ny(); j++)
            for (int i = 0; i < negCols; i++) assertEquals(Pieces.EMPTY, r.at(i, j), "x < 0 at " + i + "," + j);
    }

    @Test
    void equationErrorsDontCrash() {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.EQUATION;
        s.src = "y = sin(x";
        Solver.Result r = Solver.run(s);
        assertNotNull(r.target().error);
        for (byte b : r.grid()) assertEquals(Pieces.EMPTY, b);
    }
}
