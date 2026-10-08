package dev.curvegen.core;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.curvegen.core.Pieces3.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The volumetric solver's results. The regression numbers pin what gets built: if a change moves them, make sure
 * that's intended, then update them.
 */
class Solver3Test {
    private static final double EPS = 1e-3;

    private static ShapeSettings ellipsoid(int w, int h, int d, ShapeSettings.EllipseMode m, double t) {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.ELLIPSOID; s.e3W = w; s.e3H = h; s.e3D = d; s.e3Mode = m; s.e3T = t;
        return s;
    }

    private static ShapeSettings torus(int ring, int tube, int w, int h, int d, boolean hollow) {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.TORUS; s.tRing = ring; s.tTube = tube; s.tW = w; s.tH = h; s.tD = d; s.tHollow = hollow;
        return s;
    }

    private static int blocks(Solver3.Result r) { return r.grid().length - r.counts()[AIR]; }

    @Test
    void regressionEllipsoids() {
        // Error, ideal volume and blocks placed, for Thin, Filled, Outwards, Inwards and Middle.
        double[][] expected = {{212.0176, 4848.1426, 1342}, {212.0176, 4848.1426, 5353}, {462.3320, 2388.8711, 3496}, {366.0820, 1795.5391, 2576}, {409.8496, 2081.6465, 3068}};
        ShapeSettings.EllipseMode[] modes = ShapeSettings.EllipseMode.values();
        for (int k = 0; k < modes.length; k++) {
            Solver3.Result r = Solver3.run(ellipsoid(21, 21, 21, modes[k], 1.5));
            assertEquals(expected[k][0], r.err(), EPS, "error, " + modes[k]);
            assertEquals(expected[k][1], r.volume(), EPS, "volume, " + modes[k]);
            assertEquals((int) expected[k][2], blocks(r), "blocks, " + modes[k]);
        }
        Solver3.Result r = Solver3.run(ellipsoid(31, 12, 20, ShapeSettings.EllipseMode.OUTWARDS, 2));
        assertEquals(438.1172, r.err(), EPS);
        assertEquals(3273.6289, r.volume(), EPS);
        assertEquals(4392, blocks(r));
        assertEquals(35, r.nx()); assertEquals(16, r.ny()); assertEquals(24, r.nz());

        ShapeSettings all = ellipsoid(24, 24, 24, ShapeSettings.EllipseMode.FILLED, 1);
        all.chain = all.rod = true;
        r = Solver3.run(all);
        assertEquals(276.2402, r.err(), EPS);
        assertEquals(7237.0137, r.volume(), EPS);
        assertEquals(4.0 / 3 * Math.PI * 12 * 12 * 12, r.volume(), 3);
    }

    @Test
    void regressionToruses() {
        Solver3.Result r = Solver3.run(torus(25, 9, 25, 9, 25, false));
        assertEquals(194.6055, r.err(), EPS);
        assertEquals(3196.8438, r.volume(), EPS);
        assertEquals(2 * Math.PI * Math.PI * 8 * 4.5 * 4.5, r.volume(), 3);   // 2π²Rr²
        assertEquals(AIR, r.at(12, 4, 12));
        assertNotEquals(AIR, r.at(4, 4, 12));

        r = Solver3.run(torus(25, 9, 25, 9, 25, true));
        assertEquals(194.6055, r.err(), EPS);
        assertEquals(1328, blocks(r));

        r = Solver3.run(torus(20, 6, 30, 9, 14, false));
        assertEquals(163.0117, r.err(), EPS);
        assertEquals(1957.1680, r.volume(), EPS);
    }


    @Test
    void solvesAreMirrorSymmetric() {
        for (Shapes3Cases.Case c : Shapes3Cases.all()) {
            if (!c.shape().symX()) continue;
            Solver3.Result r = c.solve();
            int nx = r.nx(), ny = r.ny(), nz = r.nz();
            for (int y = 0; y < ny; y++)
                for (int z = 0; z < nz; z++)
                    for (int x = 0; x < nx; x++) {
                        int s = r.at(x, y, z);
                        String where = c.name() + " at " + x + "," + y + "," + z;
                        assertEquals(MX[s], r.at(nx - 1 - x, y, z), where);
                        assertEquals(MZ[s], r.at(x, y, nz - 1 - z), where);
                        // A wall's heights and post come from the block above, which isn't the same upside down.
                        if (isWall(s)) assertTrue(isWall(r.at(x, ny - 1 - y, z)), where);
                        else if (s != FULL || !c.shape().hollow()) assertEquals(MY[s], r.at(x, ny - 1 - y, z), where);
                    }
        }
    }

