package dev.curvegen.core;

import java.util.ArrayList;
import java.util.List;

/** Everything the user can set for the shape. Kept in memory between screen openings. */
public final class ShapeSettings {
    /** Those from ELLIPSOID on are 3D: {@link Solver3} builds them, and {@link #is3d} tells them apart. */
    public enum Gen { ELLIPSE, EQUATION, BEZIER, ELLIPSOID, TORUS, EQUATION3, BEZIER3, SURFACE }
    public enum EllipseMode { THIN, FILLED, OUTWARDS, INWARDS, MIDDLE }
    public enum EqMode { LINE, UNDER, OVER }
    public enum BzMode { LINE, FILLED }
    public enum Eq3Mode { SURFACE, BELOW, ABOVE }

    public Gen gen = Gen.ELLIPSE;

    public int eW = 31, eH = 19;
    public EllipseMode eMode = EllipseMode.THIN;
    public double eT = 1;

    public String src = "y = 2sin(x)", xmin = "-2pi", xmax = "2pi", ymin = "-3", ymax = "3";
    public int qW = 48, qH = 23;
    public boolean qLock = true;
    public EqMode qMode = EqMode.LINE;
    public double qLW = 1;

    public int bW = 40, bH = 24;
    public final List<double[]> pts = new ArrayList<>(List.of(
            new double[]{2, 2}, new double[]{10, 22}, new double[]{30, 22}, new double[]{38, 4}));
    public BzMode bMode = BzMode.LINE;
    public double bLW = 1;
    public boolean snap = false;

    /** Ellipsoid: width (east to west), height and depth (north to south), with the ellipse's modes. */
    public int e3W = 21, e3H = 21, e3D = 21;
    public EllipseMode e3Mode = EllipseMode.THIN;
    public double e3T = 1;

    /** Torus: a ring tRing across with a tube tTube thick, stretched to fill a tW × tH × tD box. */
    public int tRing = 25, tTube = 9, tW = 25, tH = 9, tD = 25;
    public boolean tHollow = false;

    /**
     * 3D equation in x, y and z, where z is height. The ranges map onto a box q3W wide (x, east), q3D deep (y, north)
     * and q3H high (z, up). With q3Lock the depth and height follow the width, to give every axis the same scale.
     */
    public String src3 = "z = sin(x) cos(y)", x3min = "-pi", x3max = "pi", y3min = "-pi", y3max = "pi", z3min = "-1.5", z3max = "1.5";
    public int q3W = 32, q3D = 32, q3H = 15;
    public boolean q3Lock = true;
    public Eq3Mode q3Mode = Eq3Mode.SURFACE;
    public double q3T = 1;

    /** 3D Bézier curve: one curve through control points {x, y, z} in blocks from the box's lowest corner, as a tube b3T thick. */
    public int b3W = 32, b3H = 16, b3D = 32;
    public final List<double[]> pts3 = new ArrayList<>(List.of(
            new double[]{2, 2, 2}, new double[]{10, 14, 4}, new double[]{22, 14, 28}, new double[]{30, 2, 30}));
    public double b3T = 1;

    /**
     * Bézier surface: one patch sT thick with sRows × sCols control points, row by row, so the point in row r and
     * column c is sPts.get(r * sCols + c). {@link Bezier3} adds and removes rows and columns.
     */
    public int sW = 30, sH = 12, sD = 30, sRows = 4, sCols = 4;
    public final List<double[]> sPts = new ArrayList<>();
    {
        double[] rise = {2, 5, 5, 2, 5, 11, 11, 5, 5, 11, 11, 5, 2, 5, 5, 2};
        for (int k = 0; k < 16; k++) sPts.add(new double[]{k % 4 * 10, rise[k], k / 4 * 10});
    }
    public double sT = 1;

    public boolean is3d() { return is3d(gen); }
    public static boolean is3d(Gen gen) { return gen.ordinal() >= Gen.ELLIPSOID.ordinal(); }

    /** Piece families the solver may use (full blocks are always allowed). */
    public boolean slab = true, stair = true, trap = true, shelf = true, fence = true, pane = true, wall = true;
    /** Chains and end rods are thin lines rather than building blocks, so they start switched off. */
    public boolean chain = false, rod = false;

    public boolean allows(Pieces.Family f) {
        return switch (f) {
            case SLAB -> slab; case STAIRS -> stair; case TRAPDOOR -> trap; case SHELF -> shelf; case FENCE -> fence; case PANE -> pane; case WALL -> wall;
            case CHAIN -> chain; case ROD -> rod; default -> true;
        };
    }

    public void allow(Pieces.Family f, boolean v) {
        switch (f) {
            case SLAB -> slab = v; case STAIRS -> stair = v; case TRAPDOOR -> trap = v; case SHELF -> shelf = v; case FENCE -> fence = v; case PANE -> pane = v; case WALL -> wall = v;
            case CHAIN -> chain = v; case ROD -> rod = v; default -> {}
        }
    }
    /** False when the chosen full block is one fences/panes refuse to attach to (leaves, pumpkins…). */
    public boolean fullConnects = true;
    /** How many blocks deep the shape is extruded when placed or exported. */
    public int depth = 1;
    /** Build flat on the ground (a floor, drawn from above) instead of upright (a wall, drawn from the side). */
    public boolean floor = false;
    /** Placement: may the shape replace blocks that are already there? (Off: only air and replaceable blocks.) */
    public boolean overwrite = true;
    /** Placement: clear the space the shape encloses (inside an ellipse wall, the far side of a filled equation). */
    public boolean carve = false;
    /** 3D shapes: match block colours to the top texture rather than the side. */
    public boolean topColours = false;

