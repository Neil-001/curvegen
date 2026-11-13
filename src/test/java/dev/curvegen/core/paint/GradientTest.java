package dev.curvegen.core.paint;

import static dev.curvegen.core.paint.TestWorld.m;
import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.Pieces;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.Pieces3;
import dev.curvegen.core.Shape3;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.Solver;
import dev.curvegen.core.Solver3;
import dev.curvegen.core.edit.Orient;
import dev.curvegen.core.edit.Turned;
import dev.curvegen.core.paint.Paint.Edges;
import dev.curvegen.core.paint.Paint.Flow;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class GradientTest {
    /** Dark to light in four steps, the third chosen by hand. */
    public static final Gradient GREYS = new Gradient("Greys",
            List.of(new Gradient.Stop(0x080a0f, m("black_concrete")), new Gradient.Stop(0xe9ecec, null)),
            List.of(new Gradient.Step(0x101010, m("black_concrete"), false), new Gradient.Step(0x404040, m("gray_concrete"), false),
                    new Gradient.Step(0x808080, m("stone_bricks"), true), new Gradient.Step(0xe0e0e0, m("quartz_block"), false)));

    static Paint paint(Flow flow, Edges edges) {
        Paint p = new Paint();
        p.source = Paint.Source.GRADIENT;
        p.gradient = GREYS;
        p.flow = flow;
        p.edges = edges;
        return p;
    }

    @Test
    void gradientsRoundTripAsTextAndBrokenTextIsRefused() {
        assertEquals(GREYS, Gradient.decode(GREYS.encode()));
        Gradient odd = new Gradient("Logs", List.of(), List.of(new Gradient.Step(0x6d5532, m("oak_log[axis=x]"), true), new Gradient.Step(0x123456, "somemod:gone", false)));
        assertEquals(odd, Gradient.decode(odd.encode()));
        for (String bad : new String[]{"", "#123456", "#12345@minecraft:stone", "#123456@Not An Id", "123456@minecraft:stone"})
            assertNull(Gradient.decode(Map.of("name", "x", "steps", bad)), bad);
        assertNull(Gradient.decode(Map.of("name", "x", "stops", "#12@x", "steps", "#123456@minecraft:stone")));
    }

    @Test
    void stepsAreCellsWithTheirBlockAndIdealColour() {
        assertEquals(4, GREYS.paletteSize());
        assertEquals(m("stone_bricks"), GREYS.cell(2).state());
        assertEquals(0x808080, GREYS.cell(2).rgb());
        assertFalse(GREYS.cell(2).empty());
    }

    @Test
    void paintSavesTheGradientWithItsSettings() {
        Paint p = paint(Flow.OUT, Edges.SHARP);
        p.reverse = true;
        p.gradientOneMaterial = false;
        Paint back = new Paint();
        back.apply(p.capture());
        assertTrue(back.same(p));
        assertEquals(GREYS, back.gradient);
        // Off by default: one material per colour is on, edges ragged, bottom to top, and no gradient, so nothing is painted.
        Paint fresh = new Paint();
        fresh.source = Paint.Source.GRADIENT;
        assertFalse(fresh.on());
        assertTrue(fresh.oneMaterial());
        assertEquals(Edges.RAGGED, fresh.edges);
        assertEquals(Flow.UP, fresh.flow);
        // A version that doesn't know gradients turns paint off.
        Map<String, String> d = new java.util.LinkedHashMap<>(p.capture());
        d.put("source", "SOMETHING_NEW");
        back.apply(d);
        assertFalse(back.on());
    }

    @Test
    void aShapeThatCantWrapRunsAlongAndAcrossBottomToTop() {
        Paint p = paint(Flow.ALONG, Edges.SHARP);
        assertEquals(Flow.ALONG, p.flowFor(true));
        assertEquals(Flow.UP, p.flowFor(false));
        p.flow = Flow.OUT;
        assertEquals(Flow.OUT, p.flowFor(false));
    }

    @Test
    void sharpStepsAreEqualBandsInOrderAndReverseFlipsThem() {
        GradientLayout l = new GradientLayout(4, 40, false, false), r = new GradientLayout(4, 40, true, false);
        for (int at = 0; at < 40; at++) {
            assertEquals(at / 10, l.at(at + .5, at, 0, 0));
            assertEquals(3 - at / 10, r.at(at + .5, at, 0, 0));
        }
        assertEquals(0, l.at(-5, 0, 0, 0));
        assertEquals(3, l.at(55, 0, 0, 0));
    }

    @Test
    void raggedEdgesMixOnlyNeighboursKeepTheSharesAndNeverChange() {
        for (double extent : new double[]{8, 40, 200}) {
            GradientLayout l = new GradientLayout(8, extent, false, true), again = new GradientLayout(8, extent, false, true);
            long[] n = new long[8];
            int mixed = 0, rows = (int) extent;
            for (int at = 0; at < rows; at++) {
                int sharp = (int) ((at + .5) / (extent / 8));
                for (int x = 0; x < 40; x++)
                    for (int z = 0; z < 10; z++) {
                        int k = l.at(at + .5, x, at, z);
                        assertEquals(k, again.at(at + .5, x, at, z));
                        assertTrue(Math.abs(k - sharp) <= 1, "step " + k + " strayed from " + sharp + " at " + at + " of " + extent);
                        if (k != sharp) mixed++;
                        n[k]++;
                    }
            }
            assertTrue(mixed > 0, "ragged edges should mix some blocks at " + extent);
            for (long c : n) assertEquals(1.0 / 8, c / (double) (rows * 400), .03, "shares at " + extent);
        }
    }

    @Test
    void aGradientNeverChangesThePiecesAndPaintsEveryBlock() {
        for (ShapeSettings s : Painter3Test.shapes()) {
            Shape3 own = Shape3.of(s);
            for (Orient o : new Orient[]{Painter3Test.UNTURNED, Orient.facing(Orient.WEST).tip(Orient.WEST)}) {
                Solver3.Result r = Solver3.solve(Turned.of(own, o), s, () -> false);
                short[] before = r.grid().clone();
                Solve3Painter painter = new Solve3Painter(r, own, o);
                for (Flow f : Flow.values())
                    for (Edges e : Edges.values()) {
                        Painted p = painter.paint(paint(f, e), s.fullConnects, TestWorld.catalog()).painted();
                        assertArrayEquals(before, r.grid());
                        assertEquals(Painter3Test.blocks(r), p.total(), s.gen + " " + f);
                        assertEquals(Painter3Test.blocks(r), p.slots().length);
                    }
            }
        }
        for (ShapeSettings s : Painter2DTest.shapes()) {
            Solver.Result r = Solver.run(s.copy());
            if (r.target().error != null) continue;
            byte[] before = r.grid().clone();
            for (Flow f : Flow.values()) {
                Painted p = Painter2D.paint(r, s.depth, paint(f, Edges.RAGGED), s.fullConnects, TestWorld.catalog());
                assertArrayEquals(before, r.grid());
                long blocks = 0;
                for (int c = 1; c < Pieces.COUNT; c++) blocks += r.counts()[c];
                assertEquals(blocks * s.depth, p.total(), s.gen + " " + f);
            }
        }
    }

    /** Bottom to top on a ball: full blocks take each step's own block, rising level by level, hand swap included. */
    @Test
    void bottomToTopOnABallRisesThroughEveryStep() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.ELLIPSOID; s.e3W = 24; s.e3H = 24; s.e3D = 24;
        Solver3.Result r = Solver3.run(s);
        Paint paint = paint(Flow.UP, Edges.SHARP);
        paint.gradientOneMaterial = false;
        Painted p = new Solve3Painter(r, Shape3.of(s), Painter3Test.UNTURNED).paint(paint, s.fullConnects, TestWorld.catalog()).painted();
        List<String> order = List.of(m("black_concrete"), m("gray_concrete"), m("stone_bricks"), m("quartz_block"));
        int[] lowest = new int[4], highest = new int[4];
        java.util.Arrays.fill(lowest, Integer.MAX_VALUE);
        java.util.Arrays.fill(highest, -1);
        for (int y = 0, k = 0; y < r.ny(); y++)
            for (int z = 0; z < r.nz(); z++)
                for (int x = 0; x < r.nx(); x++) {
                    int piece = r.at(x, y, z);
                    if (piece == Pieces3.AIR) continue;
                    int e = p.at(k++);
                    if (Pieces3.FAMILY[piece] != Family.FULL) continue;
                    int step = order.indexOf(p.blocks().get(e));
                    assertTrue(step >= 0, p.blocks().get(e));
                    lowest[step] = Math.min(lowest[step], y);
                    highest[step] = Math.max(highest[step], y);
                }
        for (int k = 1; k < 4; k++) assertTrue(lowest[k] > highest[k - 1], "step " + k + " should sit above step " + (k - 1));
        // Reversed, the light end is at the bottom.
        paint.reverse = true;
        Painted rev = new Solve3Painter(r, Shape3.of(s), Painter3Test.UNTURNED).paint(paint, s.fullConnects, TestWorld.catalog()).painted();
        int k = 0;
        for (int c = 0; c < r.grid().length && !(r.grid()[c] != Pieces3.AIR && Pieces3.FAMILY[r.grid()[c]] == Family.FULL); c++)
            if (r.grid()[c] != Pieces3.AIR) k++;
        assertEquals(m("quartz_block"), rev.blocks().get(rev.at(k)), "the lowest full block");
    }

    /** Pieces other than full blocks take the step's material, or the piece type's block closest to the step's ideal colour. */
    @Test
    void otherPiecesFollowTheLadderWithTheIdealColour() {
        Ladder l = new Ladder(TestWorld.catalog(), GREYS, false, true, java.util.EnumSet.allOf(Family.class));
        assertEquals(m("stone_brick_stairs"), l.blocks().get(l.pick(2, Family.STAIRS)));
        assertEquals(Ladder.Why.OWN, l.why(2, Family.STAIRS));
        // Black concrete has no slab: the closest slab to the step's ideal colour, near black.
        assertEquals(m("nether_brick_slab"), l.blocks().get(l.pick(0, Family.SLAB)));
        assertEquals(Ladder.Why.CLOSEST, l.why(0, Family.SLAB));
        assertEquals(m("black_concrete"), l.blocks().get(l.pick(0, Family.FULL)));
    }
}