    @Test
    void symmetryOnlySavesWork() {
        // Solved as a whole, without the mirroring, the shape and its volume are the same and the error is close.
        for (ShapeSettings s : List.of(ellipsoid(13, 10, 16, ShapeSettings.EllipseMode.MIDDLE, 1.5), torus(18, 6, 18, 6, 18, false))) {
            Shape3 sh = Shape3.of(s);
            Solver3.Result fast = Solver3.run(s);
            Solver3.Result whole = Solver3.solve(Shapes3Cases.shape(sh.nx(), sh.ny(), sh.nz(), sh.lo(), sh.hi(), sh::field), s, () -> false);
            assertEquals(whole.volume(), fast.volume(), 1e-9);
            assertEquals(whole.err(), fast.err(), whole.err() * 0.03);
            assertTrue(whole.err() <= fast.err() + 1e-9, "mirroring can only cost a little");
        }
    }

    @Test
    void thinShapesKeepNoHiddenBlocks() {
        for (ShapeSettings s : List.of(ellipsoid(21, 21, 21, ShapeSettings.EllipseMode.THIN, 1), ellipsoid(30, 9, 17, ShapeSettings.EllipseMode.THIN, 1),
                torus(25, 9, 25, 9, 25, true), torus(24, 10, 30, 12, 20, true))) {
            Solver3.Result thin = Solver3.run(s);
            ShapeSettings f = s.copy();
            f.e3Mode = ShapeSettings.EllipseMode.FILLED; f.tHollow = false;
            Solver3.Result filled = Solver3.run(f);
            int removed = 0;
            for (int y = 0; y < thin.ny(); y++)
                for (int z = 0; z < thin.nz(); z++)
                    for (int x = 0; x < thin.nx(); x++) {
                        int st = filled.at(x, y, z);
                        boolean hidden = st == FULL;
                        int[][] sides = {{0, 0, -1, S}, {1, 0, 0, W}, {0, 0, 1, N}, {-1, 0, 0, E}, {0, -1, 0, UP}, {0, 1, 0, DOWN}};
                        for (int[] d : sides) {
                            int qx = x + d[0], qy = y + d[1], qz = z + d[2];
                            boolean in = qx >= 0 && qy >= 0 && qz >= 0 && qx < thin.nx() && qy < thin.ny() && qz < thin.nz();
                            hidden &= in && FACE[filled.at(qx, qy, qz)][d[3]];
                        }
                        assertEquals(hidden ? AIR : st, thin.at(x, y, z), "at " + x + "," + y + "," + z);
                        if (hidden) removed++;
                    }
            assertTrue(removed > 100, "only " + removed + " removed");
            assertEquals(filled.err(), thin.err(), 1e-9);
        }
    }

    @Test
    void carveClearsWhatTheShapeEncloses() {
        assertNull(Solver3.carve(Shape3.of(ellipsoid(9, 9, 9, ShapeSettings.EllipseMode.FILLED, 1))));
        assertNull(Solver3.carve(Shape3.of(torus(25, 9, 25, 9, 25, false))));
        // A shell 2 thick: outwards it surrounds the whole ellipsoid, inwards a smaller one.
        BitSet out = Solver3.carve(Shape3.of(ellipsoid(11, 11, 11, ShapeSettings.EllipseMode.OUTWARDS, 2)));
        BitSet in = Solver3.carve(Shape3.of(ellipsoid(11, 11, 11, ShapeSettings.EllipseMode.INWARDS, 2)));
        BitSet thin = Solver3.carve(Shape3.of(ellipsoid(11, 11, 11, ShapeSettings.EllipseMode.THIN, 2)));
        int outN = 0, inN = 0;
        for (int y = 0; y < 11; y++)
            for (int z = 0; z < 11; z++)
                for (int x = 0; x < 11; x++) {
                    double d = Math.sqrt((x - 5) * (x - 5) + (y - 5) * (y - 5) + (z - 5) * (z - 5));
                    assertEquals(d < 5.5, out.get(((y + 2) * 15 + z + 2) * 15 + x + 2), "outwards at " + x + "," + y + "," + z);   // padded by 2
                    assertEquals(d < 3.5, in.get((y * 11 + z) * 11 + x), "inwards at " + x + "," + y + "," + z);
                    assertEquals(d < 5.5, thin.get((y * 11 + z) * 11 + x));
                    if (d < 5.5) outN++;
                    if (d < 3.5) inN++;
                }
        assertEquals(outN, out.cardinality());
        assertEquals(inN, in.cardinality());
        BitSet tube = Solver3.carve(Shape3.of(torus(25, 9, 25, 9, 25, true)));
        assertTrue(tube.get((4 * 25 + 12) * 25 + 4));
        assertFalse(tube.get((4 * 25 + 12) * 25 + 12));
    }

