package dev.curvegen.core.paint;

import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.Solver;
import java.util.Arrays;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * How long repainting a 2D shape takes, without solving it again: what a paint change costs, and what each turn of
 * the mouse wheel on a paint setting waits for. Take the {@code @Disabled} off and run
 * {@code ./gradlew :fabric:test --tests '*Paint2DTiming*' -i} to see the numbers.
 *
 * <p>The target is 25 ms for any shape up to 200 blocks across, so scrolling a setting follows the wheel: in
 * November 2026 the slowest of those, a filled ellipse painted on its edge 8 deep, took 23 ms, and everything else
 * at that size under 3 ms. At 400 across an outline took 7 ms and the filled ellipse on its edge 90 ms.
 * {@code Painter2DTest.repaintingIsQuick} holds a loose bound that a slow machine still meets.
 */
class Paint2DTiming {
    static double millis(Solver.Result r, ShapeSettings s, Catalog c) {
        long[] took = new long[20];
        for (int k = 0; k < took.length; k++) {
            s.paint.turn = (s.paint.turn + 15) % 360;
            long t = System.nanoTime();
            Painter2D.paint(r, s.depth, s.paint, s.fullConnects, c);
            took[k] = System.nanoTime() - t;
        }
        Arrays.sort(took);
        return took[took.length / 2] / 1e6;
    }

    private static void time(String what, ShapeSettings s) {
        Solver.Result r = Solver.run(s);
        int blocks = 0;
        for (byte b : r.grid()) if (b != 0) blocks++;
        System.out.printf("%-44s %3d x %3d  middle %6.2f ms, %d blocks%n", what, r.nx(), r.ny(), millis(r, s, TestWorld.catalog()), blocks);
    }

    static ShapeSettings painted(ShapeSettings s) {
        s.paint.source = Paint.Source.PATTERN;
        s.paint.pattern = Patterns.MOSSY_MIX;
        return s;
    }

    @Test
    @Disabled("prints timings; asserts nothing")
    void timings() {
        for (int size : new int[]{64, 200, 400}) {
            ShapeSettings s = painted(new ShapeSettings());
            s.eW = s.eH = size;
            time("ellipse outline, " + size + " across", s);
            s.eMode = ShapeSettings.EllipseMode.FILLED;
            time("filled ellipse, " + size + " across", s);
            s.paint.on = Paint.On.EDGE; s.depth = 8;
            time("filled ellipse, " + size + " across, edge, 8 deep", s);
        }
        ShapeSettings s = painted(new ShapeSettings());
        s.gen = Gen.EQUATION; s.qW = 400;
        time("y = 2 sin(x), 400 wide", s);
        s = painted(new ShapeSettings());
        s.gen = Gen.BEZIER; s.bW = 300; s.bH = 200; s.bLW = 9;
        s.pts.clear();
        s.pts.add(new double[]{5, 5}); s.pts.add(new double[]{100, 195}); s.pts.add(new double[]{200, 5}); s.pts.add(new double[]{295, 195});
        time("Bézier line 9 wide, 300 x 200", s);
    }
}
