package dev.curvegen.core.paint;

import dev.curvegen.core.paint.Pattern.Cell;

/**
 * Turns a box of blocks the player marked in the world into a pattern, as they saw it. A box more than one block
 * thick is flattened along the way they look, keeping the first block seen in each column, and the pattern's up is
 * what was up on their screen. Axes are numbered x 0, y 1, z 2, and the box's cells are counted from its lowest corner.
 */
public final class Flatten {
    private Flatten() {}

    /** One cell of the box, from its lowest corner. */
    @FunctionalInterface
    public interface Cells { Cell at(int x, int y, int z); }

    /**
     * Where the pattern lies in the box: the axis its x runs along and the axis its y runs up, each with the way it
     * runs (+1 or -1), and the axis it is flattened along, with the way the player looks along it.
     */
    public record Frame(int along, int alongSign, int across, int acrossSign, int depth, int depthSign) {
        public int width(int[] size) { return size[along]; }
        public int height(int[] size) { return size[across]; }
    }

    /**
     * The frame for a box of {@code size} blocks seen from {@code eye}, in blocks from the box's lowest corner, looking
     * along {@code look} with {@code up} up on the screen. A box one block thick on some axis lies flat across it,
     * whatever the view. Otherwise it is flattened along the axis nearest the view. Up is the axis left that the
     * screen's up leans along most, or the view's when the screen's up is square to both, as when looking level at a
     * floor. The other axis runs to the right, so the pattern isn't mirrored. The side the box is seen from is the side
     * the eye is on, or within the box's own span the side the look comes from.
     */
    public static Frame frame(int[] size, double[] eye, double[] look, double[] up) {
        boolean thin = size[0] == 1 || size[1] == 1 || size[2] == 1;
        int depth = -1;
        for (int a = 0; a < 3; a++)
            if ((!thin || size[a] == 1) && (depth < 0 || Math.abs(look[a]) > Math.abs(look[depth]))) depth = a;
        int depthSign = eye[depth] < 0 ? 1 : eye[depth] > size[depth] ? -1 : look[depth] < 0 ? -1 : 1;
        int p = (depth + 1) % 3, q = (depth + 2) % 3;
        double[] u = up;
        if (Math.hypot(up[p], up[q]) < 0.1) u = look;
        int across = Math.abs(u[q]) > Math.abs(u[p]) ? q : p;
        int acrossSign = u[across] < 0 ? -1 : 1;
        int along = across == p ? q : p;
        // Right is up × towards the viewer.
        int[] a = unit(across, acrossSign), w = unit(depth, -depthSign);
        int[] right = {a[1] * w[2] - a[2] * w[1], a[2] * w[0] - a[0] * w[2], a[0] * w[1] - a[1] * w[0]};
        return new Frame(along, right[along], across, acrossSign, depth, depthSign);
    }

    private static int[] unit(int axis, int sign) {
        int[] v = new int[3];
        v[axis] = sign;
        return v;
    }

    /**
     * The pattern the box makes in {@code f}. Each cell is the first block in its column that isn't air, from the side
     * the player looks from, even one painting can't use, such as a chest, which then fills. A column of air is empty.
     */
    public static Pattern flatten(String name, int[] size, Frame f, Cells cells) {
        int w = size[f.along()], h = size[f.across()], d = size[f.depth()];
        Cell[][] rows = new Cell[h][w];
        int[] at = new int[3];
        for (int py = 0; py < h; py++)
            for (int px = 0; px < w; px++) {
                at[f.along()] = f.alongSign() > 0 ? px : w - 1 - px;
                at[f.across()] = f.acrossSign() > 0 ? py : h - 1 - py;
                Cell found = Cell.EMPTY;
                for (int k = 0; k < d; k++) {
                    at[f.depth()] = f.depthSign() > 0 ? k : d - 1 - k;
                    Cell c = cells.at(at[0], at[1], at[2]);
                    if (!c.equals(Cell.EMPTY)) { found = c; break; }
                }
                rows[h - 1 - py][px] = found;
            }
        return Pattern.of(name, rows);
    }
}