    @Test
    void aSheetOfWallsStopsAtAnyCell() {
        // Beside an asymptote the filled side narrows to a sliver, which is walls in columns as tall as the shape, and a
        // pick in one weighs the whole column. So a solve asks whether it's still wanted at every cell, not every so many.
        ShapeSettings s = Shapes3Test.equation("z = tan(x)", ShapeSettings.Eq3Mode.BELOW, 1, 40, "-4", "4", "-4", "4", "-4", "4");
        s.slab = s.stair = s.trap = s.shelf = s.fence = s.pane = false;
        AtomicInteger checks = new AtomicInteger();
        Solver3.Result r = Solver3.solve(Shape3.of(s), s, () -> checks.incrementAndGet() < 0);
        int walls = 0;
        for (int p = WALL; p < COUNT; p++) walls += r.counts()[p];
        assertTrue(walls > 1000, walls + " walls");
        assertTrue(r.sweeps() <= Solver3.MAX_SWEEPS, "refinement didn't settle");
        // Once when a cell is sampled and once in each pass of refinement.
        assertTrue(checks.get() >= 2 * walls, checks.get() + " checks for " + walls + " walls");
        assertNull(Solver3.carve(Shape3.of(ellipsoid(20, 20, 20, ShapeSettings.EllipseMode.THIN, 1)), () -> true));
    }

    @Test
    void aSupersededSolveStops() {
        ShapeSettings s = ellipsoid(20, 20, 20, ShapeSettings.EllipseMode.MIDDLE, 2);
        assertNull(Solver3.solve(Shape3.of(s), s, () -> true));
        AtomicInteger checks = new AtomicInteger();
        assertNotNull(Solver3.solve(Shape3.of(s), s, () -> checks.incrementAndGet() < 0));
        assertTrue(checks.get() > 20, "only " + checks.get() + " checks");
        for (int after = 0; after < checks.get(); after++) {   // through sampling, refinement and the last check before returning
            int limit = after;
            AtomicInteger calls = new AtomicInteger();
            assertNull(Solver3.solve(Shape3.of(s), s, () -> calls.incrementAndGet() > limit), "cancelled after " + after + " checks");
        }
        // Cancelled from inside the sampling of a single layer, with nothing to refine afterwards.
        ShapeSettings plain = new ShapeSettings();
        plain.slab = plain.stair = plain.trap = plain.shelf = plain.fence = plain.pane = plain.wall = false;
        AtomicBoolean stop = new AtomicBoolean();
        Shape3 flat = Shapes3Cases.shape(8, 1, 8, Double.NEGATIVE_INFINITY, 0, (x, y, z) -> { stop.set(true); return Math.hypot(x - 4, z - 4) - 3; });
        assertNull(Solver3.solve(flat, plain, stop::get));
    }

    @Test
    void tinyShapesSurvive() {
        // The fields crease at the middle of an ellipsoid and along a tube's core, so small shapes are sampled point by point.
        ShapeSettings s = ellipsoid(1, 1, 1, ShapeSettings.EllipseMode.FILLED, 1);
        s.slab = s.stair = s.trap = s.shelf = s.fence = s.pane = s.wall = false;
        Solver3.Result r = Solver3.run(s);
        assertEquals(FULL, r.at(0, 0, 0));
        assertEquals(Math.PI / 6, r.volume(), 0.01);
        ShapeSettings t = torus(1, 1, 1, 1, 1, false);
        t.slab = t.stair = t.trap = t.shelf = t.fence = t.pane = t.wall = false;
        assertEquals(FULL, Solver3.run(t).at(0, 0, 0));
        for (int size = 1; size <= 6; size++) {
            r = Solver3.run(ellipsoid(size, size, size, ShapeSettings.EllipseMode.FILLED, 1));
            assertEquals(Math.PI / 6 * size * size * size, r.volume(), 0.01 * size * size * size, "sphere " + size);
            r = Solver3.run(torus(3 * size, size, 3 * size, size, 3 * size, false));
            double minor = size / 2.0, major = size;
            assertEquals(2 * Math.PI * Math.PI * major * minor * minor, r.volume(), 0.02 * r.volume() + 0.01, "torus tube " + size);
        }
    }

