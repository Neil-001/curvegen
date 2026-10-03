package dev.curvegen.client.screen;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.LitematicExporter;
import dev.curvegen.client.Placement;
import dev.curvegen.client.PresetStore;
import dev.curvegen.core.Layout;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.Pieces;
import dev.curvegen.core.PresetData;
import dev.curvegen.core.ShapeSettings.BzMode;
import dev.curvegen.core.ShapeSettings.EllipseMode;
import dev.curvegen.core.ShapeSettings.EqMode;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.Silhouette;
import dev.curvegen.core.Solver;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class CurveScreen extends Screen {
    private enum Tab { ELLIPSE, EQUATION, BEZIER, BLOCKS, COUNT }

    private static final ShapeSettings S = CurveGenClient.SETTINGS;
    static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Curve Generator solver");
        t.setDaemon(true);
        return t;
    });


    private static Tab tab = Tab.ELLIPSE;
    static PreviewTexture.Colors colors = PreviewTexture.Colors.BLOCKS;
    private static boolean showCurve = true;

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
    private EditBox qHField, qLWField, bLWField;
    private CycleButton<EqMode> eqShape;
    /** What a right-click does on buttons that step through options: step backwards. */
    private final Map<AbstractWidget, Runnable> reverse = new HashMap<>();
    private String status;
    private long statusUntil;

    private int dragPoint = -1;
    private boolean panning;

    public CurveScreen() {
        super(Component.literal("Curve Generator"));
        if (tab != Tab.BLOCKS && tab != Tab.COUNT)
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
        labels.clear(); icons.clear(); pointFields.clear(); reverse.clear(); spinners.clear(); countRows = null; hint = null; qHField = null; qLWField = null; bLWField = null; eqShape = null;

        // Top bar: tabs on the left, view options on the right, each button as wide as its text needs.
        String[] names = {"Ellipse", "Equation", "Bézier", "Blocks", "Count"};
        Function<PreviewTexture.Colors, Component> colourName = c -> Component.literal(switch (c) {
            case STONE -> "Plain"; case PIECES -> "Piece types"; case BLOCKS -> "Block colours"; });
        int colourText = 0;
        for (PreviewTexture.Colors c : PreviewTexture.Colors.values()) colourText = Math.max(colourText, tw("Colour: " + colourName.apply(c).getString()));
        int curveText = Math.max(tw("Curve: " + CommonComponents.optionStatus(true).getString()), tw("Curve: " + CommonComponents.optionStatus(false).getString()));
        int pad = 12, gap = 4;
        for (;; pad -= 2) {
            int tabs = 0;
            for (String n : names) tabs += tw(n) + pad + 2;
            if (M + tabs + gap + colourText + curveText + 2 * pad + gap + M <= width || pad <= 4) break;
        }
        int tx = M;
        for (int k = 0; k < names.length; k++) {
            Tab t = Tab.values()[k];
            int w = tw(names[k]) + pad;
            Button b = Button.builder(Component.literal(names[k]), btn -> switchTab(t)).bounds(tx, 6, w, 20).build();
            b.active = tab != t;
            if (t == Tab.COUNT) b.setTooltip(Tooltip.create(Component.literal("How many of each block and orientation the shape uses.")));
            addRenderableWidget(b);
            tx += w + 2;
        }
        int curveW = curveText + pad, colourW = colourText + pad;
        addRenderableWidget(cycler(List.of(PreviewTexture.Colors.values()), colors, colourName,
                width - M - curveW - gap - colourW, 6, colourW, "Colour", v -> { colors = v; textureDirty = true; }));
        addRenderableWidget(toggle(showCurve, width - M - curveW, 6, curveW, "Curve", v -> { showCurve = v; textureDirty = true; }));

        switch (tab) {
            case ELLIPSE -> { initEllipse(); presetButtons(); }
            case EQUATION -> { initEquation(); presetButtons(); }
            case BEZIER -> { initBezier(); presetButtons(); }
            case BLOCKS -> initBlocks();
            case COUNT -> { }
        }

        // Bottom bar: build options on the left, actions on the right, each button as wide as its text needs.
        int by = height - 26;
        String on = CommonComponents.optionStatus(true).getString(), off = CommonComponents.optionStatus(false).getString();
        int orientText = Math.max(tw("Upright"), tw("Flat")), labelW = Math.max(tw("Depth"), tw("Height")) + 2, fieldW = 30;
        int replaceText = Math.max(tw("Replace: " + on), tw("Replace: " + off)), carveText = Math.max(tw("Carve: " + on), tw("Carve: " + off));
        int actionText = Math.max(tw("Place"), Math.max(tw("Export"), tw("Done")));
        int bpad = 12, bgap = 4;
        for (;; bpad -= 2) {
            int left = M + orientText + bpad + bgap + labelW + fieldW + bgap + replaceText + bpad + bgap + carveText + bpad;
            int right = 3 * (actionText + bpad + 8) + 2 * bgap;
            if (left + bgap + right + M <= width || bpad <= 4) break;
            if (bgap > 2) bgap--;
        }
        int x = M;
        CycleButton<Boolean> orient = CycleButton.builder((Boolean v) -> Component.literal(v ? "Flat" : "Upright"), S.floor)
                .withValues(List.of(false, true)).displayOnlyValue()
                .create(x, by, orientText + bpad, 20, Component.literal("Build"), (b, v) -> setFloor(v));
        orient.setTooltip(Tooltip.create(Component.literal("Upright builds a wall, drawn from the side. Flat builds a floor, drawn from above.")));
        reverse.put(orient, () -> setFloor(!S.floor));
        addRenderableWidget(orient);
        x += orientText + bpad + bgap;
        labels.add(new Label(x, by + 6, S.floor ? "Height" : "Depth"));
        x += labelW;
        EditBox depthField = spin(x, by, fieldW, 20, String.valueOf(S.depth), v -> {
            int d = clampInt(v, 1, 64, S.depth);
            if (d != S.depth) { S.depth = d; dirty = true; }   // walls look different when more than one deep
        }, () -> S.depth, 1, 1, 64, () -> true, true);
        depthField.setTooltip(Tooltip.create(Component.literal(S.floor ? "How many layers the floor is stacked up." : "How many blocks deep the shape is built.")));
        x += fieldW + bgap;
        CycleButton<Boolean> replace = toggle(S.overwrite, x, by, replaceText + bpad, "Replace", v -> S.overwrite = v);
        replace.setTooltip(Tooltip.create(Component.literal(
                "On: the shape replaces blocks already in its way. Off: it only fills air and things like grass, water and snow layers.")));
        addRenderableWidget(replace);
        x += replaceText + bpad + bgap;
        CycleButton<Boolean> carve = toggle(S.carve, x, by, carveText + bpad, "Carve", v -> S.carve = v);
        carve.setTooltip(Tooltip.create(Component.literal(
                "Clears existing blocks from the space the shape encloses: inside a thin or thick ellipse, or the other side of a filled equation. Filled ellipses, lines and Bézier curves don't carve.")));
        addRenderableWidget(carve);
        int aw = actionText + bpad + 8, bx = width - M - 3 * aw - 2 * bgap;
        addRenderableWidget(Button.builder(Component.literal("Place"), b -> place()).bounds(bx, by, aw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Export"), b -> export()).bounds(bx + aw + bgap, by, aw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(bx + 2 * (aw + bgap), by, aw, 20).build());
    }

    private int tw(String s) { return font.width(s); }

    // ---------- presets ----------
    /** Top of the Save/Load preset row, the last thing in the left panel. */
    private int presetRowY() { return height - 52; }

    private void presetButtons() {
        int y = presetRowY(), w = (PANEL_W - 4) / 2;
        Button save = Button.builder(Component.literal("Save preset"), b -> savePreset()).bounds(M, y, w, 20).build();
        save.setTooltip(Tooltip.create(Component.literal("Save this tab's shape settings under a name.")));
        addRenderableWidget(save);
        Button load = Button.builder(Component.literal("Load preset"),
                b -> minecraft.gui.setScreen(new PresetsScreen(this, S.gen, this::loadPreset))).bounds(M + w + 4, y, w, 20).build();
        load.setTooltip(Tooltip.create(Component.literal("Browse, preview and load saved shapes.")));
        addRenderableWidget(load);
    }

    private void savePreset() {
        Gen g = S.gen;
        String suggestion = PresetStore.unique(g, PresetData.defaultName(S, g));
        minecraft.gui.setScreen(new NameDialogScreen(this, "Save preset", suggestion, name -> {
            if (name.isEmpty()) return new NameDialogScreen.Check(false, "Save", "Type a name for the preset.");
            if (PresetStore.find(g, name) != null) return new NameDialogScreen.Check(true, "Replace", "A preset with this name exists. Saving replaces it.");
            return new NameDialogScreen.Check(true, "Save", null);
        }, "Save blocks", "Also save the block for each piece type, and whether it's used.", (name, withBlocks) -> {
            PresetStore.save(g, name, S, withBlocks);
            flash("Saved preset \"" + name + "\"");
        }));
    }

    private void loadPreset(PresetStore.Preset p, boolean withBlocks) {
        ShapeSettings.Gen g = p.gen();
        if (g == null) return;
        PresetData.apply(p.data, g, S);
        if (withBlocks && p.blocks != null) { BlockChoices.apply(p.blocks, S, BlockChoices.CHOICE); textureDirty = true; }
        tab = switch (g) { case ELLIPSE -> Tab.ELLIPSE; case EQUATION -> Tab.EQUATION; case BEZIER -> Tab.BEZIER; };
        pointScroll = 0;
        dirty = true; autoFit = true;
        rebuildWidgets();
        flash("Loaded preset \"" + p.name + "\"");
    }

    private void switchTab(Tab t) {
        tab = t;
        if (t == Tab.ELLIPSE) S.gen = Gen.ELLIPSE;
        if (t == Tab.EQUATION) S.gen = Gen.EQUATION;
        if (t == Tab.BEZIER) S.gen = Gen.BEZIER;
        if (t == Tab.ELLIPSE || t == Tab.EQUATION || t == Tab.BEZIER) { dirty = true; autoFit = true; }
        rebuildWidgets();
    }

    private void initEllipse() {
        spin(M, row(0), 64, 20, String.valueOf(S.eW), v -> { S.eW = clampInt(v, 1, 400, S.eW); dirty = true; }, () -> S.eW, 1, 1, 400, () -> true, true);
        labels.add(new Label(M + 71, row(0) + 6, "×"));
        spin(M + 86, row(0), 64, 20, String.valueOf(S.eH), v -> { S.eH = clampInt(v, 1, 400, S.eH); dirty = true; }, () -> S.eH, 1, 1, 400, () -> true, true);
        addRenderableWidget(cycler(List.of(EllipseMode.values()), S.eMode, m -> Component.literal(switch (m) {
                    case THIN -> "Thin"; case FILLED -> "Filled"; case OUTWARDS -> "Thick outwards";
                    case INWARDS -> "Thick inwards"; case MIDDLE -> "Thick middle"; }),
                M, row(1), PANEL_W, "Shape", v -> { S.eMode = v; dirty = true; rebuildWidgets(); }));
        labels.add(new Label(M, row(2) + 6, "Thickness"));
        boolean thick = S.eMode == EllipseMode.OUTWARDS || S.eMode == EllipseMode.INWARDS || S.eMode == EllipseMode.MIDDLE;
        EditBox t = spin(M + 60, row(2), PANEL_W - 60, 20, fmt(S.eT), v -> { S.eT = clampNum(v, 0.0625, 50, S.eT); dirty = true; },
                () -> S.eT, 0.25, 0.0625, 50, () -> thick, false);
        t.setEditable(thick);
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
        EditBox eq = new EditBox(font, M, row(0), PANEL_W, 20, Component.literal("Equation"));
        eq.setMaxLength(256);
        eq.setValue(S.src);
        eq.setResponder(v -> { S.src = v; dirty = true; updateEquationControls(); });
        addRenderableWidget(eq);
        labels.add(new Label(M, row(1) + 6, "x"));
        addRenderableWidget(text(M + 10, row(1), 62, S.xmin, v -> { S.xmin = v; dirty = true; }));
        labels.add(new Label(M + 76, row(1) + 6, "to"));
        addRenderableWidget(text(M + 90, row(1), 60, S.xmax, v -> { S.xmax = v; dirty = true; }));
        labels.add(new Label(M, row(2) + 6, "y"));
        addRenderableWidget(text(M + 10, row(2), 62, S.ymin, v -> { S.ymin = v; dirty = true; }));
        labels.add(new Label(M + 76, row(2) + 6, "to"));
        addRenderableWidget(text(M + 90, row(2), 60, S.ymax, v -> { S.ymax = v; dirty = true; }));
        labels.add(new Label(M, row(3) + 6, "W"));
        spin(M + 10, row(3), 62, 20, String.valueOf(S.qW), v -> { S.qW = clampInt(v, 1, 400, S.qW); dirty = true; }, () -> S.qW, 1, 1, 400, () -> true, true);
        labels.add(new Label(M + 76, row(3) + 6, "H"));
        qHField = spin(M + 90, row(3), 60, 20, String.valueOf(S.qH), v -> { if (!S.qLock) { S.qH = clampInt(v, 1, 400, S.qH); dirty = true; } },
                () -> S.qH, 1, 1, 400, () -> !S.qLock, true);
        qHField.setEditable(!S.qLock);
        addRenderableWidget(toggle(S.qLock, M, row(4), PANEL_W, "Same scale", v -> { S.qLock = v; qHField.setEditable(!v); dirty = true; }));
        eqShape = cycler(List.of(EqMode.values()), S.qMode, m -> Component.literal(switch (m) {
                    case LINE -> "Line"; case UNDER -> "Fill under"; case OVER -> "Fill over"; }),
                M, row(5), PANEL_W, "Shape", v -> { S.qMode = v; dirty = true; updateEquationControls(); });
        addRenderableWidget(eqShape);
        labels.add(new Label(M, row(6) + 6, "Line width"));
        qLWField = spin(M + 60, row(6), PANEL_W - 60, 20, fmt(S.qLW), v -> { S.qLW = clampNum(v, 0.0625, 50, S.qLW); dirty = true; },
                () -> S.qLW, 0.25, 0.0625, 50, () -> !isInequality(S.src) && S.qMode == EqMode.LINE, false);
        updateEquationControls();
    }


    private static boolean isInequality(String src) {
        return src.contains("<") || src.contains(">") || src.contains("≤") || src.contains("≥");
    }

    /** An inequality decides the filled side itself, so the Shape choice (and line width) don't apply. */
    private void updateEquationControls() {
        if (eqShape == null) return;
        boolean ineq = isInequality(S.src);
        eqShape.active = !ineq;
        eqShape.setTooltip(ineq ? Tooltip.create(Component.literal("Your inequality sets the shape. Use = to choose it here.")) : null);
        if (qLWField != null) qLWField.setEditable(!ineq && S.qMode == EqMode.LINE);
    }

    /** A filled Bézier shape ignores line width. */
    private void updateBezierControls() {
        boolean line = S.bMode == BzMode.LINE;
        bLWField.setEditable(line);
        bLWField.setTooltip(line ? null : Tooltip.create(Component.literal("Filled shapes don't use a line width.")));
    }

    private void initBezier() {
        spin(M, row(0), 64, 20, String.valueOf(S.bW), v -> { S.bW = clampInt(v, 1, 400, S.bW); dirty = true; autoFit = true; }, () -> S.bW, 1, 1, 400, () -> true, true);
        labels.add(new Label(M + 71, row(0) + 6, "×"));
        spin(M + 86, row(0), 64, 20, String.valueOf(S.bH), v -> { S.bH = clampInt(v, 1, 400, S.bH); dirty = true; autoFit = true; }, () -> S.bH, 1, 1, 400, () -> true, true);
        addRenderableWidget(cycler(List.of(BzMode.values()), S.bMode, m -> Component.literal(m == BzMode.LINE ? "Line" : "Filled"),
                M, row(1), PANEL_W, "Shape", v -> { S.bMode = v; dirty = true; updateBezierControls(); }));
        labels.add(new Label(M, row(2) + 6, "Line width"));
        bLWField = spin(M + 60, row(2), PANEL_W - 60, 20, fmt(S.bLW), v -> { S.bLW = clampNum(v, 0.0625, 50, S.bLW); dirty = true; },
                () -> S.bLW, 0.25, 0.0625, 50, () -> S.bMode == BzMode.LINE, false);
        updateBezierControls();
        addRenderableWidget(toggle(S.snap, M, row(3), PANEL_W, "Snap to half blocks", v -> S.snap = v));
        Button add = Button.builder(Component.literal("Add point"), b -> {
            int n = S.pts.size();
            double[] a = S.pts.get(n - 2), c = S.pts.get(n - 1);
            S.pts.add(n - 1, new double[]{(a[0] + c[0]) / 2, (a[1] + c[1]) / 2});
            dirty = true; rebuildWidgets();
        }).bounds(M, row(4), 73, 20).build();
        add.active = S.pts.size() < 10;
        Button rem = Button.builder(Component.literal("Remove"), b -> {
            S.pts.remove(S.pts.size() - 2 >= 1 ? S.pts.size() - 2 : S.pts.size() - 1);
            dirty = true; rebuildWidgets();
        }).bounds(M + 77, row(4), 73, 20).build();
        rem.active = S.pts.size() > 2;
        addRenderableWidget(add);
        addRenderableWidget(rem);
        hint = "Drag the numbered handles in the preview.";
        hintY = row(5) + 2;

        // Point list: number, x and y (in blocks from the bottom left), scrollable when it doesn't fit.
        pointFields.clear();
        int n = S.pts.size();
        listTop = row(5) + 22;
        listVisible = Math.max(1, (presetRowY() - 4 - listTop) / POINT_ROW);
        pointScroll = Math.max(0, Math.min(pointScroll, n - listVisible));
        for (int k = pointScroll; k < Math.min(n, pointScroll + listVisible); k++) {
            int y = listTop + (k - pointScroll) * POINT_ROW, idx = k;
            labels.add(new Label(M, y + 4, String.valueOf(k + 1)));
            labels.add(new Label(M + 10, y + 4, "x"));
            labels.add(new Label(M + 71, y + 4, "y"));
            EditBox fx = new EditBox(font, M + 17, y, 50 - ARROW_SLOT, 16, Component.literal("Point " + (k + 1) + " x"));
            EditBox fy = new EditBox(font, M + 78, y, 50 - ARROW_SLOT, 16, Component.literal("Point " + (k + 1) + " y"));
            Button x = Button.builder(Component.literal("×"), b -> {
                if (S.pts.size() > 2) { S.pts.remove(idx); dirty = true; rebuildWidgets(); }
            }).bounds(M + 132, y, 18, 16).build();
            x.active = n > 2;
            x.setTooltip(Tooltip.create(Component.literal(n > 2 ? "Remove point " + (k + 1) : "A curve needs at least two points")));
            addRenderableWidget(x);
            for (int axis = 0; axis < 2; axis++) {
                EditBox f = axis == 0 ? fx : fy;
                int a = axis;
                f.setMaxLength(12);
                f.setValue(coord(S.pts.get(k)[a]));
                f.setResponder(v -> {
                    if (syncingPoints) return;
                    try {
                        double d = Double.parseDouble(v.trim());
                        if (Double.isFinite(d)) { S.pts.get(idx)[a] = d; dirty = true; }
                    } catch (NumberFormatException ignored) { }
                });
                addRenderableWidget(f);
                // Arrows step by half a block and keep the point inside the grid.
                arrows(f, (axis == 0 ? M + 17 : M + 78) + 50 - ARROW_SLOT + 1, y, 16,
                        () -> idx < S.pts.size() ? S.pts.get(idx)[a] : 0, 0.5, 0, a == 0 ? S.bW : S.bH, () -> true, false);
            }
            pointFields.add(new EditBox[]{fx, fy});
        }
    }

    private static final int POINT_ROW = 18;
    private static int pointScroll = 0;
    private int listTop, listVisible;
    private final List<EditBox[]> pointFields = new ArrayList<>();
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
                EditBox f = pointFields.get(r)[a];
                String want = coord(S.pts.get(k)[a]);
                if (!f.isFocused() && !f.getValue().equals(want)) f.setValue(want);
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
                boolean on = S.allows(f);
                Button use = Button.builder(Component.literal(on ? "Use" : "Off"), b -> { S.allow(f, !S.allows(f)); dirty = true; rebuildWidgets(); })
                        .bounds(M, y, 26, 20).build();
                if (S.floor && (f == Family.SLAB || f == Family.STAIRS)) {
                    use.active = false;
                    use.setTooltip(Tooltip.create(Component.literal("From above, " + BlockChoices.familyName(f).toLowerCase(java.util.Locale.ROOT)
                            + " look like full blocks, so flat builds don't use them.")));
                }
                addRenderableWidget(use);
            }
            Block block = BlockChoices.CHOICE.get(f);
            String name = font.plainSubstrByWidth(block.getName().getString(), PANEL_W - 54 - 8);
            Button pick = Button.builder(Component.literal(name), b -> minecraft.gui.setScreen(new BlockPickerScreen(this, f, chosen -> {
                BlockChoices.CHOICE.put(f, chosen);
                S.fullConnects = BlockChoices.fullBlockConnects();
                dirty = true; textureDirty = true;
                rebuildWidgets();
            }))).bounds(M + 48, y, PANEL_W - 48, 20).build();
            pick.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(BlockChoices.familyName(f) + ": " + block.getName().getString())));
            addRenderableWidget(pick);
            icons.add(new Icon(M + 29, y + 2, new ItemStack(block)));
        }
        addRenderableWidget(Button.builder(Component.literal("Match a colour…"), b -> minecraft.gui.setScreen(
                new ColorPickerScreen(this, BlockChoices.pickedColor >= 0 ? BlockChoices.pickedColor : 0x8E6B4A, rgb -> {
                    BlockChoices.autoSelect(rgb);
                    S.fullConnects = BlockChoices.fullBlockConnects();
                    dirty = true; textureDirty = true;
                    rebuildWidgets();
                }))).bounds(M, row(BlockChoices.FAMILIES.length), PANEL_W, 20).build());
        hint = "Choose a block per piece type, or match them all to one colour.";
        hintY = row(BlockChoices.FAMILIES.length + 1) + 2;
    }

    // ---------- block count (the web version's "Materials") ----------
    private int countScroll, countContentH;

    /** One line of the list, with its texts already cut to fit. */
    private record CountRow(boolean header, String text, String count, int countW, int state, boolean dim, String tip) {}
    /** The rows last built, and the result and depth they were built for. Every other input rebuilds the widgets, which clears them. */
    private List<CountRow> countRows;
    private Solver.Result countResult;
    private int countDepth;
    private static final int ICON = 14;
    /** Each piece's icon as rectangles, filled in on first use. */
    private static final int[][][] ICON_RECTS = new int[Pieces.COUNT][][];

    /** The states of a family that the current orientation can produce, in list order. */
    private List<Integer> statesFor(Family f, boolean floor) {
        List<Integer> out = new ArrayList<>();
        switch (f) {
            case FULL -> out.add(Pieces.FULL);
            case SLAB -> { if (!floor) { out.add(Pieces.SLAB_B); out.add(Pieces.SLAB_T); } }
            case STAIRS -> { if (!floor) for (int st = Pieces.ST_UR; st <= Pieces.ST_DL; st++) out.add(st); }
            case TRAPDOOR -> out.addAll(floor ? List.of(Pieces.TD_L, Pieces.TD_R, Pieces.F_TD_U, Pieces.F_TD_D)
                                              : List.of(Pieces.TD_B, Pieces.TD_T, Pieces.TD_L, Pieces.TD_R));
            case FENCE -> { int b = floor ? Pieces.F_FENCE : Pieces.FENCE; for (int k = 0; k < (floor ? 16 : 4); k++) out.add(b + k); }
            case PANE -> { int b = floor ? Pieces.F_PANE : Pieces.PANE; for (int k = 0; k < (floor ? 16 : 4); k++) out.add(b + k); }
            case WALL -> {
                if (floor) for (int k = 0; k < 16; k++) out.add(Pieces.F_WALL + k);
                else for (int st = Pieces.WALL; st < Pieces.F_TD_U; st++) {
                    int l = Pieces.wallLeft(st), r = Pieces.wallRight(st);
                    boolean possible = Pieces.wallPost(st) == (S.depth > 1 || Pieces.wallPostRule(l, r, Pieces.wallCovered(st)));
                    if (possible || result.counts()[st] > 0) out.add(st);
                }
            }
            default -> { }
        }
        return out;
    }

    private static String shortName(int st) {
        String n = Pieces.NAME[st];
        if (st == Pieces.FULL) return n;
        int c = n.indexOf(", ");
        n = c >= 0 ? n.substring(c + 2) : n;
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** Grouped by piece type, then one row per state. States that look the same (and are named the same) share a row. */
    private List<CountRow> countRows() {
        if (countRows != null && countResult == result && countDepth == S.depth) return countRows;
        List<CountRow> rows = new ArrayList<>();
        countRows = rows; countResult = result; countDepth = S.depth;
        if (result == null || result.target().error != null) return rows;
        boolean floor = result.floor();
        int mult = Math.max(1, S.depth);
        for (Family f : BlockChoices.FAMILIES) {
            java.util.LinkedHashMap<String, int[]> merged = new java.util.LinkedHashMap<>();
            for (int st : statesFor(f, floor)) merged.computeIfAbsent(shortName(st), k -> new int[]{st, 0})[1] += result.counts()[st] * mult;
            int total = 0;
            for (int[] m : merged.values()) total += m[1];
            boolean unused = floor && (f == Family.SLAB || f == Family.STAIRS);
            String blockName = BlockChoices.CHOICE.get(f).getName().getString();
            String n = String.valueOf(total);
            int nw = tw(n);
            String title = BlockChoices.familyName(f) + (unused ? " (not used flat)" : S.allows(f) ? "" : " (off)");
            rows.add(new CountRow(true, font.plainSubstrByWidth(title, PANEL_W - nw - 6), n, nw, -1, unused || !S.allows(f), blockName));
            for (var e : merged.entrySet()) {
                int st = e.getValue()[0], count = e.getValue()[1];
                n = String.valueOf(count);
                nw = tw(n);
                String name = e.getKey();
                int room = PANEL_W - 20 - nw - 6;
                if (tw(name) > room) name = font.plainSubstrByWidth(name, room - tw("…")) + "…";
                rows.add(new CountRow(false, name, n, nw, st, count == 0, Pieces.NAME[st] + ": " + blockName + " ×" + count));
            }
        }
        return rows;
    }

    private void drawCount(GuiGraphicsExtractor ctx, int mx, int my) {
        int top = top(), bottom = height - 30, x0 = M, x1 = M + PANEL_W;
        ctx.enableScissor(x0, top, x1 + 4, bottom);
        int y = top - countScroll;
        String tip = null;
        for (CountRow r : countRows()) {
            if (r.header) y += 4;
            int h = r.header ? 14 : 17;
            if (y + h <= top || y >= bottom) { y += h; continue; }   // scrolled out of view
            if (r.header) {
                int c = r.dim ? 0xFF808A96 : 0xFFE4E9EF;
                ctx.text(font, r.text, x0, y, c, false);
                ctx.text(font, r.count, x1 - r.countW, y, c, false);
                ctx.fill(x0, y + 10, x1, y + 11, 0x40FFFFFF);
                if (my >= y && my < y + 11 && mx >= x0 && mx < x1 && my >= top && my < bottom) tip = r.tip;
            } else {
                drawIcon(ctx, r.state, x0 + 1, y + 1, r.dim);
                int c = r.dim ? 0xFF5C6470 : 0xFFC8CED6;
                ctx.text(font, r.text, x0 + 20, y + 4, c, false);
                ctx.text(font, r.count, x1 - r.countW, y + 4, r.dim ? 0xFF5C6470 : 0xFFFFFFFF, false);
                if (my >= y && my < y + 16 && mx >= x0 && mx < x1 && my >= top && my < bottom) tip = r.tip;
            }
            y += h;
        }
        countContentH = y + countScroll - top;
        ctx.disableScissor();
        int view = bottom - top;
        if (countContentH > view) {
            int bar = Math.max(10, view * view / countContentH), by = top + (view - bar) * countScroll / (countContentH - view);
            ctx.fill(x1 + 1, top, x1 + 3, bottom, 0x30FFFFFF);
            ctx.fill(x1 + 1, by, x1 + 3, by + bar, 0xA0FFFFFF);
        }
        if (tip != null) ctx.setTooltipForNextFrame(font, Component.literal(tip), mx, my);
    }

    /** A 14-pixel piece icon painted exactly like the preview: same colours, same silhouette outline. */
    private void drawIcon(GuiGraphicsExtractor ctx, int p, int x, int y, boolean dim) {
        ctx.fill(x - 1, y - 1, x + ICON + 1, y + ICON + 1, 0x40FFFFFF);
        ctx.fill(x, y, x + ICON, y + ICON, PreviewTexture.BG);
        int[][] rects = ICON_RECTS[p];
        if (rects == null) rects = ICON_RECTS[p] = Silhouette.rects(p, ICON);
        int fill = PreviewTexture.colorFor(p, colors), dark = PreviewTexture.darken(fill, 0.55f);
        if (dim) { fill = (fill & 0xFFFFFF) | 0x58000000; dark = (dark & 0xFFFFFF) | 0x58000000; }
        for (int[] r : rects) ctx.fill(x + r[0], y + r[1], x + r[2], y + r[3], r[4] == 1 ? dark : fill);
    }

    // ---------- widgets ----------
    /** Horizontal space the arrows take from a field: 8 px of arrows plus a 1 px gap. */
    private static final int ARROW_SLOT = 9;

    private record Spinner(Arrow up, Arrow down, DoubleSupplier value, double min, double max, BooleanSupplier enabled) {}
    private final List<Spinner> spinners = new ArrayList<>();

    /** A small up or down arrow. Drawn by hand so it stays crisp at 8 pixels, and greyed out when it can't go further. */
    private static final class Arrow extends AbstractButton {
        private final boolean up;
        private final Runnable action;

        Arrow(int x, int y, int w, int h, boolean up, Runnable action) {
            super(x, y, w, h, Component.literal(up ? "Increase" : "Decrease"));
            this.up = up; this.action = action;
        }

        @Override public void onPress(InputWithModifiers input) { action.run(); }

        @Override
        protected void extractContents(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY();
            ctx.fill(x, y, x + width, y + height, !active ? 0xFF22262C : isHovered() ? 0xFF55606E : 0xFF3A424D);
            int c = active ? 0xFFE4E9EF : 0xFF4E5560, cx = x + width / 2, cy = y + height / 2;
            for (int r = 0; r < 3; r++) {          // a 5-pixel-wide triangle
                int half = up ? r : 2 - r, yy = cy - 1 + r - (up ? 1 : 0);
                ctx.fill(cx - half, yy, cx + half + 1, yy + 1, c);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
    }

    /**
     * A number field of total width w whose right edge holds up/down arrows (taken from the field, not added to it).
     * The arrows step the value within [min, max] and grey out at the ends or when the field doesn't apply.
     */
    private EditBox spin(int x, int y, int w, int h, String value, Consumer<String> onChange,
                                 DoubleSupplier current, double step, double min, double max, BooleanSupplier enabled, boolean integer) {
        EditBox f = new EditBox(font, x, y, w - ARROW_SLOT, h, Component.empty());
        f.setMaxLength(64);
        f.setValue(value);
        f.setResponder(onChange);
        addRenderableWidget(f);
        arrows(f, x + w - ARROW_SLOT + 1, y, h, current, step, min, max, enabled, integer);
        return f;
    }

    private void arrows(EditBox f, int ax, int y, int h, DoubleSupplier current, double step, double min, double max,
                        BooleanSupplier enabled, boolean integer) {
        int top = h / 2;
        Arrow up = new Arrow(ax, y, ARROW_SLOT - 1, top, true, () -> nudge(f, current, step, min, max, integer));
        Arrow down = new Arrow(ax, y + top, ARROW_SLOT - 1, h - top, false, () -> nudge(f, current, -step, min, max, integer));
        addRenderableWidget(up);
        addRenderableWidget(down);
        Spinner sp = new Spinner(up, down, current, min, max, enabled);
        spinners.add(sp);
        refresh(sp);
    }

    /** Steps the value and writes it into the field, whose own listener then applies it (clamped to the limits). */
    private void nudge(EditBox f, DoubleSupplier current, double step, double min, double max, boolean integer) {
        double v = Math.max(min, Math.min(max, current.getAsDouble() + step));
        f.setValue(integer ? String.valueOf(Math.round(v)) : coord(v));
    }

    private static void refresh(Spinner sp) {
        boolean on = sp.enabled.getAsBoolean();
        double v = sp.value.getAsDouble();
        sp.up.active = on && v < sp.max - 1e-9;
        sp.down.active = on && v > sp.min + 1e-9;
    }

    /** A button that steps through options: click for the next one, right-click for the previous one. */
    private <T> CycleButton<T> cycler(List<T> values, T initial, Function<T, Component> names,
                                              int x, int y, int w, String label, Consumer<T> onChange) {
        CycleButton<T> b = CycleButton.builder(names, initial).withValues(values)
                .create(x, y, w, 20, Component.literal(label), (btn, v) -> onChange.accept(v));
        reverse.put(b, () -> {
            T prev = values.get((values.indexOf(b.getValue()) - 1 + values.size()) % values.size());
            b.setValue(prev);
            onChange.accept(prev);
        });
        return b;
    }

    private CycleButton<Boolean> toggle(boolean initial, int x, int y, int w, String label, Consumer<Boolean> onChange) {
        return cycler(List.of(true, false), initial, CommonComponents::optionStatus, x, y, w, label, onChange);
    }

    private void setFloor(boolean flat) {
        S.floor = flat;
        dirty = true; textureDirty = true; autoFit = true;
        rebuildWidgets();
    }

    private EditBox text(int x, int y, int w, String value, Consumer<String> onChange) {
        EditBox f = new EditBox(font, x, y, w, 20, Component.empty());
        f.setMaxLength(64);
        f.setValue(value);
        f.setResponder(onChange);
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
                    if (qHField != null && !qHField.getValue().equals(String.valueOf(S.qH))) qHField.setValue(String.valueOf(S.qH));
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
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        poll();
        for (Spinner sp : spinners) refresh(sp);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        for (Label l : labels) ctx.text(font, l.text, l.x, l.y, 0xFFC8CED6);
        for (Icon ic : icons) ctx.item(ic.stack, ic.x, ic.y);
        int hintLimit = tab == Tab.BLOCKS || tab == Tab.COUNT ? height - 30 : presetRowY() - 4;
        if (hint != null && hintY + 18 < hintLimit) ctx.textWithWordWrap(font, Component.literal(hint), M, hintY, PANEL_W, 0xFF9AA5B3, false);
        if (tab == Tab.BEZIER && S.pts.size() > listVisible) {
            int h = listVisible * POINT_ROW - 2, bar = Math.max(6, h * listVisible / S.pts.size());
            int by = listTop + (h - bar) * pointScroll / Math.max(1, S.pts.size() - listVisible);
            ctx.fill(M + PANEL_W + 1, listTop, M + PANEL_W + 3, listTop + h, 0x30FFFFFF);
            ctx.fill(M + PANEL_W + 1, by, M + PANEL_W + 3, by + bar, 0xA0FFFFFF);
        }

        if (tab == Tab.COUNT) drawCount(ctx, mouseX, mouseY);

        int x0 = cx0(), y0 = cy0(), x1 = cx1(), y1 = cy1();
        ctx.fill(x0, y0, x1, y1, 0xFF15181D);
        ctx.enableScissor(x0, y0, x1, y1);
        if (result != null && preview.id() != null) {
            var m = ctx.pose();
            m.pushMatrix();
            m.translate(panX, panY);
            float s = zoom / preview.sub;
            m.scale(s, s);
            ctx.blit(RenderPipelines.GUI_TEXTURED, preview.id(), 0, 0, 0f, 0f, preview.width, preview.height, preview.width, preview.height);
            m.popMatrix();
            drawHover(ctx, mouseX, mouseY);
            if (S.gen == Gen.BEZIER && tab == Tab.BEZIER) drawHandles(ctx);
        }
        ctx.disableScissor();
        ctx.outline(x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, 0xFF3A424D);

        // status line across the top of the canvas
        String line; int color = 0xFFDDE3EA;
        if (result == null) line = "Working…";
        else if (result.target().error != null) { line = result.target().error; color = 0xFFFF8098; }
        else {
            int total = 0;
            for (int p = 1; p < Pieces.COUNT; p++) total += result.counts()[p];
            double pct = result.area() > 0 ? result.err() / result.area() * 100 : 0;
            line = (result.floor() ? "Seen from above: " : "") + total + " pieces on " + result.nx() + "×" + result.ny() + String.format(", mismatch %.2f blocks² (%.1f%%)", result.err(), pct)
                    + (job != null ? "  Updating…" : "");
        }
        if (status != null && System.currentTimeMillis() < statusUntil) { line = status; color = 0xFFFFE08A; }
        ctx.fill(x0, y0, x1, y0 + 12, 0xB0000000);
        ctx.text(font, font.plainSubstrByWidth(line, x1 - x0 - 6), x0 + 3, y0 + 2, color, false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(0, 0, width, height, 0x80101317);
    }

    private int[] cellAt(double mx, double my) {
        if (result == null || mx < cx0() || mx >= cx1() || my < cy0() + 12 || my >= cy1()) return null;
        int i = (int) Math.floor((mx - panX) / zoom), jr = (int) Math.floor((my - panY) / zoom);
        if (i < 0 || jr < 0 || i >= result.nx() || jr >= result.ny()) return null;
        return new int[]{i, result.ny() - 1 - jr};
    }

    private void drawHover(GuiGraphicsExtractor ctx, int mx, int my) {
        int[] c = cellAt(mx, my);
        if (c == null) return;
        int sx = Math.round(panX + c[0] * zoom), sy = Math.round(panY + (result.ny() - 1 - c[1]) * zoom), sz = Math.max(2, Math.round(zoom));
        ctx.outline(sx, sy, sz, sz, 0xFF6EA0FF);
        int p = result.at(c[0], c[1]);
        String txt = "Column " + (c[0] + 1) + ", row " + (c[1] + 1) + ": " + Pieces.NAME[p]
                + (p != Pieces.EMPTY ? " (" + BlockChoices.blockFor(p).getName().getString() + ")" : "");
        if (result.target().hasMath) {
            var t = result.target();
            txt += String.format(", x ≈ %.2f, y ≈ %.2f", t.mx0 + (c[0] + .5) * t.msx, t.my0 + (c[1] + .5) * t.msy);
        }
        int y = cy1() - 12;
        ctx.fill(cx0(), y, cx1(), cy1(), 0xB0000000);
        ctx.text(font, font.plainSubstrByWidth(txt, cx1() - cx0() - 6), cx0() + 3, y + 2, 0xFFDDE3EA, false);
    }

    private float[] handleScreen(int k) {
        double[] p = S.pts.get(k);
        return new float[]{panX + (float) p[0] * zoom, panY + (float) (result.ny() - p[1]) * zoom};
    }

    private void drawHandles(GuiGraphicsExtractor ctx) {
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
            ctx.text(font, n, x - font.width(n) / 2 + 1, y - 3, 0xFFFFFFFF, false);
        }
    }

    private void dashedLine(GuiGraphicsExtractor ctx, float x1, float y1, float x2, float y2) {
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y(); int button = event.button();
        if (button == 1)
            for (var e : reverse.entrySet()) {
                AbstractWidget w = e.getKey();
                if (w.active && w.visible && w.isMouseOver(mx, my)) {
                    w.playDownSound(minecraft.getSoundManager());
                    e.getValue().run();   // may rebuild the screen, so stop looking straight away
                    return true;
                }
            }
        if (super.mouseClicked(event, doubled)) return true;
        boolean inCanvas = mx >= cx0() && mx < cx1() && my >= cy0() && my < cy1();
        if (!inCanvas || result == null) return false;
        setFocused(null);
        if (button == 0 && S.gen == Gen.BEZIER && tab == Tab.BEZIER) {
            for (int k = S.pts.size() - 1; k >= 0; k--) {
                float[] h = handleScreen(k);
                if (Math.abs(mx - h[0]) <= 6 && Math.abs(my - h[1]) <= 6) { dragPoint = k; return true; }
            }
        }
        if (button == 1 || button == 2 || button == 0) { panning = true; return true; }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double mx = event.x(), my = event.y();
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
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragPoint = -1; panning = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (tab == Tab.COUNT && mx < M + PANEL_W + 4 && my >= top()) {
            countScroll = Math.max(0, Math.min(Math.max(0, countContentH - (height - 30 - top())), countScroll - (int) Math.round(v * 24)));
            return true;
        }
        if (tab == Tab.BEZIER && mx < M + PANEL_W && my >= listTop && S.pts.size() > listVisible) {
            pointScroll = Math.max(0, Math.min(S.pts.size() - listVisible, pointScroll - (int) Math.signum(v)));
            rebuildWidgets();
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
        if (minecraft.player != null && !minecraft.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            Placement.say(Component.literal("Note: you'll need operator permissions to confirm placement. Export works for everyone."));
        Placement.start(layout, S.depth, S.overwrite, S.carve, S.floor);
        onClose();
    }

    private void export() {
        if (!ready()) return;
        Layout layout = Layout.of(result);
        if (layout.isEmpty()) { flash("The shape is empty, so there's nothing to export."); return; }
        String kind = switch (S.gen) { case ELLIPSE -> "ellipse"; case EQUATION -> "equation"; case BEZIER -> "bezier"; };
        try {
            String author = minecraft.player != null ? minecraft.player.getName().getString() : "Curve Generator";
            Path file = LitematicExporter.export(layout, S.depth, S.floor, LitematicExporter.defaultName(kind) + (S.floor ? "_floor" : ""), author);
            flash("Exported to schematics/" + file.getFileName());
            Placement.say(Component.literal("Curve Generator: exported to schematics/" + file.getFileName()));
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
