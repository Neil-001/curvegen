package dev.curvegen.core;

import java.util.ArrayList;
import java.util.List;

/** Shapes and settings the 3D tests solve: ellipsoids and toruses, and a few shapes made to bring out connectors and corner stairs. */
public final class Shapes3Cases {
    private Shapes3Cases() {}

    public record Case(String name, Shape3 shape, ShapeSettings settings) {
        public Solver3.Result solve() { return Solver3.solve(shape, settings, () -> false); }
    }

    /** A shape given only by its box and field, with none of the optional hints. */
    public interface Field { double at(double x, double y, double z); }

    public static Shape3 shape(int nx, int ny, int nz, double lo, double hi, Field f) {
        return new Shape3() {
            @Override public int nx() { return nx; }
            @Override public int ny() { return ny; }
            @Override public int nz() { return nz; }
            @Override public double lo() { return lo; }
            @Override public double hi() { return hi; }
            @Override public double field(double x, double y, double z) { return f.at(x, y, z); }
            @Override public List<double[]> wireframe() { return List.of(); }
        };
    }

    /** Two thin upright sheets that cross, under a sloping top: walls and panes of every height, with and without posts. */
    public static Shape3 sheets(double half) {
        return shape(14, 9, 13, Double.NEGATIVE_INFINITY, 0, (x, y, z) ->
                Math.max(Math.min(Math.abs(x - 6.5), Math.abs(z - 5.5)) - half, (y - 3 - 0.3 * x - 0.2 * z) / 1.2));
    }

    /** Thin posts on a grid, joined by rails at two heights: fences, chains and end rods. */
    public static Shape3 posts() {
        return shape(13, 8, 13, Double.NEGATIVE_INFINITY, 0, (x, y, z) -> {
            double px = Math.abs(x % 4 - 2.5), pz = Math.abs(z % 4 - 2.5);
            double post = Math.max(Math.hypot(px, pz) - 0.13, y - 6.5);
            double rail = Math.hypot(Math.min(px, pz), Math.min(Math.abs(y - 0.5), Math.abs(y - 4.5))) - 0.1;
            return Math.min(post, rail);
        });
    }

    /** A stepped pyramid with a pit in its top: stairs that turn outer and inner corners. */
    public static Shape3 pyramid(boolean upsideDown) {
        return shape(15, 8, 15, Double.NEGATIVE_INFINITY, 0, (x, y, z) -> {
            double r = Math.max(Math.abs(x - 7.5), Math.abs(z - 7.5)), h = upsideDown ? 8 - y : y;
            return Math.max((r + h - 7.5) / 1.5, (2.5 - r - (h - 4)) / 1.5);
        });
    }

    private static ShapeSettings pieces(boolean slab, boolean stair, boolean trap, boolean shelf, boolean fence, boolean pane, boolean wall, boolean lines) {
        ShapeSettings s = new ShapeSettings();
        s.slab = slab; s.stair = stair; s.trap = trap; s.shelf = shelf; s.fence = fence; s.pane = pane; s.wall = wall; s.chain = s.rod = lines;
        return s;
    }

    /** Piece sets: the default, everything, and sets that leave connectors little competition. */
    public static List<ShapeSettings> pieceSets() {
        return List.of(new ShapeSettings(), pieces(true, true, true, true, true, true, true, true),
                pieces(false, false, false, false, true, true, true, false), pieces(false, true, false, false, false, false, true, false),
                pieces(false, false, false, false, false, true, true, true), pieces(true, true, false, false, true, false, false, false),
                pieces(false, false, false, false, false, false, true, false));
    }

    public static List<Case> all() {
        List<Case> out = new ArrayList<>();
        int n = 0;
        for (ShapeSettings set : pieceSets()) {
            for (boolean fullConnects : new boolean[]{true, false}) {
                ShapeSettings base = set.copy();
                base.fullConnects = fullConnects;
                String tag = " set " + n + (fullConnects ? "" : " no full connections");
                for (ShapeSettings.EllipseMode m : ShapeSettings.EllipseMode.values())
                    for (int[] d : new int[][]{{9, 9, 9}, {16, 11, 13}, {7, 20, 12}}) {
                        ShapeSettings s = base.copy();
                        s.gen = ShapeSettings.Gen.ELLIPSOID; s.e3W = d[0]; s.e3H = d[1]; s.e3D = d[2]; s.e3Mode = m; s.e3T = 1.5;
                        out.add(new Case("ellipsoid " + d[0] + "x" + d[1] + "x" + d[2] + " " + m + tag, Shape3.of(s), s));
                    }
                for (int[] d : new int[][]{{17, 5, 17, 17, 5, 0}, {16, 6, 16, 22, 9, 13}, {20, 7, 20, 20, 7, 1}}) {
                    ShapeSettings s = base.copy();
                    s.gen = ShapeSettings.Gen.TORUS; s.tRing = d[0]; s.tTube = d[1]; s.tW = d[3]; s.tH = d[4]; s.tD = d[2]; s.tHollow = d[5] == 1;
                    if (d[5] == 13) s.tD = 13;
                    out.add(new Case("torus " + d[0] + "/" + d[1] + " in " + s.tW + "x" + s.tH + "x" + s.tD + tag, Shape3.of(s), s));
                }
                out.add(new Case("sheets" + tag, sheets(0.18), base));
                out.add(new Case("thick sheets" + tag, sheets(0.45), base));
                out.add(new Case("posts" + tag, posts(), base));
                out.add(new Case("pyramid" + tag, pyramid(false), base));
                out.add(new Case("upside-down pyramid" + tag, pyramid(true), base));
            }
            n++;
        }
        return out;
    }
}
