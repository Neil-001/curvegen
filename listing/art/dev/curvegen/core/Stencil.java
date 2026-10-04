package dev.curvegen.core;

/** A target from any picture, for artwork the three generators can't draw. Lives in this package to reach Target's internals. */
public final class Stencil {
    private Stencil() {}

    /** in holds 16×16 pixels a cell, row 0 at the top. */
    public static Target of(boolean[] in, int nx, int ny) {
        Target t = new Target(nx, ny);
        int w = nx * 16;
        for (int idx = 0; idx < nx * ny; idx++)
            t.mixed(idx, (x, y) -> in[(ny * 16 - 1 - (int) (y * 16)) * w + (int) (x * 16)]);
        return t;
    }
}
