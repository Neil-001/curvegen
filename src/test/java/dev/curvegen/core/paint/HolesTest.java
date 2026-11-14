package dev.curvegen.core.paint;

import static dev.curvegen.core.paint.TestWorld.m;
import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.Pieces;
import dev.curvegen.core.Pieces3;
import dev.curvegen.core.Settle2D;
import dev.curvegen.core.Shape3;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.Solver;
import dev.curvegen.core.Solver3;
import dev.curvegen.core.paint.Paint.Empty;
import dev.curvegen.core.paint.Paint.Mapping;
import dev.curvegen.core.paint.Pattern.Cell;
import java.util.BitSet;
import org.junit.jupiter.api.Test;

/** "Empty spots: Open": which blocks a pattern's empty cells open, and what the blocks beside them become. */
class HolesTest {
    /** A 2×2 pattern with one cell of each kind: a block, a captured block painting can't use, an empty cell, a colour. */
    private static final Pattern MIXED = Pattern.of("t", new Cell[][]{
            {Cell.block(m("stone_bricks")), Cell.UNUSABLE},
            {Cell.EMPTY, Cell.colour(0xa12722)}});

    private static Paint paint(Pattern p, Empty e) {
        Paint paint = new Paint();
        paint.source = Paint.Source.PATTERN;
        paint.pattern = p;
        paint.empty = e;
        return paint;
    }

    private static void assertSame(Painted a, Painted b, String what) {
        assertArrayEquals(a.slots(), b.slots(), what);
        assertEquals(a.layers(), b.layers(), what);
        assertEquals(a.blocks(), b.blocks(), what);
        assertArrayEquals(a.quarters(), b.quarters(), what);
        assertArrayEquals(a.why(), b.why(), what);
        assertArrayEquals(a.counts(), b.counts(), what);
        assertEquals(0, b.open(), what);
        assertNull(b.settled(), what);
    }

    @Test
    void onlyTrulyEmptyCellsOpen() {
        assertFalse(Paint.hasHoles(Patterns.STRIPES));
        assertTrue(Paint.hasHoles(Patterns.LATTICE));
        assertTrue(Paint.hasHoles(MIXED));
        assertFalse(Paint.hasHoles(Pattern.of("t", new Cell[][]{{Cell.block(m("stone")), Cell.UNUSABLE}})), "an unusable block always fills");
        assertNull(paint(MIXED, Empty.FILL).holes());
        boolean[] holes = paint(MIXED, Empty.OPEN).holes();
        for (int k = 0; k < MIXED.paletteSize(); k++) assertEquals(MIXED.cell(k).equals(Cell.EMPTY), holes[k], MIXED.cell(k).toString());
        Paint mix = paint(MIXED, Empty.OPEN);
        mix.source = Paint.Source.MIX;
        assertNull(mix.holes(), "a mix ignores empty cells");
    }

    /** With Fill, or a pattern without empty cells, Open changes nothing at all. */
    @Test
    void fillAndPatternsWithoutHolesChangeNothing2D() {
        for (ShapeSettings s : Painter2DTest.shapes())
            for (Pattern p : new Pattern[]{Patterns.BRICK_ROAD, Patterns.LATTICE, MIXED}) {
                Solver.Result r = Solver.run(s);
                Painted fill = Painter2D.paint(r, s.depth, paint(p, Empty.FILL), s.fullConnects, TestWorld.catalog());
                assertEquals(0, fill.open());
                assertNull(fill.settled());
                if (!Paint.hasHoles(p)) assertSame(fill, Painter2D.paint(r, s.depth, paint(p, Empty.OPEN), s.fullConnects, TestWorld.catalog()), s.gen + " " + p.name);
            }
    }

