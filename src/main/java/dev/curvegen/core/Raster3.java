package dev.curvegen.core;

import dev.curvegen.core.edit.Orbit;
import java.util.Arrays;

/**
 * Draws a {@link Mesh3} into an array of pixels, as flat coloured faces lit by which way they face. It needs no
 * graphics card, so the same picture can be drawn and checked without the game running, and the menu only draws
 * again when the camera or the shape changes.
 *
 * <p>A picture is {@code width} × {@code height} ARGB pixels, row by row from the top, with a depth for each.
 * The camera has no perspective, so every face looking the same way is the same parallelogram on the screen:
 * a pixel finds its place on a face by one small matrix worked out per direction.
 */
public final class Raster3 {
    public final int width, height;
    public final int[] argb;
    private final float[] depth;
    /** Pixels a hidden line has already shown through, so stretches that overlap don't add up to a brighter line. */
    private final boolean[] faint;

    /** How bright each direction's faces are: north, east, south, west, down, up. */
    private static final float[] LIGHT = {0.74f, 0.62f, 0.82f, 0.56f, 0.45f, 1f};
    /** Below this many pixels to a block the lines between blocks would be all there is, so they're left out. */
    private static final double GRID_FROM = 5;
    private static final float GRID_SHADE = 0.8f;

    public Raster3(int width, int height) {
        this.width = width; this.height = height;
        argb = new int[width * height];
        depth = new float[width * height];
        faint = new boolean[width * height];
    }

    public void clear(int background) {
        Arrays.fill(argb, background);
        Arrays.fill(depth, Float.POSITIVE_INFINITY);
        Arrays.fill(faint, false);
    }