    @Test
    void sizesAreCapped() {
        ShapeSettings s = ellipsoid(1000, 0, 256, ShapeSettings.EllipseMode.INWARDS, 1);
        Shape3 sh = Shape3.of(s);
        assertEquals(256, sh.nx()); assertEquals(1, sh.ny()); assertEquals(256, sh.nz());
        sh = Shape3.of(torus(900, 10, 999, -4, 300, false));
        assertEquals(256, sh.nx()); assertEquals(1, sh.ny()); assertEquals(256, sh.nz());
        s.gen = ShapeSettings.Gen.BEZIER;
        assertThrows(IllegalArgumentException.class, () -> Shape3.of(s));
        assertNotNull(Target.build(ellipsoid(5, 5, 5, ShapeSettings.EllipseMode.THIN, 1)).error);
    }

    @Test
    void ellipsoidDistanceIsExact() {
        double[][] axes = {{5, 5, 5}, {10, 4, 6}, {3, 12, 7.5}, {0.5, 8, 2}};
        assertEquals(-0.5, Shapes3.sdEllipsoid(1e-20, 0, 0, .5, .5, .5), 1e-9);
        assertEquals(-0.5, Shapes3.sdEllipsoid(1e-12, -1e-15, 1e-11, .5, 3, 2), 1e-6);
        double[][] points = {{0, 0, 0}, {3, 0, 0}, {0, 2, 0}, {0, 0, 1}, {1, 2, 3}, {9, 0.5, 0}, {12, 7, -4}, {-2.5, 3.25, 0}, {0.25, 0.25, 0.25}, {0, 3, 2}, {20, 0, 0}};
        for (double[] e : axes)
            for (double[] p : points) {
                // Brute force: the nearest of a fine mesh of surface points.
                double best = Double.MAX_VALUE;
                int n = 700;
                for (int a = 0; a <= n; a++) {
                    double th = Math.PI * a / n, st = Math.sin(th), ct = Math.cos(th);
                    for (int b = 0; b < 2 * n; b++) {
                        double ph = Math.PI * b / n;
                        double dx = e[0] * st * Math.cos(ph) - p[0], dy = e[1] * ct - p[1], dz = e[2] * st * Math.sin(ph) - p[2];
                        best = Math.min(best, dx * dx + dy * dy + dz * dz);
                    }
                }
                boolean inside = p[0] * p[0] / (e[0] * e[0]) + p[1] * p[1] / (e[1] * e[1]) + p[2] * p[2] / (e[2] * e[2]) < 1;
                double expected = Math.sqrt(best) * (inside ? -1 : 1);
                assertEquals(expected, Shapes3.sdEllipsoid(p[0], p[1], p[2], e[0], e[1], e[2]), 0.02,
                        "point " + p[0] + "," + p[1] + "," + p[2] + " in " + e[0] + "," + e[1] + "," + e[2]);
            }
    }

    @Test
    void skippedCellsReallyAreUniform() {
        // The shapes' fields must never be steeper than 1 per block, or the solver would skip cells that hold the surface.
        for (ShapeSettings s : List.of(ellipsoid(14, 9, 20, ShapeSettings.EllipseMode.MIDDLE, 1.5), ellipsoid(30, 5, 9, ShapeSettings.EllipseMode.FILLED, 1),
                ellipsoid(8, 8, 8, ShapeSettings.EllipseMode.OUTWARDS, 3), torus(20, 6, 30, 9, 14, false), torus(12, 6, 12, 20, 9, true))) {
            Shape3 sh = Shape3.of(s);
            int skipped = 0;
            for (int j = 0; j < sh.ny(); j++)
                for (int k = 0; k < sh.nz(); k++)
                    for (int i = 0; i < sh.nx(); i++) {
                        int run = sh.uniform(i, j, k);
                        for (int q = 0; q < Math.abs(run) && i + q < sh.nx(); q++, skipped++)
                            for (int c = 0; c < 125; c++) {
                                double v = sh.field(i + q + (c % 5) / 4.0, j + (c / 5 % 5) / 4.0, k + (c / 25) / 4.0);
                                assertEquals(run > 0, v >= sh.lo() && v <= sh.hi(), "cell " + (i + q) + "," + j + "," + k);
                            }
                    }
            assertTrue(skipped > 100);
        }
    }

