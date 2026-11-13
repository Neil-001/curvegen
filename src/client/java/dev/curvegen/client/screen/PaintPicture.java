package dev.curvegen.client.screen;

import static dev.curvegen.client.Lang.tr;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.Lang;
import dev.curvegen.client.PaintBlocks;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.Solver;
import dev.curvegen.core.paint.Catalog;
import dev.curvegen.core.paint.Paint;
import dev.curvegen.core.paint.Painted;
import dev.curvegen.core.paint.Painter2D;
import java.util.concurrent.Future;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * A shape drawn in its block colours, painted: the Paint screen's picture, and the pattern library's, which shows the
 * pattern under the mouse. Each frame the screen passes the settings to show; the picture solves the shape once and
 * solves again only when something other than its paint changes, such as whether fences attach to full blocks, which
 * follows the pattern. A paint change repaints that solve on the worker thread, and the old picture stays until the
 * new one is ready.
 */
public final class PaintPicture {
    private final PreviewTexture texture = new PreviewTexture();
    /** The solve under way and what it's for, and the last one finished and what it was for. */
    private Future<Solver.Result> solveJob;
    private ShapeSettings solving, solvedFor;
    private Solver.Result result;
    /** The paint under way, and the last finished: its paint, the solve it painted, and the painted blocks. */
    private Future<Painted> paintJob;
    private Paint paintingFor, paintedFor;
    private Solver.Result paintingResult, paintedResult;
    private Painted painted;
    private boolean dirty;

    /** Whether the last solve was a line or an outline, which takes Repeat along as its fit unless one is chosen. */
    public boolean line() { return result != null && result.target().error == null && result.target().centre != null; }

    /** The last painted shape, and the paint it was painted with, or null until one is ready. */
    public Painted painted() { return painted; }
    public Paint paintedFor() { return paintedFor; }

    /** The error the shown shape's solve gave, as the player reads it, or null. */
    public String error() { return paintedResult != null && paintedResult.target().error != null ? Lang.text(paintedResult.target().error) : null; }

    /** Whether {@code want} is what {@code s} was solved with, paint aside. */
    private static boolean sameShape(ShapeSettings want, ShapeSettings s) {
        ShapeSettings now = want.copy();
        now.paint.set(s.paint);
        return now.same(s);
    }

    /** Starts or collects the work for showing {@code want}, which the picture keeps no hold on. */
    public void poll(ShapeSettings want) {
        if (want.is3d()) return;
        if (solveJob != null && solveJob.isDone()) {
            try { result = solveJob.get(); } catch (Exception _) { result = null; }
            solvedFor = solving;
            solveJob = null;
        }
        if (solveJob == null && (solvedFor == null || !sameShape(want, solvedFor))) {
            // The solve settles values in its own copy, such as a locked equation's height, so the one compared against stays as it was.
            ShapeSettings s = want.copy();
            solving = want.copy();
            solveJob = CurveScreen.EXEC.submit(() -> Solver.run(s));
        }
        if (paintJob != null && paintJob.isDone()) {
            try { painted = paintJob.get(); } catch (Exception _) { painted = null; }
            paintedFor = paintingFor; paintedResult = paintingResult;
            paintJob = null;
            dirty = true;
        }
        if (paintJob == null && solveJob == null && result != null && (paintedResult != result || !paintedFor.same(want.paint))) {
            Paint paint = want.paint.copy();
            Solver.Result r = result;
            int depth = solvedFor.depth;
            boolean connects = solvedFor.fullConnects;
            Catalog catalog = paint.on() ? PaintBlocks.catalog(want) : null;
            paintingFor = paint; paintingResult = r;
            paintJob = CurveScreen.EXEC.submit(() -> Painter2D.paint(r, depth, paint, connects, catalog));
        }
        if (paintedResult != null && texture.id() == null) dirty = true;   // released while another screen was open
        if (dirty && paintedResult != null && paintedResult.target().error == null) {
            dirty = false;
            texture.update(paintedResult, PreviewTexture.Colors.BLOCKS, false, true, BlockChoices.CHOICE, painted);
        }
    }

    /** Draws the picture in the box, or a note while there's none: 3D shapes, an error, or still working. */
    public void draw(GuiGraphicsExtractor ctx, Font font, boolean is3d, int px0, int py0, int px1, int py1) {
        ctx.fill(px0, py0, px1, py1, 0xFF15181D);
        ctx.outline(px0 - 1, py0 - 1, px1 - px0 + 2, py1 - py0 + 2, 0xFF3A424D);
        if (is3d) {
            note(ctx, font, tr("curvegen.paint.picture_3d"), px0, py0, px1, py1, 0xFF9AA5B3);
        } else if (error() != null) {
            note(ctx, font, error(), px0, py0, px1, py1, 0xFFFF8098);
        } else if (paintedResult == null || texture.id() == null) {
            note(ctx, font, tr("curvegen.screen.working"), px0, py0, px1, py1, 0xFF9AA5B3);
        } else {
            Solver.Result r = paintedResult;
            float aw = px1 - px0 - 12, ah = py1 - py0 - 12;
            float z = Math.min(aw / r.nx(), ah / r.ny());
            float ox = px0 + (px1 - px0 - r.nx() * z) / 2f, oy = py0 + (py1 - py0 - r.ny() * z) / 2f;
            ctx.enableScissor(px0, py0, px1, py1);
            var m = ctx.pose();
            m.pushMatrix();
            m.translate(ox, oy);
            float s = z / texture.sub;
            m.scale(s, s);
            ctx.blit(RenderPipelines.GUI_TEXTURED, texture.id(), 0, 0, 0f, 0f, texture.width, texture.height, texture.width, texture.height);
            m.popMatrix();
            ctx.disableScissor();
        }
    }

    /** A short message in the middle of the picture. */
    private static void note(GuiGraphicsExtractor ctx, Font font, String text, int x0, int y0, int x1, int y1, int colour) {
        var lines = font.split(Component.literal(text), x1 - x0 - 16);
        int y = (y0 + y1) / 2 - lines.size() * 9 / 2;
        for (var l : lines) { ctx.centeredText(font, l, (x0 + x1) / 2, y, colour); y += 9; }
    }

    /** Frees the texture. The picture makes it again if it's drawn after this. */
    public void close() { texture.close(); }
}
