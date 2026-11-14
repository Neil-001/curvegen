package dev.curvegen.core.paint;

import dev.curvegen.core.paint.Pattern.Cell;

/**
 * Turns a picture into a pattern of colour-only cells, one per block, by scaling it to a chosen width. The height
 * follows the picture's proportions, and neither may pass {@link Pattern#MAX}.
 *
 * <p>Each cell takes the average of the part of the picture it covers, each pixel weighted by how much of it the cell
 * covers and by how opaque it is, so a clear pixel's hidden colour never tints its neighbours. Colours are averaged in
 * linear light, as light mixes, so fine black and white stripes come out mid grey rather than dark grey.
 *
 * <p>A cell is empty when less than half of what it covers is opaque: its average alpha is below 128 out of 255. Each
 * colour is then rounded to 32 levels a channel, a step no block colour match can tell apart, which keeps the number of
 * distinct cells, and so the work of matching them, down for a photograph.
 */
public final class ImagePattern {
    private ImagePattern() {}

    /** A picture's pixels as 0xAARRGGBB, x from the left and y from the top. */
    public interface Pixels { int argb(int x, int y); }

    /** The widest pattern a {@code w}×{@code h} picture can make, with its height still within {@link Pattern#MAX}. */
    public static int maxWidth(int w, int h) {
        return (int) Math.max(1, Math.min(Pattern.MAX, Math.floor((Pattern.MAX + 0.5) * w / h)));
    }

    /** The pattern's height for a picture {@code w}×{@code h} scaled to {@code width} cells, at least 1 and at most {@link Pattern#MAX}. */
    public static int heightFor(int w, int h, int width) {
        return (int) Math.max(1, Math.min(Pattern.MAX, Math.round((double) h * width / w)));
    }

    /**
     * The picture scaled to {@code width} cells across, at most {@link #maxWidth}. Null when every cell would be empty,
     * as for a picture that is clear all over.
     */
    public static Pattern of(String name, Pixels img, int w, int h, int width) {
        if (w < 1 || h < 1 || width < 1 || width > maxWidth(w, h)) throw new IllegalArgumentException();
        int height = heightFor(w, h, width);
        double[] lin = new double[256];
        for (int k = 0; k < 256; k++) lin[k] = toLinear(k / 255.0);
        Cell[][] rows = new Cell[height][width];
        boolean any = false;
        for (int cy = 0; cy < height; cy++) {
            double y0 = (double) cy * h / height, y1 = (double) (cy + 1) * h / height;
            for (int cx = 0; cx < width; cx++) {
                double x0 = (double) cx * w / width, x1 = (double) (cx + 1) * w / width;
                double area = 0, alpha = 0, r = 0, g = 0, b = 0;
                for (int py = (int) y0; py < Math.min(h, Math.ceil(y1)); py++) {
                    double wy = Math.min(py + 1, y1) - Math.max(py, y0);
                    for (int px = (int) x0; px < Math.min(w, Math.ceil(x1)); px++) {
                        double cover = wy * (Math.min(px + 1, x1) - Math.max(px, x0));
                        int c = img.argb(px, py);
                        double a = (c >>> 24) / 255.0 * cover;
                        area += cover;
                        alpha += a;
                        r += a * lin[c >> 16 & 0xFF];
                        g += a * lin[c >> 8 & 0xFF];
                        b += a * lin[c & 0xFF];
                    }
                }
                if (alpha * 255 < 127.5 * area) { rows[cy][cx] = Cell.EMPTY; continue; }
                rows[cy][cx] = Cell.colour(level(r / alpha) << 16 | level(g / alpha) << 8 | level(b / alpha));
                any = true;
            }
        }
        return any ? Pattern.of(name, rows) : null;
    }

    /** A linear channel back in sRGB, rounded to one of 32 levels and written as 0 to 255. */
    static int level(double linear) {
        int q = (int) Math.round(fromLinear(Math.max(0, Math.min(1, linear))) * 31);
        return (q * 255 + 15) / 31;
    }

    private static double toLinear(double c) { return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
    private static double fromLinear(double c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055; }
}
