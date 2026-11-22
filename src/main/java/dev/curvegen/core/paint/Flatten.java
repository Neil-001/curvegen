package dev.curvegen.core.paint;

import dev.curvegen.core.paint.Pattern.Cell;

public final class Flatten {
    private Flatten() {}

    @FunctionalInterface
    public interface Cells { Cell at(int x, int y, int z); }

    public record Frame(int along, int alongSign, int across, int acrossSign, int depth, int depthSign) {
        public int width(int[] size) { return size[along]; }
        public int height(int[] size) { return size[across]; }
    }

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
        int[] a = unit(across, acrossSign), w = unit(depth, -depthSign);
        int[] right = {a[1] * w[2] - a[2] * w[1], a[2] * w[0] - a[0] * w[2], a[0] * w[1] - a[1] * w[0]};
        return new Frame(along, right[along], across, acrossSign, depth, depthSign);
    }

    private static int[] unit(int axis, int sign) {
        int[] v = new int[3];
        v[axis] = sign;
        return v;
    }

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
