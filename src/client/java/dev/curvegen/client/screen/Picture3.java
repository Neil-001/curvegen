package dev.curvegen.client.screen;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.core.Message;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.paint.Paint;
import dev.curvegen.core.paint.Painted;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A painted 3D shape for a screen's picture, in its block colours. It solves the shape once, and again only when
 * something other than its paint changes, such as whether fences attach to full blocks; a paint change repaints the
 * solve on the worker thread and the old picture stays until the new one is ready. Dragging turns it.
 */
final class Picture3 implements AutoCloseable {
    private final Preview3 preview = new Preview3();
    private final Repaint3 repaint = new Repaint3();
    private Future<CurveScreen.Solved3> job;
    private AtomicBoolean cancel;
    private ShapeSettings solving, solvedFor;
    private CurveScreen.Solved3 solved;
    private int[] fittedIn;
    /** The paint on show, as of the last poll. */
    private Repaint3.View shown;

    /** Call every frame with the settings on show. */
    void poll(ShapeSettings s) {
        if (job != null && job.isDone()) {
            CurveScreen.Solved3 r = null;
            try { r = job.get(); } catch (Exception ignored) { }
            job = null;
            if (r != null) {
                solved = r; solvedFor = solving;
                preview.mesh(r.mesh(), r.result() == null ? 0 : r.result().shape().pad());
                fittedIn = null;
            }
        }
        if (solvedFor == null || !sameShape(s, solvedFor)) {
            if (job == null) {
                ShapeSettings copy = s.copy();
                solving = s.copy();
                AtomicBoolean cancelled = cancel = new AtomicBoolean();
                job = CurveScreen.EXEC.submit(() -> CurveScreen.solve3(copy, cancelled::get));
            } else if (solving != null && !sameShape(s, solving)) cancel.set(true);   // dropped, and solved again for the settings as they are
        }
        repaint.poll(solved == null ? null : solved.painter(), solved != null && solved.fullConnects(), s);
        Repaint3.View v = shown = view(s);
        if (solved != null && solved.result() != null) preview.mesh(v != null && v.mesh() != null ? v.mesh() : solved.mesh(), solved.result().shape().pad());
        if (v != null) preview.palette(v.palette(), v.perBlock());
        else preview.palette(Preview3.palette(PreviewTexture.Colors.BLOCKS, BlockChoices.CHOICE, s.topColours));
    }

    /** Whether the settings are those {@code solved} was solved with, paint aside. */
    private static boolean sameShape(ShapeSettings s, ShapeSettings solved) {
        ShapeSettings now = s.copy();
        now.paint.set(solved.paint);
        return now.same(solved);
    }

    private Repaint3.View view(ShapeSettings s) { return solved == null ? null : repaint.current(solved.painter(), s.paint); }

    /** The paint on show, as of the last poll, or null while it's unpainted or not ready. */
    Painted painted() { return shown == null ? null : shown.done().painted(); }

    /** The paint settings the last finished repaint was for, or null before the first. */
    Paint paintedFor() { return repaint.shownPaint(); }

    /** Why the shape can't be built, or null. */
    Message error() { return solved == null ? null : solved.error(); }

    /** Whether there's a solve to show yet. */
    boolean ready() { return solved != null; }

    /** Draws into a rectangle the caller has filled; clips to it. */
    void draw(GuiGraphics ctx, int x0, int y0, int x1, int y1) {
        if (solved == null || solved.result() == null) return;
        int[] in = {x0, y0, x1, y1};
        if (!Arrays.equals(in, fittedIn)) {
            fittedIn = in;
            var shape = solved.result().shape();
            preview.wires(List.of(), 0);
            preview.fit(shape.nx(), shape.ny(), shape.nz(), shape.pad(), x0, y0, x1, y1);
        }
        ctx.enableScissor(x0, y0, x1, y1);
        preview.draw(ctx, x0, y0, x1, y1);
        ctx.disableScissor();
    }

    /** Turns the view, for a drag across the picture. */
    void orbit(double dx, double dy) { preview.cam.orbit(dx, dy); }

    @Override
    public void close() {
        preview.close();
        if (cancel != null) cancel.set(true);
    }
}
