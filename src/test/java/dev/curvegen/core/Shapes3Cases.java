package dev.curvegen.core;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Shapes and settings the 3D tests solve: one or more of every kind the mod builds, and a few shapes made to bring out
 * connectors and corner stairs. Some have holes, emptied after the solve with {@link Solver3#settle}.
 */
public final class Shapes3Cases {
    private Shapes3Cases() {}

    public record Case(String name, Shape3 shape, ShapeSettings settings, Holes holes) {
        public Case(String name, Shape3 shape, ShapeSettings settings) { this(name, shape, settings, null); }

        public Solver3.Result solve() {
            Solver3.Result r = Solver3.solve(shape, settings, () -> false);
            return holes == null ? r : Solver3.settle(r, gone(r, holes), settings.fullConnects, () -> false);
        }
    }

    /** Blocks to empty after a solve. */
    public interface Holes { boolean at(int x, int y, int z); }

    /** The cells of this grid that {@code holes} empties, indexed like the grid. */
    public static BitSet gone(Solver3.Result r, Holes holes) {
        BitSet gone = new BitSet(r.grid().length);
        for (int y = 0; y < r.ny(); y++)
            for (int z = 0; z < r.nz(); z++)
                for (int x = 0; x < r.nx(); x++) if (holes.at(x, y, z)) gone.set((y * r.nz() + z) * r.nx() + x);
        return gone;
    }

    /** About one block in nine, scattered. */
    static final Holes SCATTERED = (x, y, z) -> (5 * x + 3 * y + 7 * z) % 9 == 0;

    /** A lattice's openings, 2 blocks across with bars 1 wide, run straight through the shape from front to back, as a pattern's holes do. */
    static final Holes LATTICE = (x, y, _) -> x % 3 != 0 && y % 3 != 0;

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
                for (ShapeSettings.EllipseMode m : new ShapeSettings.EllipseMode[]{ShapeSettings.EllipseMode.MIDDLE, ShapeSettings.EllipseMode.FILLED}) {
                    ShapeSettings s = base.copy();
                    s.gen = ShapeSettings.Gen.ELLIPSOID; s.e3W = 16; s.e3H = 13; s.e3D = 11; s.e3Mode = m; s.e3T = 2.5;
                    out.add(new Case("ellipsoid " + m + " with a lattice through it" + tag, Shape3.of(s), s, LATTICE));
                }
                ShapeSettings ring = base.copy();
                ring.gen = ShapeSettings.Gen.TORUS; ring.tRing = 16; ring.tTube = 6; ring.tW = 22; ring.tH = 9; ring.tD = 22;
                out.add(new Case("torus with a lattice through it" + tag, Shape3.of(ring), ring, LATTICE));
                for (ShapeSettings s : List.of(Shapes3Test.equation("z = x y / 3", ShapeSettings.Eq3Mode.SURFACE, 0.75, 14, "-3", "3", "-3", "3", "-3", "3"),
                        Shapes3Test.equation("z < 2 - floor(x^2 + y^2)/3", ShapeSettings.Eq3Mode.SURFACE, 1, 14, "-3", "3", "-3", "3", "-1", "3"),
                        Shapes3Test.curve(14, 10, 14, 1.25, 1, 1, 2, 4, 12, 13, 13, 9, 1, 12, 2, 12),
                        Shapes3Test.hollow(Shapes3Test.curve(18, 14, 18, 4.5, 2, 2, 3, 9, 10, 4, 6, 12, 12, 14, 4, 13), true),
                        Shapes3Test.hollow(Shapes3Test.curve(12, 10, 12, 3, 1, 2, 1, 4, 9, 2, 9, 1, 11), false),
                        Shapes3Test.surface(14, 9, 12, 3, 4, 0.75, 2, 6, 3, 7, 5, 2, 8, 4, 3, 7, 2, 6))) {
                    ShapeSettings own = base.copy();
                    own.set(s);
                    for (Pieces.Family f : Pieces.Family.values()) own.allow(f, base.allows(f));
                    own.fullConnects = fullConnects;
                    String hollow = own.b3Hollow ? own.b3Open ? " hollow" : " hollow, capped" : "";
                    out.add(new Case(PresetData.defaultName(own, own.gen) + hollow + tag, Shape3.of(own), own));
                }
                out.add(new Case("sheets" + tag, sheets(0.18), base));
                out.add(new Case("thick sheets" + tag, sheets(0.45), base));
                out.add(new Case("posts" + tag, posts(), base));
                out.add(new Case("pyramid" + tag, pyramid(false), base));
                out.add(new Case("upside-down pyramid" + tag, pyramid(true), base));
                out.add(new Case("sheets with holes" + tag, sheets(0.18), base, SCATTERED));
                out.add(new Case("posts with holes" + tag, posts(), base, SCATTERED));
                out.add(new Case("pyramid with holes" + tag, pyramid(false), base, SCATTERED));
            }
            n++;
        }
        return out;
    }
}