    /** Independent copy, so the solver can run on a worker thread while the UI keeps editing. */
    public ShapeSettings copy() {
        ShapeSettings c = new ShapeSettings();
        c.set(this);
        return c;
    }

    /** Takes every setting from another instance. The in-world editor's undo uses it to put a snapshot back. */
    public void set(ShapeSettings o) {
        if (o == this) return;
        gen = o.gen; eW = o.eW; eH = o.eH; eMode = o.eMode; eT = o.eT;
        src = o.src; xmin = o.xmin; xmax = o.xmax; ymin = o.ymin; ymax = o.ymax;
        qW = o.qW; qH = o.qH; qLock = o.qLock; qMode = o.qMode; qLW = o.qLW;
        bW = o.bW; bH = o.bH; bMode = o.bMode; bLW = o.bLW; snap = o.snap;
        e3W = o.e3W; e3H = o.e3H; e3D = o.e3D; e3Mode = o.e3Mode; e3T = o.e3T;
        tRing = o.tRing; tTube = o.tTube; tW = o.tW; tH = o.tH; tD = o.tD; tHollow = o.tHollow;
        src3 = o.src3; x3min = o.x3min; x3max = o.x3max; y3min = o.y3min; y3max = o.y3max; z3min = o.z3min; z3max = o.z3max;
        q3W = o.q3W; q3D = o.q3D; q3H = o.q3H; q3Lock = o.q3Lock; q3Mode = o.q3Mode; q3T = o.q3T;
        b3W = o.b3W; b3H = o.b3H; b3D = o.b3D; b3T = o.b3T;
        sW = o.sW; sH = o.sH; sD = o.sD; sRows = o.sRows; sCols = o.sCols; sT = o.sT;
        copy(o.pts, pts); copy(o.pts3, pts3); copy(o.sPts, sPts);
        slab = o.slab; stair = o.stair; trap = o.trap; shelf = o.shelf; fence = o.fence; pane = o.pane; wall = o.wall; chain = o.chain; rod = o.rod;
        fullConnects = o.fullConnects; depth = o.depth; overwrite = o.overwrite; carve = o.carve; floor = o.floor;
        topColours = o.topColours;
    }

    private static void copy(List<double[]> from, List<double[]> to) {
        to.clear();
        for (double[] p : from) to.add(p.clone());
    }

    private static boolean same(List<double[]> a, List<double[]> b) {
        if (a.size() != b.size()) return false;
        for (int k = 0; k < a.size(); k++) if (!java.util.Arrays.equals(a.get(k), b.get(k))) return false;
        return true;
    }

    /** Whether every setting matches. {@code ShapeSettingsTest} checks that this and {@link #set} cover every field. */
    public boolean same(ShapeSettings o) {
        return same(pts, o.pts) && same(pts3, o.pts3) && same(sPts, o.sPts) && gen == o.gen && eW == o.eW && eH == o.eH && eMode == o.eMode && eT == o.eT
                && src.equals(o.src) && xmin.equals(o.xmin) && xmax.equals(o.xmax) && ymin.equals(o.ymin) && ymax.equals(o.ymax)
                && qW == o.qW && qH == o.qH && qLock == o.qLock && qMode == o.qMode && qLW == o.qLW
                && bW == o.bW && bH == o.bH && bMode == o.bMode && bLW == o.bLW && snap == o.snap
                && e3W == o.e3W && e3H == o.e3H && e3D == o.e3D && e3Mode == o.e3Mode && e3T == o.e3T
                && tRing == o.tRing && tTube == o.tTube && tW == o.tW && tH == o.tH && tD == o.tD && tHollow == o.tHollow
                && src3.equals(o.src3) && x3min.equals(o.x3min) && x3max.equals(o.x3max) && y3min.equals(o.y3min) && y3max.equals(o.y3max)
                && z3min.equals(o.z3min) && z3max.equals(o.z3max)
                && q3W == o.q3W && q3D == o.q3D && q3H == o.q3H && q3Lock == o.q3Lock && q3Mode == o.q3Mode && q3T == o.q3T
                && b3W == o.b3W && b3H == o.b3H && b3D == o.b3D && b3T == o.b3T
                && sW == o.sW && sH == o.sH && sD == o.sD && sRows == o.sRows && sCols == o.sCols && sT == o.sT
                && slab == o.slab && stair == o.stair && trap == o.trap && shelf == o.shelf && fence == o.fence && pane == o.pane
                && wall == o.wall && chain == o.chain && rod == o.rod
                && fullConnects == o.fullConnects && depth == o.depth && overwrite == o.overwrite && carve == o.carve && floor == o.floor
                && topColours == o.topColours;
    }
}
