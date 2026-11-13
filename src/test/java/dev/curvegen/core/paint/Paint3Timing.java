package dev.curvegen.core.paint;

import dev.curvegen.core.Shape3;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.Solver3;
import dev.curvegen.core.edit.Orient;
import dev.curvegen.core.paint.Paint.Mapping;
import java.util.Arrays;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * How long repainting a 3D shape takes without solving it again. A repaint after a turn, slide or pattern change
 * reuses the shape's {@link Wrap3}, which only a new solve or another mapping works out again. Take the
 * {@code @Disabled} off and run {@code ./gradlew :fabric:test --tests '*Paint3Timing*' -i} to see the numbers.
 *
 * <p>The target is 10 ms per repaint for a hollow dome 64 blocks across, so scrolling a pattern round it follows the
 * wheel, and 40 ms up to 128 across. Figures from this machine are in the pull request that added this.
 * {@code Painter3Test.repaintingIsQuick} holds a loose bound that a slow machine still meets.
 */
class Paint3Timing {
    /** A hollow half ball, {@code size} across: an ellipsoid shell cut through the middle is what a dome is built as. */
    static ShapeSettings dome(int size) {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.ELLIPSOID; s.e3W = s.e3D = size; s.e3H = size;
        s.paint.source = Paint.Source.PATTERN;
        s.paint.pattern = Patterns.MOSSY_MIX;
        return s;
    }

    static double millis(Solver3.Result r, Wrap3 w, ShapeSettings s, Catalog c) {
        long[] took = new long[20];
        for (int k = 0; k < took.length; k++) {
            s.paint.along++;
            long t = System.nanoTime();
            Painter3.paint(r, w, s.paint, s.fullConnects, c);
            took[k] = System.nanoTime() - t;
        }
        Arrays.sort(took);
        return took[took.length / 2] / 1e6;
    }

    private static void time(String what, ShapeSettings s) {
        Shape3 own = Shape3.of(s);
        Solver3.Result r = Solver3.run(s);
        Orient o = new Orient(Orient.EAST, Orient.UP, Orient.SOUTH);
        for (Mapping m : new Mapping[]{Mapping.WRAP, Mapping.ABOVE}) {
            if (m == Mapping.WRAP && own.wrapping() == null) continue;
            long t = System.nanoTime();
            Wrap3 w = Wrap3.of(r, own, o, m);
            double place = (System.nanoTime() - t) / 1e6;
            millis(r, w, s, TestWorld.catalog());
            System.out.printf("%-40s %-5s %8d blocks: placing %7.1f ms, each repaint %6.2f ms%n", what, m, w.blocks(), place, millis(r, w, s, TestWorld.catalog()));
        }
    }

    @Test
    @Disabled("prints timings; asserts nothing")
    void timings() {
        for (int size : new int[]{64, 128, 256}) time("hollow ball, " + size + " across", dome(size));
        ShapeSettings s = dome(128);
        s.e3Mode = ShapeSettings.EllipseMode.FILLED;
        time("filled ball, 128 across", s);
        s = dome(64);
        s.gen = Gen.TORUS; s.tRing = s.tW = s.tD = 128; s.tTube = s.tH = 32; s.tHollow = true;
        time("hollow torus, 128 across", s);
        s.tHollow = false;
        time("solid torus, 128 across", s);
        s = dome(64);
        s.gen = Gen.BEZIER3; s.b3W = s.b3D = 128; s.b3H = 64; s.b3T = 9;
        s.pts3.clear();
        s.pts3.add(new double[]{0, 0, 0}); s.pts3.add(new double[]{40, 64, 128}); s.pts3.add(new double[]{128, 0, 40}); s.pts3.add(new double[]{128, 64, 128});
        time("3D curve 9 thick, 128 across", s);
        s = dome(64);
        s.gen = Gen.SURFACE; s.sW = s.sD = 128; s.sH = 32; s.sT = 2;
        s.sPts.clear();
        for (int k = 0; k < 16; k++) s.sPts.add(new double[]{k % 4 * 128 / 3.0, (k * 7) % 4 * 8, k / 4 * 128 / 3.0});
        time("surface 2 thick, 128 across", s);
        s = dome(64);
        s.gen = Gen.EQUATION3; s.q3W = 128;
        time("3D equation, 128 across", s);
    }
}