    @Test
    void fillAndPatternsWithoutHolesChangeNothing3D() {
        for (ShapeSettings s : Painter3Test.shapes()) {
            Shape3 own = Shape3.of(s);
            Solver3.Result r = Solver3.solve(own, s, () -> false);
            Solve3Painter painter = new Solve3Painter(r, own, Painter3Test.UNTURNED);
            for (Pattern p : new Pattern[]{Patterns.BRICK_ROAD, Patterns.LATTICE}) {
                Solve3Painter.Done fill = painter.paint(paint(p, Empty.FILL), s.fullConnects, TestWorld.catalog());
                assertEquals(0, fill.painted().open());
                assertEquals(r, fill.result());
                if (Paint.hasHoles(p)) continue;
                Solve3Painter.Done open = painter.paint(paint(p, Empty.OPEN), s.fullConnects, TestWorld.catalog());
                assertEquals(r, open.result(), "the solve itself, unsettled");
                assertSame(fill.painted(), open.painted(), s.gen + " " + p.name);
            }
        }
    }

    /**
     * Every block on an empty cell opens and every other block is painted as with Fill. A hole goes through every layer
     * of a pattern on the face, and the counts leave it out.
     */
    @Test
    void holesOpenOnEmptyCells2D() {
        long opened = 0;
        for (ShapeSettings s : Painter2DTest.shapes()) {
            Solver.Result r = Solver.run(s);
            int n = r.nx() * r.ny(), d = Math.max(1, s.depth);
            for (Paint.On on : Paint.On.values()) {
                Paint fillPaint = paint(Patterns.LATTICE, Empty.FILL), openPaint = paint(Patterns.LATTICE, Empty.OPEN);
                fillPaint.on = openPaint.on = on;
                Painted fill = Painter2D.paint(r, s.depth, fillPaint, s.fullConnects, TestWorld.catalog());
                Painted open = Painter2D.paint(r, s.depth, openPaint, s.fullConnects, TestWorld.catalog());
                String what = s.gen + " " + (s.floor ? "flat" : "upright") + " depth " + s.depth + " " + on;
                assertEquals(fill.layers(), open.layers(), what);
                long blocks = 0, holes = 0;
                for (int k = 0; k < open.layers(); k++)
                    for (int c = 0; c < n; c++) {
                        if (r.at(c % r.nx(), c / r.nx()) == Pieces.EMPTY) continue;
                        int slot = k * n + c, per = open.layers() == 1 ? d : 1;
                        blocks += per;
                        if (open.at(slot) == Painted.OPEN) {
                            holes += per;
                            assertEquals(-1, fill.at(slot), what + ": Fill gives an empty cell the Blocks tab's block");
                        } else assertEquals(entry(fill, fill.at(slot)), entry(open, open.at(slot)), what + ": painted as with Fill");
                    }
                assertEquals(holes, open.open(), what);
                assertEquals(blocks - holes, open.total(), what + ": the counts leave holes out");
                long counted = 0;
                for (long[] f : open.counts()) for (long v : f) counted += v;
                assertEquals(blocks - holes, counted, what);
                if (holes == 0) { assertNull(open.settled(), what); continue; }
                opened += holes;
                Settle2D settled = open.settled();
                for (int j = 0; j < r.ny(); j++)
                    for (int i = 0; i < r.nx(); i++)
                        for (int k = 0; k < d; k++) {
                            boolean hole = open.at((open.layers() == 1 ? 0 : k) * n + j * r.nx() + i) == Painted.OPEN;
                            assertEquals(r.at(i, j) != Pieces.EMPTY && !hole, settled.filled(i, j, k), what + " at " + i + "," + j + "," + k);
                            if (!hole) assertEquals(Pieces.FAMILY[r.at(i, j)], Pieces.FAMILY[settled.piece(i, j, k)], what + ": a block keeps its piece type");
                        }
            }
        }
        assertTrue(opened > 1000, opened + " blocks opened");
    }

