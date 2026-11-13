package dev.curvegen.core.paint;

import static dev.curvegen.core.paint.TestWorld.m;
import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.Pieces;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.Solver;
import dev.curvegen.core.paint.Pattern.Cell;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Painter2DTest {
    /** Every 2D shape and mode, upright and flat, one and three deep. */
    static List<ShapeSettings> shapes() {
        List<ShapeSettings> out = new ArrayList<>();
        for (boolean floor : new boolean[]{false, true})
            for (int depth : new int[]{1, 3}) {
                for (ShapeSettings.EllipseMode mode : ShapeSettings.EllipseMode.values()) {
                    ShapeSettings s = new ShapeSettings();
                    s.eMode = mode; s.eT = 2; s.floor = floor; s.depth = depth;
                    out.add(s);
                }
                for (ShapeSettings.EqMode mode : ShapeSettings.EqMode.values()) {
                    ShapeSettings s = new ShapeSettings();
                    s.gen = Gen.EQUATION; s.qMode = mode; s.floor = floor; s.depth = depth;
                    out.add(s);
                }
                ShapeSettings circle = new ShapeSettings();
                circle.gen = Gen.EQUATION; circle.src = "x^2 + y^2 = 16"; circle.xmin = "-5"; circle.xmax = "5"; circle.ymin = "-5"; circle.ymax = "5";
                circle.floor = floor; circle.depth = depth;
                out.add(circle);
                for (ShapeSettings.BzMode mode : ShapeSettings.BzMode.values()) {
                    ShapeSettings s = new ShapeSettings();
                    s.gen = Gen.BEZIER; s.bMode = mode; s.bLW = 3; s.floor = floor; s.depth = depth;
                    out.add(s);
                }
            }
        return out;
    }

    private static Paint paint(Pattern p) {
        Paint paint = new Paint();
        paint.source = Paint.Source.PATTERN;
        paint.pattern = p;
        return paint;
    }

    @Test
    void paintingNeverChangesThePieces() {
        for (ShapeSettings s : shapes()) {
            Solver.Result plain = Solver.run(s.copy());
            ShapeSettings withPaint = s.copy();
            withPaint.paint.set(paint(Patterns.BRICK_ROAD));
            Solver.Result r = Solver.run(withPaint);
            assertArrayEquals(plain.grid(), r.grid(), "the paint settings changed the solve");
            byte[] before = r.grid().clone();
            for (Pattern p : Patterns.BUNDLED)
                for (Paint.On on : Paint.On.values())
                    for (Paint.Fit fit : Paint.Fit.values()) {
                        Paint paint = paint(p);
                        paint.on = on; paint.fit = fit; paint.turn = 30; paint.patternOneMaterial = true;
                        Painted out = Painter2D.paint(r, s.depth, paint, s.fullConnects, TestWorld.catalog());
                        assertArrayEquals(before, r.grid(), "painting changed the solve");
                        assertNotNull(out);
                        // Empty cells get nothing, and every block is counted.
                        int n = r.nx() * r.ny();
                        for (int k = 0; k < out.layers(); k++)
                            for (int c = 0; c < n; c++)
                                if (r.grid()[c] == Pieces.EMPTY) assertEquals(-1, out.at(k * n + c));
                        long blocks = 0;
                        for (int c = 0; c < n; c++) if (r.grid()[c] != Pieces.EMPTY) blocks += Math.max(1, s.depth);
                        assertEquals(blocks, out.total());
                    }
        }
    }

    @Test
    void offPaintsNothing() {
        ShapeSettings s = new ShapeSettings();
        assertNull(Painter2D.paint(Solver.run(s), 1, s.paint, true, TestWorld.catalog()));
    }

    @Test
    void stripesAlternateRoundAnEllipse() {
        ShapeSettings s = new ShapeSettings();
        s.shelf = s.slab = s.stair = s.trap = s.fence = s.pane = s.wall = false;
        Pattern two = Pattern.of("two", new Cell[][]{{Cell.block(m("oak_planks")), Cell.block(m("stone_bricks"))}});
        Solver.Result r = Solver.run(s);
        Painted out = Painter2D.paint(r, 1, paint(two), true, TestWorld.catalog());
        int oak = 0, stone = 0;
        for (int c = 0; c < r.nx() * r.ny(); c++) {
            if (r.grid()[c] == Pieces.EMPTY) continue;
            String b = out.blocks().get(out.at(c));
            if (b.equals(m("oak_planks"))) oak++; else if (b.equals(m("stone_bricks"))) stone++;
        }
        assertTrue(oak > 10 && stone > 10 && Math.abs(oak - stone) < (oak + stone) / 4, oak + " oak, " + stone + " stone");
    }

    /** A flat Bézier line 9 wide with the road on it: white edges, grey between, and a dashed yellow middle. */
    @Test
    void theTwoLaneRoadLiesAlongAFlatCurve() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.BEZIER; s.floor = true; s.bW = 60; s.bH = 20; s.bLW = 9;
        s.pts.clear();
        s.pts.add(new double[]{0, 10.5}); s.pts.add(new double[]{60, 10.5});
        s.shelf = s.trap = s.fence = s.pane = s.wall = false;
        Solver.Result r = Solver.run(s);
        Painted out = Painter2D.paint(r, 1, paint(Patterns.TWO_LANE_ROAD), true, TestWorld.catalog());
        // A straight road along y = 10.5: rows 6 to 14 are its 9 blocks across.
        assertEquals(m("white_concrete"), block(out, r, 20, 6));
        assertEquals(m("white_concrete"), block(out, r, 20, 14));
        assertEquals(m("gray_concrete"), block(out, r, 20, 8));
        int yellow = 0;
        for (int i = 0; i < 16; i++) if (block(out, r, i, 10).equals(m("yellow_concrete"))) yellow++;
        assertEquals(8, yellow, "dashed 4 on, 4 off");
    }

    private static String block(Painted out, Solver.Result r, int i, int j) {
        int k = out.at(j * r.nx() + i);
        return k < 0 ? "blocks tab" : out.blocks().get(k);
    }

    @Test
    void edgeRunsThroughTheDepth() {
        ShapeSettings s = new ShapeSettings();
        s.depth = 4;
        Pattern rows = Pattern.of("rows", new Cell[][]{{Cell.block(m("oak_planks"))}, {Cell.block(m("stone_bricks"))}});
        Paint p = paint(rows);
        p.on = Paint.On.EDGE; p.fit = Paint.Fit.STRETCH;
        Solver.Result r = Solver.run(s);
        Painted out = Painter2D.paint(r, 4, p, true, TestWorld.catalog());
        assertEquals(4, out.layers());
        int c = 0;
        while (r.grid()[c] != Pieces.FULL) c++;
        int n = r.nx() * r.ny();
        assertEquals(m("stone_bricks"), out.blocks().get(out.at(c)));
        assertEquals(m("oak_planks"), out.blocks().get(out.at(3 * n + c)));
        // At depth 1 there's no edge to speak of, so the face takes it.
        assertEquals(1, Painter2D.paint(Solver.run(new ShapeSettings()), 1, p, true, TestWorld.catalog()).layers());
    }

    @Test
    void countsAddUp() {
        ShapeSettings s = new ShapeSettings();
        s.depth = 2;
        Solver.Result r = Solver.run(s);
        Painted out = Painter2D.paint(r, 2, paint(Patterns.MOSSY_MIX), true, TestWorld.catalog());
        long sum = 0;
        for (long[] f : out.counts()) for (long v : f) sum += v;
        assertEquals(out.total(), sum);
        int blocks = 0;
        for (int p = 1; p < Pieces.COUNT; p++) blocks += r.counts()[p];
        assertEquals(2L * blocks, sum);
    }

    /** See {@link Paint2DTiming} for the target. This only catches painting becoming many times slower. */
    @Test
    void repaintingIsQuick() {
        ShapeSettings s = Paint2DTiming.painted(new ShapeSettings());
        s.eW = s.eH = 200; s.eMode = ShapeSettings.EllipseMode.MIDDLE; s.eT = 4;
        Solver.Result r = Solver.run(s);
        Paint2DTiming.millis(r, s, TestWorld.catalog());   // warm up
        assertTrue(Paint2DTiming.millis(r, s, TestWorld.catalog()) < 100);
    }

    @Test
    void blocksTurnWithTheNudgedAngle() {
        ShapeSettings s = new ShapeSettings();
        s.eW = s.eH = 31; s.eMode = ShapeSettings.EllipseMode.MIDDLE; s.eT = 3;
        Paint p = paint(Pattern.of("two", new Cell[][]{{Cell.block(m("oak_log[axis=x]")), Cell.block(m("stone_bricks"))}}));
        p.fit = Paint.Fit.REPEAT; p.size = 3; p.turn = 44;
        Painted out = Painter2D.paint(Solver.run(s), 1, p, true, TestWorld.catalog());
        for (int k = 0; k < out.blocks().size(); k++)
            if (out.blocks().get(k).startsWith(m("oak_log"))) assertEquals(1, out.quarters()[k], "44° is nudged past 45°");
    }

    /** Two loops of one equation, nudged to either side of 45°, turn their blocks each their own way. */
    @Test
    void eachLoopTurnsItsOwnBlocks() {
        ShapeSettings s = new ShapeSettings();
        s.gen = Gen.EQUATION; s.src = "((x+6)^2+y^2-9)*((x-6)^2+y^2-4)=0"; s.qLW = 3; s.qLock = false;
        s.xmin = "-10"; s.xmax = "10"; s.ymin = "-5"; s.ymax = "5"; s.qW = 80; s.qH = 40;
        Paint p = paint(Pattern.of("two", new Cell[][]{{Cell.block(m("oak_log[axis=x]")), Cell.block(m("stone_bricks"))}}));
        p.fit = Paint.Fit.REPEAT; p.size = 3; p.turn = 44;
        Solver.Result r = Solver.run(s);
        Painted out = Painter2D.paint(r, 1, p, true, TestWorld.catalog());
        java.util.Set<Integer> turns = new java.util.HashSet<>();
        for (int c = 0; c < r.nx() * r.ny(); c++) {
            int e = out.at(c);
            if (e >= 0 && out.blocks().get(e).startsWith(m("oak_log"))) turns.add(out.quarters()[e] * 1000 + (c % r.nx() < r.nx() / 2 ? 0 : 1));
        }
        assertEquals(java.util.Set.of(0, 1001), turns, "the left loop turns 0 quarters, the right one 1");
    }
}
