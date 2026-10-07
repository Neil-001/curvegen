package dev.curvegen.core;

import dev.curvegen.core.edit.Orient;
import dev.curvegen.core.edit.Turned;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static dev.curvegen.core.Pieces3.*;
import static dev.curvegen.core.ShapeSettings.Eq3Mode.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 3D equations, Bézier curves and Bézier surfaces as the volumetric solver builds them. As in {@link Solver3Test},
 * the regression numbers pin what gets built: change them only on purpose.
 */
class Shapes3Test {
    private static final double EPS = 1e-3;

    static ShapeSettings equation(String src, ShapeSettings.Eq3Mode mode, double thickness, int width, String... ranges) {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.EQUATION3; s.src3 = src; s.q3Mode = mode; s.q3T = thickness; s.q3W = width;
        if (ranges.length == 6) { s.x3min = ranges[0]; s.x3max = ranges[1]; s.y3min = ranges[2]; s.y3max = ranges[3]; s.z3min = ranges[4]; s.z3max = ranges[5]; }
        return s;
    }

    private static ShapeSettings cube(String src, ShapeSettings.Eq3Mode mode, double thickness, int width, String from, String to) {
        return equation(src, mode, thickness, width, from, to, from, to, from, to);
    }

    static ShapeSettings curve(int w, int h, int d, double thickness, double... xyz) {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.BEZIER3; s.b3W = w; s.b3H = h; s.b3D = d; s.b3T = thickness;
        if (xyz.length > 0) s.pts3.clear();
        for (int k = 0; k < xyz.length; k += 3) s.pts3.add(new double[]{xyz[k], xyz[k + 1], xyz[k + 2]});
        return s;
    }

    private static ShapeSettings randomCurve(int size, int points, double thickness, long seed) {
        Random r = new Random(seed);
        ShapeSettings s = curve(size, size, size, thickness);
        s.pts3.clear();
        for (int k = 0; k < points; k++) s.pts3.add(new double[]{r.nextDouble() * size, r.nextDouble() * size, r.nextDouble() * size});
        return s;
    }

    /** A patch over a w × d floor with its control points evenly spread and at the given heights, row by row. */
    static ShapeSettings surface(int w, int h, int d, int rows, int cols, double thickness, double... heights) {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.SURFACE; s.sW = w; s.sH = h; s.sD = d; s.sRows = rows; s.sCols = cols; s.sT = thickness;
        s.sPts.clear();
        for (int k = 0; k < rows * cols; k++) s.sPts.add(new double[]{k % cols * (double) w / (cols - 1), heights[k % heights.length], k / cols * (double) d / (rows - 1)});
        return s;
    }

    private static ShapeSettings bumpy(int size, int height, double thickness, long seed) {
        Random r = new Random(seed);
        double[] heights = new double[16];
        for (int k = 0; k < 16; k++) heights[k] = 2 + r.nextDouble() * (height - 4);
        return surface(size, height, size, 4, 4, thickness, heights);
    }

    /** Leaning, sheared and off the block grid. */
    private static ShapeSettings skew(double thickness) {
        ShapeSettings s = bumpy(30, 16, thickness, 5);
        for (int k = 0; k < 16; k++) { double[] p = s.sPts.get(k); p[0] = 3.3 + k % 4 * 6.1 + k / 4 * 2.2; p[2] = 2.7 + k / 4 * 7.3; p[1] = 3 + k % 4 * 2.1 + p[1] / 5; }
        return s;
    }

    /** Bent back over itself, with three blocks between the two sheets. */
    private static ShapeSettings folded(double thickness) {
        ShapeSettings s = surface(20, 12, 20, 2, 4, thickness, 0);
        double[][] bend = {{2, 3}, {26, 3}, {26, 7}, {2, 7}};
        for (int k = 0; k < 8; k++) { s.sPts.get(k)[0] = bend[k % 4][0]; s.sPts.get(k)[1] = bend[k % 4][1]; s.sPts.get(k)[2] = 2 + k / 4 * 16; }
        return s;
    }

    private static double area(ShapeSettings s) {
        int n = 300;
        double[][] g = new double[(n + 1) * (n + 1)][];
        for (int v = 0; v <= n; v++) for (int u = 0; u <= n; u++) g[v * (n + 1) + u] = Bezier3.patchPoint(s.sPts, s.sRows, s.sCols, (double) u / n, (double) v / n);
        double a = 0;
        for (int v = 0; v < n; v++)
            for (int u = 0; u < n; u++) {
                double[] p = g[v * (n + 1) + u], q = g[v * (n + 1) + u + 1], r = g[(v + 1) * (n + 1) + u], t = g[(v + 1) * (n + 1) + u + 1];
                a += triangle(p, q, t) + triangle(p, t, r);
            }
        return a;
    }

