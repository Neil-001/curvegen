package dev.curvegen.core.paint;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.Pieces;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.Solver;
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
        // The loop starts at the bottom and runs anticlockwise, so the right-hand side of the circle is a quarter round.
        int nx = r.nx(), mid = r.ny() / 2;
        int c = mid * nx + nx - 3;
        assertEquals(Pieces.FULL, r.grid()[c]);
        assertEquals(strip.length() / 4, w.u[c], 1.5);
        // Across runs from the outside in.
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
        s.gen = Gen.EQUATION;   // y = 2 sin(x) over two whole waves
        Solver.Result r = Solver.run(s);
        Wrap2D w = Wrap2D.of(r, false, 1);
        assertEquals(1, w.strips.size());
        assertFalse(w.strips.get(0).closed());
        // It runs left to right: along grows with x.
        int mid = r.ny() / 2, left = -1, right = -1;
        for (int i = 0; i < r.nx(); i++) if (r.grid()[mid * r.nx() + i] != Pieces.EMPTY) { if (left < 0) left = i; right = i; }
        assertTrue(w.u[mid * r.nx() + left] < w.u[mid * r.nx() + right]);

        s.src = "x^2 + y^2 = 16"; s.xmin = "-5"; s.xmax = "5"; s.ymin = "-5"; s.ymax = "5";
        w = Wrap2D.of(Solver.run(s), false, 1);
        assertEquals(1, w.strips.size());
        assertTrue(w.strips.get(0).closed());
    }
}
