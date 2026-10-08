package dev.curvegen.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.edit.Orbit;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The menu's 3D preview without the game: which faces a solved shape shows, how they're drawn, and the Count tab's rows. */
class Preview3Test {
    private static final int BG = 0xFF101010;

    private static short[] grid(int nx, int ny, int nz, int... cells) {
        short[] g = new short[nx * ny * nz];
        for (int k = 0; k < cells.length; k += 4) g[(cells[k + 1] * nz + cells[k + 2]) * nx + cells[k]] = (short) cells[k + 3];
        return g;
    }

    private static int[] palette(int rgb) {
        int[] p = new int[Family.values().length];
        java.util.Arrays.fill(p, rgb);
        return p;
    }

    // ---------- the mesh ----------

    @Test
    void aLoneBlockShowsItsSixFaces() {
        Mesh3 m = new Mesh3(grid(1, 1, 1, 0, 0, 0, Pieces3.FULL), 1, 1, 1);
        assertEquals(6, m.faces());
        for (int d = 0; d < 6; d++) {
            assertEquals(1, m.count[d]);
            assertArrayEquals(new float[]{0, 0, 1, 1, Mesh3.SIGN[d] > 0 ? 1 : 0}, java.util.Arrays.copyOf(m.rects[d], 5));
        }
    }

    @Test
    void facesBetweenFullBlocksAreLeftOut() {
        Mesh3 m = new Mesh3(grid(2, 1, 1, 0, 0, 0, Pieces3.FULL, 1, 0, 0, Pieces3.FULL), 2, 1, 1);
        assertEquals(10, m.faces());
        assertEquals(1, m.count[Pieces3.E]);
        assertEquals(1, m.count[Pieces3.W]);
        // A solid 5×5×5 only shows its skin, and the block in the middle none at all.
        short[] solid = new short[125];
        java.util.Arrays.fill(solid, (short) Pieces3.FULL);
        assertEquals(6 * 25, new Mesh3(solid, 5, 5, 5).faces());
    }

    @Test
    void aSlabHidesOnlyTheFaceItFills() {
        // A bottom slab on a full block: the block's top is still seen past nothing, since the slab's bottom fills it.
        Mesh3 m = new Mesh3(grid(1, 2, 1, 0, 0, 0, Pieces3.FULL, 0, 1, 0, Pieces3.SLAB_B), 1, 2, 1);
        assertEquals(5 + 5, m.faces());
        // A top slab leaves a gap, so both faces across it show.
        m = new Mesh3(grid(1, 2, 1, 0, 0, 0, Pieces3.FULL, 0, 1, 0, Pieces3.SLAB_T), 1, 2, 1);
        assertEquals(6 + 6, m.faces());
        float[] up = m.rects[Pieces3.UP];
        assertEquals(1f, up[4]);
        assertEquals(2f, up[9]);
        assertEquals(1.5f, m.rects[Pieces3.DOWN][9]);
    }

    @Test
    void everyStateMeshesWithItsFamilyAndStaysInsideItsBlock() {
        for (int s = 1; s < Pieces3.COUNT; s++) {
            Mesh3 m = new Mesh3(grid(1, 1, 1, 0, 0, 0, s), 1, 1, 1);
            assertEquals(6 * Pieces3.BOXES[s].length, m.faces(), Pieces3.name(s));
            for (int d = 0; d < 6; d++)
                for (int q = 0; q < m.count[d]; q++) {
                    assertEquals(Pieces3.FAMILY[s].ordinal(), m.family[d][q]);
                    for (int k = 0; k < 5; k++) assertTrue(m.rects[d][q * 5 + k] >= 0 && m.rects[d][q * 5 + k] <= 1);
                    assertTrue(m.rects[d][q * 5] < m.rects[d][q * 5 + 2] && m.rects[d][q * 5 + 1] < m.rects[d][q * 5 + 3]);
                }
        }
    }

    @Test
    void aSolvedSphereShowsOnlyItsSkin() {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.ELLIPSOID; s.e3W = s.e3H = s.e3D = 33; s.e3Mode = ShapeSettings.EllipseMode.FILLED;
        Solver3.Result r = Solver3.run(s);
        int blocks = 0;
        for (int p = 1; p < Pieces3.COUNT; p++) blocks += r.counts()[p];
        Mesh3 m = new Mesh3(r);
        assertTrue(m.faces() > 0 && m.faces() < 2 * blocks, m.faces() + " faces for " + blocks + " blocks");
        // The shape is mirrored every way, so opposite directions show the same number of faces.
        assertEquals(m.count[Pieces3.N], m.count[Pieces3.S]);
        assertEquals(m.count[Pieces3.E], m.count[Pieces3.W]);
    }

    // ---------- drawing ----------

    private static Orbit lookingAt(double yaw, double pitch, double scale, int size) {
        Orbit o = new Orbit();
        o.yaw = Math.toRadians(yaw); o.pitch = Math.toRadians(pitch); o.scale = scale;
        o.tx = o.ty = o.tz = 0.5; o.ox = o.oy = size / 2.0;
        return o;
    }