    static int shade(int rgb, float f) {
        int r = Math.min(255, (int) ((rgb >> 16 & 0xFF) * f)), g = Math.min(255, (int) ((rgb >> 8 & 0xFF) * f)), b = Math.min(255, (int) ((rgb & 0xFF) * f));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Draws the faces the camera can see. {@code palette} gives an RGB colour per piece family, by ordinal. */
    public void draw(Mesh3 mesh, Orbit cam, int[] palette) {
        double[] r = cam.right(), u = cam.up(), f = cam.forward();
        double s = cam.scale;
        // A step of one block along each world axis, on the screen and in depth.
        double[] ex = new double[3], ey = new double[3], ed = new double[3];
        for (int a = 0; a < 3; a++) { ex[a] = s * r[a]; ey[a] = -s * u[a]; ed[a] = f[a]; }
        double bx = cam.ox - (cam.tx * ex[0] + cam.ty * ex[1] + cam.tz * ex[2]);
        double by = cam.oy - (cam.tx * ey[0] + cam.ty * ey[1] + cam.tz * ey[2]);
        double bd = -(cam.tx * ed[0] + cam.ty * ed[1] + cam.tz * ed[2]);
        boolean grid = s >= GRID_FROM;
        int[] lit = new int[palette.length], dark = new int[palette.length];
        for (int d = 0; d < 6; d++) {
            int n = mesh.count[d], axis = Mesh3.AXIS[d], a = Mesh3.A[d], b = Mesh3.B[d];
            if (n == 0 || f[axis] * Mesh3.SIGN[d] > -1e-4) continue;      // turned away, or edge on
            double eax = ex[a], eay = ey[a], ebx = ex[b], eby = ey[b];
            double det = eax * eby - ebx * eay;
            if (Math.abs(det) < 1e-9) continue;
            // From a pixel to its place on the face: (a, b) = inverse · (pixel - base).
            double iax = eby / det, iay = -ebx / det, ibx = -eay / det, iby = eax / det;
            double lineA = 1 / Math.hypot(eax, eay), lineB = 1 / Math.hypot(ebx, eby);   // one pixel, in blocks
            for (int k = 0; k < palette.length; k++) { lit[k] = shade(palette[k], LIGHT[d]); dark[k] = shade(palette[k], LIGHT[d] * GRID_SHADE); }
            float[] rects = mesh.rects[d];
            byte[] fam = mesh.family[d];
            for (int q = 0, o = 0; q < n; q++, o += 5) {
                double a0 = rects[o], b0 = rects[o + 1], a1 = rects[o + 2], b1 = rects[o + 3], c = rects[o + 4];
                double cx = bx + c * ex[axis], cy = by + c * ey[axis];
                double xa0 = a0 * eax, xa1 = a1 * eax, xb0 = b0 * ebx, xb1 = b1 * ebx;
                double ya0 = a0 * eay, ya1 = a1 * eay, yb0 = b0 * eby, yb1 = b1 * eby;
                int px0 = (int) Math.ceil(cx + Math.min(xa0, xa1) + Math.min(xb0, xb1) - 0.5), px1 = (int) Math.floor(cx + Math.max(xa0, xa1) + Math.max(xb0, xb1) - 0.5);
                int py0 = (int) Math.ceil(cy + Math.min(ya0, ya1) + Math.min(yb0, yb1) - 0.5), py1 = (int) Math.floor(cy + Math.max(ya0, ya1) + Math.max(yb0, yb1) - 0.5);
                if (px0 < 0) px0 = 0;
                if (py0 < 0) py0 = 0;
                if (px1 >= width) px1 = width - 1;
                if (py1 >= height) py1 = height - 1;
                if (px0 > px1 || py0 > py1) continue;
                double dc = bd + c * ed[axis];
                int colour = lit[fam[q]], line = dark[fam[q]];
                for (int py = py0; py <= py1; py++) {
                    double qy = py + 0.5 - cy, qx = px0 + 0.5 - cx;
                    double fa = qx * iax + qy * iay, fb = qx * ibx + qy * iby;
                    int i = py * width + px0;
                    for (int px = px0; px <= px1; px++, i++, fa += iax, fb += ibx) {
                        if (fa < a0 || fa >= a1 || fb < b0 || fb >= b1) continue;
                        float z = (float) (dc + fa * ed[a] + fb * ed[b]);
                        if (z >= depth[i]) continue;
                        depth[i] = z;
                        argb[i] = grid && (fa - Math.floor(fa) < lineA || fb - Math.floor(fb) < lineB) ? line : colour;
                    }
                }
            }
        }
    }

    /**
     * Draws a line {@code thick} pixels wide over the picture. Where the shape is in front of it, it shows through
     * faintly, so an outline can be followed round the back.
     */
    public void line(Orbit cam, double x0, double y0, double z0, double x1, double y1, double z1, int rgb, int thick) {
        double[] p = cam.project(x0, y0, z0), q = cam.project(x1, y1, z1);
        // Only the part on the picture is walked, however far off it the line runs.
        double t0 = 0, t1 = 1;
        double[] lo = {-thick, -thick}, hi = {width + thick, height + thick};
        for (int a = 0; a < 2; a++) {
            double d = q[a] - p[a];
            if (Math.abs(d) < 1e-12) { if (p[a] < lo[a] || p[a] > hi[a]) return; continue; }
            double ta = (lo[a] - p[a]) / d, tb = (hi[a] - p[a]) / d;
            t0 = Math.max(t0, Math.min(ta, tb)); t1 = Math.min(t1, Math.max(ta, tb));
        }
        if (t0 > t1) return;
        double sx = p[0] + (q[0] - p[0]) * t0, sy = p[1] + (q[1] - p[1]) * t0, sz = p[2] + (q[2] - p[2]) * t0;
        double tx = p[0] + (q[0] - p[0]) * t1, ty = p[1] + (q[1] - p[1]) * t1, tz = p[2] + (q[2] - p[2]) * t1;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(tx - sx), Math.abs(ty - sy))));
        int cr = rgb >> 16 & 0xFF, cg = rgb >> 8 & 0xFF, cb = rgb & 0xFF;
        for (int k = 0; k <= steps; k++) {
            double t = (double) k / steps;
            int px = (int) Math.floor(sx + (tx - sx) * t) - thick / 2, py = (int) Math.floor(sy + (ty - sy) * t) - thick / 2;
            float z = (float) (sz + (tz - sz) * t) - 0.05f;
            for (int j = 0; j < thick; j++)
                for (int i = 0; i < thick; i++) {
                    int x = px + i, y = py + j;
                    if (x < 0 || y < 0 || x >= width || y >= height) continue;
                    int at = y * width + x;
                    if (z <= depth[at]) { argb[at] = 0xFF000000 | rgb; faint[at] = true; continue; }
                    if (faint[at]) continue;
                    faint[at] = true;
                    int old = argb[at];
                    argb[at] = 0xFF000000 | mix(old >> 16 & 0xFF, cr) << 16 | mix(old >> 8 & 0xFF, cg) << 8 | mix(old & 0xFF, cb);
                }
        }
    }

    private static int mix(int under, int over) { return under + (over - under) * 22 / 100; }

    /** The depth of what is drawn at a pixel, or infinity where there's nothing. */
    public float depthAt(int x, int y) { return depth[y * width + x]; }
}
