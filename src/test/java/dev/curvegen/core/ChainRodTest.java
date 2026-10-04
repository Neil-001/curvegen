package dev.curvegen.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Chains and end rods: thin lines the solver uses only when asked. */
class ChainRodTest {
    private static boolean isLine(int p) { return Pieces.FAMILY[p] == Pieces.Family.CHAIN || Pieces.FAMILY[p] == Pieces.Family.ROD; }

    /** A line an eighth of a block wide through the middle of a row or column of cells. */
    private static ShapeSettings thinLine(String eq) {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.EQUATION;
        s.src = eq; s.xmin = "0"; s.xmax = "5"; s.ymin = "0"; s.ymax = "5";
        s.qW = 5; s.qLock = false; s.qH = 5; s.qMode = ShapeSettings.EqMode.LINE; s.qLW = 0.125;
        return s;
    }

    @Test
    void offUnlessSwitchedOn() {
        ShapeSettings s = new ShapeSettings();
        assertFalse(s.chain);
        assertFalse(s.rod);
        for (ShapeSettings c : new ShapeSettings[]{s, thinLine("y = 2.5"), thinLine("x = 2.5")})
            for (boolean floor : new boolean[]{false, true}) {
                c.floor = floor;
                for (byte p : Solver.run(c).grid()) assertFalse(isLine(p), Pieces.NAME[p]);
            }
    }

    @Test
    void thinLinesBecomeChains() {
        for (boolean floor : new boolean[]{false, true})
            for (boolean rod : new boolean[]{false, true}) {
                ShapeSettings s = thinLine("y = 2.5");
                s.floor = floor; s.chain = true; s.rod = rod;
                s.pane = false;   // a lone pane looks the same as an upright chain, and the older piece wins a tie
                Solver.Result r = Solver.run(s);
                for (int i = 1; i < 4; i++) assertEquals(Pieces.CHAIN_H, r.at(i, 2), "across, column " + i);
                s.src = "x = 2.5";
                r = Solver.run(s);
                for (int j = 1; j < 4; j++) assertEquals(Pieces.CHAIN_V, r.at(2, j), "up, row " + j);
            }
    }

    @Test
    void rodsStandInWhenChainsAreOff() {
        ShapeSettings s = thinLine("y = 2.5");
        s.rod = true;
        Solver.Result r = Solver.run(s);
        for (int i = 1; i < 4; i++) assertEquals(Pieces.Family.ROD, Pieces.FAMILY[r.at(i, 2)], "column " + i);
    }

    @Test
    void mirrorsAreMirrorImages() {
        for (int p = 0; p < Pieces.COUNT; p++) {
            if (Pieces.isConnector(p)) continue;   // connectors take their shape from neighbours
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    assertEquals(Pieces.MASK[p][y * 16 + x], Pieces.MASK[Pieces.MX[p]][y * 16 + 15 - x], Pieces.NAME[p] + " left to right");
                    assertEquals(Pieces.MASK[p][y * 16 + x], Pieces.MASK[Pieces.MY[p]][(15 - y) * 16 + x], Pieces.NAME[p] + " top to bottom");
                }
        }
    }

    @Test
    void neverConnectOrSupport() {
        for (int p = Pieces.CHAIN_H; p <= Pieces.ROD_R; p++) {
            assertFalse(Pieces.isConnector(p), Pieces.NAME[p]);
            assertFalse(Pieces.isFloorConnector(p), Pieces.NAME[p]);
            assertFalse(Pieces.isType(p), Pieces.NAME[p]);
            for (int face = 0; face < 4; face++) assertFalse(Pieces.STURDY[p][face], Pieces.NAME[p] + " face " + face);
        }
    }

    @Test
    void coverOnlyTheCentreOfAWallBelow() {
        // Upright, a chain or rod standing on a wall covers its post but not its sides, at any depth.
        for (int p : new int[]{Pieces.CHAIN_V, Pieces.ROD_U, Pieces.ROD_D}) {
            assertEquals(0x0180, Pieces.BOTTOM[p] & 0x0180, Pieces.NAME[p]);
            assertNotEquals(0x01FF, Pieces.BOTTOM[p] & 0x01FF, Pieces.NAME[p]);
            assertNotEquals(0xFF80, Pieces.BOTTOM[p] & 0xFF80, Pieces.NAME[p]);
            assertFalse(Pieces.spansDepth(p), Pieces.NAME[p]);
        }
        for (int p : new int[]{Pieces.CHAIN_H, Pieces.ROD_L, Pieces.ROD_R}) assertEquals(0, Pieces.BOTTOM[p], Pieces.NAME[p]);
        for (int p = 0; p < Pieces.CHAIN_H; p++) assertTrue(Pieces.spansDepth(p), Pieces.NAME[p]);

        ShapeSettings s = thinLine("x = 2.5");
        s.chain = true; s.pane = false;
        Layout l = Layout.of(Solver.run(s));
        for (Layout.Cell c : l.cells()) if (c.y() < l.height() - 1) assertEquals(Pieces.CHAIN_V, c.above(), "above row " + c.y());
    }

    @Test
    void ellipsesStaySymmetric() {
        ShapeSettings s = new ShapeSettings();
        s.chain = s.rod = true;
        int lines = 0;
        for (boolean floor : new boolean[]{false, true})
            for (ShapeSettings.EllipseMode m : ShapeSettings.EllipseMode.values())
                for (int w : new int[]{7, 20, 31}) {
                    s.floor = floor; s.eMode = m; s.eW = w; s.eT = 0.3;
                    Solver.Result r = Solver.run(s);
                    for (int j = 0; j < r.ny(); j++)
                        for (int i = 0; i < r.nx(); i++) {
                            int p = r.at(i, j);
                            if (!isLine(p)) continue;
                            lines++;
                            assertEquals(Pieces.MX[p], r.at(r.nx() - 1 - i, j), m + " at " + i + "," + j);
                            assertEquals(Pieces.MY[p], r.at(i, r.ny() - 1 - j), m + " at " + i + "," + j);
                        }
                }
        assertTrue(lines >= 50, "only " + lines + " chains and rods checked");
    }
}
