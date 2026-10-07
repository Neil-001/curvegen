package dev.curvegen.core;

import java.util.List;

/**
 * A 3D shape as the volumetric solver sees it: a box of blocks and a number at every point in it. The shape is
 * solid wherever that number, the field, lies from {@link #lo} to {@link #hi}. Coordinates are in blocks from the
 * box's lowest corner, in the world's orientation: x east, y up, z south.
 *
 * A shell is a band of a signed distance (0 to the thickness, say) rather than a field with a crease in the
 * middle of the wall, so the solver can interpolate it. To add a shape, implement the three sizes, the field and
 * the wireframe. The rest have defaults.
 */
public interface Shape3 {
    /** No shape may be larger than this along any axis, before padding. */
    int MAX_SIZE = 256;
    /** Half a block's diagonal: a field value this far inside the band means the whole block is. */
    double R_CELL = 0.8661;

    int nx();
    int ny();
    int nz();

    /**
     * The field at a point. By default the solver assumes it changes by at most 1 per block, as a signed distance
     * does, and that it's smooth enough to interpolate between samples a quarter of a block apart. Scale a field
     * down until the first holds, or override {@link #uniform}; override {@link #smooth} if the second doesn't.
     * NaN counts as outside.
     */
    double field(double x, double y, double z);

    default double lo() { return Double.NEGATIVE_INFINITY; }
    default double hi() { return 0; }

    /**
     * How many blocks from this one along +x are certainly all solid (a positive count) or all empty (a negative
     * one). 0 means the block may hold the surface, and the solver samples it. The default relies on the field
     * changing by at most 1 per block.
     */
    default int uniform(int i, int j, int k) {
        double d = field(i + .5, j + .5, k + .5), lo = lo(), hi = hi();
        if (Double.isNaN(d)) return 0;
        boolean in = d >= lo && d <= hi;
        double m = in ? Math.min(d - lo, hi - d) : d > hi ? d - hi : lo - d;
        if (!(m > R_CELL)) return 0;
        int n = 1 + (int) Math.min(MAX_SIZE * 2, Math.floor(m - R_CELL));
        return in ? n : -n;
    }

    /** False makes the solver evaluate the field at all 4096 points of a block that holds the surface. It's slow. */
    default boolean smooth() { return true; }

    /**
     * Samples a block for the solver to interpolate: the field at (i + a/4, j + b/4, k + c/4) goes in
     * lattice[(b*5 + c)*5 + a], with the outermost samples {@link #inset} inside the block's faces. A shape whose
     * field is quicker to work out from a neighbouring sample's answer overrides this. Only asked of a {@link #smooth} shape.
     */
    default void lattice(int i, int j, int k, double[] lattice) {
        double e = inset();
        for (int b = 0, q = 0; b < 5; b++)
            for (int c = 0; c < 5; c++)
                for (int a = 0; a < 5; a++)
                    lattice[q++] = field(i + (a == 0 ? e : a == 4 ? 1 - e : a * .25), j + (b == 0 ? e : b == 4 ? 1 - e : b * .25), k + (c == 0 ? e : c == 4 ? 1 - e : c * .25));
    }

    /**
     * Can the solver interpolate between those samples? A field that jumps in some blocks only, as an equation
     * with a floor in it does, returns false for those, and the solver evaluates every point there instead.
     */
    default boolean follows(double[] lattice) { return true; }

    /**
     * How far inside a block's faces the solver takes its outermost samples. A field that can jump on a block
     * boundary sets this just above 0, so a block never sees the far side of a jump along its own face.
     */
    default double inset() { return 0; }

    /** Should full blocks that no face shows be removed after solving? */
    default boolean hollow() { return false; }

    /**
     * The space the shape encloses, which carving clears: {low, high}, for blocks whose centre has a field value
     * strictly between the two. Null when there's nothing to carve.
     */
    default double[] carve() { return null; }

    /** Is the field unchanged when mirrored through the middle of the box along this axis? The solver then works on half. */
    default boolean symX() { return false; }
    default boolean symY() { return false; }
    default boolean symZ() { return false; }

    /** Blocks of room added on every side of the size the player set, for a shell that grows outwards. */
    default int pad() { return 0; }

    /** The ideal shape as lines to draw: each array is one polyline {x0, y0, z0, x1, y1, z1, ...}. Cheap to call. */
    List<double[]> wireframe();

    /** A message for the player when the shape can't be built, or null. */
    default String error() { return null; }

    /** The shape the settings describe. Building it is cheap: the work happens in {@link Solver3#solve}. */
    static Shape3 of(ShapeSettings s) {
        return switch (s.gen) {
            case ELLIPSOID -> new Shapes3.Ellipsoid(s);
            case TORUS -> new Shapes3.Torus(s);
            case EQUATION3 -> new Shapes3.Equation(s);
            case BEZIER3 -> new Shapes3.Curve(s);
            case SURFACE -> new Shapes3.Patch(s);
            default -> throw new IllegalArgumentException(s.gen + " is not a 3D shape");
        };
    }
}
