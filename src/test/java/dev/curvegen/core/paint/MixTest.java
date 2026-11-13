package dev.curvegen.core.paint;

import static dev.curvegen.core.paint.TestWorld.m;
import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.Pieces;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.Solver;
import dev.curvegen.core.paint.Paint.Arrange;
import dev.curvegen.core.paint.Pattern.Cell;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MixTest {
    private static final Cell A = Cell.block(m("stone_bricks")), B = Cell.block(m("mossy_stone_bricks")), C = Cell.block(m("oak_log[axis=x]"));

    @Test
    void aPatternGivesItsBlocksWeightedByHowOftenTheyAppear() {
        Pattern p = Pattern.of("p", new Cell[][]{{A, A, Cell.EMPTY}, {A, B, Cell.EMPTY}, {C, Cell.EMPTY, A}});
        Mix mix = Mix.of(p);
        assertEquals(3, mix.paletteSize(), "empty cells are left out");
        Map<Cell, Integer> w = new HashMap<>();
        for (int k = 0; k < mix.paletteSize(); k++) w.put(mix.cell(k), mix.count(k));
        assertEquals(Map.of(A, 4, B, 1, C, 1), w);
        assertEquals("p", mix.name);
        assertNull(Mix.of(Pattern.of("none", new Cell[][]{{Cell.EMPTY}})));
    }

    @Test
    void mixesRoundTripAsTextAndBrokenTextIsRefused() {
        Mix mix = new Mix("Mine", List.of(A, C, Cell.colour(0x12ab34)), new int[]{5, 1, 2});
        assertEquals(mix, Mix.decode(mix.encode()));
        for (Pattern p : Patterns.BUNDLED) assertEquals(Mix.of(p), Mix.decode(Mix.of(p).encode()));
        for (String bad : new String[]{"", "minecraft:stone", "minecraft:stone*0", "minecraft:stone*-2", "-*3",
                "minecraft:stone*1 minecraft:stone*2", "Not An Id*1", "minecraft:stone*x"})
            assertNull(Mix.decode(Map.of("name", "x", "blocks", bad)), bad);
    }

    @Test
    void theSameSettingsAlwaysGiveTheSameBlocks() {
        Mix mix = Mix.of(Patterns.MOSSY_MIX);
        for (Arrange a : Arrange.values()) {
            MixLayout one = new MixLayout(mix, a, 5, 42), two = new MixLayout(mix, a, 5, 42), other = new MixLayout(mix, a, 5, 43);
            int differ = 0;
            for (int x = -20; x < 20; x++)
                for (int y = -20; y < 20; y++) {
                    assertEquals(one.at(x, y, 3), two.at(x, y, 3));
                    if (one.at(x, y, 3) != other.at(x, y, 3)) differ++;
                }
            assertTrue(differ > 400, a + ": another seed should lay the blocks out another way, but only " + differ + " of 1600 differ");
        }
    }

    @Test
    void sharesFollowTheWeights() {
        Mix mix = new Mix("w", List.of(A, B, C), new int[]{6, 3, 1});
        for (Arrange a : Arrange.values())
            for (int patch : new int[]{2, 6, 16}) {
                MixLayout l = new MixLayout(mix, a, patch, 7);
                long[] n = new long[3];
                int size = 160;
                for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) for (int z = 0; z < 8; z++) n[l.at(x, y, z * 40)]++;
                double total = n[0] + n[1] + n[2];
                // Patches are lumps, so their shares wander more: about 1,000 lumps of 16 here, against 200,000 blocks.
                double tol = a == Arrange.SCATTER ? .01 : patch < 10 ? .03 : .06;
                assertEquals(.6, n[0] / total, tol, a + " " + patch);
                assertEquals(.3, n[1] / total, tol, a + " " + patch);
                assertEquals(.1, n[2] / total, tol, a + " " + patch);
            }
    }

    /** The average length of runs of the same entry along one axis, over a cube. */
    private static double run(MixLayout l, int axis) {
        int size = 96, runs = 0, cells = 0;
        for (int a = 0; a < size; a++)
            for (int b = 0; b < size; b++) {
                int last = -1;
                for (int t = 0; t < size; t++) {
                    int e = axis == 0 ? l.at(t, a, b) : axis == 1 ? l.at(a, t, b) : l.at(a, b, t);
                    if (e != last) runs++;
                    last = e;
                    cells++;
                }
            }
        return (double) cells / runs;
    }

    @Test
    void patchesAreLumpsTheSameEveryWayRoundAndGrowWithTheirSize() {
        // Two equal blocks, so scatter's runs average 2 and patches' show their size.
        Mix mix = new Mix("two", List.of(A, B), new int[]{1, 1});
        double scatter = run(new MixLayout(mix, Arrange.SCATTER, 8, 1), 0);
        assertEquals(2, scatter, .1);
        double last = scatter;
        for (int patch : new int[]{2, 4, 8, 16}) {
            MixLayout l = new MixLayout(mix, Arrange.PATCHES, patch, 1);
            double x = run(l, 0), y = run(l, 1), z = run(l, 2);
            double lo = Math.min(x, Math.min(y, z)), hi = Math.max(x, Math.max(y, z));
            assertTrue(hi / lo < 1.15, "patch " + patch + ": runs " + x + ", " + y + ", " + z + " should be alike every way");
            assertTrue(lo > last * 1.3, "patch " + patch + ": runs of " + lo + " should be longer than at the size before, " + last);
            last = lo;
        }
        assertTrue(last > 10, "patches of 16 should run well over 10 blocks, not " + last);
    }

    @Test
    void aMixPaintsEveryLayerOnItsOwnAndNeverChangesThePieces() {
        for (ShapeSettings s : Painter2DTest.shapes()) {
            Solver.Result r = Solver.run(s.copy());
            byte[] before = r.grid().clone();
            Paint paint = new Paint();
            paint.source = Paint.Source.MIX;
            paint.mix = new Mix("m", List.of(Cell.block(m("stone_bricks")), Cell.block(m("oak_planks")), Cell.block(m("red_wool"))), new int[]{2, 1, 1});
            for (Arrange a : Arrange.values()) {
                paint.arrange = a;
                Painted out = Painter2D.paint(r, s.depth, paint, s.fullConnects, TestWorld.catalog());
                assertArrayEquals(before, r.grid(), "painting changed the solve");
                assertEquals(Math.max(1, s.depth), out.layers());
                int n = r.nx() * r.ny();
                long blocks = 0;
                Set<Integer> entries = new HashSet<>();
                for (int k = 0; k < out.layers(); k++)
                    for (int c = 0; c < n; c++) {
                        if (r.grid()[c] == Pieces.EMPTY) assertEquals(-1, out.at(k * n + c));
                        else { blocks++; entries.add(out.at(k * n + c)); }
                    }
                assertEquals(blocks, out.total());
                long counted = 0;
                for (long[] f : out.counts()) for (long v : f) counted += v;
                assertEquals(blocks, counted);
                if (blocks > 40) assertTrue(entries.size() > 1, "a mix of three blocks gave one");
                for (int q : out.quarters()) assertEquals(0, q, "a mix turns nothing");
            }
        }
    }
}
