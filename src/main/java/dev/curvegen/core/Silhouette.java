package dev.curvegen.core;

import java.util.ArrayList;
import java.util.List;

/** A piece drawn at a given pixel size, and the pixels on its outline. Shared by the preview and the Count tab icons. */
public final class Silhouette {
    private Silhouette() {}

    /** The piece at size×size pixels, row 0 at the top. */
    public static boolean[] of(int piece, int size) {
        boolean[] in = new boolean[size * size];
        for (int[] q : Pieces.RECTS[piece]) {
            int x0 = Math.max(0, Math.min(size, Math.round(q[0] * size / 16f)));
            int top = Math.max(0, Math.min(size, size - Math.round(q[3] * size / 16f)));
            int x1 = Math.max(0, Math.min(size, Math.max(x0 + 1, Math.round(q[2] * size / 16f))));
            int bot = Math.max(0, Math.min(size, Math.max(top + 1, size - Math.round(q[1] * size / 16f))));
            for (int y = top; y < bot; y++) for (int x = x0; x < x1; x++) in[y * size + x] = true;
        }
        return in;
    }

    /**
     * Pixels on the edge of the silhouette: any filled pixel touching an empty one, diagonals included, so inside
     * corners (like a stair's) are outlined too. Pieces without inside corners are unaffected by the diagonal check.
     */
    public static boolean[] outline(boolean[] in, int size) {
        boolean[] e = new boolean[size * size];
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                if (!in[y * size + x]) continue;
                boolean edge = x == 0 || y == 0 || x == size - 1 || y == size - 1;
                for (int dy = -1; !edge && dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++)
                        if (!in[(y + dy) * size + x + dx]) { edge = true; break; }
                e[y * size + x] = edge;
            }
        return e;
    }

    /**
     * The same picture as {@link #of} and {@link #outline}, as rectangles that don't overlap: {x0, y0, x1, y1, edge}
     * each, with edge 1 for outline pixels. Far fewer shapes to draw than one per pixel.
     */
    public static int[][] rects(int piece, int size) {
        boolean[] in = of(piece, size), edge = outline(in, size);
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; ) {
                int k = y * size + x, x0 = x;
                if (!in[k]) { x++; continue; }
                while (x < size && in[y * size + x] && edge[y * size + x] == edge[k]) x++;
                int e = edge[k] ? 1 : 0, x1 = x;
                int[] above = null;                 // a run directly above with the same span and kind grows down instead
                for (int[] r : out) if (r[3] == y && r[0] == x0 && r[2] == x1 && r[4] == e) above = r;
                if (above != null) above[3]++;
                else out.add(new int[]{x0, y, x1, y + 1, e});
            }
        return out.toArray(new int[0][]);
    }
}
