package dev.curvegen.core;

import java.util.List;

public interface Shape3 {
    int MAX_SIZE = 256;
    double R_CELL = 0.8661;

    int nx();
    int ny();
    int nz();

    double field(double x, double y, double z);

    default double lo() { return Double.NEGATIVE_INFINITY; }
    default double hi() { return 0; }

    default int uniform(int i, int j, int k) {
        double d = field(i + .5, j + .5, k + .5), lo = lo(), hi = hi();
        if (Double.isNaN(d)) return 0;
        boolean in = d >= lo && d <= hi;
        double m = in ? Math.min(d - lo, hi - d) : d > hi ? d - hi : lo - d;
        if (!(m > R_CELL)) return 0;
        int n = 1 + (int) Math.min(MAX_SIZE * 2, Math.floor(m - R_CELL));
        return in ? n : -n;
    }

    default boolean uniformRunsInAllDirections() { return false; }

    default boolean smooth() { return true; }

    default void lattice(int i, int j, int k, double[] lattice) {
        double e = inset();
        for (int b = 0, q = 0; b < 5; b++)
            for (int c = 0; c < 5; c++)
                for (int a = 0; a < 5; a++)
                    lattice[q++] = field(i + (a == 0 ? e : a == 4 ? 1 - e : a * .25), j + (b == 0 ? e : b == 4 ? 1 - e : b * .25), k + (c == 0 ? e : c == 4 ? 1 - e : c * .25));
    }

    default boolean follows(double[] lattice) { return true; }

    default double inset() { return 0; }

    default boolean hollow() { return false; }

    default double[] carve() { return null; }

    default boolean cuts() { return false; }

    default boolean cut(double x, double y, double z) { return false; }

    default boolean symX() { return false; }
    default boolean symY() { return false; }
    default boolean symZ() { return false; }

    default int pad() { return 0; }

    List<double[]> wireframe();

    default Message error() { return null; }

    default Wrapping wrapping() { return null; }

    interface Wrapping {
        double length();
        double width();
        boolean closedAlong();
        boolean closedAcross();
        void place(double x, double y, double z, double[] out);
    }

    static Shape3 of(ShapeSettings s) {
        return switch (s.gen) {
            case ELLIPSOID -> new Shapes3.Ellipsoid(s);
            case TORUS -> new Shapes3.Torus(s);
            case EQUATION3 -> new Shapes3.Equation(s);
            case BEZIER3 -> new Shapes3.Curve(s);
            case SURFACE -> new Shapes3.Patch(s);
            default -> throw new IllegalArgumentException(Message.of("curvegen.diagnostic.not_shape3", s.gen).toString());
        };
    }
}
