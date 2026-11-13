package dev.curvegen.core.paint;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.Pieces3;
import dev.curvegen.core.Shape3;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.Solver3;
import dev.curvegen.core.edit.Orient;
import dev.curvegen.core.edit.Turned;
import dev.curvegen.core.paint.Paint.Fit;
import dev.curvegen.core.paint.Paint.Mapping;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Painter3Test {
    static final Orient UNTURNED = new Orient(Orient.EAST, Orient.UP, Orient.SOUTH);

    /** Every 3D shape, small, solid and hollow. */
    static List<ShapeSettings> shapes() {
        List<ShapeSettings> out = new ArrayList<>();
        for (ShapeSettings.EllipseMode mode : ShapeSettings.EllipseMode.values()) {
            ShapeSettings s = new ShapeSettings();
            s.gen = Gen.ELLIPSOID; s.e3W = 15; s.e3H = 11; s.e3D = 13; s.e3Mode = mode; s.e3T = 2;
            out.add(s);
        }
        for (boolean hollow : new boolean[]{false, true}) {
            ShapeSettings s = new ShapeSettings();
            s.gen = Gen.TORUS; s.tHollow = hollow;
            out.add(s);
        }
        for (int k = 0; k < 3; k++) {
            ShapeSettings s = new ShapeSettings();
            s.gen = Gen.BEZIER3; s.b3T = 4; s.b3Hollow = k > 0; s.b3Open = k == 1;
            out.add(s);
        }
        ShapeSettings patch = new ShapeSettings();
        patch.gen = Gen.SURFACE; patch.sT = 2;
        out.add(patch);
        for (ShapeSettings.Eq3Mode mode : new ShapeSettings.Eq3Mode[]{ShapeSettings.Eq3Mode.SURFACE, ShapeSettings.Eq3Mode.BELOW}) {
            ShapeSettings s = new ShapeSettings();
            s.gen = Gen.EQUATION3; s.q3Mode = mode; s.q3W = 16;
            out.add(s);
        }
        return out;
    }

    static Paint paint(Pattern p) {
        Paint paint = new Paint();
        paint.source = Paint.Source.PATTERN;
        paint.pattern = p;
        return paint;
    }

    static int blocks(Solver3.Result r) {
        int n = 0;
        for (short c : r.grid()) if (c != Pieces3.AIR) n++;
        return n;
    }

    @Test
    void paintingNeverChangesThePieces() {
        for (ShapeSettings s : shapes()) {
            Solver3.Result plain = Solver3.run(s.copy());
            ShapeSettings withPaint = s.copy();
            withPaint.paint.set(paint(Patterns.BRICK_ROAD));
            withPaint.paint.mapping = Mapping.FRONT;
            assertArrayEquals(plain.grid(), Solver3.run(withPaint).grid(), "the paint settings changed the solve of " + s.gen);
            Shape3 own = Shape3.of(s);
            for (Orient o : new Orient[]{UNTURNED, Orient.facing(Orient.NORTH), Orient.facing(Orient.WEST).tip(Orient.WEST)}) {
                Solver3.Result r = Solver3.solve(Turned.of(own, o), s, () -> false);
                short[] before = r.grid().clone();
                int[] counts = r.counts().clone();
                int blocks = blocks(r);
                for (Mapping m : Mapping.values()) {
                    if (m == Mapping.WRAP && own.wrapping() == null) continue;
                    Wrap3 w = Wrap3.of(r, own, o, m);
                    assertEquals(blocks, w.blocks());
                    for (Fit fit : Fit.values()) {
                        Paint paint = paint(Patterns.MOSSY_MIX);
                        paint.mapping = m; paint.fit = fit; paint.turn = 30; paint.patternOneMaterial = true;
                        Painted out = Painter3.paint(r, w, paint, s.fullConnects, TestWorld.catalog());
                        assertArrayEquals(before, r.grid(), "painting changed the solve");
                        assertArrayEquals(counts, r.counts(), "painting changed the counts");
                        assertEquals(blocks, out.slots().length);
                        assertEquals(blocks, out.total());
                        long counted = 0;
                        for (long[] f : out.counts()) for (long n : f) counted += n;
                        assertEquals(blocks, counted);
                    }
                }
            }
        }
    }

    @Test
    void offPaintsNothing() {
        ShapeSettings s = shapes().getFirst();
        Solver3.Result r = Solver3.run(s);
        assertNull(Painter3.paint(r, Wrap3.of(r, Shape3.of(s), UNTURNED, Mapping.WRAP), new Paint(), true, TestWorld.catalog()));
    }

    @Test
    void anEquationCantWrapAndFallsToFromAbove() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.EQUATION3;
        assertNull(Shape3.of(s).wrapping());
        assertEquals(Mapping.ABOVE, s.paint.mappingFor(false));
        assertEquals(Mapping.WRAP, s.paint.mappingFor(true));
        s.paint.mapping = Mapping.SIDE;
        assertEquals(Mapping.SIDE, s.paint.mappingFor(false));
    }

    private static double[] place(Shape3.Wrapping w, double x, double y, double z) {
        double[] out = new double[2];
        w.place(x, y, z, out);
        return out;
    }

    /** The distance between two places round a loop of length n. */
    private static double round(double a, double b, double n) {
        double d = Math.abs(a - b) % n;
        return Math.min(d, n - d);
    }

    @Test
    void anEllipsoidWrapsRoundItsEquatorFromTheBackAndFromPoleToPole() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.ELLIPSOID; s.e3W = 20; s.e3H = 20; s.e3D = 20;
        Shape3.Wrapping w = Shape3.of(s).wrapping();
        assertEquals(Math.PI * 20, w.length(), 1e-6);
        assertEquals(Math.PI * 10, w.width(), 1e-6);
        assertTrue(w.closedAlong());
        assertFalse(w.closedAcross());
        double L = w.length(), A = w.width();
        // Own z runs away from the player: the back is +z, and from there u runs to the player's left.
        assertEquals(0, round(place(w, 10, 10, 20)[0], 0, L), 1e-9);
        assertEquals(L / 4, place(w, 0, 10, 10)[0], 1e-9);
        assertEquals(L / 2, place(w, 10, 10, 0)[0], 1e-9);
        assertEquals(3 * L / 4, place(w, 20, 10, 10)[0], 1e-9);
        assertEquals(A / 2, place(w, 10, 10, 0)[1], 1e-9);
        assertEquals(A, place(w, 10, 20, 10)[1], 1e-9);
        assertEquals(0, place(w, 10, 0, 10)[1], 1e-9);
        // A point deeper in takes the place of the surface straight out from the middle.
        double[] out = place(w, 13, 17, 4), in = place(w, 11.5, 13.5, 7);
        assertEquals(out[0], in[0], 1e-9);
        assertEquals(out[1], in[1], 1e-9);
    }

    /**
     * Inside a long, thin filled ellipsoid, each block takes the place of its nearest point on the surface, not of the
     * point straight out from the middle, so the pattern runs straight through the thickness.
     */
    @Test
    void insideAnEllipsoidTheNearestSurfaceDecides() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.ELLIPSOID; s.e3W = 60; s.e3H = 20; s.e3D = 20; s.e3Mode = ShapeSettings.EllipseMode.FILLED;
        Shape3.Wrapping w = Shape3.of(s).wrapping();
        // Under the top at x = 50, a point deeper in lies straight below its nearest surface point.
        double[] top = new double[2];
        double x = 50, y = 10 + 10 * Math.sqrt(1 - Math.pow((x - 30) / 30, 2));
        w.place(x, y, 10, top);
        for (double depth = 0.5; depth < 3; depth += 0.5) {
            double nx = (x - 30) / (30 * 30), ny = (y - 10) / (10 * 10), l = Math.hypot(nx, ny);
            double[] in = place(w, x - depth * nx / l, y - depth * ny / l, 10);
            assertEquals(top[0], in[0], 1e-6, "along, " + depth + " in");
            assertEquals(top[1], in[1], 1e-6, "across, " + depth + " in");
        }
    }

    @Test
    void aTorusWrapsRoundItsRingAndRoundItsTubeFromUnderneath() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.TORUS; s.tRing = s.tW = s.tD = 30; s.tTube = s.tH = 10;
        Shape3.Wrapping w = Shape3.of(s).wrapping();
        assertTrue(w.closedAlong());
        assertTrue(w.closedAcross());
        assertEquals(2 * Math.PI * 10, w.length(), 1e-6);   // round the middle of the tube
        assertEquals(2 * Math.PI * 5, w.width(), 1e-6);
        double A = w.width();
        // The tube's middle at the back is (15, 5, 25).
        assertEquals(0, round(place(w, 15, 0, 25)[1], 0, A), 1e-9, "underneath");
        assertEquals(A / 4, place(w, 15, 5, 30)[1], 1e-9, "the outside");
        assertEquals(A / 2, place(w, 15, 10, 25)[1], 1e-9, "on top");
        assertEquals(3 * A / 4, place(w, 15, 5, 20)[1], 1e-9, "the hole");
        assertEquals(0, round(place(w, 15, 5, 30)[0], 0, w.length()), 1e-9, "the ring starts at the back");
    }

    @Test
    void aCurveWrapsAlongItselfWithTheSeamUnderneath() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.BEZIER3; s.b3W = 30; s.b3H = 10; s.b3D = 10; s.b3T = 6;
        s.pts3.clear();
        s.pts3.add(new double[]{0, 5, 5}); s.pts3.add(new double[]{30, 5, 5});
        Shape3 shape = Shape3.of(s);
        int pad = shape.pad();
        Shape3.Wrapping w = shape.wrapping();
        assertEquals(30, w.length(), 1e-6);
        assertEquals(2 * Math.PI * 3, w.width(), 1e-6);
        assertFalse(w.closedAlong());
        assertTrue(w.closedAcross());
        double A = w.width(), y = 5 + pad, z = 5 + pad;
        assertEquals(12, place(w, 12 + pad, y - 2, z)[0], 1e-9);
        assertEquals(0, round(place(w, 12 + pad, y - 2, z)[1], 0, A), 1e-9, "the seam is underneath");
        // Seen from the player's side, -z, the pattern runs to the right along the curve and up round the tube.
        assertEquals(A / 4, place(w, 12 + pad, y, z - 2)[1], 1e-9);
        assertEquals(A / 2, place(w, 12 + pad, y + 2, z)[1], 1e-9);
        assertEquals(0, place(w, pad - 2, y, z)[0], 1e-9, "past the first point is the start");

        // Round a bend lying flat, the seam stays underneath all the way.
        s.pts3.clear();
        s.b3W = s.b3D = 30;
        s.pts3.add(new double[]{0, 5, 0}); s.pts3.add(new double[]{30, 5, 0}); s.pts3.add(new double[]{30, 5, 30});
        shape = Shape3.of(s);
        w = shape.wrapping();
        for (double t = 0.05; t < 1; t += 0.1) {
            double[] p = dev.curvegen.core.Bezier3.point(s.pts3, t);
            assertEquals(0, round(place(w, p[0] + pad, p[1] + pad - 2, p[2] + pad)[1], 0, w.width()), 0.05, "under the curve at " + t);
        }
    }

    @Test
    void aSurfaceWrapsAlongItsColumnsAndRows() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.SURFACE; s.sW = 30; s.sH = 1; s.sD = 20; s.sRows = 2; s.sCols = 2;
        s.sPts.clear();
        s.sPts.add(new double[]{0, 0, 0}); s.sPts.add(new double[]{30, 0, 0});
        s.sPts.add(new double[]{0, 0, 20}); s.sPts.add(new double[]{30, 0, 20});
        Shape3 shape = Shape3.of(s);
        Shape3.Wrapping w = shape.wrapping();
        int pad = shape.pad();
        // Along the columns runs from the first row to the last, across z; along the rows across x.
        assertEquals(20, w.length(), 1e-6);
        assertEquals(30, w.width(), 1e-6);
        double[] at = place(w, 12 + pad, pad, 5 + pad);
        assertEquals(12, at[1], 1e-6);
        // It lies flat, so it reads unmirrored from above. With v running to the right, u runs towards the player.
        assertEquals(15, at[0], 1e-6);
    }

    @Test
    void aProjectionRunsStraightThrough() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.ELLIPSOID; s.e3Mode = ShapeSettings.EllipseMode.FILLED; s.e3W = s.e3H = s.e3D = 15;
        Orient o = Orient.facing(Orient.NORTH);
        Shape3 own = Shape3.of(s);
        Solver3.Result r = Solver3.solve(Turned.of(own, o), s, () -> false);
        Wrap3 w = Wrap3.of(r, own, o, Mapping.ABOVE);
        // Own y is up here, so every block of a column has the same place.
        float[][] seen = new float[r.nx() * r.nz()][];
        for (int y = 0, k = 0; y < r.ny(); y++)
            for (int z = 0; z < r.nz(); z++)
                for (int x = 0; x < r.nx(); x++) {
                    if (r.at(x, y, z) == Pieces3.AIR) continue;
                    float[] here = {w.u[k], w.v[k]};
                    k++;
                    float[] first = seen[z * r.nx() + x];
                    if (first == null) seen[z * r.nx() + x] = here;
                    else assertArrayEquals(first, here);
                }
        assertEquals(15, w.strip.length(), 1e-9);
        assertEquals(Orient.EAST, w.right);
        assertEquals(Orient.NORTH, w.up);
    }

    @Test
    void handedness() {
        for (int d : new int[]{Orient.NORTH, Orient.EAST, Orient.SOUTH, Orient.WEST}) {
            assertEquals(-1, Wrap3.handedness(Orient.facing(d)));
            assertEquals(-1, Wrap3.handedness(Orient.facing(d).tip(d)));
            assertEquals(-1, Wrap3.handedness(Orient.facing(d).turn()));
        }
        assertEquals(1, Wrap3.handedness(UNTURNED));
    }

    /**
     * A shape set out unturned is the mirror image of one an editor sets out for a player facing south. Each reads
     * its pattern unmirrored, so on a shape that is its own mirror image the pattern lands on the same blocks. From
     * the side is the exception: the mirror swaps which side is the player's right, so each reads from its own.
     */
    @Test
    void aPatternReadsTheSameWhicheverWayRoundTheAxesAre() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.ELLIPSOID; s.e3W = 16; s.e3H = 12; s.e3D = 14;
        Shape3 own = Shape3.of(s);
        Orient editor = Orient.facing(Orient.SOUTH);
        Solver3.Result a = Solver3.solve(Turned.of(own, editor), s, () -> false), b = Solver3.solve(own, s, () -> false);
        assertArrayEquals(a.grid(), b.grid());
        for (Mapping m : new Mapping[]{Mapping.WRAP, Mapping.ABOVE, Mapping.FRONT}) {
            Wrap3 wa = Wrap3.of(a, own, editor, m), wb = Wrap3.of(b, own, UNTURNED, m);
            for (int k = 0; k < wa.blocks(); k++) {
                assertEquals(0, round(wa.u[k], wb.u[k], wa.strip.length()), 1e-3, m + " along, block " + k);
                assertEquals(wa.v[k], wb.v[k], 1e-3, m + " across, block " + k);
            }
            assertEquals(wa.right, wb.right);
            assertEquals(wa.up, wb.up);
        }
    }

    @Test
    void wrapNeverTurnsBlocksAndAProjectionTurnsThemWithThePattern() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.ELLIPSOID;
        Solver3.Result r = Solver3.run(s);
        Shape3 own = Shape3.of(s);
        Paint p = paint(Patterns.CHECKER);
        p.turn = 90;
        Painted wrapped = Painter3.paint(r, Wrap3.of(r, own, UNTURNED, Mapping.WRAP), p, true, TestWorld.catalog());
        for (int q : wrapped.quarters()) assertEquals(0, q);
        Painted front = Painter3.paint(r, Wrap3.of(r, own, UNTURNED, Mapping.FRONT), p, true, TestWorld.catalog());
        for (int q : front.quarters()) assertEquals(1, q);
    }

    /** See {@link Paint3Timing} for the target. This only catches painting becoming many times slower. */
    @Test
    void repaintingIsQuick() {
        ShapeSettings s = Paint3Timing.dome(64);
        Solver3.Result r = Solver3.run(s);
        Wrap3 w = Wrap3.of(r, Shape3.of(s), UNTURNED, Mapping.WRAP);
        Paint3Timing.millis(r, w, s, TestWorld.catalog());   // warm up
        assertTrue(Paint3Timing.millis(r, w, s, TestWorld.catalog()) < 100);
    }
}
