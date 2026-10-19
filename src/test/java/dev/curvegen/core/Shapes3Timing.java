package dev.curvegen.core;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

/**
 * How long the 3D shapes take to solve. Nothing is asserted, because the answer depends on the machine: take the
 * {@code @Disabled} off and run {@code ./gradlew :fabric:test --tests '*Shapes3Timing*' -i} to see the numbers.
 * The editor solves again on every drag, so these want to stay near the sphere's.
 */
class Shapes3Timing {
    private static void time(String what, ShapeSettings s) {
        long[] took = new long[30];
        Solver3.Result r = null;
        for (int k = 0; k < took.length; k++) {
            long t = System.nanoTime();
            r = Solver3.run(s);
            took[k] = System.nanoTime() - t;
        }
        Arrays.sort(took);
        System.out.printf("%-40s %3d x %3d x %3d  least %6.1f ms, middle %6.1f ms, %d blocks%n", what, r.nx(), r.ny(), r.nz(),
                took[0] / 1e6, took[took.length / 2] / 1e6, r.grid().length - r.counts()[Pieces3.AIR]);
    }

    @Test
    @Disabled("prints timings; asserts nothing")
    void timings() {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.ELLIPSOID; s.e3W = s.e3H = s.e3D = 64;
        time("hollow sphere, 64 across", s);
        Random r = new Random(7);
        for (double t : new double[]{0.5, 1, 2, 4}) {
            ShapeSettings c = Shapes3Test.curve(64, 64, 64, t);
            c.pts3.clear();
            for (int k = 0; k < 14; k++) c.pts3.add(new double[]{r.nextDouble() * 64, r.nextDouble() * 64, r.nextDouble() * 64});
            time("curve, 64 box, 14 points, " + t + " thick", c);
        }
        for (double t : new double[]{0.25, 1, 2, 4}) {
            double[] heights = new double[16];
            for (int k = 0; k < 16; k++) heights[k] = 2 + r.nextDouble() * 20;
            time("patch, 48 across, 4 by 4, " + t + " thick", Shapes3Test.surface(48, 24, 48, 4, 4, t, heights));
        }
        for (ShapeSettings.Eq3Mode m : ShapeSettings.Eq3Mode.values()) {
            time("z = sin(x) cos(y), 48 wide, " + m, Shapes3Test.equation("z = sin(x) cos(y)", m, 1, 48));
            time("x^2 + y^2 + z^2 = 16, 48 wide, " + m, Shapes3Test.equation("x^2 + y^2 + z^2 = 16", m, 1, 48, "-5", "5", "-5", "5", "-5", "5"));
            time("z = tan(x), 48 wide, " + m, Shapes3Test.equation("z = tan(x)", m, 1, 48, "-4", "4", "-4", "4", "-4", "4"));
        }
    }
}