    @Test
    void aBlockSeenFromTheSouthIsASquareOfItsSouthFace() {
        Mesh3 m = new Mesh3(grid(1, 1, 1, 0, 0, 0, Pieces3.FULL), 1, 1, 1);
        Raster3 r = new Raster3(40, 40);
        r.clear(BG);
        r.draw(m, lookingAt(0, 0, 4, 40), palette(0xC86432));     // too small for lines between blocks
        int south = Raster3.shade(0xC86432, 0.82f), n = 0;
        for (int y = 0; y < 40; y++)
            for (int x = 0; x < 40; x++) {
                boolean in = x >= 18 && x < 22 && y >= 18 && y < 22;
                assertEquals(in ? south : BG, r.argb[y * 40 + x], x + ", " + y);
                if (in) { n++; assertEquals(-0.5f, r.depthAt(x, y), 1e-6f); }
            }
        assertEquals(16, n);
        assertEquals(Float.POSITIVE_INFINITY, r.depthAt(0, 0));
    }

    @Test
    void fromAboveAndToOneSideThreeFacesShowEachLitItsOwnWay() {
        Mesh3 m = new Mesh3(grid(1, 1, 1, 0, 0, 0, Pieces3.FULL), 1, 1, 1);
        Raster3 r = new Raster3(64, 64);
        r.clear(BG);
        r.draw(m, lookingAt(45, 30, 4, 64), palette(0xFFFFFF));
        Set<Integer> seen = new HashSet<>();
        for (int c : r.argb) seen.add(c);
        assertEquals(Set.of(BG, Raster3.shade(0xFFFFFF, 1f), Raster3.shade(0xFFFFFF, 0.82f), Raster3.shade(0xFFFFFF, 0.62f)), seen);
    }

    @Test
    void theNearerBlockHidesTheOneBehindWhicheverIsDrawnFirst() {
        // Two blocks in a row running away from a camera in the south: only the southern one's face is seen.
        for (int near : new int[]{Pieces3.FULL, Pieces3.SLAB_B}) {
            short[] g = grid(1, 1, 3, 0, 0, 0, Pieces3.FULL, 0, 0, 2, near);
            Mesh3 m = new Mesh3(g, 1, 1, 3);
            Raster3 r = new Raster3(40, 40);
            r.clear(BG);
            int[] p = palette(0x404040);
            p[Family.SLAB.ordinal()] = 0xFF0000;
            Orbit o = lookingAt(0, 0, 4, 40);
            r.draw(m, o, p);
            // The lower half is the near piece either way. Above a slab, the block behind shows.
            assertEquals(Raster3.shade(p[Pieces3.FAMILY[near].ordinal()], 0.82f), r.argb[21 * 40 + 20]);
            assertEquals(-2.5f, r.depthAt(20, 21), 1e-6f);
            assertEquals(near == Pieces3.FULL ? -2.5f : -0.5f, r.depthAt(20, 18), 1e-6f);
        }
    }

    @Test
    void aSurfaceOfBlocksHasNoHolesAtAnyZoom() {
        short[] g = new short[20 * 20];
        java.util.Arrays.fill(g, (short) Pieces3.FULL);
        Mesh3 m = new Mesh3(g, 20, 1, 20);
        for (double scale : new double[]{0.4, 0.9, 1.3, 3.7, 11}) {
            Raster3 r = new Raster3(64, 64);
            r.clear(BG);
            Orbit o = new Orbit();
            o.yaw = 0.6; o.pitch = 0.9; o.scale = scale; o.tx = 10; o.ty = 1; o.tz = 10; o.ox = o.oy = 32;
            r.draw(m, o, palette(0x808080));
            // Every pixel whose centre lies well inside the top face's outline is drawn.
            for (int y = 0; y < 64; y++)
                for (int x = 0; x < 64; x++) {
                    double[] p = o.unproject(x + 0.5, y + 0.5, 0);
                    double[] f = o.forward();
                    double t = (1 - p[1]) / f[1];
                    double wx = p[0] + t * f[0], wz = p[2] + t * f[2];
                    if (wx > 0.01 && wx < 19.99 && wz > 0.01 && wz < 19.99) assertNotEquals(BG, r.argb[y * 64 + x], "hole at " + x + ", " + y + " at scale " + scale);
                }
        }
    }

    @Test
    void closeUpTheBlocksOfAFlatFaceAreToldApartByLines() {
        short[] g = new short[4];
        java.util.Arrays.fill(g, (short) Pieces3.FULL);
        Mesh3 m = new Mesh3(g, 4, 1, 1);
        Raster3 r = new Raster3(64, 16);
        r.clear(BG);
        Orbit o = new Orbit();
        o.yaw = 0; o.pitch = 0; o.scale = 16; o.tx = 0; o.ty = 0; o.tz = 0; o.ox = 0; o.oy = 16;
        r.draw(m, o, palette(0xFFFFFF));
        int face = Raster3.shade(0xFFFFFF, 0.82f);
        int lines = 0;
        for (int x = 0; x < 64; x++) if (r.argb[8 * 64 + x] != face) { lines++; assertEquals(0, x % 16); }
        assertEquals(4, lines);
    }