    @Test
    void holesOpenOnEmptyCells3D() {
        long opened = 0;
        for (ShapeSettings s : Painter3Test.shapes()) {
            Shape3 own = Shape3.of(s);
            Solver3.Result r = Solver3.solve(own, s, () -> false);
            Solve3Painter painter = new Solve3Painter(r, own, Painter3Test.UNTURNED);
            for (Mapping m : Mapping.values()) {
                if (m == Mapping.WRAP && own.wrapping() == null) continue;
                Paint fillPaint = paint(Patterns.LATTICE, Empty.FILL), openPaint = paint(Patterns.LATTICE, Empty.OPEN);
                fillPaint.mapping = openPaint.mapping = m;
                Solve3Painter.Done fill = painter.paint(fillPaint, s.fullConnects, TestWorld.catalog()), open = painter.paint(openPaint, s.fullConnects, TestWorld.catalog());
                String what = s.gen + " " + m;
                int blocks = Painter3Test.blocks(r);
                assertEquals(blocks - open.painted().open(), Painter3Test.blocks(open.result()), what);
                assertEquals(open.painted().slots().length, Painter3Test.blocks(open.result()), what + ": a slot for every block left");
                assertEquals(blocks - open.painted().open(), open.painted().total(), what);
                // Every block left keeps its piece type and its paint; the holes were empty cells under Fill.
                for (int c = 0, k = 0, o = 0; c < r.grid().length; c++) {
                    if (r.grid()[c] == Pieces3.AIR) { assertEquals(Pieces3.AIR, open.result().grid()[c]); continue; }
                    int e = fill.painted().at(k++);
                    if (open.result().grid()[c] == Pieces3.AIR) { assertEquals(-1, e, what); continue; }
                    assertEquals(Pieces3.FAMILY[r.grid()[c]], Pieces3.FAMILY[open.result().grid()[c]], what);
                    int f = open.painted().at(o++);
                    assertEquals(e < 0 ? null : fill.painted().blocks().get(e), f < 0 ? null : open.painted().blocks().get(f), what);
                }
                opened += open.painted().open();
            }
        }
        assertTrue(opened > 1000, opened + " blocks opened");
    }

    /** A hole on the pattern's face goes straight through a thick shape: every layer, front to back, opens alike. */
    @Test
    void aHoleRunsThroughTheDepth() {
        ShapeSettings s = new ShapeSettings();
        s.eT = 3; s.depth = 4;
        Solver.Result r = Solver.run(s);
        Painted open = Painter2D.paint(r, s.depth, paint(Patterns.LATTICE, Empty.OPEN), s.fullConnects, TestWorld.catalog());
        assertEquals(1, open.layers());
        Settle2D settled = open.settled();
        int columns = 0;
        for (int j = 0; j < r.ny(); j++)
            for (int i = 0; i < r.nx(); i++) {
                if (r.at(i, j) == Pieces.EMPTY || settled.filled(i, j, 0)) continue;
                columns++;
                for (int k = 1; k < s.depth; k++) assertFalse(settled.filled(i, j, k));
            }
        assertTrue(columns > 10);
        assertEquals(columns * (long) s.depth, open.open());
    }

