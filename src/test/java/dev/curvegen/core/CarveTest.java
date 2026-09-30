package dev.curvegen.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Which space "Carve" clears, per generator and shape. */
class CarveTest {
    @Test
    void ellipseCarvesTheHollow() {
        ShapeSettings s = new ShapeSettings();
        s.eMode = ShapeSettings.EllipseMode.THIN;
        Layout l = Layout.of(Solver.run(s));
        assertFalse(l.carve().isEmpty());
        // The centre of the ellipse is carved, and nothing carved is also built.
        Solver.Result r = Solver.run(s);
        assertTrue(r.target().carve[(r.ny() / 2) * r.nx() + r.nx() / 2]);
        for (Layout.Cell c : l.carve()) assertEquals(Pieces.EMPTY, c.piece());

        s.eMode = ShapeSettings.EllipseMode.FILLED;
        assertTrue(Layout.of(Solver.run(s)).carve().isEmpty(), "filled ellipses don't carve");
    }

    @Test
    void equationCarvesTheOtherSide() {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.EQUATION;
        s.src = "y = 9 - x^2/4"; s.xmin = "-6"; s.xmax = "6"; s.ymin = "0"; s.ymax = "9"; s.qW = 24;
        s.qMode = ShapeSettings.EqMode.UNDER;
        Solver.Result r = Solver.run(s);
        boolean[] cv = r.target().carve;
        assertTrue(cv[(r.ny() - 1) * r.nx()], "top-left corner (above the arch) is carved");
        assertFalse(cv[r.nx() / 2], "bottom middle (under the arch) isn't");

        s.qMode = ShapeSettings.EqMode.OVER;
        r = Solver.run(s);
        assertFalse(r.target().carve[(r.ny() - 1) * r.nx()]);
        assertTrue(r.target().carve[r.nx() / 2]);

        s.qMode = ShapeSettings.EqMode.LINE;
        assertNull(Solver.run(s).target().carve, "lines don't carve");
    }

    @Test
    void bezierNeverCarves() {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.BEZIER;
        assertNull(Solver.run(s).target().carve);
        s.bMode = ShapeSettings.BzMode.FILLED;
        assertNull(Solver.run(s).target().carve);
    }
}
