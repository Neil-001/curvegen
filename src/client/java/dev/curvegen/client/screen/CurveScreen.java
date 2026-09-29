package dev.curvegen.client.screen;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.LitematicExporter;
import dev.curvegen.client.Placement;
import dev.curvegen.core.Layout;
import dev.curvegen.core.Pieces;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.BzMode;
import dev.curvegen.core.ShapeSettings.EllipseMode;
import dev.curvegen.core.ShapeSettings.EqMode;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.Solver;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class CurveScreen extends Screen {
    private enum Tab { ELLIPSE, EQUATION, BEZIER, BLOCKS }

    private static final ShapeSettings S = CurveGenClient.SETTINGS;
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Curve Generator solver");
        t.setDaemon(true);
        return t;
    });

    private record Preset(String name, String src, String xmin, String xmax, String ymin, String ymax, EqMode mode) {}
    private static final Preset[] PRESETS = {
            new Preset("Sine wave", "y = 2sin(x)", "-2pi", "2pi", "-3", "3", EqMode.LINE),
            new Preset("Parabolic arch", "y = 9 - x^2/4", "-6", "6", "0", "9", EqMode.UNDER),
            new Preset("Catenary arch", "y = 14 - 2cosh(x/2)", "-5.2", "5.2", "0", "12", EqMode.UNDER),
            new Preset("Gothic arch", "y = sqrt(64 - (|x| + 3)^2)", "-5", "5", "0", "8", EqMode.UNDER),
            new Preset("Circle", "x^2 + y^2 = 16", "-5", "5", "-5", "5", EqMode.LINE),
            new Preset("Heart", "(x^2 + y^2 - 1)^3 = x^2 y^3", "-1.5", "1.5", "-1.3", "1.5", EqMode.UNDER),
            new Preset("Tangent", "y = tan(x)", "-4", "4", "-4", "4", EqMode.LINE)};

    private static Tab tab = Tab.ELLIPSE;
    private static PreviewTexture.Colors colors = PreviewTexture.Colors.BLOCKS;
    private static boolean showCurve = true;
    private static int presetIndex = -1;

    private Solver.Result result;
    private Future<Solver.Result> job;
    private boolean dirty = true, textureDirty, autoFit = true;
    private final PreviewTexture preview = new PreviewTexture();
    private float zoom = 8, panX, panY;
    private int lastNx = -1, lastNy = -1;

    private record Label(int x, int y, String text) {}
    private final List<Label> labels = new ArrayList<>();
    private record Icon(int x, int y, ItemStack stack) {}
    private final List<Icon> icons = new ArrayList<>();
    private String hint;
    private int hintY;
    private TextFieldWidget qHField;
    private String status;
    private long statusUntil;

    private int dragPoint = -1;
    private boolean panning;

    public CurveScreen() {
        super(Text.literal("Curve Generator"));
        if (tab != Tab.BLOCKS)
            tab = switch (S.gen) { case ELLIPSE -> Tab.ELLIPSE; case EQUATION -> Tab.EQUATION; case BEZIER -> Tab.BEZIER; };
        S.fullConnects = BlockChoices.fullBlockConnects();
    }

    // ---------- layout ----------
    private static final int M = 6, PANEL_W = 150, ROW = 22;
    private int top() { return 32; }
    private int row(int n) { return top() + n * ROW; }
    private int cx0() { return M + PANEL_W + 6; }
    private int cy0() { return top(); }
    private int cx1() { return width - M; }
    private int cy1() { return height - 30; }

    @Override
    protected void init() {
        labels.clear(); icons.clear(); pointFields.clear(); hint = null; qHField = null;

        String[] names = {"Ellipse", "Equation", "Bézier", "Blocks"};
        for (int k = 0; k < 4; k++) {
            Tab t = Tab.values()[k];
            ButtonWidget b = ButtonWidget.builder(Text.literal(names[k]), btn -> switchTab(t)).dimensions(M + k * 56, 6, 54, 20).build();
            b.active = tab != t;
            addDrawableChild(b);
        }
        addDrawableChild(CyclingButtonWidget.<PreviewTexture.Colors>builder(c -> Text.literal(switch (c) {
                    case STONE -> "Plain"; case PIECES -> "Piece types"; case BLOCKS -> "Block colours"; }))
                .values(PreviewTexture.Colors.values()).initially(colors)
                .build(width - M - 170, 6, 104, 20, Text.literal("Colour"), (b, v) -> { colors = v; textureDirty = true; }));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(showCurve)
                .build(width - M - 62, 6, 62, 20, Text.literal("Curve"), (b, v) -> { showCurve = v; textureDirty = true; }));

        switch (tab) {
            case ELLIPSE -> initEllipse();
            case EQUATION -> initEquation();
            case BEZIER -> initBezier();
            case BLOCKS -> initBlocks();
        }

        int by = height - 26;
        labels.add(new Label(M, by + 6, "Depth"));
        addDrawableChild(num(M + 34, by, 30, String.valueOf(S.depth), v -> { S.depth = clampInt(v, 1, 64, S.depth); }));
        CyclingButtonWidget<Boolean> replace = CyclingButtonWidget.onOffBuilder(S.overwrite)
                .build(M + 70, by, 70, 20, Text.literal("Replace"), (b, v) -> S.overwrite = v);
        replace.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
                "On: the shape replaces blocks already in its way. Off: it only fills air and things like grass, water and snow layers.")));
        addDrawableChild(replace);
        CyclingButtonWidget<Boolean> carve = CyclingButtonWidget.onOffBuilder(S.carve)
                .build(M + 144, by, 66, 20, Text.literal("Carve"), (b, v) -> S.carve = v);
        carve.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
                "Clears existing blocks from the space the shape encloses: inside a thin or thick ellipse, or the other side of a filled equation. Filled ellipses, lines and Bézier curves don't carve.")));
        addDrawableChild(carve);
        int bx = width - M - 3 * 64 - 8;
        addDrawableChild(ButtonWidget.builder(Text.literal("Place"), b -> place()).dimensions(bx, by, 64, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Export"), b -> export()).dimensions(bx + 68, by, 64, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(bx + 136, by, 64, 20).build());
    }

    private void switchTab(Tab t) {
        tab = t;
        if (t == Tab.ELLIPSE) S.gen = Gen.ELLIPSE;
        if (t == Tab.EQUATION) S.gen = Gen.EQUATION;
        if (t == Tab.BEZIER) S.gen = Gen.BEZIER;
        if (t != Tab.BLOCKS) { dirty = true; autoFit = true; }
        clearAndInit();
    }

    private void initEllipse() {
        addDrawableChild(num(M, row(0), 64, String.valueOf(S.eW), v -> { S.eW = clampInt(v, 1, 400, S.eW); dirty = true; }));
        labels.add(new Label(M + 71, row(0) + 6, "×"));
        addDrawableChild(num(M + 86, row(0), 64, String.valueOf(S.eH), v -> { S.eH = clampInt(v, 1, 400, S.eH); dirty = true; }));
        addDrawableChild(CyclingButtonWidget.<EllipseMode>builder(m -> Text.literal(switch (m) {
                    case THIN -> "Thin"; case FILLED -> "Filled"; case OUTWARDS -> "Thick outwards";
                    case INWARDS -> "Thick inwards"; case MIDDLE -> "Thick middle"; }))
                .values(EllipseMode.values()).initially(S.eMode)
                .build(M, row(1), PANEL_W, 20, Text.literal("Shape"), (b, v) -> { S.eMode = v; dirty = true; clearAndInit(); }));
        labels.add(new Label(M, row(2) + 6, "Thickness"));
        TextFieldWidget t = num(M + 60, row(2), PANEL_W - 60, fmt(S.eT), v -> { S.eT = clampNum(v, 0.0625, 50, S.eT); dirty = true; });
        t.setEditable(S.eMode == EllipseMode.OUTWARDS || S.eMode == EllipseMode.INWARDS || S.eMode == EllipseMode.MIDDLE);
        addDrawableChild(t);
        hint = switch (S.eMode) {
            case THIN -> "A hollow shell: the outer surface follows the ellipse.";
            case FILLED -> "Everything inside the ellipse.";
            case OUTWARDS -> "A wall that grows outwards from the ellipse.";
            case INWARDS -> "A wall that grows inwards from the ellipse.";
            case MIDDLE -> "A wall centred on the ellipse.";
        };
        hintY = row(3) + 2;
    }

    private void initEquation() {
        TextFieldWidget eq = new TextFieldWidget(textRenderer, M, row(0), PANEL_W, 20, Text.literal("Equation"));
        eq.setMaxLength(256);
        eq.setText(S.src);
        eq.setChangedListener(v -> { S.src = v; dirty = true; });
        addDrawableChild(eq);
        labels.add(new Label(M, row(1) + 6, "x"));
        addDrawableChild(text(M + 10, row(1), 62, S.xmin, v -> { S.xmin = v; dirty = true; }));
        labels.add(new Label(M + 76, row(1) + 6, "to"));
        addDrawableChild(text(M + 90, row(1), 60, S.xmax, v -> { S.xmax = v; dirty = true; }));
        labels.add(new Label(M, row(2) + 6, "y"));
        addDrawableChild(text(M + 10, row(2), 62, S.ymin, v -> { S.ymin = v; dirty = true; }));
        labels.add(new Label(M + 76, row(2) + 6, "to"));
        addDrawableChild(text(M + 90, row(2), 60, S.ymax, v -> { S.ymax = v; dirty = true; }));
        labels.add(new Label(M, row(3) + 6, "W"));
        addDrawableChild(num(M + 10, row(3), 62, String.valueOf(S.qW), v -> { S.qW = clampInt(v, 1, 400, S.qW); dirty = true; }));
        labels.add(new Label(M + 76, row(3) + 6, "H"));
        qHField = num(M + 90, row(3), 60, String.valueOf(S.qH), v -> { if (!S.qLock) { S.qH = clampInt(v, 1, 400, S.qH); dirty = true; } });
        qHField.setEditable(!S.qLock);
        addDrawableChild(qHField);
        addDrawableChild(CyclingButtonWidget.onOffBuilder(S.qLock).build(M, row(4), PANEL_W, 20, Text.literal("Same scale"),
                (b, v) -> { S.qLock = v; qHField.setEditable(!v); dirty = true; }));
        addDrawableChild(CyclingButtonWidget.<EqMode>builder(m -> Text.literal(switch (m) {
                    case LINE -> "Line"; case UNDER -> "Fill under"; case OVER -> "Fill over"; }))
                .values(EqMode.values()).initially(S.qMode)
                .build(M, row(5), PANEL_W, 20, Text.literal("Shape"), (b, v) -> { S.qMode = v; dirty = true; }));
        labels.add(new Label(M, row(6) + 6, "Line width"));
        addDrawableChild(num(M + 60, row(6), PANEL_W - 60, fmt(S.qLW), v -> { S.qLW = clampNum(v, 0.0625, 50, S.qLW); dirty = true; }));
        String pn = presetIndex < 0 ? "Try an example" : "Example: " + PRESETS[presetIndex].name;
        addDrawableChild(ButtonWidget.builder(Text.literal(pn), b -> {
            presetIndex = (presetIndex + 1) % PRESETS.length;
            Preset p = PRESETS[presetIndex];
            S.src = p.src; S.xmin = p.xmin; S.xmax = p.xmax; S.ymin = p.ymin; S.ymax = p.ymax; S.qMode = p.mode;
            dirty = true; autoFit = true; clearAndInit();
        }).dimensions(M, row(7), PANEL_W, 20).build());
    }

    private void initBezier() {
        addDrawableChild(num(M, row(0), 64, String.valueOf(S.bW), v -> { S.bW = clampInt(v, 1, 400, S.bW); dirty = true; autoFit = true; }));
        labels.add(new Label(M + 71, row(0) + 6, "×"));
        addDrawableChild(num(M + 86, row(0), 64, String.valueOf(S.bH), v -> { S.bH = clampInt(v, 1, 400, S.bH); dirty = true; autoFit = true; }));
        addDrawableChild(CyclingButtonWidget.<BzMode>builder(m -> Text.literal(m == BzMode.LINE ? "Line" : "Filled"))
                .values(BzMode.values()).initially(S.bMode)
                .build(M, row(1), PANEL_W, 20, Text.literal("Shape"), (b, v) -> { S.bMode = v; dirty = true; }));
        labels.add(new Label(M, row(2) + 6, "Line width"));
        addDrawableChild(num(M + 60, row(2), PANEL_W - 60, fmt(S.bLW), v -> { S.bLW = clampNum(v, 0.0625, 50, S.bLW); dirty = true; }));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(S.snap).build(M, row(3), PANEL_W, 20, Text.literal("Snap to half blocks"),
                (b, v) -> S.snap = v));
        ButtonWidget add = ButtonWidget.builder(Text.literal("Add point"), b -> {
            int n = S.pts.size();
            double[] a = S.pts.get(n - 2), c = S.pts.get(n - 1);
            S.pts.add(n - 1, new double[]{(a[0] + c[0]) / 2, (a[1] + c[1]) / 2});
            dirty = true; clearAndInit();
        }).dimensions(M, row(4), 73, 20).build();
        add.active = S.pts.size() < 10;
        ButtonWidget rem = ButtonWidget.builder(Text.literal("Remove"), b -> {
            S.pts.remove(S.pts.size() - 2 >= 1 ? S.pts.size() - 2 : S.pts.size() - 1);
            dirty = true; clearAndInit();
        }).dimensions(M + 77, row(4), 73, 20).build();
        rem.active = S.pts.size() > 2;
        addDrawableChild(add);
        addDrawableChild(rem);
        hint = "Drag the numbered handles in the preview.";
        hintY = row(5) + 2;

        // Point list: number, x and y (in blocks from the bottom left), scrollable when it doesn't fit.
        pointFields.clear();
        int n = S.pts.size();
        listTop = row(5) + 22;
        listVisible = Math.max(1, (height - 34 - listTop) / POINT_ROW);
        pointScroll = Math.max(0, Math.min(pointScroll, n - listVisible));
        for (int k = pointScroll; k < Math.min(n, pointScroll + listVisible); k++) {
            int y = listTop + (k - pointScroll) * POINT_ROW, idx = k;
            labels.add(new Label(M, y + 4, String.valueOf(k + 1)));
            labels.add(new Label(M + 12, y + 4, "x"));
            labels.add(new Label(M + 80, y + 4, "y"));
            TextFieldWidget fx = new TextFieldWidget(textRenderer, M + 20, y, 56, 16, Text.literal("Point " + (k + 1) + " x"));
            TextFieldWidget fy = new TextFieldWidget(textRenderer, M + 88, y, 56, 16, Text.literal("Point " + (k + 1) + " y"));
            for (int axis = 0; axis < 2; axis++) {
                TextFieldWidget f = axis == 0 ? fx : fy;
                int a = axis;
                f.setMaxLength(12);
                f.setText(coord(S.pts.get(k)[a]));
                f.setChangedListener(v -> {
                    if (syncingPoints) return;
                    try {
                        double d = Double.parseDouble(v.trim());
                        if (Double.isFinite(d)) { S.pts.get(idx)[a] = d; dirty = true; }
                    } catch (NumberFormatException ignored) { }
                });
                addDrawableChild(f);
            }
            pointFields.add(new TextFieldWidget[]{fx, fy});
        }
    }

    private static final int POINT_ROW = 18;
    private static int pointScroll = 0;
    private int listTop, listVisible;
    private final List<TextFieldWidget[]> pointFields = new ArrayList<>();
    private boolean syncingPoints;

    private static String coord(double v) {
        String t = String.format(java.util.Locale.ROOT, "%.3f", v);
        return t.contains(".") ? t.replaceAll("0+$", "").replaceAll("\\.$", "") : t;
    }

    /** Keeps the typed coordinates in step with dragged handles (without fighting a field being edited). */
    private void syncPointFields() {
        syncingPoints = true;
        for (int r = 0; r < pointFields.size(); r++) {
            int k = pointScroll + r;
            if (k >= S.pts.size()) break;
            for (int a = 0; a < 2; a++) {
                TextFieldWidget f = pointFields.get(r)[a];
                String want = coord(S.pts.get(k)[a]);
                if (!f.isFocused() && !f.getText().equals(want)) f.setText(want);
            }
        }
        syncingPoints = false;
    }

    private void initBlocks() {
        for (int k = 0; k < BlockChoices.FAMILIES.length; k++) {
            Family f = BlockChoices.FAMILIES[k];
            int y = row(k);
            if (f == Family.FULL) {
                labels.add(new Label(M + 4, y + 6, "Use"));
            } else {
                boolean on = allowed(f);
                addDrawableChild(ButtonWidget.builder(Text.literal(on ? "Use" : "Off"), b -> { setAllowed(f, !allowed(f)); dirty = true; clearAndInit(); })
                        .dimensions(M, y, 26, 20).build());
            }
            Block block = BlockChoices.CHOICE.get(f);
            String name = textRenderer.trimToWidth(block.getName().getString(), PANEL_W - 54 - 8);
            ButtonWidget pick = ButtonWidget.builder(Text.literal(name), b -> client.setScreen(new BlockPickerScreen(this, f, chosen -> {
                BlockChoices.CHOICE.put(f, chosen);
                S.fullConnects = BlockChoices.fullBlockConnects();
                dirty = true; textureDirty = true;
                clearAndInit();
            }))).dimensions(M + 48, y, PANEL_W - 48, 20).build();
            pick.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(BlockChoices.familyName(f) + ": " + block.getName().getString())));
            addDrawableChild(pick);
            icons.add(new Icon(M + 29, y + 2, new ItemStack(block)));
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Match a colour…"), b -> client.setScreen(
                new ColorPickerScreen(this, BlockChoices.pickedColor >= 0 ? BlockChoices.pickedColor : 0x8E6B4A, rgb -> {
                    BlockChoices.autoSelect(rgb);
                    S.fullConnects = BlockChoices.fullBlockConnects();
                    dirty = true; textureDirty = true;
                    clearAndInit();
                }))).dimensions(M, row(BlockChoices.FAMILIES.length), PANEL_W, 20).build());
        hint = "Choose a block per piece type, or match them all to one colour.";
        hintY = row(BlockChoices.FAMILIES.length + 1) + 2;
    }

    private static boolean allowed(Family f) {
        return switch (f) { case SLAB -> S.slab; case STAIRS -> S.stair; case TRAPDOOR -> S.trap; case FENCE -> S.fence; case PANE -> S.pane; case WALL -> S.wall; default -> true; };
    }
    private static void setAllowed(Family f, boolean v) {
        switch (f) { case SLAB -> S.slab = v; case STAIRS -> S.stair = v; case TRAPDOOR -> S.trap = v; case FENCE -> S.fence = v; case PANE -> S.pane = v; case WALL -> S.wall = v; default -> {} }
    }

    // ---------- widgets ----------
    private TextFieldWidget num(int x, int y, int w, String value, Consumer<String> onChange) {
        return text(x, y, w, value, onChange);
    }
    private TextFieldWidget text(int x, int y, int w, String value, Consumer<String> onChange) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 20, Text.empty());
        f.setMaxLength(64);
        f.setText(value);
        f.setChangedListener(onChange);
        return f;
    }
    private static int clampInt(String v, int lo, int hi, int d) {
        try { return Math.max(lo, Math.min(hi, Integer.parseInt(v.trim()))); } catch (NumberFormatException e) { return d; }
    }
    private static double clampNum(String v, double lo, double hi, double d) {
        try { double x = Double.parseDouble(v.trim()); return x > 0 ? Math.max(lo, Math.min(hi, x)) : d; } catch (NumberFormatException e) { return d; }
    }
    private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    // ---------- solving ----------
    private void poll() {
        if (job != null && job.isDone()) {
            try { result = job.get(); } catch (Exception e) { flash("Couldn't build this shape: " + e.getMessage()); }
            job = null;
            if (result != null) {
                if (S.gen == Gen.EQUATION && S.qLock && result.target().error == null) {
                    S.qH = result.ny();
                    if (qHField != null && !qHField.getText().equals(String.valueOf(S.qH))) qHField.setText(String.valueOf(S.qH));
                }
                textureDirty = true;
            }
        }
        if (dirty && job == null) {
            dirty = false;
            ShapeSettings copy = S.copy();
            job = EXEC.submit(() -> Solver.run(copy));
        }
        if (result != null && preview.id() == null) textureDirty = true;   // released while a picker was open
        if (textureDirty && result != null) {
            textureDirty = false;
            preview.update(result, colors, showCurve, true);
            if (autoFit || result.nx() != lastNx || result.ny() != lastNy) fitView();
            lastNx = result.nx(); lastNy = result.ny();
        }
    }

    private void fitView() {
        if (result == null) return;
        float cw = cx1() - cx0() - 8, ch = cy1() - cy0() - 22;
        zoom = Math.max(0.5f, Math.min(64f, Math.min(cw / result.nx(), ch / result.ny())));
        panX = cx0() + (cx1() - cx0() - result.nx() * zoom) / 2f;
        panY = cy0() + 12 + (cy1() - cy0() - 12 - result.ny() * zoom) / 2f;
        autoFit = false;
    }

    // ---------- rendering ----------
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        poll();
        super.render(ctx, mouseX, mouseY, delta);
        for (Label l : labels) ctx.drawTextWithShadow(textRenderer, l.text, l.x, l.y, 0xC8CED6);
        for (Icon ic : icons) ctx.drawItem(ic.stack, ic.x, ic.y);
        if (hint != null && hintY + 18 < height - 30) ctx.drawTextWrapped(textRenderer, Text.literal(hint), M, hintY, PANEL_W, 0x9AA5B3);
        if (tab == Tab.BEZIER && S.pts.size() > listVisible) {
            int h = listVisible * POINT_ROW - 2, bar = Math.max(6, h * listVisible / S.pts.size());
            int by = listTop + (h - bar) * pointScroll / Math.max(1, S.pts.size() - listVisible);
            ctx.fill(M + PANEL_W + 1, listTop, M + PANEL_W + 3, listTop + h, 0x30FFFFFF);
            ctx.fill(M + PANEL_W + 1, by, M + PANEL_W + 3, by + bar, 0xA0FFFFFF);
        }

        int x0 = cx0(), y0 = cy0(), x1 = cx1(), y1 = cy1();
        ctx.fill(x0, y0, x1, y1, 0xFF15181D);
        ctx.enableScissor(x0, y0, x1, y1);
        if (result != null && preview.id() != null) {
            var m = ctx.getMatrices();
            m.push();
            m.translate(panX, panY, 0);
            float s = zoom / preview.sub;
            m.scale(s, s, 1);
            ctx.drawTexture(preview.id(), 0, 0, 0f, 0f, preview.width, preview.height, preview.width, preview.height);
            m.pop();
            drawHover(ctx, mouseX, mouseY);
            if (S.gen == Gen.BEZIER && tab != Tab.BLOCKS) drawHandles(ctx);
        }
        ctx.disableScissor();
        ctx.drawBorder(x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, 0xFF3A424D);

        // status line across the top of the canvas
        String line; int color = 0xDDE3EA;
        if (result == null) line = "Working…";
        else if (result.target().error != null) { line = result.target().error; color = 0xFF8098; }
        else {
            int total = 0;
            for (int p = 1; p < Pieces.COUNT; p++) total += result.counts()[p];
            double pct = result.area() > 0 ? result.err() / result.area() * 100 : 0;
            line = total + " pieces on " + result.nx() + "×" + result.ny() + String.format(", mismatch %.2f blocks² (%.1f%%)", result.err(), pct)
                    + (job != null ? "  Updating…" : "");
        }
        if (status != null && System.currentTimeMillis() < statusUntil) { line = status; color = 0xFFE08A; }
        ctx.fill(x0, y0, x1, y0 + 12, 0xB0000000);
        ctx.drawText(textRenderer, textRenderer.trimToWidth(line, x1 - x0 - 6), x0 + 3, y0 + 2, color, false);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(0, 0, width, height, 0x80101317);
    }

    private int[] cellAt(double mx, double my) {
        if (result == null || mx < cx0() || mx >= cx1() || my < cy0() + 12 || my >= cy1()) return null;
        int i = (int) Math.floor((mx - panX) / zoom), jr = (int) Math.floor((my - panY) / zoom);
        if (i < 0 || jr < 0 || i >= result.nx() || jr >= result.ny()) return null;
        return new int[]{i, result.ny() - 1 - jr};
    }

    private void drawHover(DrawContext ctx, int mx, int my) {
        int[] c = cellAt(mx, my);
        if (c == null) return;
        int sx = Math.round(panX + c[0] * zoom), sy = Math.round(panY + (result.ny() - 1 - c[1]) * zoom), sz = Math.max(2, Math.round(zoom));
        ctx.drawBorder(sx, sy, sz, sz, 0xFF6EA0FF);
        int p = result.at(c[0], c[1]);
        String txt = "Column " + (c[0] + 1) + ", row " + (c[1] + 1) + ": " + Pieces.NAME[p]
                + (p != Pieces.EMPTY ? " (" + BlockChoices.blockFor(p).getName().getString() + ")" : "");
        if (result.target().hasMath) {
            var t = result.target();
            txt += String.format(", x ≈ %.2f, y ≈ %.2f", t.mx0 + (c[0] + .5) * t.msx, t.my0 + (c[1] + .5) * t.msy);
        }
        int y = cy1() - 12;
        ctx.fill(cx0(), y, cx1(), cy1(), 0xB0000000);
        ctx.drawText(textRenderer, textRenderer.trimToWidth(txt, cx1() - cx0() - 6), cx0() + 3, y + 2, 0xDDE3EA, false);
    }

    private float[] handleScreen(int k) {
        double[] p = S.pts.get(k);
        return new float[]{panX + (float) p[0] * zoom, panY + (float) (result.ny() - p[1]) * zoom};
    }

    private void drawHandles(DrawContext ctx) {
        float[] prev = null;
        for (int k = 0; k < S.pts.size(); k++) {
            float[] h = handleScreen(k);
            if (prev != null) dashedLine(ctx, prev[0], prev[1], h[0], h[1]);
            prev = h;
        }
        for (int k = 0; k < S.pts.size(); k++) {
            float[] h = handleScreen(k);
            int x = Math.round(h[0]), y = Math.round(h[1]);
            ctx.fill(x - 5, y - 5, x + 6, y + 6, 0xFFFFFFFF);
            ctx.fill(x - 4, y - 4, x + 5, y + 5, k == dragPoint ? 0xFFFFB84D : 0xFF3F7BE0);
            String n = String.valueOf(k + 1);
            ctx.drawText(textRenderer, n, x - textRenderer.getWidth(n) / 2 + 1, y - 3, 0xFFFFFF, false);
        }
    }

    private void dashedLine(DrawContext ctx, float x1, float y1, float x2, float y2) {
        float len = (float) Math.hypot(x2 - x1, y2 - y1);
        int steps = (int) (len / 3);
        for (int q = 0; q <= steps; q++) {
            if ((q / 2) % 2 == 1) continue;
            float u = steps == 0 ? 0 : (float) q / steps;
            int x = Math.round(x1 + (x2 - x1) * u), y = Math.round(y1 + (y2 - y1) * u);
            ctx.fill(x, y, x + 1, y + 1, 0xAA6EA0FF);
        }
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        boolean inCanvas = mx >= cx0() && mx < cx1() && my >= cy0() && my < cy1();
        if (!inCanvas || result == null) return false;
        setFocused(null);
        if (button == 0 && S.gen == Gen.BEZIER && tab != Tab.BLOCKS) {
            for (int k = S.pts.size() - 1; k >= 0; k--) {
                float[] h = handleScreen(k);
                if (Math.abs(mx - h[0]) <= 6 && Math.abs(my - h[1]) <= 6) { dragPoint = k; return true; }
            }
        }
        if (button == 1 || button == 2 || button == 0) { panning = true; return true; }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragPoint >= 0 && result != null) {
            double bx = (mx - panX) / zoom, by = result.ny() - (my - panY) / zoom;
            bx = Math.max(0, Math.min(result.nx(), bx));
            by = Math.max(0, Math.min(result.ny(), by));
            if (S.snap) { bx = Math.round(bx * 2) / 2.0; by = Math.round(by * 2) / 2.0; }
            S.pts.set(dragPoint, new double[]{bx, by});
            dirty = true;
            syncPointFields();
            return true;
        }
        if (panning) { panX += (float) dx; panY += (float) dy; return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragPoint = -1; panning = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (tab == Tab.BEZIER && mx < M + PANEL_W && my >= listTop && S.pts.size() > listVisible) {
            pointScroll = Math.max(0, Math.min(S.pts.size() - listVisible, pointScroll - (int) Math.signum(v)));
            clearAndInit();
            return true;
        }
        if (mx >= cx0() && mx < cx1() && my >= cy0() && my < cy1() && result != null) {
            float old = zoom;
            zoom = Math.max(0.5f, Math.min(96f, zoom * (float) Math.pow(1.15, v)));
            panX = (float) (mx - (mx - panX) * zoom / old);
            panY = (float) (my - (my - panY) * zoom / old);
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    // ---------- actions ----------
    private boolean ready() {
        if (result == null || job != null || dirty) { flash("Still working on the shape, try again in a moment."); return false; }
        if (result.target().error != null) { flash("Fix the equation first."); return false; }
        return true;
    }

    private void place() {
        if (!ready()) return;
        Layout layout = Layout.of(result);
        if (layout.isEmpty()) { flash("The shape is empty, so there's nothing to place."); return; }
        if (client.player != null && !client.player.hasPermissionLevel(2))
            client.player.sendMessage(Text.literal("Note: you'll need operator permissions to confirm placement. Export works for everyone."), false);
        Placement.start(layout, S.depth, S.overwrite, S.carve);
        close();
    }

    private void export() {
        if (!ready()) return;
        Layout layout = Layout.of(result);
        if (layout.isEmpty()) { flash("The shape is empty, so there's nothing to export."); return; }
        String kind = switch (S.gen) { case ELLIPSE -> "ellipse"; case EQUATION -> "equation"; case BEZIER -> "bezier"; };
        try {
            String author = client.player != null ? client.player.getName().getString() : "Curve Generator";
            Path file = LitematicExporter.export(layout, S.depth, LitematicExporter.defaultName(kind), author);
            flash("Exported to schematics/" + file.getFileName());
            if (client.player != null) client.player.sendMessage(Text.literal("Curve Generator: exported to schematics/" + file.getFileName()), false);
        } catch (Exception e) {
            flash("Export failed: " + e.getMessage());
        }
    }

    private void flash(String msg) { status = msg; statusUntil = System.currentTimeMillis() + 4000; }

    @Override
    public void removed() {
        preview.close();
        super.removed();
    }
}