    private static double triangle(double[] a, double[] b, double[] c) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        double x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
        return Math.sqrt(x * x + y * y + z * z) / 2;
    }

    private static double length(ShapeSettings s) {
        double l = 0;
        double[] a = Bezier3.point(s.pts3, 0);
        for (int k = 1; k <= 4000; k++) {
            double[] b = Bezier3.point(s.pts3, k / 4000.0);
            l += Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
            a = b;
        }
        return l;
    }

    private static int blocks(Solver3.Result r) { return r.grid().length - r.counts()[AIR]; }

    private static void pinned(String what, ShapeSettings s, double err, double volume, int blocks) {
        Solver3.Result r = Solver3.run(s);
        assertNull(r.shape().error(), what);
        assertTrue(r.sweeps() <= Solver3.MAX_SWEEPS, what + " settles");
        String got = String.format("%s: %.4f, %.4f, %d", what, r.err(), r.volume(), blocks(r));
        assertEquals(err, r.err(), EPS, got);
        assertEquals(volume, r.volume(), EPS, got);
        assertEquals(blocks, blocks(r), got);
    }

    /** The same shape, with every point of a block that holds the surface looked at instead of interpolated. */
    private static Shape3 pointByPoint(Shape3 sh) {
        return new Shape3() {
            @Override public int nx() { return sh.nx(); }
            @Override public int ny() { return sh.ny(); }
            @Override public int nz() { return sh.nz(); }
            @Override public double lo() { return sh.lo(); }
            @Override public double hi() { return sh.hi(); }
            @Override public double field(double x, double y, double z) { return sh.field(x, y, z); }
            @Override public int uniform(int i, int j, int k) { return sh.uniform(i, j, k); }
            @Override public boolean smooth() { return false; }
            @Override public List<double[]> wireframe() { return List.of(); }
        };
    }

    // ---------- regression ----------

    @Test
    void regressionEquations() {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.EQUATION3;
        pinned("default", s, 311.3047, 1242.2344, 2064);
        s.q3Mode = BELOW;
        pinned("default below", s, 166.3477, 7680.0000, 8080);
        s.q3Mode = ABOVE;
        pinned("default above", s, 166.3477, 7680.0000, 8080);
        pinned("sphere", cube("x^2 + y^2 + z^2 = 16", SURFACE, 1.5, 24, "-5", "5"), 331.7793, 1740.7715, 2528);
        pinned("ball", cube("x^2 + y^2 + z^2 < 16", SURFACE, 1, 24, "-5", "5"), 169.8477, 3703.8906, 4128);
        pinned("saddle", equation("z = x y / 3", SURFACE, 0.5, 20, "-3", "3", "-3", "3", "-3", "3"), 146.9873, 256.0186, 576);
        pinned("tangent", cube("z = tan(x)", SURFACE, 1, 32, "-4", "4"), 688.7500, 2603.7500, 4544);
        pinned("steps", cube("z > floor(x)", SURFACE, 1, 32, "-4", "4"), 0.0000, 18432.0000, 18432);
        ShapeSettings all = cube("x^2 + y^2 + z^2 = 16", SURFACE, 1, 24, "-5", "5");
        all.chain = all.rod = true;
        pinned("sphere with every piece", all, 332.3555, 1159.0430, 1928);
    }

    @Test
    void regressionCurves() {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.BEZIER3;
        pinned("default", s, 22.9229, 34.7979, 78);
        s.b3T = 3;
        pinned("default, 3 thick", s, 68.7134, 335.8657, 496);
        s.b3T = 0.5; s.chain = s.rod = true;
        pinned("default, thin, with chains and rods", s, 9.0132, 9.0747, 12);
        pinned("14 points", randomCurve(40, 14, 1.5, 7), 42.7620, 98.5720, 173);
        pinned("straight", curve(12, 6, 6, 2, 1, 3, 3, 11, 3, 3), 6.6777, 35.1426, 48);
    }

    @Test
    void regressionSurfaces() {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.SURFACE;
        pinned("default", s, 219.0579, 959.4568, 1672);
        s.sT = 0.25;
        pinned("default, thin", s, 210.1045, 239.9629, 540);
        s.sT = 3;
        pinned("default, 3 thick", s, 269.6045, 2879.6108, 3724);
        pinned("bumpy", bumpy(24, 14, 1, 3), 124.5295, 597.3811, 1058);
        pinned("skew", skew(1), 109.0015, 424.4878, 774);
        pinned("folded", folded(1), 100.1250, 587.8750, 1088);
        ShapeSettings grid = surface(20, 10, 16, 2, 6, 1.5, 2, 8, 3, 7, 2, 6, 5, 3, 8, 2, 7, 4);
        pinned("2 by 6", grid, 86.9478, 491.8833, 716);
    }

    // ---------- every shape ----------

    private static List<ShapeSettings> awkward() {
        List<ShapeSettings> out = new ArrayList<>();
        for (ShapeSettings.Eq3Mode m : ShapeSettings.Eq3Mode.values()) {
            out.add(equation("z = sin(x) cos(y)", m, 1, 24));
            out.add(cube("z = tan(x)", m, 1, 32, "-4", "4"));
            out.add(equation("z = tan(x)", m, 1, 12, "-4", "4", "-4", "4", "-10", "10"));      // a pole to every other block
            out.add(cube("z = 1/x", m, 1.5, 24, "-4", "4"));
            out.add(cube("z = floor(x)", m, 1, 32, "-4", "4"));
            out.add(equation("z = floor(x)", m, 1, 30, "-4.1", "4", "-4", "4", "-4", "4.2"));  // steps that miss the block grid
            out.add(cube("x^2 + y^2 + z^2 = 16", m, 2, 24, "-5", "5"));
            out.add(cube("max(|x|, |y|, |z|) = 3", m, 1, 20, "-5", "5"));
            out.add(equation("z = sqrt(16 - x^2 - y^2)", m, 1, 24, "-5", "5", "-5", "5", "-1", "5"));   // undefined outside a circle
            out.add(cube("(x^2 + 9/4 y^2 + z^2 - 1)^3 = x^2 z^3 + 9/80 y^2 z^3", m, 1, 24, "-1.5", "1.5"));
        }
        out.add(equation("z = sin(x) cos(y)", SURFACE, 0.25, 24));
        out.add(equation("z = sin(3x) + y", SURFACE, 6, 40, "-4", "4", "-4", "4", "-12", "12"));   // mostly far from the surface
        for (double t : new double[]{0.25, 0.75, 1, 2.5, 7}) {
            out.add(randomCurve(24, 8, t, 11));
            out.add(bumpy(24, 14, t, 3));
            out.add(skew(t));
        }
        out.add(folded(0.5)); out.add(folded(1.5));
        out.add(curve(12, 6, 6, 2, 1, 3, 3, 11, 3, 3));
        return out;
    }

    @Test
    void skippedCellsReallyAreUniform() {
        // No field here keeps to a slope of 1 by itself. Whatever each does instead, a skipped cell must look the same to the solver throughout.
        double[] lattice = new double[125];
        for (ShapeSettings s : awkward()) {
            Shape3 sh = Shape3.of(s);
            String what = PresetData.defaultName(s, s.gen);
            assertNull(sh.error(), what);
            int skipped = 0;
            for (int j = 0; j < sh.ny(); j++)
                for (int k = 0; k < sh.nz(); k++)
                    for (int i = 0; i < sh.nx(); i++) {
                        int run = sh.uniform(i, j, k);
                        for (int q = 0; q < Math.abs(run) && i + q < sh.nx(); q++, skipped++) {
                            sh.lattice(i + q, j, k, lattice);
                            for (double v : lattice) assertEquals(run > 0, v >= sh.lo() && v <= sh.hi(), what + ", cell " + (i + q) + "," + j + "," + k);
                        }
                    }
            assertTrue(skipped > 100, what + " skipped " + skipped);
        }
    }

    @Test
    void theyAllSolveAndSettle() {
        for (ShapeSettings s : awkward()) {
            Solver3.Result r = Solver3.run(s);
            String what = PresetData.defaultName(s, s.gen);
            assertTrue(r.sweeps() <= Solver3.MAX_SWEEPS, what);
            assertTrue(r.err() <= r.volume() + 1e-9, what + ": worse than building nothing");
            int sum = 0;
            for (int c : r.counts()) sum += c;
            assertEquals(r.grid().length, sum);
            Shape3 sh = r.shape();
            assertEquals(sh.nx(), r.nx()); assertEquals(sh.ny(), r.ny()); assertEquals(sh.nz(), r.nz());
        }
    }

    // ---------- equations ----------

    @Test
    void equationVolumesAreRight() {
        // A ball of radius 4 in a box 10 across and 30 blocks wide is 12 blocks in radius.
        double radius = 12;
        Solver3.Result r = Solver3.run(cube("x^2 + y^2 + z^2 < 16", SURFACE, 1, 30, "-5", "5"));
        assertEquals(4.0 / 3 * Math.PI * radius * radius * radius, r.volume(), 8);
        r = Solver3.run(cube("x^2 + y^2 + z^2 > 16", SURFACE, 1, 30, "-5", "5"));
        assertEquals(27000 - 4.0 / 3 * Math.PI * radius * radius * radius, r.volume(), 8);
        for (double t : new double[]{0.25, 1, 2, 4}) {
            r = Solver3.run(cube("x^2 + y^2 + z^2 = 16", SURFACE, t, 30, "-5", "5"));
            double shell = 4.0 / 3 * Math.PI * (Math.pow(radius + t / 2, 3) - Math.pow(radius - t / 2, 3));
            assertEquals(shell, r.volume(), shell * 0.005, "shell " + t + " thick");
        }
        // The thickness is measured across the surface, not up and down: a steep plane is as thick as a level one.
        r = Solver3.run(equation("z = 3x", SURFACE, 1, 20, "-1", "1", "-1", "1", "-3", "3"));
        assertEquals(60, r.ny());
        assertEquals(20 * Math.sqrt(20 * 20 + 60 * 60), r.volume(), 20 * 63.2 * 0.02);
    }

    @Test
    void xRunsEastYNorthAndZUp() {
        ShapeSettings s = cube("z = x", BELOW, 1, 8, "0", "8");
        Solver3.Result r = Solver3.run(s);
        assertEquals(FULL, r.at(7, 3, 4)); assertEquals(AIR, r.at(1, 5, 4));
        r = Solver3.run(cube("z = y", BELOW, 1, 8, "0", "8"));
        // North is towards low z in the world, so the ground rises that way.
        assertEquals(FULL, r.at(4, 3, 0)); assertEquals(AIR, r.at(4, 5, 6));
        for (int x = 0; x < 8; x++) assertEquals(r.at(0, 3, 2), r.at(x, 3, 2));
        // With that, the box isn't a mirror image: x to y to z turns the same way as east to north to up.
        r = Solver3.run(cube("x > 6", SURFACE, 1, 8, "0", "8"));
        assertEquals(128, blocks(r));
        assertEquals(FULL, r.at(7, 0, 0)); assertEquals(AIR, r.at(5, 7, 7));
    }

    @Test
    void theRelationPicksTheSideAndTheSettingOnlyAppliesToEquals() {
        for (ShapeSettings.Eq3Mode setting : ShapeSettings.Eq3Mode.values()) {
            assertEquals(BELOW, ((Shapes3.Equation) Shape3.of(cube("z < x", setting, 1, 8, "0", "8"))).mode());
            assertEquals(BELOW, ((Shapes3.Equation) Shape3.of(cube("z ≤ x", setting, 1, 8, "0", "8"))).mode());
            assertEquals(ABOVE, ((Shapes3.Equation) Shape3.of(cube("z > x", setting, 1, 8, "0", "8"))).mode());
            assertEquals(setting, ((Shapes3.Equation) Shape3.of(cube("z = x", setting, 1, 8, "0", "8"))).mode());
            assertEquals(setting, ((Shapes3.Equation) Shape3.of(cube("x", setting, 1, 8, "0", "8"))).mode(), "a bare expression is z = …");
        }
        Solver3.Result below = Solver3.run(cube("z < x", SURFACE, 1, 8, "0", "8")), above = Solver3.run(cube("z > x", SURFACE, 1, 8, "0", "8"));
        // Half the box each, and both get the points exactly on the surface, which here is a sheet of them.
        assertEquals(258, below.volume(), 1e-9);
        assertEquals(258, above.volume(), 1e-9);
        assertEquals(FULL, below.at(7, 0, 0)); assertEquals(AIR, above.at(7, 0, 0));
        assertEquals(AIR, below.at(0, 7, 0)); assertEquals(FULL, above.at(0, 7, 0));
    }

    @Test
    void sameScaleSetsDepthAndHeightFromTheWidth() {
        ShapeSettings s = equation("z = x", SURFACE, 1, 40, "-2", "2", "0", "3", "-1", "1");
        s.q3D = 7; s.q3H = 9;
        Shape3 sh = Shape3.of(s);
        assertEquals(40, sh.nx()); assertEquals(30, sh.nz()); assertEquals(20, sh.ny());
        assertEquals(7, s.q3D, "building the shape leaves the settings alone");
        s.q3Lock = false;
        sh = Shape3.of(s);
        assertEquals(40, sh.nx()); assertEquals(7, sh.nz()); assertEquals(9, sh.ny());
        // Locked sizes stay within the limits too.
        s = equation("z = x", SURFACE, 1, 200, "0", "1", "0", "1000", "0", "0.001");
        sh = Shape3.of(s);
        assertEquals(Shape3.MAX_SIZE, sh.nz()); assertEquals(1, sh.ny());
        s.q3W = 100000;
        assertEquals(Shape3.MAX_SIZE, Shape3.of(s).nx());
    }

    @Test
    void mistakesComeBackAsAMessage() {
        Map<String, String> wrong = Map.of(
                "z = sin(x", "sin( is missing its closing ).",
                "", "Type an equation, for example z = sin(x) cos(y).",
                "x + z", "Add an = sign, for example x^2 + y^2 + z^2 = 9.",
                "z = w", "\"w\" isn't something I know. Use x, y, z, pi, e or a function name.");
        for (Map.Entry<String, String> e : wrong.entrySet()) {
            ShapeSettings s = equation(e.getKey(), SURFACE, 1, 12);
            Shape3 sh = Shape3.of(s);
            assertEquals(e.getValue(), sh.error());
            Solver3.Result r = Solver3.run(s);
            assertEquals(0, blocks(r));
            assertEquals(sh.nx() * sh.ny() * sh.nz(), r.grid().length);
            assertNull(Solver3.carve(sh));
            assertFalse(sh.wireframe().isEmpty(), "the box still shows");
        }
        assertEquals("The y range must end higher than it starts.", Shape3.of(equation("z = x", SURFACE, 1, 12, "0", "1", "2", "2", "0", "1")).error());
        assertEquals("z to: \"q\" isn't something I know. Use x, y, pi, e or a function name.", Shape3.of(equation("z = x", SURFACE, 1, 12, "0", "1", "0", "2", "0", "q")).error());
        assertEquals("x from must be a number, like 5 or 2pi.", Shape3.of(equation("z = x", SURFACE, 1, 12, "y", "1", "0", "2", "0", "1")).error());
        assertNull(Shape3.of(equation("z = x", SURFACE, 1, 12, "-2pi", "2pi", "0", "e", "-1/2", "sqrt(2)")).error());
    }

    @Test
    void asymptotesAndJumpsAreNotSurface() {
        // tan(x) leaves the box at each asymptote, 6.28 blocks either side of the middle of 32. Below it is solid up to there and empty after.
        Solver3.Result r = Solver3.run(cube("z < tan(x)", SURFACE, 1, 32, "-4", "4"));
        for (int y = 0; y < 28; y++) { assertEquals(FULL, r.at(21, y, 9), "left of the asymptote, level " + y); assertEquals(AIR, r.at(23, 20 + y % 12, 9)); }
        assertEquals(16384, r.volume(), 1);      // half the box, by symmetry
        // As a surface, the two branches pass each other without a wall joining them along the asymptote.
        r = Solver3.run(cube("z = tan(x)", SURFACE, 1, 32, "-4", "4"));
        Solver3.Result one = Solver3.run(cube("z = tan(x)", SURFACE, 1, 32, "-1.5", "1.5"));
        assertTrue(blocks(one) > 500);
        for (int z = 0; z < 32; z++)
            for (int y = 0; y < 32; y++) {
                // Between the branch rising on the left of x = pi/2 and the one arriving on its right, level with the middle, there's air.
                if (y >= 14 && y <= 17) { assertEquals(AIR, r.at(21, y, z)); assertEquals(AIR, r.at(22, y, z)); assertEquals(AIR, r.at(23, y, z)); }
            }
        // 1/x: nothing stands on the plane x = 0 near z = 0, where both branches are far away.
        r = Solver3.run(cube("z = 1/x", SURFACE, 1, 32, "-4", "4"));
        for (int z = 0; z < 32; z++) for (int y = 14; y <= 17; y++) for (int x = 15; x <= 16; x++) assertEquals(AIR, r.at(x, y, z));
        assertNotEquals(AIR, r.at(31, 16, 5));
        // Steps on the block grid are exactly their treads: a slab's thickness above and below each level, and no risers.
        r = Solver3.run(cube("z = floor(x)", SURFACE, 1, 32, "-4", "4"));
        assertEquals(0, r.err(), 1e-9);
        assertEquals(7.5 * 4 * 32, r.volume(), 1e-9);    // seven treads and the bottom half of the eighth, which the box cuts
        assertEquals(AIR, r.at(16, 14, 3)); assertEquals(AIR, r.at(15, 18, 3));
        assertEquals(SLAB_T, r.at(16, 15, 3)); assertEquals(SLAB_B, r.at(16, 16, 3));
        // Filled, they're whole blocks.
        r = Solver3.run(cube("z < floor(x)", SURFACE, 1, 32, "-4", "4"));
        assertEquals(0, r.err(), 1e-9);
        assertEquals(blocks(r), r.counts()[FULL]);
        assertEquals(FULL, r.at(16, 15, 0)); assertEquals(AIR, r.at(15, 15, 0)); assertEquals(FULL, r.at(15, 11, 0));
    }

    @Test
    void anEquationThatTurnsAtItsSurfaceStillHasOne() {
        // x^2 is flat where x is 0, so a slope taken there says the surface is nowhere near. It's half a block off.
        ShapeSettings s = cube("x^2 = 0.16", SURFACE, 1, 9, "-4.5", "4.5");
        Shape3 sh = Shape3.of(s);
        assertEquals(0, sh.uniform(4, 0, 0));
        Solver3.Result r = Solver3.run(s);
        assertEquals(1.8 * 81, r.volume(), 5);      // two walls 0.8 apart, each a block thick, run together
        for (int x = 3; x <= 5; x++) assertNotEquals(AIR, r.at(x, 4, 4));
        // Squared, an equation touches 0 without crossing it. It's the same surface as before.
        assertEquals(81, Solver3.run(cube("x = 0", SURFACE, 1, 9, "-4.5", "4.5")).volume(), 0.5);
        assertEquals(81, Solver3.run(cube("x^2 = 0", SURFACE, 1, 9, "-4.5", "4.5")).volume(), 0.5);
        double ball = Solver3.run(cube("x^2 + y^2 + z^2 = 16", SURFACE, 1, 24, "-5", "5")).volume();
        assertEquals(ball, Solver3.run(cube("(x^2 + y^2 + z^2 - 16)^2 = 0", SURFACE, 1, 24, "-5", "5")).volume(), ball * 0.02);
        // Thin, too: the two sides of a surface that is only touched are told apart, so a thin wall doesn't fall between samples.
        Solver3.Result thin = Solver3.run(cube("x = 0.3", SURFACE, 0.25, 9, "-4.5", "4.5")), touched = Solver3.run(cube("(x - 0.3)^2 = 0", SURFACE, 0.25, 9, "-4.5", "4.5"));
        assertEquals(20.25, thin.volume(), 0.01);
        assertEquals(20.25, touched.volume(), 0.3);
        assertEquals(81, blocks(touched));
        // A pole still isn't one, however the stepping goes.
        assertEquals(0, Solver3.run(cube("1/x^2 = 0", SURFACE, 1, 9, "-4.5", "4.5")).volume(), 1e-9);
    }

    @Test
    void interpolatingAnEquationChangesLittle() {
        List<ShapeSettings> cases = List.of(equation("z = sin(x) cos(y)", SURFACE, 1, 24), equation("z = sin(x) cos(y)", SURFACE, 0.25, 24),
                cube("x^2 + y^2 + z^2 = 16", SURFACE, 2, 24, "-5", "5"), cube("x^2 + y^2 + z^2 = 16", BELOW, 1, 24, "-5", "5"),
                cube("z = tan(x)", ABOVE, 1, 32, "-4", "4"), cube("z = 1/x", BELOW, 1, 24, "-4", "4"),
                equation("z = floor(x)", BELOW, 1, 30, "-4.1", "4", "-4", "4", "-4", "4.2"),
                cube("(x^2 + 9/4 y^2 + z^2 - 1)^3 = x^2 z^3 + 9/80 y^2 z^3", BELOW, 1, 24, "-1.5", "1.5"));
        for (ShapeSettings s : cases) {
            Solver3.Result a = Solver3.run(s), b = Solver3.solve(pointByPoint(Shape3.of(s)), s, () -> false);
            String what = PresetData.defaultName(s, s.gen);
            assertEquals(b.volume(), a.volume(), b.volume() * 0.004, what);
            assertEquals(b.err(), a.err(), b.err() * 0.02, what);
            int differ = 0;
            for (int q = 0; q < a.grid().length; q++) if (a.grid()[q] != b.grid()[q]) differ++;
            assertTrue(differ <= blocks(b) * 0.02, what + ": " + differ + " of " + blocks(b) + " blocks differ");
        }
    }

    @Test
    void carvingAnEquationClearsTheOtherSide() {
        assertNull(Solver3.carve(Shape3.of(cube("z = x", SURFACE, 1, 8, "0", "8"))));
        BitSet below = Solver3.carve(Shape3.of(cube("z < x", SURFACE, 1, 8, "0", "8"))), above = Solver3.carve(Shape3.of(cube("z = x", ABOVE, 1, 8, "0", "8")));
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++) {
                assertEquals(y > x, below.get((y * 8 + 3) * 8 + x), "below, " + x + "," + y);
                assertEquals(y < x, above.get((y * 8 + 3) * 8 + x), "above, " + x + "," + y);
            }
    }

    // ---------- curves ----------

    @Test
    void theTubeIsAsFarFromTheCurveAsItSays() {
        ShapeSettings s = randomCurve(24, 9, 3, 21);
        Shape3 sh = Shape3.of(s);
        assertEquals(2, sh.pad());
        assertEquals(28, sh.nx());
        Random r = new Random(4);
        double[][] on = new double[20001][];
        for (int k = 0; k <= 20000; k++) on[k] = Bezier3.point(s.pts3, k / 20000.0);
        for (int trial = 0; trial < 400; trial++) {
            double[] c = on[r.nextInt(on.length)];
            double x = c[0] + r.nextGaussian() * 1.5, y = c[1] + r.nextGaussian() * 1.5, z = c[2] + r.nextGaussian() * 1.5, best = Double.MAX_VALUE;
            for (double[] p : on) best = Math.min(best, (p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y) + (p[2] - z) * (p[2] - z));
            // The shape's own coordinates include the room it adds around the box, and it stops counting 2 blocks beyond the tube.
            assertEquals(Math.min(Math.sqrt(best), 3.5), sh.field(x + 2, y + 2, z + 2), 0.011, "trial " + trial);
        }
    }

    @Test
    void tubeVolumesAreRight() {
        // A straight tube is a cylinder with half a ball on each end.
        for (double t : new double[]{0.5, 1, 2, 4}) {
            Solver3.Result r = Solver3.run(curve(20, 8, 8, t, 3, 4, 4, 17, 4, 4));
            double rad = t / 2, exact = Math.PI * rad * rad * 14 + 4.0 / 3 * Math.PI * rad * rad * rad;
            assertEquals(exact, r.volume(), exact * 0.05, "straight, " + t + " thick");
        }
        for (double t : new double[]{0.5, 1.5, 3}) {
            ShapeSettings s = randomCurve(30, 6, t, 13);
            Solver3.Result r = Solver3.run(s);
            double rad = t / 2, exact = Math.PI * rad * rad * length(s) + 4.0 / 3 * Math.PI * rad * rad * rad;
            assertEquals(exact, r.volume(), exact * 0.04, "bent, " + t + " thick");
        }
        // Sampled point by point, a tube a block thick or more is a little fatter: interpolating rounds its core off.
        ShapeSettings s = randomCurve(24, 8, 1, 11);
        Solver3.Result a = Solver3.run(s), b = Solver3.solve(pointByPoint(Shape3.of(s)), s, () -> false);
        assertTrue(a.volume() <= b.volume() && a.volume() >= b.volume() * 0.94, a.volume() + " against " + b.volume());
    }

    @Test
    void aCurveNeedsTwoPoints() {
        ShapeSettings s = curve(8, 8, 8, 1, 4, 4, 4);
        assertEquals("A curve needs at least two points.", Shape3.of(s).error());
        assertEquals(0, blocks(Solver3.run(s)));
        // Two points in one place are a ball.
        s = curve(8, 8, 8, 4, 4, 4, 4, 4, 4, 4);
        assertNull(Shape3.of(s).error());
        assertEquals(4.0 / 3 * Math.PI * 8, Solver3.run(s).volume(), 0.5);
        // Whatever sticks out of the box and its margin is cut off.
        s = curve(4, 4, 4, 1, -20, 2, 2, 30, 2, 2);
        Solver3.Result r = Solver3.run(s);
        assertEquals(6, r.nx());
        assertEquals(Math.PI * 0.25 * 6, r.volume(), 0.3);
    }

    // ---------- surfaces ----------

    @Test
    void aFlatPatchIsALayerOfBlocks() {
        // Level, on the block grid and a block thick: nothing but whole blocks, out to the edge and no further.
        ShapeSettings s = surface(12, 4, 12, 4, 4, 1, 2.5);
        Solver3.Result r = Solver3.run(s);
        assertEquals(1, r.shape().pad());
        assertEquals(144, r.counts()[FULL]);
        assertEquals(144, blocks(r));
        assertEquals(0, r.err(), 1e-9);
        for (int z = 0; z < 14; z++) for (int x = 0; x < 14; x++) assertEquals(x >= 1 && x <= 12 && z >= 1 && z <= 12 ? FULL : AIR, r.at(x, 3, z));
        // Half as thick and half a block lower, it's slabs.
        s = surface(12, 4, 12, 3, 5, 0.5, 2.25);
        r = Solver3.run(s);
        assertEquals(144, r.counts()[SLAB_B]);
        assertEquals(0, r.err(), 1e-9);
        // Upright, on a block boundary: a wall one block thick.
        s = surface(6, 8, 6, 2, 2, 1, 0);
        for (int k = 0; k < 4; k++) { double[] p = s.sPts.get(k); p[0] = 2.5; p[1] = k / 2 * 8; p[2] = k % 2 * 6; }
        r = Solver3.run(s);
        assertEquals(48, r.counts()[FULL]);
        assertEquals(0, r.err(), 1e-9);
    }

    @Test
    void patchVolumesAreItsAreaTimesItsThickness() {
        for (double t : new double[]{0.125, 0.25, 0.5, 1, 2}) {
            for (ShapeSettings s : List.of(bumpy(30, 16, t, 7), skew(t), folded(t))) {
                double exact = area(s) * t;
                // Off the block grid, an edge can sit up to an eighth of a block out.
                assertEquals(exact, Solver3.run(s).volume(), exact * 0.012, PresetData.defaultName(s, s.gen));
            }
        }
    }

    @Test
    void thePatchIsAsFarAsItSays() {
        for (ShapeSettings s : List.of(bumpy(30, 16, 2, 7), skew(2), folded(2), surface(20, 10, 16, 2, 6, 2, 2, 8, 3, 7, 2, 6, 5, 3, 8, 2, 7, 4))) {
            Shape3 sh = Shape3.of(s);
            int n = 500;
            double[][] on = new double[(n + 1) * (n + 1)][];
            for (int v = 0; v <= n; v++) for (int u = 0; u <= n; u++) on[v * (n + 1) + u] = Bezier3.patchPoint(s.sPts, s.sRows, s.sCols, (double) u / n, (double) v / n);
            Random r = new Random(6);
            for (int trial = 0; trial < 300; trial++) {
                // Around a point well inside the patch, so the nearest point isn't on an edge.
                double[] c = Bezier3.patchPoint(s.sPts, s.sRows, s.sCols, 0.15 + 0.7 * r.nextDouble(), 0.15 + 0.7 * r.nextDouble());
                double x = c[0] + r.nextGaussian() * 0.8, y = c[1] + r.nextGaussian() * 0.8, z = c[2] + r.nextGaussian() * 0.8, best = Double.MAX_VALUE;
                for (double[] p : on) best = Math.min(best, (p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y) + (p[2] - z) * (p[2] - z));
                // The nearest of those points is up to 0.05 from the truly nearest point of the patch, which only matters close in.
                double far = Math.sqrt(best), near = Math.sqrt(Math.max(0, best - 0.05 * 0.05)), got = Math.abs(sh.field(x + 1, y + 1, z + 1));
                assertTrue(got <= far + 0.01 && got >= near - 0.01, PresetData.defaultName(s, s.gen) + ", trial " + trial + ": " + got + " outside " + near + " to " + far);
            }
        }
    }

    @Test
    void aBlocksSamplesMatchTheFieldPointByPoint() {
        // The patch works a block's samples out from one another. That must give what asking for each alone does.
        double[] fast = new double[125];
        for (ShapeSettings s : List.of(bumpy(24, 14, 1, 3), skew(0.25), folded(1))) {
            Shape3 sh = Shape3.of(s);
            int compared = 0;
            for (int j = 0; j < sh.ny(); j++)
                for (int k = 0; k < sh.nz(); k++)
                    for (int i = 0; i < sh.nx(); i++) {
                        if (sh.uniform(i, j, k) != 0) continue;
                        sh.lattice(i, j, k, fast);
                        for (int q = 0; q < 125; q++) {
                            double slow = sh.field(i + q % 5 / 4.0, j + q / 25 / 4.0, k + q / 5 % 5 / 4.0);
                            // Right on an edge the two may fall either side of it.
                            if (Double.isNaN(slow) || Double.isNaN(fast[q])) continue;
                            assertEquals(slow, fast[q], 5e-3, "cell " + i + "," + j + "," + k + ", sample " + q);
                            compared++;
                        }
                    }
            assertTrue(compared > 10000);
        }
    }

    @Test
    void bothSheetsOfAFoldAreBuilt() {
        Solver3.Result r = Solver3.run(folded(1));
        // The sheets lie at heights 3 and 7 on the way out and back (4 and 8 with the margin), three blocks apart.
        int lower = 0, upper = 0, between = 0;
        for (int z = 4; z < 17; z++)
            for (int x = 4; x < 12; x++) {
                if (r.at(x, 3, z) != AIR || r.at(x, 4, z) != AIR) lower++;
                if (r.at(x, 7, z) != AIR || r.at(x, 8, z) != AIR) upper++;
                if (r.at(x, 5, z) != AIR && r.at(x, 6, z) != AIR) between++;
            }
        assertEquals(104, lower); assertEquals(104, upper); assertEquals(0, between);
    }

    @Test
    void aHairpinKeepsBothItsArms() {
        // A sheet bent double within a block or two: every point on it is on it, whichever arm is nearer the bucket's middle.
        ShapeSettings s = surface(18, 22, 4, 2, 3, 1, 0);
        double[][] bend = {{9, 9}, {13, 19}, {7, 10}};
        for (int k = 0; k < 6; k++) { s.sPts.get(k)[0] = bend[k % 3][0]; s.sPts.get(k)[1] = bend[k % 3][1]; s.sPts.get(k)[2] = k / 3 * 4; }
        Shape3 sh = Shape3.of(s);
        for (int a = 0; a <= 200; a++)
            for (int b = 1; b < 8; b++) {
                double[] p = Bezier3.patchPoint(s.sPts, 2, 3, a / 200.0, b / 8.0);
                assertEquals(0, sh.field(p[0] + 1, p[1] + 1, p[2] + 1), 0.02, "at u " + a / 200.0 + ", v " + b / 8.0);
            }
        double exact = area(s);
        assertEquals(exact, Solver3.run(s).volume(), exact * 0.06);
    }

    @Test
    void anotherHairpinKeepsBothItsArms() {
        ShapeSettings s = surface(24, 24, 12, 2, 3, 1, 0);
        double[][] bend = {{17, 22}, {10, 4}, {12, 11}};
        for (int k = 0; k < 6; k++) { s.sPts.get(k)[0] = bend[k % 3][0]; s.sPts.get(k)[1] = bend[k % 3][1]; s.sPts.get(k)[2] = k / 3 * 12; }
        Shape3 sh = Shape3.of(s);
        double[] lattice = new double[125];
        for (int a = 0; a <= 400; a++)
            for (int b = 1; b < 12; b++) {
                double[] p = Bezier3.patchPoint(s.sPts, 2, 3, a / 400.0, b / 12.0);
                assertEquals(0, sh.field(p[0] + 1, p[1] + 1, p[2] + 1), 0.02, "at u " + a / 400.0 + ", v " + b / 12.0);
                // And as the solver samples it: the block's sample nearest the point is within its distance of the patch.
                int i = (int) (p[0] + 1), j = (int) (p[1] + 1), k = (int) (p[2] + 1);
                sh.lattice(i, j, k, lattice);
                int qa = (int) Math.round((p[0] + 1 - i) * 4), qb = (int) Math.round((p[1] + 1 - j) * 4), qc = (int) Math.round((p[2] + 1 - k) * 4);
                double v = lattice[(qb * 5 + qc) * 5 + qa];
                if (v == v) assertTrue(Math.abs(v) <= 0.25, "sample by u " + a / 400.0 + ", v " + b / 12.0 + " is " + v);
            }
        // The arms are close enough that their thickness runs together near the bend, so it's a little under area times thickness.
        double exact = area(s), built = Solver3.run(s).volume();
        assertTrue(built > exact * 0.85 && built < exact * 1.01, built + " against " + exact);
    }

    @Test
    void aSurfaceNeedsItsWholeGrid() {
        ShapeSettings s = surface(12, 4, 12, 4, 4, 1, 2.5);
        s.sPts.remove(3);
        assertNotNull(Shape3.of(s).error());
        assertEquals(0, blocks(Solver3.run(s)));
        s = surface(12, 4, 12, 4, 4, 1, 2.5);
        s.sRows = 7;
        assertNotNull(Shape3.of(s).error());
        // A patch squeezed to a line or a point has no area, and builds next to nothing rather than failing.
        s = surface(12, 4, 12, 4, 4, 1, 2.5);
        for (double[] p : s.sPts) p[2] = 6;
        assertTrue(Solver3.run(s).volume() < 30);
        for (double[] p : s.sPts) p[0] = 6;
        assertTrue(Solver3.run(s).volume() < 2);
    }

    // ---------- shared ----------

    @Test
    void turningAShapeKeepsItsOwnWayOfSampling() {
        // The editor solves a turned shape through Turned, which has to pass on how these shapes sample a block.
        Orient[] ways = {new Orient(Orient.SOUTH, Orient.UP, Orient.WEST), new Orient(Orient.EAST, Orient.NORTH, Orient.UP), new Orient(Orient.DOWN, Orient.WEST, Orient.SOUTH)};
        List<ShapeSettings> shapes = List.of(equation("z < floor(x)", SURFACE, 1, 14, "-4.1", "4", "-2", "2", "-4", "4.2"), equation("z = floor(x)", SURFACE, 1, 16, "-2", "2", "-2", "2", "-2", "2"),
                skew(0.5), randomCurve(14, 5, 1.5, 3));
        double[] own = new double[125], world = new double[125];
        for (ShapeSettings s : shapes) {
            Shape3 sh = Shape3.of(s);
            Solver3.Result plain = Solver3.run(s);
            int[] size = {sh.nx(), sh.ny(), sh.nz()};
            for (Orient o : ways) {
                Shape3 t = Turned.of(sh, o);
                assertEquals(sh.inset(), t.inset());
                Solver3.Result r = Solver3.solve(t, s, () -> false);
                String what = PresetData.defaultName(s, s.gen) + " " + o;
                // The same shape, so the same volume, whichever way it lies. (The blocks differ: slabs and stairs know which way is up.)
                assertEquals(plain.volume(), r.volume(), plain.volume() * 1e-3 + 1e-9, what);
                for (int j = 0; j < t.ny(); j += 3)
                    for (int k = 0; k < t.nz(); k += 2)
                        for (int i = 0; i < t.nx(); i += 3) {
                            double[] centre = o.toOwn(new double[]{i + .5, j + .5, k + .5}, size);
                            int[] cell = {(int) centre[0], (int) centre[1], (int) centre[2]};
                            t.lattice(i, j, k, world);
                            sh.lattice(cell[0], cell[1], cell[2], own);
                            double sumWorld = 0, sumOwn = 0;
                            for (int q = 0; q < 125; q++) { if (world[q] == world[q]) sumWorld += world[q]; if (own[q] == own[q]) sumOwn += own[q]; }
                            assertEquals(sumOwn, sumWorld, 1e-9, what);
                            assertEquals(sh.follows(own), t.follows(world), what);
                            // Sample 1 is a quarter of a block along the world's x from the cell's corner.
                            double[] p = o.toOwn(new double[]{i + .25, j + t.inset(), k + t.inset()}, size);
                            double direct = sh.field(p[0], p[1], p[2]);
                            if (direct == direct && world[1] == world[1]) assertEquals(direct, world[1], 5e-3, what);
                        }
            }
        }
    }

    @Test
    void wireframesShowTheShape() {
        // A curve: the curve itself from its first point to its last, then the control polygon.
        ShapeSettings s = randomCurve(20, 6, 3, 2);
        List<double[]> lines = Shape3.of(s).wireframe();
        assertEquals(2, lines.size());
        double[] line = lines.get(0), polygon = lines.get(1);
        assertEquals(18, polygon.length);
        for (int k = 0; k < 6; k++) for (int c = 0; c < 3; c++) assertEquals(s.pts3.get(k)[c] + 2, polygon[k * 3 + c], 1e-9);
        for (int c = 0; c < 3; c++) { assertEquals(polygon[c], line[c], 1e-9); assertEquals(polygon[15 + c], line[line.length - 3 + c], 1e-9); }
        Shape3 tube = Shape3.of(s);
        for (int q = 0; q < line.length; q += 3) assertEquals(0, tube.field(line[q], line[q + 1], line[q + 2]), 1e-6);
        assertTrue(line.length / 3 < 400, "few enough points to draw every frame");
        // A patch: nine lines each way across it, then the rows and columns of the control net.
        s = bumpy(24, 14, 2, 3);
        Shape3 patch = Shape3.of(s);
        lines = patch.wireframe();
        assertEquals(18 + 4 + 4, lines.size());
        assertSame(lines, patch.wireframe(), "worked out once");
        for (int l = 0; l < 18; l++) {
            double[] iso = lines.get(l);
            for (int q = 0; q < iso.length; q += 3) assertEquals(0, patch.field(iso[q], iso[q + 1], iso[q + 2]), 0.02, "line " + l);
        }
        for (int c = 0; c < 3; c++) assertEquals(s.sPts.get(5)[c] + 1, lines.get(19)[3 + c], 1e-9);     // row 1, column 1
        for (int c = 0; c < 3; c++) assertEquals(s.sPts.get(9)[c] + 1, lines.get(23)[6 + c], 1e-9);     // column 1, row 2
        // An equation: the box's twelve edges in six lines, then where the surface cuts slices of the box.
        s = cube("x^2 + y^2 + z^2 = 16", SURFACE, 1, 30, "-5", "5");
        Shape3 ball = Shape3.of(s);
        lines = ball.wireframe();
        assertSame(lines, ball.wireframe());
        assertTrue(lines.size() > 100 && lines.size() < 3000, lines.size() + " lines");
        for (int l = 0; l < lines.size(); l++) {
            double[] p = lines.get(l);
            for (int q = 0; q < p.length; q += 3) {
                assertTrue(p[q] >= 0 && p[q] <= 30 && p[q + 1] >= 0 && p[q + 1] <= 30 && p[q + 2] >= 0 && p[q + 2] <= 30);
                if (l >= 6) assertEquals(12, Math.sqrt((p[q] - 15) * (p[q] - 15) + (p[q + 1] - 15) * (p[q + 1] - 15) + (p[q + 2] - 15) * (p[q + 2] - 15)), 0.05, "line " + l);
            }
        }
        // An asymptote isn't outlined either.
        for (double[] p : Shape3.of(cube("z = tan(x)", SURFACE, 1, 32, "-1.7", "1.7")).wireframe().subList(6, 60))
            assertTrue(Math.abs(Math.tan(-1.7 + p[0] * 3.4 / 32) - (-1.7 + p[1] * 3.4 / 32)) < 0.3);
    }

    @Test
    void sizesAreCapped() {
        ShapeSettings s = curve(1000, 0, 256, 1);
        Shape3 sh = Shape3.of(s);
        assertEquals(258, sh.nx()); assertEquals(3, sh.ny()); assertEquals(258, sh.nz());
        s = surface(999, -3, 256, 2, 2, 100, 1);
        sh = Shape3.of(s);
        assertEquals(25, sh.pad());     // the thickness stops at 50
        assertEquals(306, sh.nx()); assertEquals(51, sh.ny()); assertEquals(306, sh.nz());
        ShapeSettings e = equation("z = x", SURFACE, 1, 999);
        e.q3Lock = false; e.q3D = 0; e.q3H = 300;
        sh = Shape3.of(e);
        assertEquals(256, sh.nx()); assertEquals(256, sh.ny()); assertEquals(1, sh.nz());
        for (ShapeSettings.Gen g : new ShapeSettings.Gen[]{ShapeSettings.Gen.EQUATION3, ShapeSettings.Gen.BEZIER3, ShapeSettings.Gen.SURFACE}) {
            ShapeSettings t = new ShapeSettings();
            t.gen = g;
            assertTrue(t.is3d());
            assertNotNull(Target.build(t).error, "the 2D solver has nothing to say about " + g);
        }
    }

    @Test
    void settingsCopyAndPresetsCarryTheNewShapes() {
        ShapeSettings s = equation("x^2 + y^2 = z + 2pi", BELOW, 2.25, 77, "-3", "3", "-2pi", "2pi", "0", "e");
        s.q3D = 40; s.q3H = 12; s.q3Lock = false;
        s.b3W = 50; s.b3H = 20; s.b3D = 30; s.b3T = 0.75;
        s.pts3.clear();
        for (int k = 0; k < 7; k++) s.pts3.add(new double[]{k * 7.5, 20 - k * 2.25, k % 3 * 10.125});
        s.sW = 44; s.sH = 9; s.sD = 31; s.sT = 1.75; s.sRows = 3; s.sCols = 5;
        s.sPts.clear();
        for (int k = 0; k < 15; k++) s.sPts.add(new double[]{k % 5 * 11, k * 0.5625, k / 5 * 15.5});
        ShapeSettings c = s.copy();
        assertTrue(s.same(c));
        for (ShapeSettings.Gen gen : new ShapeSettings.Gen[]{ShapeSettings.Gen.EQUATION3, ShapeSettings.Gen.BEZIER3, ShapeSettings.Gen.SURFACE}) {
            Map<String, String> saved = PresetData.capture(s, gen);
            assertEquals(saved, PresetData.capture(c, gen));
            ShapeSettings loaded = new ShapeSettings();
            PresetData.apply(saved, gen, loaded);
            assertEquals(gen, loaded.gen);
            assertEquals(saved, PresetData.capture(loaded, gen));
            // The same shape comes back, block for block.
            ShapeSettings again = s.copy();
            again.gen = gen;
            assertArrayEquals(Solver3.run(again).grid(), Solver3.run(loaded).grid(), gen + " shape");
        }
        assertEquals("x^2 + y^2 = z + 2pi, fill below, 77 wide", PresetData.defaultName(s, ShapeSettings.Gen.EQUATION3));
        assertEquals("7-point 3D Bézier 50×20×30, thickness 0.75", PresetData.defaultName(s, ShapeSettings.Gen.BEZIER3));
        assertEquals("Bézier surface 3×5 points, 44×9×31, thickness 1.75", PresetData.defaultName(s, ShapeSettings.Gen.SURFACE));
        s.src3 = "z < x"; s.q3Mode = SURFACE;
        assertEquals("z < x, 77 wide", PresetData.defaultName(s, ShapeSettings.Gen.EQUATION3), "inequalities have no shape");
        assertEquals("z = x, surface 2.25, 77 wide", PresetData.defaultName(equation("z = x", SURFACE, 2.25, 77), ShapeSettings.Gen.EQUATION3));
        assertEquals("equation3-waves", PresetData.fileStem(ShapeSettings.Gen.EQUATION3, "Waves"));
        assertEquals("bezier3-arch", PresetData.fileStem(ShapeSettings.Gen.BEZIER3, "Arch"));
        assertEquals("surface-roof", PresetData.fileStem(ShapeSettings.Gen.SURFACE, "Roof"));
        // Three numbers to a point, as text.
        assertEquals("0,20,0;7.5,17.75,10.125", PresetData.capture(s, ShapeSettings.Gen.BEZIER3).get("points").substring(0, 23));
        assertEquals("3", PresetData.capture(s, ShapeSettings.Gen.SURFACE).get("rows"));
        assertEquals("5", PresetData.capture(s, ShapeSettings.Gen.SURFACE).get("columns"));
    }

    @Test
    void badPresetValuesKeepCurrentSettings() {
        ShapeSettings t = new ShapeSettings();
        // A 2D curve's points aren't a 3D curve's, and one good point isn't a curve.
        PresetData.apply(Map.of("points", "1,2;3,4;5,6", "thickness", "abc", "depth", "-4"), ShapeSettings.Gen.BEZIER3, t);
        assertEquals(4, t.pts3.size());
        assertEquals(1, t.b3T); assertEquals(1, t.b3D);
        PresetData.apply(Map.of("points", "1,2,3;oops;4,5,NaN;6,7,8,9"), ShapeSettings.Gen.BEZIER3, t);
        assertEquals(4, t.pts3.size());
        PresetData.apply(Map.of("points", "1,2,3;oops;4,5,6"), ShapeSettings.Gen.BEZIER3, t);
        assertEquals(2, t.pts3.size());
        assertArrayEquals(new double[]{4, 5, 6}, t.pts3.get(1));
        // A surface takes its grid whole or not at all.
        PresetData.apply(Map.of("rows", "2", "columns", "2", "points", "0,0,0;1,0,0;0,0,1"), ShapeSettings.Gen.SURFACE, t);
        assertEquals(4, t.sRows); assertEquals(16, t.sPts.size());
        PresetData.apply(Map.of("rows", "2", "columns", "9", "points", "0,0,0;1,0,0;0,0,1;1,0,1"), ShapeSettings.Gen.SURFACE, t);
        assertEquals(4, t.sRows);
        PresetData.apply(Map.of("rows", "2", "columns", "2", "points", "0,0,0;1,0,0;0,0,1;1,0,1", "thickness", "900"), ShapeSettings.Gen.SURFACE, t);
        assertEquals(2, t.sRows); assertEquals(2, t.sCols); assertEquals(4, t.sPts.size());
        assertEquals(50, t.sT);
        PresetData.apply(Map.of("shape", "SQUIGGLE", "width", "9999", "sameScale", "false"), ShapeSettings.Gen.EQUATION3, t);
        assertEquals(SURFACE, t.q3Mode); assertEquals(256, t.q3W); assertFalse(t.q3Lock);
        assertEquals("z = sin(x) cos(y)", t.src3);
        // A 2D curve preset still loads as it did, and 3D points don't leak into it.
        PresetData.apply(Map.of("points", "1,2;3,4;5,6"), ShapeSettings.Gen.BEZIER, t);
        assertEquals(3, t.pts.size());
        PresetData.apply(Map.of("points", "1,2,3;4,5,6"), ShapeSettings.Gen.BEZIER, t);
        assertEquals(3, t.pts.size());
    }
}
