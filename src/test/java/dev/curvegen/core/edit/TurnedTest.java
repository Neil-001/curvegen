package dev.curvegen.core.edit;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.Pieces3;
import dev.curvegen.core.Shape3;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.Shapes3Cases;
import dev.curvegen.core.Solver3;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnedTest {
    private static final Orient UNTURNED = new Orient(Orient.EAST, Orient.UP, Orient.SOUTH);

    /** Every way a shape can end up: four turns of each of the six ways its own y can point. */
    private static List<Orient> all() {
        List<Orient> out = new java.util.ArrayList<>();
        for (Orient base : new Orient[]{UNTURNED, UNTURNED.tip(Orient.NORTH), UNTURNED.tip(Orient.NORTH).tip(Orient.NORTH),
                UNTURNED.tip(Orient.SOUTH), UNTURNED.tip(Orient.EAST), UNTURNED.tip(Orient.WEST)})
            for (int k = 0; k < 4; k++) { out.add(base); base = base.turn(); }
        return out;
    }

    private static ShapeSettings torus() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.TORUS; s.tRing = 15; s.tTube = 5; s.tW = 19; s.tH = 6; s.tD = 13;
        return s;
    }

    @Test
    void anUnturnedShapeIsItself() {
        Shape3 shape = Shape3.of(torus());
        assertSame(shape, Turned.of(shape, UNTURNED));
        assertEquals(24, new java.util.HashSet<>(all()).size());
    }

    @Test
    void theFieldAndTheBoxFollowTheOrientation() {
        Shape3 shape = Shapes3Cases.shape(7, 4, 9, Double.NEGATIVE_INFINITY, 0, (x, y, z) -> x + 10 * y + 100 * z);
        for (Orient o : all()) {
            Shape3 t = Turned.of(shape, o);
            int[] own = {7, 4, 9}, world = o.worldSize(own);
            assertArrayEquals(world, new int[]{t.nx(), t.ny(), t.nz()}, o.toString());
            double[] p = {1.25, 2.5, 3.75}, w = o.toWorld(p, own);
            assertEquals(shape.field(p[0], p[1], p[2]), t.field(w[0], w[1], w[2]), 1e-9, o.toString());
        }
    }

    @Test
    void mirrorPlanesAndTheWireframeTurnToo() {
        Shape3 inner = Shape3.of(torus());
        Shape3 shape = new Shape3() {
            @Override public int nx() { return inner.nx(); }
            @Override public int ny() { return inner.ny(); }
            @Override public int nz() { return inner.nz(); }
            @Override public double field(double x, double y, double z) { return inner.field(x, y, z); }
            @Override public boolean symY() { return true; }
            @Override public List<double[]> wireframe() { return List.of(new double[]{1, 2, 3, 4, 5, 6}); }
        };
        Orient tipped = UNTURNED.tip(Orient.NORTH);   // own y now runs north
        Shape3 t = Turned.of(shape, tipped);
        assertFalse(t.symX());
        assertFalse(t.symY());
        assertTrue(t.symZ());
        int[] own = {19, 6, 13};
        double[] a = tipped.toWorld(new double[]{1, 2, 3}, own), b = tipped.toWorld(new double[]{4, 5, 6}, own);
        assertArrayEquals(new double[]{a[0], a[1], a[2], b[0], b[1], b[2]}, t.wireframe().get(0), 1e-9);
    }

    @Test
    void aTurnedTorusFillsTheCellsItsOwnFieldSaysItShould() {
        ShapeSettings s = torus();
        Shape3 shape = Shape3.of(s);
        int[] own = {19, 6, 13};
        Solver3.Result flat = Solver3.solve(shape, s, () -> false);
        for (Orient o : all()) {
            Solver3.Result r = Solver3.solve(Turned.of(shape, o), s, () -> false);
            assertNotNull(r);
            int filled = 0;
            for (int k = 0; k < own[2]; k++)
                for (int j = 0; j < own[1]; j++)
                    for (int i = 0; i < own[0]; i++) {
                        int[] w = o.cell(i, j, k, own);
                        boolean block = r.at(w[0], w[1], w[2]) != Pieces3.AIR;
                        if (block) filled++;
                        double d = shape.field(i + .5, j + .5, k + .5);
                        // Well inside is a block and well outside is air, whichever way up the pieces had to go.
                        if (d < -0.9) assertEquals(Pieces3.FULL, r.at(w[0], w[1], w[2]), o + " at " + i + "," + j + "," + k);
                        if (d > 0.9) assertFalse(block, o + " at " + i + "," + j + "," + k);
                    }
            assertEquals(flat.volume(), r.volume(), 1e-6, o.toString());
            assertTrue(filled > 300, o + ": " + filled);
        }
    }

    @Test
    void aShapeWithItsOwnRunsIsOnlyTrustedOneBlockAtATime() {
        Shape3 shape = new Shape3() {
            @Override public int nx() { return 8; }
            @Override public int ny() { return 8; }
            @Override public int nz() { return 8; }
            @Override public double field(double x, double y, double z) { return x - 4; }
            @Override public int uniform(int i, int j, int k) { return i < 3 ? 3 - i : i > 4 ? -(8 - i) : 0; }
            @Override public List<double[]> wireframe() { return List.of(); }
        };
        Shape3 t = Turned.of(shape, UNTURNED.turn());   // own x now runs south
        assertEquals(1, t.uniform(5, 2, 0));
        assertEquals(0, t.uniform(5, 2, 3));
        assertEquals(-1, t.uniform(5, 2, 7));
    }
}
