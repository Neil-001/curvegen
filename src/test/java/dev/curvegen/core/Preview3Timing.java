package dev.curvegen.core;

import dev.curvegen.core.edit.Orbit;
import java.util.Arrays;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * How long the menu's 3D picture takes: building the faces once per solve, and drawing them once per camera move.
 * Nothing is asserted. Take the {@code @Disabled} off and run
 * {@code ./gradlew :fabric:test --tests '*Preview3Timing*' -i} to see the numbers. Drawing happens on the client
 * thread, so it wants to stay well inside a frame.
 */
class Preview3Timing {
    @Test
    @Disabled("prints timings; asserts nothing")
    void timings() {
        for (int size : new int[]{64, 128, 256})
            for (ShapeSettings.EllipseMode mode : new ShapeSettings.EllipseMode[]{ShapeSettings.EllipseMode.THIN, ShapeSettings.EllipseMode.FILLED}) {
                ShapeSettings s = new ShapeSettings();
                s.gen = ShapeSettings.Gen.ELLIPSOID; s.e3W = s.e3H = s.e3D = size; s.e3Mode = mode;
                Solver3.Result r = Solver3.run(s);
                long t = System.nanoTime();
                Mesh3 m = new Mesh3(r);
                double build = (System.nanoTime() - t) / 1e6;
                int[] palette = new int[Pieces.Family.values().length];
                Arrays.fill(palette, 0x808080);
                // A 259 by 144 preview at GUI scale 3, fitted and then zoomed in five times.
                for (double zoom : new double[]{1, 5}) {
                    Raster3 raster = new Raster3(777, 432);
                    Orbit o = new Orbit();
                    o.fit(size, size, size, 0, 0, 777, 432, 18);
                    o.zoom(388, 216, zoom);
                    long[] took = new long[20];
                    for (int k = 0; k < took.length; k++) {
                        o.orbit(3, 1);
                        t = System.nanoTime();
                        raster.clear(0);
                        raster.draw(m, o, palette);
                        took[k] = System.nanoTime() - t;
                    }
                    Arrays.sort(took);
                    System.out.printf("%s sphere %3d: %7d faces built in %6.1f ms, drawn at zoom %.0f in %5.1f ms%n", mode, size, m.faces(), build, zoom, took[took.length / 2] / 1e6);
                }
            }
    }
}
