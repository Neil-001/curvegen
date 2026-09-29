package dev.curvegen.client.screen;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.ColorIndex;
import dev.curvegen.core.Pieces;
import dev.curvegen.core.Solver;
import dev.curvegen.core.Target;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** Paints a solved grid (pieces, grid lines, axes and the true curve) into one texture. */
public final class PreviewTexture implements AutoCloseable {
    public enum Colors { STONE, PIECES, BLOCKS }

    static final int BG = 0xFF1B1F25, STONE = 0xFFA9AAA6, GRID = 0x22FFFFFF, GRID_MAJOR = 0x55FFFFFF,
            AXIS = 0xAA6EA0FF, CURVE = 0xFFFF4D73;

    private NativeImageBackedTexture tex;
    private Identifier id;
    public int width, height, sub;

    public Identifier id() { return id; }

    public void update(Solver.Result r, Colors colors, boolean curve, boolean grid) {
        int n = Math.max(r.nx(), r.ny());
        sub = Math.max(2, Math.min(16, 1024 / Math.max(1, n)));
        int w = r.nx() * sub, h = r.ny() * sub;
        if (tex == null || width != w || height != h) {
            close();
            tex = new NativeImageBackedTexture(new NativeImage(w, h, false));
            id = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("curvegen_preview", tex);
            width = w; height = h;
        }
        NativeImage img = tex.getImage();
        img.fillRect(0, 0, w, h, abgr(BG));

        for (int j = 0; j < r.ny(); j++)
            for (int i = 0; i < r.nx(); i++) {
                int p = r.at(i, j);
                if (p == Pieces.EMPTY) continue;
                int rgb = colorFor(p, colors), edge = darken(rgb, 0.55f);
                int ox = i * sub, oy = (r.ny() - 1 - j) * sub;
                boolean[] in = shape(p), border = sub >= 8 ? outline(in, sub) : null;
                for (int y = 0; y < sub; y++)
                    for (int x = 0; x < sub; x++)
                        if (in[y * sub + x]) img.setColor(ox + x, oy + y, abgr(border != null && border[y * sub + x] ? edge : rgb));
            }

        if (grid && sub >= 4) {
            for (int i = 0; i <= r.nx(); i++) {
                int x = Math.min(w - 1, i * sub), c = i % 5 == 0 ? GRID_MAJOR : GRID;
                for (int y = 0; y < h; y++) blend(img, x, y, c);
            }
            for (int j = 0; j <= r.ny(); j++) {
                int y = Math.min(h - 1, (r.ny() - j) * sub), c = j % 5 == 0 ? GRID_MAJOR : GRID;
                for (int x = 0; x < w; x++) blend(img, x, y, c);
            }
        }
        Target t = r.target();
        if (t.axisX != null) { int x = (int) Math.round(t.axisX * sub); for (int y = 0; y < h; y += 1) if ((y / 4) % 2 == 0) blend(img, x, y, AXIS); }
        if (t.axisY != null) { int y = (int) Math.round((r.ny() - t.axisY) * sub); for (int x = 0; x < w; x++) if ((x / 4) % 2 == 0) blend(img, x, y, AXIS); }

        if (curve) {
            int thick = sub >= 8 ? 2 : 1;
            for (double[] segs : t.overlay) stroke(img, segs, r.ny(), thick, false);
            for (double[] segs : t.dashed) stroke(img, segs, r.ny(), 1, true);
        }
        tex.upload();
    }

    private boolean[][] shapeCache = new boolean[Pieces.COUNT][];
    private int shapeSub = -1;

    /** The piece scaled to sub×sub pixels (row 0 at the top), cached per scale. */
    private boolean[] shape(int p) {
        if (shapeSub != sub) { shapeCache = new boolean[Pieces.COUNT][]; shapeSub = sub; }
        boolean[] in = shapeCache[p];
        return in != null ? in : (shapeCache[p] = silhouette(p, sub));
    }

    /** A piece's silhouette at size×size pixels, row 0 at the top. */
    static boolean[] silhouette(int p, int size) {
        boolean[] in = new boolean[size * size];
        for (int[] q : Pieces.RECTS[p]) {
            int x0 = Math.round(q[0] * size / 16f), x1 = Math.max(x0 + 1, Math.round(q[2] * size / 16f));
            int top = size - Math.round(q[3] * size / 16f), bot = Math.max(top + 1, size - Math.round(q[1] * size / 16f));
            for (int y = top; y < bot; y++) for (int x = x0; x < x1; x++) in[y * size + x] = true;
        }
        return in;
    }

    /**
     * Pixels on the edge of the silhouette: any filled pixel touching an empty one, diagonals included, so inside
     * corners (like a stair's) are outlined too. Pieces without inside corners are unaffected by the diagonal check.
     */
    static boolean[] outline(boolean[] in, int size) {
        boolean[] e = new boolean[size * size];
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                if (!in[y * size + x]) continue;
                boolean edge = x == 0 || y == 0 || x == size - 1 || y == size - 1;
                for (int dy = -1; !edge && dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++)
                        if (!in[(y + dy) * size + x + dx]) { edge = true; break; }
                e[y * size + x] = edge;
            }
        return e;
    }

    /** Fill colour (ARGB) of a piece in the given colouring, exactly as the preview paints it. */
    static int colorFor(int p, Colors colors) {
        return switch (colors) {
            case STONE -> STONE;
            case PIECES -> 0xFF000000 | Pieces.COLOR[p];
            case BLOCKS -> 0xFF000000 | ColorIndex.of(BlockChoices.blockFor(p));
        };
    }

    private void stroke(NativeImage img, double[] s, int ny, int thick, boolean dashed) {
        double run = 0;
        for (int k = 0; k + 3 < s.length; k += 4) {
            double x1 = s[k] * sub, y1 = (ny - s[k + 1]) * sub, x2 = s[k + 2] * sub, y2 = (ny - s[k + 3]) * sub;
            double len = Math.hypot(x2 - x1, y2 - y1);
            int steps = Math.max(1, (int) Math.ceil(len * 2));
            for (int q = 0; q <= steps; q++) {
                double u = (double) q / steps;
                if (dashed && ((int) ((run + u * len) / 5)) % 2 == 1) continue;
                int px = (int) Math.floor(x1 + (x2 - x1) * u), py = (int) Math.floor(y1 + (y2 - y1) * u);
                for (int a = 0; a < thick; a++) for (int b = 0; b < thick; b++) {
                    int xx = px + a - thick / 2, yy = py + b - thick / 2;
                    if (xx >= 0 && yy >= 0 && xx < width && yy < height) img.setColor(xx, yy, abgr(CURVE));
                }
            }
            run += len;
        }
    }

    /** ARGB → the ABGR layout NativeImage uses. */
    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    static int darken(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f), g = (int) (((argb >> 8) & 0xFF) * f), b = (int) ((argb & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void blend(NativeImage img, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        int dst = img.getColor(x, y);   // ABGR
        float a = (argb >>> 24) / 255f;
        int sr = (argb >> 16) & 0xFF, sg = (argb >> 8) & 0xFF, sb = argb & 0xFF;
        int dr = dst & 0xFF, dg = (dst >> 8) & 0xFF, db = (dst >> 16) & 0xFF;
        int r = (int) (sr * a + dr * (1 - a)), g = (int) (sg * a + dg * (1 - a)), b = (int) (sb * a + db * (1 - a));
        img.setColor(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
    }

    @Override
    public void close() {
        if (id != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
        id = null; tex = null; width = height = 0;
    }
}
