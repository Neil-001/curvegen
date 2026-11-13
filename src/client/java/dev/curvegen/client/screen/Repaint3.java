package dev.curvegen.client.screen;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.PaintBlocks;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.paint.Catalog;
import dev.curvegen.core.paint.Paint;
import dev.curvegen.core.paint.Solve3Painter;
import dev.curvegen.core.Pieces.Family;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Future;
import net.minecraft.world.level.block.Block;

/**
 * Paints a screen's 3D solve on the worker thread whenever the solve or the paint changes, without solving again, and
 * keeps the last one finished until the next is ready. It also works out the picture's colour for every block.
 */
final class Repaint3 {
    /** A painted solve, with each block's index into {@code palette}, which colours the picture by "Block colours". */
    record View(Solve3Painter.Done done, int[] perBlock, int[] palette) {}

    private Future<View> job;
    private Solve3Painter jobSolve, shownSolve;
    private Paint jobPaint, shownPaint;
    private View shown;
    /** The Blocks tab's choices each was painted with: a fallback block or a colour may change with them. */
    private Map<Family, Block> jobChoices, shownChoices;

    /**
     * Call every frame with the solve on show, or null, the setting it was solved with for whether fences attach to
     * full blocks, and the settings. Returns true when a repaint has just finished.
     */
    boolean poll(Solve3Painter solve, boolean fullConnects, ShapeSettings s) {
        boolean done = false;
        if (job != null && job.isDone()) {
            View v = null;
            try { v = job.get(); } catch (Exception _) { }
            shown = v; shownSolve = jobSolve; shownPaint = jobPaint; shownChoices = jobChoices;
            job = null;
            done = true;
        }
        if (job == null && solve != null && (shownSolve != solve || shownPaint == null || !shownPaint.same(s.paint) || !BlockChoices.CHOICE.equals(shownChoices))) {
            Paint paint = s.paint.copy();
            Catalog catalog = paint.on() ? PaintBlocks.catalog(s) : null;
            int[] byFamily = Preview3.palette(PreviewTexture.Colors.BLOCKS, BlockChoices.CHOICE, s.topColours);
            jobSolve = solve; jobPaint = paint; jobChoices = new EnumMap<>(BlockChoices.CHOICE);
            job = CurveScreen.EXEC.submit(() -> {
                Solve3Painter.Done d = solve.paint(paint, fullConnects, catalog);
                if (d == null) return null;
                return new View(d, Solve3Painter.colourIndex(solve.result, d.painted()), PaintBlocks.palette3(d.painted(), byFamily, catalog));
            });
        }
        return done;
    }

    /** The paint of {@code solve} as {@code paint} has it, or null while it's unpainted or not ready yet. */
    View current(Solve3Painter solve, Paint paint) {
        return solve != null && shownSolve == solve && shownPaint != null && shownPaint.same(paint) ? shown : null;
    }

    /** Whether {@code solve} still waits for the paint {@code paint} asks for. */
    boolean pending(Solve3Painter solve, Paint paint) { return paint.on() && solve != null && current(solve, paint) == null; }
}
