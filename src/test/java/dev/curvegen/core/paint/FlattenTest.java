package dev.curvegen.core.paint;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.paint.Flatten.Frame;
import dev.curvegen.core.paint.Pattern.Cell;
import org.junit.jupiter.api.Test;

class FlattenTest {
    private static final double[] UP = {0, 1, 0};

    private static Cell b(String id) { return Cell.block("minecraft:" + id); }

    /** The block named after a cell's place, so a test can see which one ended up where. */
    private static Cell named(int x, int y, int z) { return b("c" + x + y + z); }

    /** The pattern's rows, top first, as the names {@link #named} gave them, with "-" for empty and "?" for unusable. */
    private static String rows(Pattern p) {
        StringBuilder s = new StringBuilder();
        for (int y = p.height - 1; y >= 0; y--) {
            if (!s.isEmpty()) s.append('/');
            for (int x = 0; x < p.width; x++) {
                Cell c = p.cell(p.at(x, y));
                s.append(x == 0 ? "" : " ").append(c.equals(Cell.EMPTY) ? "-" : c.equals(Cell.UNUSABLE) ? "?" : c.block().substring(10));
            }
        }
        return s.toString();
    }

    @Test
    void aWallSeenFromTheSouthReadsLeftToRightAsSeen() {
        int[] size = {3, 2, 1};
        Frame f = Flatten.frame(size, new double[]{1, 1, 6}, new double[]{0.1, 0, -1}, UP);   // looking north: east is on the right
        assertEquals(new Frame(0, 1, 1, 1, 2, -1), f);
        assertEquals("c010 c110 c210/c000 c100 c200", rows(Flatten.flatten("w", size, f, FlattenTest::named)));
    }

    @Test
    void theSameWallSeenFromTheNorthIsTheOtherWayRound() {
        int[] size = {3, 2, 1};
        Frame f = Flatten.frame(size, new double[]{1, 1, -5}, new double[]{0, 0, 1}, UP);   // looking south: west is on the right
        assertEquals("c210 c110 c010/c200 c100 c000", rows(Flatten.flatten("w", size, f, FlattenTest::named)));
    }

    @Test
    void aFloorSeenFromAboveHasTheFarSideUp() {
        int[] size = {2, 1, 3};
        // Facing north and looking down: the screen's up leans north, and east is on the right.
        double[] look = {0, -0.95, -0.3}, up = {0, 0.3, -0.95};
        Frame f = Flatten.frame(size, new double[]{1, 6, 4}, look, up);
        assertEquals(new Frame(0, 1, 2, -1, 1, -1), f);
        assertEquals("c000 c100/c001 c101/c002 c102", rows(Flatten.flatten("f", size, f, FlattenTest::named)));
        // Looking level across a floor, the screen's up is square to it, and the far side goes up all the same.
        assertEquals(f, Flatten.frame(size, new double[]{1, 1.6, 5}, new double[]{0, 0, -1}, UP));
    }

    @Test
    void aThickBoxKeepsTheFirstBlockSeen() {
        int[] size = {2, 2, 3};
        // Looking north along z: the column at x = 0 has air at the front, x = 1 a torch, which is seen first, then stone.
        Flatten.Cells cells = (x, unused, z) -> x == 0 ? (z == 2 ? Cell.EMPTY : named(x, 0, z))
                : z == 2 ? Cell.UNUSABLE : z == 1 ? b("stone") : b("dirt");
        Frame f = Flatten.frame(size, new double[]{1, 1, 5}, new double[]{0, 0, -1}, UP);
        assertEquals(2, f.depth());
        assertEquals("c001 ?/c001 ?", rows(Flatten.flatten("t", size, f, cells)));
        // From the far side the dirt and the first block at x = 0 are seen first.
        f = Flatten.frame(size, new double[]{1, 1, -2}, new double[]{0, 0, 1}, UP);
        assertEquals("dirt c000/dirt c000", rows(Flatten.flatten("t", size, f, cells)));
    }

    @Test
    void columnsWithNothingUsableAreEmptyOrUnusable() {
        int[] size = {2, 2, 2};
        Flatten.Cells cells = (x, unused, unused2) -> x == 0 ? Cell.EMPTY : Cell.UNUSABLE;
        Frame f = Flatten.frame(size, new double[]{1, 1, 1}, new double[]{0, 0, -1}, UP);   // from inside, by the look
        assertEquals("- ?/- ?", rows(Flatten.flatten("e", size, f, cells)));
    }

    @Test
    void aBoxOneThickOnTwoAxesLiesAcrossTheView() {
        // A row of blocks along x, seen from above: it lies flat, one block high, along x.
        int[] size = {4, 1, 1};
        Frame f = Flatten.frame(size, new double[]{2, 9, 0}, new double[]{0, -1, -0.01}, new double[]{0, 0.01, -1});
        assertEquals(1, f.depth());
        assertEquals(4, f.width(size));
        assertEquals(1, f.height(size));
    }

    @Test
    void namesForFilesAndSearch() {
        assertEquals("pattern-brick-road", PatternNames.fileStem("curvegen.pattern.brick_road"));
        assertEquals("pattern-cafe-tiles", PatternNames.fileStem("Café tiles!"));
        assertEquals("pattern", PatternNames.fileStem("★"));
        assertEquals("Road (3)", PatternNames.unique("Road ", n -> n.equals("Road") || n.equals("Road (2)")));
        assertTrue(PatternNames.matches("Two-lane road", "road two"));
        assertFalse(PatternNames.matches("Two-lane road", "brick"));
        assertTrue(PatternNames.matches("Anything", "  "));
    }
}
