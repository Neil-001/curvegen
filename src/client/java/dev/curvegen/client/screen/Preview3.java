package dev.curvegen.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import dev.curvegen.client.ColorIndex;
import dev.curvegen.core.Mesh3;
import dev.curvegen.core.Pieces;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.Raster3;
import dev.curvegen.core.edit.Orbit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * The picture of a solved 3D shape: its faces as coloured boxes, with the ideal shape's outline over them.
 * {@link Raster3} draws it into a texture one screen pixel to a pixel, and only when the camera, the shape or the
 * colours have changed since the last frame.
 */
final class Preview3 implements AutoCloseable {
    /** The view, in the screen's own coordinates and in blocks from the lowest corner of the box the player set. */
    final Orbit cam = new Orbit();

    private Mesh3 mesh;
    private int meshPad, wirePad;
    private List<double[]> wires = List.of();
    private int[] palette = new int[Family.values().length];
    private boolean showWires = true, dirty = true;
    private Orbit drawn;

    private DynamicTexture tex;
    private Identifier id;
    private Raster3 raster;
    private int factor = 1;
    private static int nextId;

    /** The canvas's own colour, so the picture has no edge. */
    static final int BG = 0xFF15181D;

    /** No more pixels than this are drawn. A larger preview is drawn coarser and stretched. */
    private static final int MOST_PIXELS = 1_600_000;

    /** The faces to draw. {@code pad} is the room the shape's own box adds around the one the player set. */
    void mesh(Mesh3 m, int pad) { mesh = m; meshPad = pad; dirty = true; }

    /** The outline, as {@code Shape3.wireframe} gives it. */
    void wires(List<double[]> lines, int pad) { wires = lines; wirePad = pad; dirty = true; }

    void showWires(boolean on) { if (on != showWires) { showWires = on; dirty = true; } }

    void palette(int[] rgb) { if (!Arrays.equals(rgb, palette)) { palette = rgb.clone(); dirty = true; } }

    boolean hasMesh() { return mesh != null; }

    /** A colour per piece family, as the 2D preview's three colourings give them. */
    static int[] palette(PreviewTexture.Colors colors, Map<Family, Block> choice, boolean top) {
        int[] out = new int[Family.values().length];
        for (Family f : Family.values())
            out[f.ordinal()] = switch (colors) {
                case STONE -> PreviewTexture.STONE & 0xFFFFFF;
                case PIECES -> pieceColour(f);
                case BLOCKS -> choice.get(f) == null ? 0 : ColorIndex.of(choice.get(f), top);
            };
        return out;
    }

    /** The colour of the first 2D piece of a family, which the Piece types colouring is known by. */
    static int pieceColour(Family f) {
        for (int p = 1; p < Pieces.COUNT; p++) if (Pieces.FAMILY[p] == f) return Pieces.COLOR[p];
        return 0;
    }

    /** Looks at the whole of a shape {@code nx} × {@code ny} × {@code nz} blocks, padding included, in a rectangle of the screen. */
    void fit(int nx, int ny, int nz, int pad, int x0, int y0, int x1, int y1) {
        cam.fit(nx, ny, nz, x0, y0, x1, y1, 6);
        cam.tx -= pad; cam.ty -= pad; cam.tz -= pad;
    }

    /** Draws the picture into a rectangle of the screen, which the caller has filled and clipped to. */
    void draw(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1) {
        int gw = x1 - x0, gh = y1 - y0;
        if (gw <= 0 || gh <= 0) return;
        int k = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        while (k > 1 && (long) gw * k * gh * k > MOST_PIXELS) k--;
        int w = gw * k, h = gh * k;
        if (raster == null || raster.width != w || raster.height != h || id == null) {
            close();
            raster = new Raster3(w, h);
            factor = k;
            id = Identifier.fromNamespaceAndPath("curvegen", "preview3_" + nextId++);
            tex = new DynamicTexture(id::toString, new NativeImage(w, h, false));
            Minecraft.getInstance().getTextureManager().register(id, tex);
            dirty = true;
        }
        Orbit view = cam.copy();
        view.ox -= x0; view.oy -= y0;
        view = view.times(factor);
        if (dirty || !view.same(drawn)) {
            dirty = false; drawn = view;
            raster.clear(BG);
            if (mesh != null) raster.draw(mesh, shifted(view, meshPad), palette);
            if (showWires) {
                Orbit v = shifted(view, wirePad);
                for (double[] line : wires)
                    for (int p = 0; p + 5 < line.length; p += 3)
                        raster.line(v, line[p], line[p + 1], line[p + 2], line[p + 3], line[p + 4], line[p + 5], PreviewTexture.CURVE & 0xFFFFFF, factor);
            }
            NativeImage img = tex.getPixels();
            int[] px = raster.argb;
            for (int y = 0, i = 0; y < h; y++)
                for (int x = 0; x < w; x++, i++) img.setPixel(x, y, px[i]);
            tex.upload();
        }
        var m = ctx.pose();
        m.pushMatrix();
        m.translate(x0, y0);
        m.scale(1f / factor, 1f / factor);
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, w, h, w, h);
        m.popMatrix();
    }

    /** The view for something whose coordinates start {@code pad} blocks before the box's corner. */
    private static Orbit shifted(Orbit view, int pad) {
        if (pad == 0) return view;
        Orbit o = view.copy();
        o.tx += pad; o.ty += pad; o.tz += pad;
        return o;
    }

    @Override
    public void close() {
        if (id != null) Minecraft.getInstance().getTextureManager().release(id);
        id = null; tex = null; raster = null; drawn = null;
    }
}
