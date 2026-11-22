package dev.curvegen.client.screen;

import dev.curvegen.client.chroma.Chroma;
import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.PaintBlocks;
import dev.curvegen.core.Mesh3;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.paint.Catalog;
import dev.curvegen.core.paint.Paint;
import dev.curvegen.core.paint.Solve3Painter;
import dev.curvegen.core.Pieces.Family;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Future;
import net.minecraft.world.level.block.Block;

final class Repaint3 {
    record View(Solve3Painter.Done done, int[] perBlock, int[] palette, Mesh3 mesh) {}

    private Future<View> job;
    private Solve3Painter jobSolve, shownSolve;
    private Paint jobPaint, shownPaint;
    private View shown;
    private Map<Family, Block> jobChoices, shownChoices;
    private boolean jobTop, shownTop;

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
            job = CurveScreen.EXEC.submit(() -> paint(solve, paint, fullConnects, catalog, byFamily));
        }
        return done;
    }

    static View paint(Solve3Painter solve, Paint paint, boolean fullConnects, Catalog catalog, int[] byFamily) {
        Solve3Painter.Done d = solve.paint(paint, fullConnects, catalog);
        if (d == null) return null;
        return new View(d, Solve3Painter.colourIndex(d.result(), d.painted()), PaintBlocks.palette3(d.painted(), byFamily, catalog),
                d.result() == solve.result ? null : new Mesh3(d.result()));
    }

    View current(Solve3Painter solve, Paint paint) {
        return paint.on() && solve != null && shownSolve == solve ? shown : null;
    }

    View fresh(Solve3Painter solve, ShapeSettings s) { return upToDate(solve, s) ? shown : null; }

    private boolean upToDate(Solve3Painter solve, ShapeSettings s) {
        return solve != null && shownSolve == solve && shownPaint != null && (shownPaint.same(s.paint) || !shownPaint.on() && !s.paint.on())
                && BlockChoices.CHOICE.equals(shownChoices) && shownTop == s.topColours
                && (!s.paint.on() || shownMatcher == Chroma.matcherVersion());
    }

    private int shownMatcher, jobMatcher;

    Paint shownPaint() { return shownPaint; }

    boolean pending(Solve3Painter solve, ShapeSettings s) { return s.paint.on() && solve != null && fresh(solve, s) == null; }
}
