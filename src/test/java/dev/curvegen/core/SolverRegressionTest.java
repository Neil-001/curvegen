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
        double[] withWalls = {8.2813, 8.2031, 18.4844, 15.5000, 18.1094};
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
    void thinEllipsesKeepNoHiddenBlocks() {
        // A full block stays only if it shows, supports a connector beside it or sets the height of a wall below.
        ShapeSettings s = new ShapeSettings();
        for (int w : new int[]{7, 19, 31, 64})
            for (int h : new int[]{5, 13, 19, 40}) {
                s.eW = w; s.eH = h;
                Solver.Result r = Solver.run(s);
                int nx = r.nx(), ny = r.ny();
                for (int j = 1; j < ny - 1; j++)
                    for (int i = 1; i < nx - 1; i++) {
                        if (r.at(i, j) != Pieces.FULL) continue;
                        int below = r.at(i, j - 1), left = r.at(i - 1, j), right = r.at(i + 1, j);
                        boolean needed = Pieces.isConnector(left) || Pieces.isConnector(right) || Pieces.FAMILY[below] == Pieces.Family.WALL;
                        boolean hidden = Pieces.EDGE[r.at(i, j + 1)][0] && Pieces.EDGE[below][1] && Pieces.EDGE[left][3] && Pieces.EDGE[right][2];
                        assertFalse(hidden && !needed, w + "x" + h + " hidden block at " + i + "," + j);
                    }
            }
    }

    @Test
    void thinEllipsesMirrorTopToBottomWithoutFullLookingConnectors() {
        // A low wall fits the default ellipse's top row, but its mirror image in the bottom row would be tall on both
        // sides: it looks like a full block and needs another block above to stay tall. Neither row may use it.
        boolean[][] pieceSets = {   // slab, stair, trap, fence, pane, wall
                {true, true, true, true, true, true}, {false, false, false, false, false, true},
                {false, false, false, false, true, true}, {true, false, true, true, true, true}};
        int walls = 0;
        for (boolean[] set : pieceSets)
            for (boolean fullConnects : new boolean[]{true, false})
                for (int depth : new int[]{1, 3})
                    for (int w = 3; w <= 64; w += 2)
                        for (int h : new int[]{3, 4, 5, 13, 19, 40}) {
                            ShapeSettings s = new ShapeSettings();
                            s.slab = set[0]; s.stair = set[1]; s.trap = set[2]; s.fence = set[3]; s.pane = set[4]; s.wall = set[5];
                            s.fullConnects = fullConnects; s.depth = depth; s.eW = w; s.eH = h;
                            Solver.Result r = Solver.run(s);
                            for (int j = 0; j < r.ny(); j++)
                                for (int i = 0; i < r.nx(); i++) {
                                    int p = r.at(i, j);
                                    String where = w + "x" + h + " depth " + depth + " at " + i + "," + j + ": " + Pieces.NAME[p];
                                    assertFalse(Pieces.isConnector(p) && Pieces.PIXELS[p] == 256, where);
                                    assertEquals(Pieces.FAMILY[p], Pieces.FAMILY[r.at(i, r.ny() - 1 - j)], where);
                                    if (Pieces.FAMILY[p] == Pieces.Family.WALL) walls++;
                                }
                        }
        assertTrue(walls > 1000, "only " + walls + " walls checked");

        Solver.Result r = Solver.run(new ShapeSettings());
        for (int i = 13; i <= 19; i++) {
            assertEquals(Pieces.FULL, r.at(i, 1), "bottom row at " + i);
            assertEquals(Pieces.FULL, r.at(i, r.ny() - 2), "top row at " + i);
            assertEquals(Pieces.EMPTY, r.at(i, 2), "above the bottom row at " + i);
            assertEquals(Pieces.EMPTY, r.at(i, r.ny() - 3), "below the top row at " + i);
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