    @Test
    void anOutlineIsDrawnInFullInFrontOfTheShapeAndFaintlyBehindIt() {
        Mesh3 m = new Mesh3(grid(1, 1, 1, 0, 0, 0, Pieces3.FULL), 1, 1, 1);
        Raster3 r = new Raster3(40, 40);
        r.clear(BG);
        Orbit o = lookingAt(0, 0, 4, 40);
        r.draw(m, o, palette(0x808080));
        int face = r.argb[20 * 40 + 20];
        r.line(o, -3, 0.5, 2, 4, 0.5, 2, 0xFF4D73, 1);      // in front, right across
        r.line(o, -3, 0.75, -2, 4, 0.75, -2, 0xFF4D73, 1);  // behind
        assertEquals(0xFFFF4D73, r.argb[20 * 40 + 20]);
        assertEquals(0xFFFF4D73, r.argb[20 * 40 + 10]);
        assertEquals(0xFFFF4D73, r.argb[19 * 40 + 10], "beside the block the far line is in full too");
        int behind = r.argb[19 * 40 + 20];
        assertNotEquals(face, behind);
        assertNotEquals(0xFFFF4D73, behind);
        // Drawn again, and thicker so its stamps overlap, a hidden line is no brighter.
        r.line(o, -3, 0.75, -2, 4, 0.75, -2, 0xFF4D73, 3);
        assertEquals(behind, r.argb[19 * 40 + 20]);
        // A line that runs far off the picture is still drawn across it, and one that misses it changes nothing.
        int[] before = r.argb.clone();
        r.line(o, 1e7, 50, 0, 1e7 + 5, 50, 0, 0xFFFFFF, 2);
        assertArrayEquals(before, r.argb);
        r.line(o, -1e6, 2, 5, 1e6, 2, 5, 0x00FF00, 1);
        for (int x = 0; x < 40; x++) assertEquals(0xFF00FF00, r.argb[14 * 40 + x]);
    }

    // ---------- Count rows ----------

    @Test
    void everyStateIsCountedInExactlyOneRowOfItsOwnFamily() {
        int[] rows = new int[Pieces3.COUNT];
        Set<String> names = new HashSet<>();
        for (Count3.Group g : Count3.GROUPS) {
            assertTrue(names.add(g.family() + "/" + g.name()), "two rows called " + g.name());
            boolean hasIcon = false;
            for (int s : g.states()) {
                rows[s]++;
                assertEquals(g.family(), Pieces3.FAMILY[s]);
                assertEquals(g.name(), Count3.name(s));
                hasIcon |= s == g.icon();
            }
            assertTrue(hasIcon);
            assertTrue(g.name().length() <= 26, g.name());
        }
        for (int s = 1; s < Pieces3.COUNT; s++) assertEquals(1, rows[s], Pieces3.name(s));
        assertEquals(0, rows[Pieces3.AIR]);
    }

    @Test
    void rowsAddUpToTheSolversCounts() {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.TORUS;
        s.chain = s.rod = true;
        Solver3.Result r = Solver3.run(s);
        int total = 0, rows = 0;
        for (int p = 1; p < Pieces3.COUNT; p++) total += r.counts()[p];
        for (Count3.Group g : Count3.GROUPS) rows += g.count(r.counts());
        assertEquals(total, rows);
        assertTrue(total > 0);
    }

    @Test
    void rowsAreNamedAsABuilderWouldSayIt() {
        assertEquals("Upright, straight", Count3.name(Pieces3.stair(0, Pieces3.E, Pieces3.STRAIGHT)));
        assertEquals("Upside-down, inner corner", Count3.name(Pieces3.stair(1, Pieces3.N, Pieces3.INNER_RIGHT)));
        assertEquals("Post only", Count3.name(Pieces3.FENCE));
        assertEquals("Corner", Count3.name(Pieces3.PANE + 3));
        assertEquals("Straight", Count3.name(Pieces3.FENCE + 5));
        assertEquals("3 sides", Count3.name(Pieces3.FENCE + 7));
        assertEquals("Straight, no post", Count3.name(Pieces3.wall(1, 0, 2, 0, false)));
        assertEquals("Straight", Count3.name(Pieces3.wall(1, 0, 1, 0, true)));
        assertEquals("Post only", Count3.name(Pieces3.WALL_POST));
        assertEquals("Up and down", Count3.name(Pieces3.CHAIN + 1));
        // A stair's icon is its profile, not its back.
        for (Count3.Group g : Count3.GROUPS)
            if (g.family() == Family.STAIRS && g.name().endsWith("straight")) assertEquals(192, Count3.silhouette(g.icon()));
    }
}
