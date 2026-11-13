package dev.curvegen.core.paint;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.paint.Pattern.Cell;
import org.junit.jupiter.api.Test;

class ImagePatternTest {
    private static final int WHITE = 0xFFFFFFFF, BLACK = 0xFF000000, RED = 0xFFFF0000, CLEAR_GREEN = 0x0000FF00;

    /** A picture from rows of pixels, top row first. */
    private static ImagePattern.Pixels img(int[][] rows) { return (x, y) -> rows[y][x]; }

    private static Pattern of(int[][] rows, int width) {
        return ImagePattern.of("img", img(rows), rows[0].length, rows.length, width);
    }

    /** The cell at x from the left and r rows from the top. */
    private static Cell cell(Pattern p, int x, int r) { return p.cell(p.at(x, p.height - 1 - r)); }

    @Test
    void theHeightFollowsThePictureAndNeitherSidePassesTheLimit() {
        assertEquals(128, ImagePattern.maxWidth(256, 128));
        assertEquals(64, ImagePattern.heightFor(256, 128, 128));
        assertEquals(32, ImagePattern.maxWidth(100, 400), "a tall picture is held back by its height");
        assertEquals(128, ImagePattern.heightFor(100, 400, 32));
        assertEquals(1, ImagePattern.maxWidth(1, 1000));
        assertEquals(1, ImagePattern.heightFor(1000, 1, 5), "a thin strip still has a row");
        assertEquals(30, ImagePattern.heightFor(640, 480, 40));
        assertThrows(IllegalArgumentException.class, () -> ImagePattern.of("x", (_, _) -> WHITE, 100, 400, 33));
        Pattern p = ImagePattern.of("x", (_, _) -> WHITE, 640, 480, 40);
        assertEquals(40, p.width);
        assertEquals(30, p.height);
    }

    @Test
    void cellsAverageTheAreaTheyCoverRatherThanPickingOnePixel() {
        // Fine stripes: any one pixel is black or white, but the area is grey, mixed as light mixes.
        int[][] stripes = new int[4][8];
        for (int[] row : stripes) for (int x = 0; x < 8; x++) row[x] = x % 2 == 0 ? BLACK : WHITE;
        Pattern p = of(stripes, 2);
        assertEquals(1, p.paletteSize(), "every cell is the same grey");
        int grey = p.cell(0).rgb();
        assertEquals(grey >> 16 & 0xFF, grey & 0xFF);
        assertEquals(189, grey & 0xFF, 5, "half white light is about 188 in sRGB, not 128");

        // Three pixels into two cells: the middle pixel is split between them.
        Pattern split = of(new int[][]{{RED, WHITE, BLACK}}, 2);
        int left = cell(split, 0, 0).rgb(), right = cell(split, 1, 0).rgb();
        assertEquals(0xFF, left >> 16 & 0xFF, "two thirds red and one third white");
        assertTrue((left & 0xFF) > 100 && (left & 0xFF) < 200, Integer.toHexString(left));
        assertTrue((right & 0xFF) > 100 && (right & 0xFF) < 200, "a third white and two thirds black: " + Integer.toHexString(right));
        assertEquals(right >> 16 & 0xFF, right & 0xFF, "no red reaches the right cell");
    }

    @Test
    void thePictureKeepsItsWayUpAndScalesUpToo() {
        Pattern p = of(new int[][]{{RED, RED}, {WHITE, BLACK}}, 4);
        assertEquals(4, p.height);
        for (int x = 0; x < 4; x++) {
            assertEquals(0xFF0000, cell(p, x, 0).rgb(), "the top row stays on top");
            assertEquals(0xFF0000, cell(p, x, 1).rgb());
        }
        assertEquals(0xFFFFFF, cell(p, 0, 3).rgb());
        assertEquals(0x000000, cell(p, 3, 3).rgb());
    }

    @Test
    void aCellIsEmptyWhenLessThanHalfOfItIsOpaque() {
        // Four pixels in a row become one cell.
        assertNull(of(new int[][]{{RED, CLEAR_GREEN, CLEAR_GREEN, CLEAR_GREEN}}, 1), "a quarter opaque");
        assertEquals(0xFF0000, of(new int[][]{{RED, RED, CLEAR_GREEN, CLEAR_GREEN}}, 1).cell(0).rgb(),
                "half opaque is a colour, and the clear pixels' green never shows");
        assertEquals(0xFF0000, of(new int[][]{{RED, RED, RED, CLEAR_GREEN}}, 1).cell(0).rgb());
        assertNull(of(new int[][]{{0x7FFF0000, 0x7FFF0000, 0x7FFF0000, 0x7FFF0000}}, 1), "just under half opaque all over");
        Pattern p = of(new int[][]{{RED, CLEAR_GREEN}, {CLEAR_GREEN, CLEAR_GREEN}}, 2);
        assertEquals(0xFF0000, cell(p, 0, 0).rgb());
        assertSame(Cell.EMPTY, cell(p, 1, 0), "a clear pixel is an empty cell");
        assertSame(Cell.EMPTY, cell(p, 0, 1));
        assertNull(ImagePattern.of("clear", (_, _) -> CLEAR_GREEN, 10, 10, 5), "a clear picture makes no pattern");
    }

    @Test
    void coloursAreRoundedToFewLevelsAndTheResultSavesAsText() {
        int[][] noise = new int[64][64];
        java.util.Random r = new java.util.Random(1);
        for (int[] row : noise) for (int x = 0; x < 64; x++) row[x] = 0xFF000000 | r.nextInt(0x1000000);
        Pattern p = of(noise, 64);
        java.util.Set<Integer> levels = new java.util.HashSet<>();
        for (int q = 0; q < 32; q++) levels.add((q * 255 + 15) / 31);
        for (int k = 0; k < p.paletteSize(); k++) {
            int c = p.cell(k).rgb();
            for (int s = 0; s < 24; s += 8) assertTrue(levels.contains(c >> s & 0xFF), "each channel is one of 32 levels");
        }
        assertEquals(0, ImagePattern.level(0));
        assertEquals(255, ImagePattern.level(1));
        assertEquals(p, Pattern.decode(p.encode()));
    }
}