    @Test
    void interpolatingTheFieldChangesLittle() {
        ShapeSettings s = ellipsoid(13, 9, 11, ShapeSettings.EllipseMode.MIDDLE, 1.25);
        Shape3 sh = Shape3.of(s);
        Shape3 exact = new Shape3() {
            @Override public int nx() { return sh.nx(); }
            @Override public int ny() { return sh.ny(); }
            @Override public int nz() { return sh.nz(); }
            @Override public double lo() { return sh.lo(); }
            @Override public double hi() { return sh.hi(); }
            @Override public double field(double x, double y, double z) { return sh.field(x, y, z); }
            @Override public boolean smooth() { return false; }
            @Override public boolean symX() { return true; }
            @Override public boolean symY() { return true; }
            @Override public boolean symZ() { return true; }
            @Override public List<double[]> wireframe() { return List.of(); }
        };
        Solver3.Result a = Solver3.run(s), b = Solver3.solve(exact, s, () -> false);
        assertEquals(b.volume(), a.volume(), b.volume() * 0.002);
        assertEquals(b.err(), a.err(), b.err() * 0.01);
        int differ = 0;
        for (int q = 0; q < a.grid().length; q++) if (a.grid()[q] != b.grid()[q]) differ++;
        assertTrue(differ < blocks(b) * 0.02, differ + " of " + blocks(b) + " blocks differ");
    }

    @Test
    void linesWinTiesWithAirAndTogglesAreHonoured() {
        ShapeSettings s = new ShapeSettings();
        s.slab = s.stair = s.trap = s.shelf = s.fence = s.pane = s.wall = false;
        Solver3.Result r = Solver3.solve(Shapes3Cases.posts(), s, () -> false);
        for (int st = 2; st < COUNT; st++) assertEquals(0, r.counts()[st], name(st));
        s.chain = s.rod = true;
        r = Solver3.solve(Shapes3Cases.posts(), s, () -> false);
        int lines = 0;
        for (int st = CHAIN; st < FENCE; st++) lines += r.counts()[st];
        assertTrue(lines > 50, lines + " chains and rods");
        assertEquals(0, r.sweeps());   // nothing here depends on a neighbour, so there's nothing to refine
    }

    @Test
    void wireframesStayInTheBox() {
        for (ShapeSettings s : List.of(ellipsoid(21, 9, 14, ShapeSettings.EllipseMode.OUTWARDS, 2), torus(20, 6, 30, 9, 14, false))) {
            Shape3 sh = Shape3.of(s);
            List<double[]> lines = sh.wireframe();
            assertFalse(lines.isEmpty());
            double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9, minZ = 1e9, maxZ = -1e9;
            for (double[] l : lines) {
                assertEquals(0, l.length % 3);
                for (int q = 0; q < l.length; q += 3) {
                    minX = Math.min(minX, l[q]); maxX = Math.max(maxX, l[q]);
                    minY = Math.min(minY, l[q + 1]); maxY = Math.max(maxY, l[q + 1]);
                    minZ = Math.min(minZ, l[q + 2]); maxZ = Math.max(maxZ, l[q + 2]);
                }
            }
            // The lines trace the size the player set, which sits inside the padding.
            int p = sh.pad();
            assertEquals(p, minX, 1e-6); assertEquals(sh.nx() - p, maxX, 1e-6);
            assertEquals(p, minY, 1e-6); assertEquals(sh.ny() - p, maxY, 1e-6);
            assertEquals(p, minZ, 1e-6); assertEquals(sh.nz() - p, maxZ, 1e-6);
        }
    }

    @Test
    void settingsCopyAndPresetsCarryTheNewShapes() {
        ShapeSettings s = ellipsoid(40, 12, 33, ShapeSettings.EllipseMode.INWARDS, 2.5);
        s.tRing = 30; s.tTube = 7; s.tW = 31; s.tH = 8; s.tD = 29; s.tHollow = true;
        ShapeSettings c = s.copy();
        for (ShapeSettings.Gen gen : new ShapeSettings.Gen[]{ShapeSettings.Gen.ELLIPSOID, ShapeSettings.Gen.TORUS}) {
            assertEquals(PresetData.capture(s, gen), PresetData.capture(c, gen));
            ShapeSettings loaded = new ShapeSettings();
            PresetData.apply(PresetData.capture(s, gen), gen, loaded);
            assertEquals(gen, loaded.gen);
            assertTrue(loaded.is3d());
            assertEquals(PresetData.capture(s, gen), PresetData.capture(loaded, gen));
            assertFalse(PresetData.defaultName(s, gen).isEmpty());
        }
        assertEquals(40, c.e3W); assertEquals(ShapeSettings.EllipseMode.INWARDS, c.e3Mode); assertEquals(2.5, c.e3T);
        assertEquals(7, c.tTube); assertTrue(c.tHollow);
        assertFalse(new ShapeSettings().is3d());
    }
}