    /** A fence arm that reached into a hole drops, and a wall under a hole loses what made it tall. */
    @Test
    void neighboursSettle() {
        // A row of five fences, upright and one deep: the middle one goes.
        byte[] grid = {Pieces.FENCE_R, Pieces.FENCE_LR, Pieces.FENCE_LR, Pieces.FENCE_LR, Pieces.FENCE_L};
        Solver.Result row = result(grid, 5, 1, false);
        BitSet gone = new BitSet();
        gone.set(2);
        Settle2D s = Settle2D.of(row, 1, gone, true, () -> false);
        assertEquals(Pieces.FENCE_L, s.piece(1, 0, 0), "the arm into the hole drops");
        assertEquals(Pieces.FENCE_R, s.piece(3, 0, 0));
        assertEquals(Pieces.EMPTY, s.piece(2, 0, 0));
        assertEquals(Pieces.FENCE_R, s.piece(0, 0, 0), "out of reach of the hole, nothing changes");

        // Three deep, a hole in the middle layer only: the fences in front of it and behind it lose the arm towards it.
        // Front is the way the layers run.
        Settle2D deep = Settle2D.of(row, 3, bits(5 + 2), true, () -> false);
        assertEquals(0, deep.joints(2, 0, 0) >> Settle2D.FRONT & 15, "layer 0 joins neither layer 1 nor anything before it");
        assertEquals(0, deep.joints(2, 0, 2) >> Settle2D.FRONT & 15);
        assertEquals(1, Settle2D.side(deep.joints(1, 0, 0), Settle2D.FRONT));
        assertEquals(0, Settle2D.side(deep.joints(1, 0, 0), Settle2D.BACK));
        assertEquals(1, Settle2D.side(deep.joints(1, 0, 1), Settle2D.BACK));
        assertEquals(Pieces.FENCE_L, deep.piece(1, 0, 1));
        assertEquals(Pieces.FENCE_LR, deep.piece(1, 0, 0));

        // Walls with tall sides under full blocks: once the blocks go, the sides are low, and the straight wall in the
        // middle has no post, as nothing sits on it.
        byte[] wall = {(byte) Pieces.wall(0, 2, true, true), (byte) Pieces.wall(2, 2, true, false), (byte) Pieces.wall(2, 0, true, true),
                Pieces.FULL, Pieces.FULL, Pieces.FULL};
        Solver.Result w = result(wall, 3, 2, false);
        Settle2D open = Settle2D.of(w, 1, bits(3, 4, 5), true, () -> false);
        assertEquals(Pieces.wall(1, 1, false, false), open.piece(1, 0, 0));
        assertEquals(Pieces.wall(0, 1, false, true), open.piece(0, 0, 0), "an end keeps its post");
        Settle2D kept = Settle2D.of(w, 1, new BitSet(), true, () -> false);
        assertEquals(Pieces.wall(2, 2, true, false), kept.piece(1, 0, 0), "nothing gone, nothing changes");

        // Flat: a cross of fences loses the arm towards a hole beside it.
        // Rows go up from the bottom: row 0 holds the arm below the centre, and the hole is the arm above it.
        byte[] flat = {0, (byte) (Pieces.F_FENCE + Pieces.TOP), 0,
                (byte) (Pieces.F_FENCE + Pieces.RIGHT), (byte) (Pieces.F_FENCE + 15), (byte) (Pieces.F_FENCE + Pieces.LEFT),
                0, (byte) (Pieces.F_FENCE + Pieces.BOTTOM_SIDE), 0};
        Settle2D cross = Settle2D.of(result(flat, 3, 3, true), 1, bits(7), true, () -> false);
        assertEquals(Pieces.F_FENCE + (Pieces.LEFT | Pieces.RIGHT | Pieces.BOTTOM_SIDE), cross.piece(1, 1, 0));
        assertEquals(Pieces.F_FENCE + (Pieces.LEFT | Pieces.RIGHT | Pieces.BOTTOM_SIDE | Pieces.TOP), Settle2D.of(result(flat, 3, 3, true), 1, new BitSet(), true, () -> false).piece(1, 1, 0));
    }

    /** A slot's block and how it's turned, or null for the Blocks tab's. */
    private static String entry(Painted p, int e) { return e < 0 ? null : p.blocks().get(e) + " turned " + p.quarters()[e]; }

    private static BitSet bits(int... on) {
        BitSet b = new BitSet();
        for (int k : on) b.set(k);
        return b;
    }

    private static Solver.Result result(byte[] grid, int nx, int ny, boolean floor) {
        int[] counts = new int[Pieces.COUNT];
        for (byte b : grid) counts[b]++;
        return new Solver.Result(grid, nx, ny, 0, 0, counts, null, floor);
    }
}
