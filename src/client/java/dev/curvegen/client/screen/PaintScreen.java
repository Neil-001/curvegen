package dev.curvegen.client.screen;

import static dev.curvegen.client.Lang.tr;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.Lang;
import dev.curvegen.client.PaintBlocks;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.Solver;
import dev.curvegen.core.edit.Stepper;
import dev.curvegen.core.paint.Catalog;
import dev.curvegen.core.paint.Ladder.Why;
import dev.curvegen.core.paint.Paint;
import dev.curvegen.core.paint.Paint.Empty;
import dev.curvegen.core.paint.Paint.Fit;
import dev.curvegen.core.paint.Paint.On;
import dev.curvegen.core.paint.Paint.Source;
import dev.curvegen.core.paint.Painted;
import dev.curvegen.core.paint.Painter2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The Paint screen: the paint source and its settings on the left, and the shape on the right in its block colours,
 * painted as the settings stand. Every control changes {@code ShapeSettings.paint} at once, and the editor and the main
 * screen repaint from it. The rows under Source belong to the source, built by one method per source, so a new source
 * brings its own rows without reshaping the screen. With Source: Off the pattern's rows stay, greyed out.
 *
 * <p>The picture solves the shape once and solves again only when something other than its paint changes, such as
 * whether fences attach to full blocks, which follows the pattern. A paint change repaints that solve on the worker
 * thread, and the old picture stays until the new one is ready.
 */
public class PaintScreen extends ControlScreen {
    private static final ShapeSettings S = CurveGenClient.SETTINGS;
    /**
     * Margin, top of the rows, rows of controls, a number field's width with its arrows and Size's, which holds one
     * digit, the gap between controls and the padding round a button's text.
     */
    private static final int M = 6, TOP = 24, ROWS = 8, NUM = 36, DIGIT = 28, GAP = 4, PAD = 8;
    private final Screen parent;

    private int pw, pitch, h;
    private record Label(int x, int y, String text) {}
    private final List<Label> labels = new ArrayList<>();
    private int swatchY = -1;
    /** Whether the shape on show was a line when the Fit button was made, as the default fit depends on it. */
    private boolean lineShown;

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
    private boolean pictureDirty;

    public PaintScreen(Screen parent) {
        super(Component.translatable("curvegen.paint.title"));
        this.parent = parent;
    }

    static Component sourceName(Source s) { return Component.translatable("curvegen.paint.source." + s.name().toLowerCase(Locale.ROOT)); }
    private static Component onName(On o) { return Component.translatable("curvegen.paint.on." + o.name().toLowerCase(Locale.ROOT)); }
    private static Component fitName(Fit f) { return Component.translatable("curvegen.paint.fit." + f.name().toLowerCase(Locale.ROOT)); }
    private static Component emptyName(Empty e) { return Component.translatable("curvegen.paint.empty." + e.name().toLowerCase(Locale.ROOT)); }

    // ---------- layout ----------
    private int y(int row) { return TOP + row * pitch; }
    private int pvX0() { return M + pw + 8; }
    private int pvY1() { return height - 32; }

    /** The width of a button's text: a name and a value, as an option button shows them. */
    private int opt(String name, Component value) { return tw(CommonComponents.optionNameValue(Component.literal(name), value).getString()); }

    private int onOffWidth(String name) { return Math.max(opt(name, CommonComponents.optionStatus(true)), opt(name, CommonComponents.optionStatus(false))); }

    /** As wide as the widest row needs, from 170 pixels up to half the screen. The picture takes the rest. */
    private int panelWidth() {
        int need = 170;
        for (Source s : Source.values()) need = Math.max(need, opt(tr("curvegen.paint.source"), sourceName(s)) + PAD);
        for (On o : On.values()) need = Math.max(need, opt(tr("curvegen.paint.pattern_on"), onName(o)) + PAD);
        int fit = 0;
        for (Fit f : Fit.values()) fit = Math.max(fit, opt(tr("curvegen.paint.fit"), fitName(f)));
        need = Math.max(need, fit + PAD + GAP + tw(tr("curvegen.paint.size")) + 3 + DIGIT);
        need = Math.max(need, tw(tr("curvegen.paint.turn")) + 3 + NUM + GAP + onOffWidth(tr("curvegen.paint.mirror")) + PAD);
        need = Math.max(need, 2 * (Math.max(tw(tr("curvegen.paint.along")), tw(tr("curvegen.paint.across"))) + 3 + NUM) + GAP);
        need = Math.max(need, opt(tr("curvegen.paint.empty"), emptyName(Empty.FILL)) + PAD);
        need = Math.max(need, onOffWidth(tr("curvegen.paint.one_material")) + PAD);
        return Math.min(need, width / 2);
    }

