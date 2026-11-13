package dev.curvegen.client.screen;

import dev.curvegen.client.chroma.Chroma;
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
    /** The Blocks tab's choices and the face colours are matched by, for each: blocks and colours change with them. */
    private Map<Family, Block> jobChoices, shownChoices;
    private boolean jobTop, shownTop;

    /**
     * Call every frame with the solve on show, or null, the setting it was solved with for whether fences attach to
     * full blocks, and the settings. Returns true when a repaint has just finished.
     */
    boolean poll(Solve3Painter solve, boolean fullConnects, ShapeSettings s) {
        boolean done = false;
        if (job != null && job.isDone()) {
            View v = null;
            try { v = job.get(); } catch (Exception _) { }
            shown = v; shownSolve = jobSolve; shownPaint = jobPaint; shownChoices = jobChoices; shownTop = jobTop; shownMatcher = jobMatcher;
            job = null;
            done = true;
        }
        if (job == null && solve != null && !upToDate(solve, s)) {
            Paint paint = s.paint.copy();
            Catalog catalog = paint.on() ? PaintBlocks.catalog(s) : null;
            int[] byFamily = Preview3.palette(PreviewTexture.Colors.BLOCKS, BlockChoices.CHOICE, s.topColours);
            jobSolve = solve; jobPaint = paint; jobChoices = new EnumMap<>(BlockChoices.CHOICE); jobTop = s.topColours;
            jobMatcher = Chroma.matcherVersion();
            job = CurveScreen.EXEC.submit(() -> {
                Solve3Painter.Done d = solve.paint(paint, fullConnects, catalog);
                if (d == null) return null;
                return new View(d, Solve3Painter.colourIndex(solve.result, d.painted()), PaintBlocks.palette3(d.painted(), byFamily, catalog));
            });
        }
        return done;
    }

    /**
     * The paint to show on {@code solve}: the last finished for it, kept while a repaint for other paint settings runs,
     * or null while paint is off or the solve hasn't been painted yet.
     */
    View current(Solve3Painter solve, Paint paint) {
        return paint.on() && solve != null && shownSolve == solve ? shown : null;
    }

    /** The paint of {@code solve} exactly as the settings ask for it, or null while it's unpainted or not ready yet. */
    View fresh(Solve3Painter solve, ShapeSettings s) { return upToDate(solve, s) ? shown : null; }

    /** Whether the last repaint finished, or failed, for {@code solve} as the settings stand. Paint that is off is all alike. */
    private boolean upToDate(Solve3Painter solve, ShapeSettings s) {
        return solve != null && shownSolve == solve && shownPaint != null && (shownPaint.same(s.paint) || !shownPaint.on() && !s.paint.on())
                && BlockChoices.CHOICE.equals(shownChoices) && shownTop == s.topColours
                && (!s.paint.on() || shownMatcher == Chroma.matcherVersion());
    }

    /** {@link Chroma#matcherVersion} for the repaint on show and the one under way: when it moves on, the shape is painted again. */
    private int shownMatcher, jobMatcher;

    /** The paint settings the last finished repaint was for, or null before the first. */
    Paint shownPaint() { return shownPaint; }

    /** Whether {@code solve} still waits for the paint the settings ask for, as an export must. */
    boolean pending(Solve3Painter solve, ShapeSettings s) { return s.paint.on() && solve != null && fresh(solve, s) == null; }
}
