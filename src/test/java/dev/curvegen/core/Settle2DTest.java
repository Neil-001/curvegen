package dev.curvegen.core;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Settle2D} settles a shape without holes from a few layers and then only the blocks near the holes. Both
 * must give exactly what laying out every layer with its holes and settling every block gives.
 */
class Settle2DTest {
    /** Piece sets: everything, fences beside full blocks, panes and walls, walls alone, walls with slabs and stairs. */
    private static final String[] SETS = {"all", "fence", "pane wall", "wall", "wall slab stair"};

    private static List<ShapeSettings> shapes() {
        List<ShapeSettings> out = new ArrayList<>();
        for (String set : SETS)
            for (boolean floor : new boolean[]{false, true})
                for (boolean fullConnects : new boolean[]{true, false}) {
                    ShapeSettings base = new ShapeSettings();
                    if (!set.equals("all")) {
                        base.slab = set.contains("slab"); base.stair = set.contains("stair"); base.trap = base.shelf = false;
                        base.fence = set.contains("fence"); base.pane = set.contains("pane"); base.wall = set.contains("wall");
                    }
                    base.floor = floor; base.fullConnects = fullConnects;
                    for (ShapeSettings.EllipseMode m : ShapeSettings.EllipseMode.values()) {
                        ShapeSettings c = base.copy();
                        c.eMode = m; c.eW = 19; c.eH = 13; c.eT = 1.5;
                        out.add(c);
                    }
                    for (String eq : new String[]{"y=2sin(x)", "x^2+y^2=16"}) {
                        ShapeSettings c = base.copy();
                        c.gen = ShapeSettings.Gen.EQUATION; c.src = eq; c.qLW = 1.3;
                        out.add(c);
                    }
                }
        return out;
    }

    @Test
    void settlingFromFewLayersAndNearTheHolesMatchesSettlingEverything() {
        int checked = 0, changed = 0;
        for (ShapeSettings s : shapes()) {
            Solver.Result r = Solver.run(s);
            int nx = r.nx(), ny = r.ny(), n = nx * ny;
            for (int depth : new int[]{1, 2, 4, 5, 6, 7, 9}) {
                Solver3.Result plain = Solver3.settleAll(Settle2D.layout(r, depth), new BitSet(), s.fullConnects, () -> false);
                // No holes, one block in forty, one in twelve through every layer, and one in four, block by block.
                for (int odds : new int[]{0, 40, -12, 4}) {
                    Random rnd = new Random(depth * 31L + odds);
                    BitSet gone = new BitSet(depth * n);
                    for (int c = 0; c < n; c++) {
                        boolean column = odds < 0 && rnd.nextInt(-odds) == 0;
                        for (int k = 0; k < depth; k++) if (column || (odds > 0 && rnd.nextInt(odds) == 0)) gone.set(k * n + c);
                    }
                    Solver3.Result laid = Settle2D.layout(r, depth);
                    int sz = laid.nz();
                    BitSet cells = new BitSet();
                    for (int b = gone.nextSetBit(0); b >= 0; b = gone.nextSetBit(b + 1)) cells.set(Settle2D.cell(b % nx, b % n / nx, b / n, nx, ny, sz, r.floor()));
                    Solver3.Result all = Solver3.settleAll(laid, cells, s.fullConnects, () -> false);
                    Settle2D fast = Settle2D.of(r, depth, gone, s.fullConnects, () -> false);
                    String what = s.gen + " " + s.eMode + " " + s.src + (r.floor() ? " flat" : " upright") + " depth " + depth + " odds " + odds;
                    for (int k = 0; k < depth; k++)
                        for (int j = 0; j < ny; j++)
                            for (int i = 0; i < nx; i++) {
                                int c = Settle2D.cell(i, j, k, nx, ny, sz, r.floor());
                                assertEquals(all.grid()[c], fast.state(i, j, k), what + " at " + i + "," + j + "," + k);
                                if (all.grid()[c] != plain.grid()[c] && !gone.get((k * ny + j) * nx + i)) changed++;
                            }
                    checked++;
                }
            }
        }
        assertTrue(checked > 1000, checked + " checked");
        // Holes did reshape the blocks beside them.
        assertTrue(changed > 1000, changed + " blocks changed beside holes");
    }
}
