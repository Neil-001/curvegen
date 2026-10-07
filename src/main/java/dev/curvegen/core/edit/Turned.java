package dev.curvegen.core.edit;

import dev.curvegen.core.Shape3;
import java.util.ArrayList;
import java.util.List;

/**
 * A 3D shape with its own axes pointing some other way in the world. The volumetric solver always works in the
 * world's orientation, so turning or tipping a shape solves it again through this rather than moving its blocks.
 */
public final class Turned implements Shape3 {
    private static final Orient UNTURNED = new Orient(Orient.EAST, Orient.UP, Orient.SOUTH);

    private final Shape3 in;
    /** For each own axis: the world axis it runs along, whether it runs backwards, and the shape's size along it. */
    private final int[] axis = new int[3], n;
    private final boolean[] back = new boolean[3];
    /** For each world axis, the own axis along it. */
    private final int[] own = new int[3];
    /** Whether the shape leaves {@link Shape3#uniform} alone, so its field can be trusted the same way in any direction. */
    private final boolean plain;

    public static Shape3 of(Shape3 shape, Orient o) { return o.equals(UNTURNED) ? shape : new Turned(shape, o); }

    private Turned(Shape3 in, Orient o) {
        this.in = in;
        n = new int[]{in.nx(), in.ny(), in.nz()};
        for (int a = 0; a < 3; a++) {
            axis[a] = Orient.worldAxis(o.dir(a));
            back[a] = Orient.sign(o.dir(a)) < 0;
            own[axis[a]] = a;
        }
        boolean plain;
        try {
            plain = in.getClass().getMethod("uniform", int.class, int.class, int.class).getDeclaringClass() == Shape3.class;
        } catch (NoSuchMethodException e) {
            plain = false;
        }
        this.plain = plain;
    }

    @Override public int nx() { return n[own[0]]; }
    @Override public int ny() { return n[own[1]]; }
    @Override public int nz() { return n[own[2]]; }

    private double coord(int a, double x, double y, double z) {
        double v = axis[a] == 0 ? x : axis[a] == 1 ? y : z;
        return back[a] ? n[a] - v : v;
    }

    @Override public double field(double x, double y, double z) { return in.field(coord(0, x, y, z), coord(1, x, y, z), coord(2, x, y, z)); }

    @Override
    public int uniform(int i, int j, int k) {
        if (plain) return Shape3.super.uniform(i, j, k);
        // The shape counts its runs along its own x, which is some other way from here. Only this block is certain.
        int run = in.uniform((int) coord(0, i + .5, j + .5, k + .5), (int) coord(1, i + .5, j + .5, k + .5), (int) coord(2, i + .5, j + .5, k + .5));
        return Integer.signum(run);
    }

    @Override public double lo() { return in.lo(); }
    @Override public double hi() { return in.hi(); }
    @Override public boolean smooth() { return in.smooth(); }
    @Override public boolean hollow() { return in.hollow(); }
    @Override public double[] carve() { return in.carve(); }
    @Override public int pad() { return in.pad(); }
    @Override public String error() { return in.error(); }

    private boolean sym(int worldAxis) { return own[worldAxis] == 0 ? in.symX() : own[worldAxis] == 1 ? in.symY() : in.symZ(); }
    @Override public boolean symX() { return sym(0); }
    @Override public boolean symY() { return sym(1); }
    @Override public boolean symZ() { return sym(2); }

    @Override
    public List<double[]> wireframe() {
        List<double[]> out = new ArrayList<>();
        for (double[] line : in.wireframe()) {
            double[] w = new double[line.length];
            for (int p = 0; p + 2 < line.length; p += 3)
                for (int a = 0; a < 3; a++) w[p + axis[a]] = back[a] ? n[a] - line[p + a] : line[p + a];
            out.add(w);
        }
        return out;
    }
}