    private <W extends AbstractWidget> W add(W w, String tip) {
        w.setHeight(h);
        if (tip != null) w.setTooltip(Tooltip.create(Component.literal(tip)));
        return addRenderableWidget(w);
    }

    private <W extends AbstractWidget> W add(W w, boolean active, String tip, String offTip) {
        w.active = active;
        return add(w, active ? tip : offTip);
    }

    @Override
    protected void init() {
        labels.clear();
        swatchY = -1;
        pw = panelWidth();
        // On short screens the rows close up, as the Blocks tab's do, leaving a line for the status under them.
        pitch = Math.min(22, (height - 30 - TOP - 12) / ROWS);
        h = Math.min(20, pitch - 1);
        lineShown = line();

        Source src = S.paint.source;
        add(cycler(List.of(Source.values()), src, PaintScreen::sourceName, M, y(0), pw, tr("curvegen.paint.source"), v -> {
            S.paint.source = v;
            paintChanged(true);
        }), tr("curvegen.paint.source_tip"));
        switch (src) {
            case OFF, PATTERN -> patternRows(src == Source.PATTERN);
        }
        oneMaterialRow(y(ROWS - 1));

        int dw = Math.max(80, tw(tr("curvegen.screen.done")) + 24);
        addRenderableWidget(Button.builder(Component.translatable("curvegen.screen.done"), _ -> onClose()).bounds(width - M - dw, height - 26, dw, 20).build());
    }

    /**
     * After a change to the paint. A change of source or pattern can change whether fences, panes and walls attach to
     * full blocks, which the shape is solved with, so that is worked out again; the picture and the main screen solve
     * again when it changes.
     */
    private void paintChanged(boolean rebuild) {
        S.fullConnects = BlockChoices.fullConnects(S);
        if (rebuild) rebuildWidgets();
    }

    /** The rows a pattern has, from the second to the seventh. {@code on} is false while paint is off: they show greyed. */
    private void patternRows(boolean on) {
        Paint p = S.paint;
        String offTip = tr("curvegen.paint.off_tip");

        // Which pattern, with its colours beside it. It can be chosen while paint is off: choosing one turns it on.
        swatchY = y(1) + (h - 16) / 2;
        String name = PatternChooserScreen.name(p.pattern), text = name + "…";
        int bw = pw - 20;
        boolean cut = tw(text) > bw - 8;
        if (cut) text = font.plainSubstrByWidth(name, bw - 8 - tw("…")) + "…";
        add(Button.builder(Component.literal(text), _ -> minecraft.gui.setScreen(new PatternChooserScreen(this, chosen -> {
            S.paint.pattern = chosen;
            if (!S.paint.on()) S.paint.source = Source.PATTERN;
            paintChanged(false);
        }))).bounds(M + 20, y(1), bw, h).build(), cut ? name + '\n' + tr("curvegen.paint.choose_tip") : tr("curvegen.paint.choose_tip"));

        // Face or edge, for a 2D shape more than one block deep. 3D shapes will have their mapping here.
        boolean deep = S.depth > 1;
        add(cycler(List.of(On.values()), p.onFor(S.depth), PaintScreen::onName, M, y(2), pw, tr("curvegen.paint.pattern_on"), v -> {
            S.paint.on = v;
        }), on && !S.is3d() && deep, tr("curvegen.paint.on_tip"),
                S.is3d() ? tr("curvegen.paint.not_3d") : !on ? offTip : tr("curvegen.paint.on_depth_tip"));

        // Fit and Size.
        int sizeW = tw(tr("curvegen.paint.size")) + 3 + DIGIT, fitW = pw - GAP - sizeW;
        add(cycler(List.of(Fit.values()), p.fitFor(lineShown), PaintScreen::fitName, M, y(3), fitW, tr("curvegen.paint.fit"), v -> {
            S.paint.fit = v;
            paintChanged(true);   // Size greys out under Stretch
        }), on, tr("curvegen.paint.fit_tip"), offTip);
        boolean sized = on && p.fitFor(lineShown) != Fit.STRETCH;
        number(M + fitW + GAP, y(3), sizeW, tr("curvegen.paint.size"), () -> S.paint.size, v -> S.paint.size = v, 1, 1, Paint.MAX_SIZE,
                sized, tr("curvegen.paint.size_tip"), on ? tr("curvegen.paint.size_stretch_tip") : offTip);

        // Turn and Mirror.
        int mirrorW = onOffWidth(tr("curvegen.paint.mirror")) + PAD, turnW = pw - GAP - mirrorW;
        turn(M, y(4), turnW, on, offTip);
        add(toggle(p.mirror, M + turnW + GAP, y(4), mirrorW, tr("curvegen.paint.mirror"), v -> S.paint.mirror = v),
                on, tr("curvegen.paint.mirror_tip"), offTip);

        // Slide along and across.
        int half = (pw - GAP) / 2;
        number(M, y(5), half, tr("curvegen.paint.along"), () -> S.paint.along, v -> S.paint.along = v, 1, -Paint.MAX_SLIDE, Paint.MAX_SLIDE,
                on, tr("curvegen.paint.along_tip"), offTip);
        number(M + half + GAP, y(5), pw - half - GAP, tr("curvegen.paint.across"), () -> S.paint.across, v -> S.paint.across = v, 1,
                -Paint.MAX_SLIDE, Paint.MAX_SLIDE, on, tr("curvegen.paint.across_tip"), offTip);

        // Empty cells: only Fill so far.
        add(cycler(List.of(Empty.FILL), Empty.FILL, PaintScreen::emptyName, M, y(6), pw, tr("curvegen.paint.empty"), v -> S.paint.empty = v),
                false, null, on ? tr("curvegen.paint.empty_tip") : offTip);
    }

