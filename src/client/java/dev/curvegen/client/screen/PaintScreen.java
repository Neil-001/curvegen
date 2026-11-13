package dev.curvegen.client.screen;

import static dev.curvegen.client.Lang.tr;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.PatternStore;
import dev.curvegen.client.chroma.Chroma;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.edit.Stepper;
import dev.curvegen.core.paint.Ladder.Why;
import dev.curvegen.core.paint.MixLayout;
import dev.curvegen.core.paint.Paint;
import dev.curvegen.core.paint.Paint.Arrange;
import dev.curvegen.core.paint.Paint.Edges;
import dev.curvegen.core.paint.Paint.Empty;
import dev.curvegen.core.paint.Paint.Fit;
import dev.curvegen.core.paint.Paint.Flow;
import dev.curvegen.core.paint.Paint.Mapping;
import dev.curvegen.core.paint.Paint.On;
import dev.curvegen.core.paint.Paint.Source;
import dev.curvegen.core.paint.Painted;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The Paint screen: the paint source and its settings on the left, and the shape on the right in its block colours,
 * painted as the settings stand ({@link PaintPicture}). Every control changes {@code ShapeSettings.paint} at once, and
 * the editor and the main screen repaint from it. The rows under Source belong to the source, built by one method per
 * source, so a new source brings its own rows without reshaping the screen. With Source: Off the pattern's rows stay,
 * greyed out. A mix has fewer rows, so it leaves two empty above "One material per colour". Dragging a 3D shape's
 * picture turns it.
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
    /** Where the pattern's or the mix's swatch sits, beside the button that chooses it, or -1 for none. */
    private int swatchY = -1;
    /** Whether the shape on show was a line when the Fit button was made, as the default fit depends on it. */
    private boolean lineShown;

    private final PaintPicture picture = new PaintPicture();
    /** Whether a 3D shape's picture is being turned by a drag. */
    private boolean turning;

    public PaintScreen(Screen parent) {
        super(Component.translatable("curvegen.paint.title"));
        this.parent = parent;
    }

    static Component sourceName(Source s) { return Component.translatable("curvegen.paint.source." + s.name().toLowerCase(Locale.ROOT)); }
    private static Component mappingName(Mapping m) { return Component.translatable("curvegen.paint.mapping." + m.name().toLowerCase(Locale.ROOT)); }
    private static Component onName(On o) { return Component.translatable("curvegen.paint.on." + o.name().toLowerCase(Locale.ROOT)); }
    private static Component fitName(Fit f) { return Component.translatable("curvegen.paint.fit." + f.name().toLowerCase(Locale.ROOT)); }
    private static Component emptyName(Empty e) { return Component.translatable("curvegen.paint.empty." + e.name().toLowerCase(Locale.ROOT)); }
    private static Component arrangeName(Arrange a) { return Component.translatable("curvegen.paint.arrange." + a.name().toLowerCase(Locale.ROOT)); }
    private static Component flowName(Flow f) { return Component.translatable("curvegen.paint.flow." + f.name().toLowerCase(Locale.ROOT)); }
    private static Component edgesName(Edges e) { return Component.translatable("curvegen.paint.edges." + e.name().toLowerCase(Locale.ROOT)); }

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
        for (Mapping m : Mapping.values()) need = Math.max(need, opt(tr("curvegen.paint.mapping"), mappingName(m)) + PAD);
        int fit = 0;
        for (Fit f : Fit.values()) fit = Math.max(fit, opt(tr("curvegen.paint.fit"), fitName(f)));
        need = Math.max(need, fit + PAD + GAP + tw(tr("curvegen.paint.size")) + 3 + DIGIT);
        need = Math.max(need, tw(tr("curvegen.paint.turn")) + 3 + NUM + GAP + onOffWidth(tr("curvegen.paint.mirror")) + PAD);
        need = Math.max(need, 2 * (Math.max(tw(tr("curvegen.paint.along")), tw(tr("curvegen.paint.across"))) + 3 + NUM) + GAP);
        need = Math.max(need, opt(tr("curvegen.paint.empty"), emptyName(Empty.FILL)) + PAD);
        need = Math.max(need, onOffWidth(tr("curvegen.paint.one_material")) + PAD);
        for (Arrange a : Arrange.values()) need = Math.max(need, opt(tr("curvegen.paint.arrange"), arrangeName(a)) + PAD);
        need = Math.max(need, tw(tr("curvegen.paint.patch")) + 3 + NUM);
        need = Math.max(need, tw(tr("curvegen.paint.shuffle")) + PAD);
        for (Flow f : Flow.values()) need = Math.max(need, opt(tr("curvegen.paint.flow"), flowName(f)) + PAD);
        int edges = 0;
        for (Edges e : Edges.values()) edges = Math.max(edges, opt(tr("curvegen.paint.edges"), edgesName(e)));
        need = Math.max(need, onOffWidth(tr("curvegen.paint.reverse")) + PAD + GAP + edges + PAD);
        need = Math.max(need, tw(tr("curvegen.paint.open_chroma")) + PAD);
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
        lineShown = picture.line();

        Source src = S.paint.source;
        // A cycle can't grey one choice, so without Chroma Studio Gradient is left out, and the tooltip says why.
        List<Source> sources = new ArrayList<>(List.of(Source.values()));
        if (!Chroma.available() && src != Source.GRADIENT) sources.remove(Source.GRADIENT);
        add(cycler(sources, src, PaintScreen::sourceName, M, y(0), pw, tr("curvegen.paint.source"), v -> {
            S.paint.source = v;
            paintChanged(true);
        }), Chroma.available() ? tr("curvegen.paint.source_tip") : tr("curvegen.paint.source_tip") + "\n\n" + tr("curvegen.paint.source_gradient_needs"));
        switch (src) {
            case OFF, PATTERN -> patternRows(src == Source.PATTERN);
            case MIX -> mixRows();
            case GRADIENT -> gradientRows();
        }
        oneMaterialRow(y(ROWS - 1));

        int dw = Math.max(80, tw(tr("curvegen.screen.done")) + 24);
        addRenderableWidget(Button.builder(Component.translatable("curvegen.screen.done"), _ -> onClose()).bounds(width - M - dw, height - 26, dw, 20).build());
    }

    /**
     * After a change to the paint. A change of source, pattern or mix can change whether fences, panes and walls attach
     * to full blocks, which the shape is solved with, so that is worked out again; the picture and the main screen solve
     * again when it changes. A chosen pattern does the same through {@link PatternLibraryScreen#use}.
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
        chooser(PatternStore.shownName(p.pattern), tr("curvegen.paint.choose_tip"), () -> new PatternLibraryScreen(this,
                chosen -> PatternLibraryScreen.use(S, chosen), () -> new PaintScreen(new CurveScreen())));

        if (S.is3d()) {
            // How the pattern goes onto a 3D shape. A 3D equation can't be wrapped, so it only offers the three projections.
            boolean wraps = S.gen != ShapeSettings.Gen.EQUATION3;
            List<Mapping> choices = wraps ? List.of(Mapping.values()) : List.of(Mapping.ABOVE, Mapping.FRONT, Mapping.SIDE);
            add(cycler(choices, p.mappingFor(wraps), PaintScreen::mappingName, M, y(2), pw, tr("curvegen.paint.mapping"), v -> {
                S.paint.mapping = v;
            }), on, wraps ? tr("curvegen.paint.mapping_tip") : tr("curvegen.paint.mapping_tip") + '\n' + '\n' + tr("curvegen.paint.mapping_no_wrap_tip"), offTip);
        } else {
            // Face or edge, for a 2D shape more than one block deep.
            boolean deep = S.depth > 1;
            add(cycler(List.of(On.values()), p.onFor(S.depth), PaintScreen::onName, M, y(2), pw, tr("curvegen.paint.pattern_on"), v -> {
                S.paint.on = v;
            }), on && deep, tr("curvegen.paint.on_tip"), !on ? offTip : tr("curvegen.paint.on_depth_tip"));
        }

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

    /** The second row's button that opens {@code open}, named {@code name} and cut to fit, with the swatch beside it. */
    private void chooser(String name, String tip, Supplier<Screen> open) { chooser(name, true, tip, null, open); }

    /** The same, greyed with {@code offTip} unless {@code active}. */
    private void chooser(String name, boolean active, String tip, String offTip, Supplier<Screen> open) {
        swatchY = y(1) + (h - 16) / 2;
        String text = name + "…";
        int bw = pw - 20;
        boolean cut = tw(text) > bw - 8;
        if (cut) text = font.plainSubstrByWidth(name, bw - 8 - tw("…")) + "…";
        add(Button.builder(Component.literal(text), _ -> minecraft.gui.setScreen(open.get())).bounds(M + 20, y(1), bw, h).build(),
                active, cut ? name + '\n' + tip : tip, offTip);
    }

    /** A mix's rows: which blocks, Scatter or Patches, the patches' size and Shuffle. */
    private void mixRows() {
        Paint p = S.paint;
        chooser(MixChooserScreen.name(p.mix), tr("curvegen.paint.mix_choose_tip"), () -> new MixChooserScreen(this, chosen -> {
            S.paint.mix = chosen;
            paintChanged(false);
        }));

        add(cycler(List.of(Arrange.values()), p.arrange, PaintScreen::arrangeName, M, y(2), pw, tr("curvegen.paint.arrange"), v -> {
            S.paint.arrange = v;
            paintChanged(true);   // Patch size greys out under Scatter
        }), tr("curvegen.paint.arrange_tip"));
        boolean patches = p.arrange == Arrange.PATCHES;
        number(M, y(3), pw, tr("curvegen.paint.patch"), () -> S.paint.patch, v -> S.paint.patch = v, 1, MixLayout.MIN_PATCH, MixLayout.MAX_PATCH,
                patches, tr("curvegen.paint.patch_tip"), tr("curvegen.paint.patch_scatter_tip"));

        add(Button.builder(Component.translatable("curvegen.paint.shuffle"), _ -> {
            int seed;
            do seed = java.util.concurrent.ThreadLocalRandom.current().nextInt(); while (seed == S.paint.seed);
            S.paint.seed = seed;
        }).bounds(M, y(4), pw, h).build(), tr("curvegen.paint.shuffle_tip"));

        // A 3D shape's mapping, greyed: a mix places blocks by where they are, not by laying a pattern on the shape.
        if (S.is3d()) {
            boolean wraps = S.gen != ShapeSettings.Gen.EQUATION3;
            add(cycler(List.of(p.mappingFor(wraps)), p.mappingFor(wraps), PaintScreen::mappingName, M, y(5), pw, tr("curvegen.paint.mapping"), _ -> { }),
                    false, null, tr("curvegen.paint.mapping_mix_tip"));
        }
    }

    /**
     * A gradient's rows: which gradient, its direction, Reverse and Edges, and a button to Chroma Studio. A 3D equation
     * can't run one along or across itself, so it only offers the other two directions. Without Chroma Studio, which
     * only a paint setting kept from before can lead to, they show greyed.
     */
    private void gradientRows() {
        Paint p = S.paint;
        boolean ok = Chroma.available();
        String needs = tr("curvegen.paint.needs_chroma");
        chooser(p.gradient == null ? tr("curvegen.paint.gradient_choose") : p.gradient.name, ok, tr("curvegen.paint.gradient_choose_tip"), needs,
                () -> new GradientChooserScreen(this, chosen -> {
                    S.paint.gradient = chosen;
                    paintChanged(false);
                }));

        boolean wraps = !S.is3d() || S.gen != ShapeSettings.Gen.EQUATION3;
        List<Flow> flows = wraps ? List.of(Flow.values()) : List.of(Flow.UP, Flow.OUT);
        add(cycler(flows, p.flowFor(wraps), PaintScreen::flowName, M, y(2), pw, tr("curvegen.paint.flow"), v -> S.paint.flow = v),
                ok, wraps ? tr("curvegen.paint.flow_tip") : tr("curvegen.paint.flow_tip") + "\n\n" + tr("curvegen.paint.flow_no_wrap_tip"), needs);

        int reverseW = onOffWidth(tr("curvegen.paint.reverse")) + PAD, edgesW = pw - GAP - reverseW;
        add(toggle(p.reverse, M, y(3), reverseW, tr("curvegen.paint.reverse"), v -> S.paint.reverse = v), ok, tr("curvegen.paint.reverse_tip"), needs);
        add(cycler(List.of(Edges.values()), p.edges, PaintScreen::edgesName, M + reverseW + GAP, y(3), edgesW, tr("curvegen.paint.edges"),
                v -> S.paint.edges = v), ok, tr("curvegen.paint.edges_tip"), needs);

        add(Button.builder(Component.translatable("curvegen.paint.open_chroma"),
                _ -> Chroma.openGradientTab(S.paint.gradient == null ? null : S.paint.gradient.name)).bounds(M, y(4), pw, h).build(), ok, tr("curvegen.paint.open_chroma_tip"), needs);
    }

    /** "One material per colour", which every source has, each with its own setting. */
    private void oneMaterialRow(int y) {
        Source src = S.paint.source;
        boolean value = switch (src) {
            case OFF, PATTERN -> S.paint.patternOneMaterial;
            case MIX -> S.paint.mixOneMaterial;
            case GRADIENT -> S.paint.gradientOneMaterial;
        };
        add(toggle(value, M, y, pw, tr("curvegen.paint.one_material"), v -> {
            switch (src) {
                case OFF, PATTERN -> S.paint.patternOneMaterial = v;
                case MIX -> S.paint.mixOneMaterial = v;
                case GRADIENT -> S.paint.gradientOneMaterial = v;
            }
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
    private void poll() {
        picture.poll(S);
        // The default fit depends on whether the shape is a line, which the Fit button shows.
        if (S.paint.fit == null && picture.line() != lineShown) rebuildWidgets();
    }

    /** The status line and its tooltip: how many blocks fell to the closest colour, and which and why. */
    private String[] status() {
        if (S.paint.source == Source.GRADIENT && !S.paint.on())
            return new String[]{tr(Chroma.available() ? "curvegen.paint.status_no_gradient" : "curvegen.paint.needs_chroma"), null};
        if (!S.paint.on()) return new String[]{tr("curvegen.paint.status_off"), tr("curvegen.paint.source_tip")};
        if (picture.error() != null) return new String[]{"", null};
        Painted painted = picture.painted();
        if (painted == null || !picture.paintedFor().on()) return new String[]{tr("curvegen.screen.working"), null};
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
        if (swatchY >= 0 && S.paint.source == Source.MIX) MixChooserScreen.drawSwatch(ctx, S.paint.mix, M + 1, swatchY, 16);
        else if (swatchY >= 0 && S.paint.source == Source.GRADIENT) {
            if (S.paint.gradient != null) GradientChooserScreen.drawSwatch(ctx, S.paint.gradient, M + 1, swatchY, 16, 16);
        }
        else if (swatchY >= 0) PatternLibraryScreen.drawSwatch(ctx, S.paint.pattern, M + 1, swatchY, 16);

        String[] status = status();
        int sy = y(ROWS) + 3;
        // Up to two lines under the rows: the left side of the bottom is free, as Done sits on the right.
        var lines = font.split(Component.literal(status[0]), pw);
        int shown = Math.min(lines.size(), 2);
        for (int k = 0; k < shown; k++) ctx.text(font, lines.get(k), M, sy + k * 10, 0xFF9AA5B3, false);
        if (status[1] != null && mx >= M && mx < M + pw && my >= sy - 2 && my < sy + 10 * shown)
            ctx.setTooltipForNextFrame(font.split(Component.literal(status[1]), 240), mx, my);

        picture.draw(ctx, font, S.is3d(), pvX0(), TOP, width - M, pvY1());
    }

    /** Dragging the 3D picture turns it. */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        turning = S.is3d() && event.x() >= pvX0() && event.x() < width - M && event.y() >= TOP && event.y() < pvY1();
        return turning;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (turning) { picture.orbit(dx, dy); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        turning = false;
        return super.mouseReleased(event);
    }

    @Override
    public void removed() {
        picture.close();
        super.removed();
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }
}
