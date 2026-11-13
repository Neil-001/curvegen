package dev.curvegen.core.paint;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.paint.Paint.Fit;
import dev.curvegen.core.paint.Pattern.Cell;
import dev.curvegen.core.paint.Tiling.Strip;
import org.junit.jupiter.api.Test;

class TilingTest {
    /** A 4 × 2 pattern whose palette index at (x, y) is y * 4 + x. */
    private static final Pattern P;
    static {
        Cell[][] rows = new Cell[2][4];
        for (int y = 0; y < 2; y++) for (int x = 0; x < 4; x++) rows[1 - y][x] = Cell.colour(y * 4 + x);
        P = Pattern.of("t", rows);
    }

    private static int at(Tiling t, double u, double v) {
        Cell c = P.cell(t.sample(u, v));
        return c.rgb();
    }

    private static Paint paint() { return new Paint(); }

    @Test
    void repeatTilesBothWaysAtTheChosenSize() {
        Paint p = paint();
        p.size = 2;
        Tiling t = new Tiling(P, p, Fit.REPEAT, new Strip(100, 100, false));
        assertEquals(0, at(t, 0.5, 0.5));
        assertEquals(0, at(t, 1.5, 1.5));
        assertEquals(1, at(t, 2.5, 0.5));
        assertEquals(4, at(t, 0.5, 2.5));
        assertEquals(0, at(t, 8.5, 4.5));   // one whole repeat on
    }

    @Test
    void mirrorAndSlide() {
        Paint p = paint();
        p.mirror = true;
        Tiling t = new Tiling(P, p, Fit.REPEAT, new Strip(100, 100, false));
        assertEquals(3, at(t, 0.5, 0.5));
        p.mirror = false; p.along = 1; p.across = 1;
        t = new Tiling(P, p, Fit.REPEAT, new Strip(100, 100, false));
        assertEquals(0, at(t, 1.5, 1.5));
        assertEquals(7, at(t, 0.5, 0.5));
    }

    @Test
    void repeatAlongStretchesOnceAcross() {
        Tiling t = new Tiling(P, paint(), Fit.REPEAT_ALONG, new Strip(100, 9, false));
        assertEquals(0, at(t, 0.5, 0.5));
        assertEquals(0, at(t, 0.5, 4.4));
        assertEquals(4, at(t, 0.5, 4.6));
        assertEquals(4, at(t, 0.5, 8.9));
        assertEquals(4, at(t, 0.5, 20));    // past the edge, the edge row
        assertEquals(1, at(t, 5.5, 0.5));
    }

    @Test
    void stretchCoversTheStripOnce() {
        Tiling t = new Tiling(P, paint(), Fit.STRETCH, new Strip(40, 10, true));
        assertEquals(0, at(t, 0.5, 0.5));
        assertEquals(3, at(t, 39.5, 0.5));
        assertEquals(7, at(t, 39.5, 9.5));
        assertEquals(2, at(t, 25, 1));
    }

    /** Round a closed strip, the end matches the start everywhere across it. */
    private static void seamless(Tiling t, double length) {
        for (double v = 0.25; v < 10; v += 0.5)
            for (double u = 0.25; u < 3; u += 0.5)
                assertEquals(at(t, u, v), at(t, u + length, v), "at " + u + ", " + v);
    }

    @Test
    void closedStripsTakeAWholeNumberOfRepeats() {
        Paint p = paint();
        p.size = 3;
        Tiling t = new Tiling(P, p, Fit.REPEAT, new Strip(37.3, 10, true));
        seamless(t, 37.3);
        t = new Tiling(P, p, Fit.REPEAT_ALONG, new Strip(37.3, 10, true));
        seamless(t, 37.3);
    }

    @Test
    void aTurnRoundAClosedStripBecomesASpiral() {
        Paint p = paint();
        for (int turn : new int[]{15, 30, 45, 90, 135, 200, 271}) {
            p.turn = turn;
            Tiling t = new Tiling(P, p, Fit.REPEAT, new Strip(53.7, 10, true));
            seamless(t, 53.7);
            assertEquals(turn, t.angle < 0 ? t.angle + 360 : t.angle, 12, "nudged only slightly, " + turn);
            t = new Tiling(P, p, Fit.REPEAT_ALONG, new Strip(53.7, 10, true));
            seamless(t, 53.7);
        }
    }

    @Test
    void aTurnOfAQuarterSwapsTheDirections() {
        Paint p = paint();
        p.turn = 90;
        Tiling t = new Tiling(P, p, Fit.REPEAT, new Strip(100, 100, false));
        // Turned anticlockwise, the pattern's x runs up the strip, and its y the other way along it.
        assertEquals(4, at(t, 0.5, 0.5));
        assertEquals(5, at(t, 0.5, 1.5));
        assertEquals(0, at(t, 1.5, 0.5));
        assertEquals(1, t.quarters());
    }
}