    /** "One material per colour", which every source has, each with its own setting. */
    private void oneMaterialRow(int y) {
        Source src = S.paint.source;
        boolean value = switch (src) { case OFF, PATTERN -> S.paint.patternOneMaterial; };
        add(toggle(value, M, y, pw, tr("curvegen.paint.one_material"), v -> {
            switch (src) { case OFF, PATTERN -> S.paint.patternOneMaterial = v; }
        }), S.paint.on(), tr("curvegen.paint.one_material_tip"), tr("curvegen.paint.off_tip"));
    }

    /** A labelled whole-number field with arrows. Typing sets any value within the limits; the wheel and arrows step it. */
    private void number(int x, int y, int w, String label, IntSupplier get, IntConsumer set, int step, int min, int max,
                        boolean active, String tip, String offTip) {
        int lw = tw(label) + 3;
        labels.add(new Label(x, y + (h - 8) / 2, label));
        EditBox f = spin(x + lw, y, w - lw, h, String.valueOf(get.getAsInt()), v -> {
            int n = parse(v, min, max, get.getAsInt());
            if (n != get.getAsInt()) set.accept(n);
        }, get::getAsInt, step, min, max, () -> active, true);
        f.setEditable(active);
        f.setTooltip(Tooltip.create(Component.literal(active ? tip : offTip)));
    }

    /** Turn: typing sets any whole degree, and the wheel and arrows step 15 degrees onto the next multiple, round past 0. */
    private void turn(int x, int y, int w, boolean active, String offTip) {
        String label = tr("curvegen.paint.turn");
        int lw = tw(label) + 3, fw = w - lw - ARROW_SLOT;
        labels.add(new Label(x, y + (h - 8) / 2, label));
        EditBox f = new EditBox(font, x + lw, y, fw, h, Component.literal(label));
        f.setMaxLength(4);
        f.setValue(String.valueOf(S.paint.turn));
        f.setResponder(v -> {
            int n = parse(v, 0, 359, S.paint.turn);
            if (n != S.paint.turn) S.paint.turn = n;
        });
        f.setEditable(active);
        f.setTooltip(Tooltip.create(Component.literal(active ? tr("curvegen.paint.turn_tip") : offTip)));
        addRenderableWidget(f);
        IntConsumer step = dir -> write(f, Math.floorMod((int) Stepper.next(S.paint.turn, 15, dir, -15, 360), 360), true);
        int ax = x + w - ARROW_SLOT + 1, top = h / 2;
        BooleanSupplier on = () -> active;
        addRenderableWidget(arrow(ax, y, ARROW_SLOT - 1, top, true, () -> step.accept(1))).active = active;
        addRenderableWidget(arrow(ax, y + top, ARROW_SLOT - 1, h - top, false, () -> step.accept(-1))).active = active;
        wheel(f, step, on);
    }

    private static int parse(String v, int lo, int hi, int fallback) {
        try { return Math.max(lo, Math.min(hi, Integer.parseInt(v.trim()))); } catch (NumberFormatException _) { return fallback; }
    }

    // ---------- the picture ----------
    /** Whether the shape on show is a line or an outline, which takes Repeat along as its fit unless one is chosen. */
    private boolean line() { return result != null && result.target().error == null && result.target().centre != null; }

    /** Whether the settings are those {@code s} was solved with, paint aside. */
    private static boolean sameShape(ShapeSettings s) {
        ShapeSettings now = S.copy();
        now.paint.set(s.paint);
        return now.same(s);
    }

