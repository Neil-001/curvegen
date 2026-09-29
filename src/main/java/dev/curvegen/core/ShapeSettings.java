package dev.curvegen.core;

import java.util.ArrayList;
import java.util.List;

/** Everything the user can set for the shape. Kept in memory between screen openings. */
public final class ShapeSettings {
    public enum Gen { ELLIPSE, EQUATION, BEZIER }
    public enum EllipseMode { THIN, FILLED, OUTWARDS, INWARDS, MIDDLE }
    public enum EqMode { LINE, UNDER, OVER }
    public enum BzMode { LINE, FILLED }

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

    /** Piece families the solver may use (full blocks are always allowed). */
    public boolean slab = true, stair = true, trap = true, fence = true, pane = true, wall = true;
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

    /** Independent copy, so the solver can run on a worker thread while the UI keeps editing. */
    public ShapeSettings copy() {
        ShapeSettings c = new ShapeSettings();
        c.gen = gen; c.eW = eW; c.eH = eH; c.eMode = eMode; c.eT = eT;
        c.src = src; c.xmin = xmin; c.xmax = xmax; c.ymin = ymin; c.ymax = ymax;
        c.qW = qW; c.qH = qH; c.qLock = qLock; c.qMode = qMode; c.qLW = qLW;
        c.bW = bW; c.bH = bH; c.bMode = bMode; c.bLW = bLW; c.snap = snap;
        c.pts.clear();
        for (double[] p : pts) c.pts.add(p.clone());
        c.slab = slab; c.stair = stair; c.trap = trap; c.fence = fence; c.pane = pane; c.wall = wall;
        c.fullConnects = fullConnects; c.depth = depth; c.overwrite = overwrite; c.carve = carve; c.floor = floor;
        return c;
    }
}
