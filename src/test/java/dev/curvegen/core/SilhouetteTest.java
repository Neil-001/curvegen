package dev.curvegen.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The outline drawn around pieces in the preview and the Count tab. */
class SilhouetteTest {
    /** The old rule: only the four direct neighbours count. */
    private static boolean[] outline4(boolean[] in, int n) {
        boolean[] e = new boolean[n * n];
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++)
                if (in[y * n + x])
                    e[y * n + x] = x == 0 || y == 0 || x == n - 1 || y == n - 1
                            || !in[y * n + x - 1] || !in[y * n + x + 1] || !in[(y - 1) * n + x] || !in[(y + 1) * n + x];
        return e;
    }

    private static int extraPixels(int piece, int size) {
        boolean[] in = Silhouette.of(piece, size), now = Silhouette.outline(in, size), old = outline4(in, size);
        int d = 0;
        for (int k = 0; k < in.length; k++) {
            assertTrue(!old[k] || now[k], "the new outline never loses a pixel");
            if (now[k] != old[k]) d++;
        }
        return d;
    }

    @Test
    void stairsGetTheirInsideCorner() {
        for (int st = Pieces.ST_UR; st <= Pieces.ST_DL; st++) {
            assertEquals(1, extraPixels(st, 16), Pieces.NAME[st]);
            assertEquals(1, extraPixels(st, 12), Pieces.NAME[st]);
        }
    }

    @Test
    void rectangularPiecesLookExactlyTheSame() {
        int[] rects = {Pieces.FULL, Pieces.SLAB_B, Pieces.SLAB_T, Pieces.TD_B, Pieces.TD_T, Pieces.TD_L, Pieces.TD_R,
                Pieces.F_TD_U, Pieces.F_TD_D, Pieces.PANE, Pieces.PANE_L, Pieces.PANE_R, Pieces.PANE_LR, Pieces.FENCE};
        for (int p : rects)
            for (int size : new int[]{8, 12, 14, 16}) assertEquals(0, extraPixels(p, size), Pieces.NAME[p] + " at " + size);
    }

    @Test
    void silhouetteMatchesTheMask() {
        for (int p = 1; p < Pieces.COUNT; p++) {
            boolean[] in = Silhouette.of(p, 16);
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++)
                    assertEquals(Pieces.MASK[p][(15 - y) * 16 + x] == 1, in[y * 16 + x], Pieces.NAME[p] + " pixel " + x + "," + y);
        }
    }

    /** The Count tab draws rectangles instead of single pixels. They must paint the very same picture. */
    @Test
    void rectsPaintTheSamePixels() {
        for (int size : new int[]{8, 12, 14, 16}) {
            int pixels = 0, rects = 0;
            for (int p = 1; p < Pieces.COUNT; p++) {
                boolean[] in = Silhouette.of(p, size), edge = Silhouette.outline(in, size);
                int[] painted = new int[size * size];   // 0 empty, 1 fill, 2 outline
                for (int[] r : Silhouette.rects(p, size)) {
                    rects++;
                    for (int y = r[1]; y < r[3]; y++)
                        for (int x = r[0]; x < r[2]; x++) {
                            assertEquals(0, painted[y * size + x], Pieces.NAME[p] + " is painted twice at " + x + "," + y);
                            painted[y * size + x] = 1 + r[4];
                        }
                }
                for (int k = 0; k < in.length; k++) {
                    assertEquals(in[k] ? edge[k] ? 2 : 1 : 0, painted[k], Pieces.NAME[p] + " at " + size + ", pixel " + k);
                    if (in[k]) pixels++;
                }
            }
            if (size == 14) assertTrue(rects * 10 < pixels, "at " + size + ": " + rects + " rectangles for " + pixels + " pixels");
        }
    }
}