    private void poll() {
        if (S.is3d()) return;
        if (solveJob != null && solveJob.isDone()) {
            try { result = solveJob.get(); } catch (Exception _) { result = null; }
            solvedFor = solving;
            solveJob = null;
            // The default fit depends on whether the shape is a line, which the Fit button shows.
            if (S.paint.fit == null && line() != lineShown) rebuildWidgets();
        }
        if (solveJob == null && (solvedFor == null || !sameShape(solvedFor))) {
            ShapeSettings s = S.copy();
            solving = s;
            solveJob = CurveScreen.EXEC.submit(() -> Solver.run(s));
        }
        if (paintJob != null && paintJob.isDone()) {
            try { painted = paintJob.get(); } catch (Exception _) { painted = null; }
            paintedFor = paintingFor; paintedResult = paintingResult;
            paintJob = null;
            pictureDirty = true;
        }
        if (paintJob == null && solveJob == null && result != null && (paintedResult != result || !paintedFor.same(S.paint))) {
            Paint paint = S.paint.copy();
            Solver.Result r = result;
            int depth = solvedFor.depth;
            boolean connects = solvedFor.fullConnects;
            Catalog catalog = paint.on() ? PaintBlocks.catalog(S) : null;
            paintingFor = paint; paintingResult = r;
            paintJob = CurveScreen.EXEC.submit(() -> Painter2D.paint(r, depth, paint, connects, catalog));
        }
        if (paintedResult != null && texture.id() == null) pictureDirty = true;   // released while the chooser was open
        if (pictureDirty && paintedResult != null && paintedResult.target().error == null) {
            pictureDirty = false;
            texture.update(paintedResult, PreviewTexture.Colors.BLOCKS, false, true, BlockChoices.CHOICE, painted);
        }
    }

    /** The status line and its tooltip: how many blocks fell to the closest colour, and which and why. */
    private String[] status() {
        if (S.is3d()) return new String[]{tr("curvegen.paint.not_3d"), null};
        if (!S.paint.on()) return new String[]{tr("curvegen.paint.status_off"), tr("curvegen.paint.source_tip")};
        if (paintedResult != null && paintedResult.target().error != null) return new String[]{"", null};
        if (painted == null || !paintedFor.on()) return new String[]{tr("curvegen.screen.working"), null};
        StringBuilder tip = new StringBuilder();
        for (Why w : new Why[]{Why.CLOSEST, Why.BLOCKS_TAB, Why.GAP}) {
            StringBuilder lines = new StringBuilder();
            for (Family f : BlockChoices.FAMILIES) {
                long n = painted.count(f, w);
                if (n > 0) lines.append('\n').append(tr("curvegen.paint.why.entry", BlockChoices.familyName(f), n));
            }
            if (lines.isEmpty()) continue;
            if (!tip.isEmpty()) tip.append('\n').append('\n');
            tip.append(tr("curvegen.paint.why." + w.name().toLowerCase(Locale.ROOT))).append(lines);
        }
        return new String[]{tr("curvegen.paint.status", painted.closest(), painted.total()),
                tip.isEmpty() ? tr("curvegen.paint.why.none") : tip.toString()};
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        poll();
        super.extractRenderState(ctx, mx, my, delta);
        ctx.text(font, title, M, 10, 0xFFFFFFFF);
        for (Label l : labels) ctx.text(font, l.text, l.x, l.y, S.paint.on() ? 0xFFC8CED6 : 0xFF808A96);
        if (swatchY >= 0) PatternChooserScreen.drawSwatch(ctx, S.paint.pattern, M + 1, swatchY, 16);

        String[] status = status();
        int sy = y(ROWS) + 3;
        ctx.text(font, font.plainSubstrByWidth(status[0], pw), M, sy, 0xFF9AA5B3, false);
        if (status[1] != null && mx >= M && mx < M + pw && my >= sy - 2 && my < sy + 10)
            ctx.setTooltipForNextFrame(font.split(Component.literal(status[1]), 240), mx, my);

        int px0 = pvX0(), py0 = TOP, px1 = width - M, py1 = pvY1();
        ctx.fill(px0, py0, px1, py1, 0xFF15181D);
        ctx.outline(px0 - 1, py0 - 1, px1 - px0 + 2, py1 - py0 + 2, 0xFF3A424D);
        if (S.is3d()) {
            note(ctx, tr("curvegen.paint.picture_3d"), px0, py0, px1, py1, 0xFF9AA5B3);
        } else if (paintedResult != null && paintedResult.target().error != null) {
            note(ctx, Lang.text(paintedResult.target().error), px0, py0, px1, py1, 0xFFFF8098);
        } else if (paintedResult == null || texture.id() == null) {
            note(ctx, tr("curvegen.screen.working"), px0, py0, px1, py1, 0xFF9AA5B3);
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
    private void note(GuiGraphicsExtractor ctx, String text, int x0, int y0, int x1, int y1, int colour) {
        int w = x1 - x0 - 16;
        var lines = font.split(Component.literal(text), w);
        int y = (y0 + y1) / 2 - lines.size() * 9 / 2;
        for (var l : lines) { ctx.centeredText(font, l, (x0 + x1) / 2, y, colour); y += 9; }
    }

    @Override
    public void removed() {
        texture.close();
        super.removed();
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }
}
