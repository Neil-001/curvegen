package dev.curvegen.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.LitematicExporter;
import dev.curvegen.client.Placement;
import dev.curvegen.client.PresetStore;
import dev.curvegen.client.edit.EditShape;
import dev.curvegen.client.edit.Editor;
import dev.curvegen.core.Bezier3;
import dev.curvegen.core.Count3;
import dev.curvegen.core.Layout;
import dev.curvegen.core.Mesh3;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.Pieces;
import dev.curvegen.core.Pieces3;
import dev.curvegen.core.PresetData;
import dev.curvegen.core.ShapeSettings.BzMode;
import dev.curvegen.core.ShapeSettings.EllipseMode;
import dev.curvegen.core.ShapeSettings.Eq3Mode;
import dev.curvegen.core.ShapeSettings.EqMode;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.Shape3;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.Silhouette;
import dev.curvegen.core.Solver;
import dev.curvegen.core.Solver3;
import dev.curvegen.core.edit.Edit3D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class CurveScreen extends ControlScreen {
    private enum Tab { ELLIPSE, EQUATION, BEZIER, ELLIPSOID, TORUS, EQUATION3, BEZIER3, SURFACE, BLOCKS, COUNT }

    private static final Tab[] TABS_2D = {Tab.ELLIPSE, Tab.EQUATION, Tab.BEZIER, Tab.BLOCKS, Tab.COUNT},
            TABS_3D = {Tab.ELLIPSOID, Tab.TORUS, Tab.EQUATION3, Tab.BEZIER3, Tab.SURFACE, Tab.BLOCKS, Tab.COUNT};
    private static final String[] NAMES_2D = {"Ellipse", "Equation", "Bézier", "Blocks", "Count"},
            NAMES_3D = {"Ellipsoid", "Torus", "Equation", "Bézier", "Surface", "Blocks", "Count"};

    private static Tab tabFor(Gen g) {
        return switch (g) {
            case ELLIPSE -> Tab.ELLIPSE; case EQUATION -> Tab.EQUATION; case BEZIER -> Tab.BEZIER; case ELLIPSOID -> Tab.ELLIPSOID;
            case TORUS -> Tab.TORUS; case EQUATION3 -> Tab.EQUATION3; case BEZIER3 -> Tab.BEZIER3; case SURFACE -> Tab.SURFACE;
        };
    }

    /** The shape a tab edits, or null for Blocks and Count, which go with whichever shape is set. */
    private static Gen genFor(Tab t) {
        return switch (t) {
            case ELLIPSE -> Gen.ELLIPSE; case EQUATION -> Gen.EQUATION; case BEZIER -> Gen.BEZIER; case ELLIPSOID -> Gen.ELLIPSOID;
            case TORUS -> Gen.TORUS; case EQUATION3 -> Gen.EQUATION3; case BEZIER3 -> Gen.BEZIER3; case SURFACE -> Gen.SURFACE;
            default -> null;
        };
    }

    private static final ShapeSettings S = CurveGenClient.SETTINGS;
    static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Curve Generator solver");
        t.setDaemon(true);
        return t;
    });


    private static Tab tab = Tab.ELLIPSE;
    /** Whether the tabs are the 3D shapes'. It always agrees with the shape that is set. */
    private static boolean mode3d;
    /** The shape each set of tabs was last on, which the 2D and 3D switch goes back to. */
    private static Gen last2d = Gen.ELLIPSE, last3d = Gen.ELLIPSOID;
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
    private String status;
    private long statusUntil;

    private int dragPoint = -1;
    private boolean panning;

    public CurveScreen() {
        super(Component.literal("Curve Generator"));
        mode3d = S.is3d();
        remember();
        if (tab != Tab.BLOCKS && tab != Tab.COUNT) tab = tabFor(S.gen);
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
    /** The bottom of the picture. Under a 3D shape's there is a row of buttons for how it's coloured. */
    private int py1() { return mode3d ? cy1() - 22 : cy1(); }

    @Override
    protected void init() {
        labels.clear(); icons.clear(); pointFields.clear(); countRows = null; hint = null; qHField = null; qLWField = null; bLWField = null; eqShape = null;
        binds.clear(); live.clear(); listCount = 0;

        // Top bar: the 2D and 3D switch and the tabs on the left, view options and the settings cog on the right, each
        // button as wide as its text needs. The 3D shapes have more tabs, so their colouring is under the picture.
        Tab[] tabs = mode3d ? TABS_3D : TABS_2D;
        String[] names = mode3d ? NAMES_3D : NAMES_2D;
        Function<PreviewTexture.Colors, Component> colourName = c -> Component.literal(switch (c) {
            case STONE -> "Plain"; case PIECES -> "Piece types"; case BLOCKS -> "Block colours"; });
        int colourText = 0;
        for (PreviewTexture.Colors c : PreviewTexture.Colors.values()) colourText = Math.max(colourText, tw("Colour: " + colourName.apply(c).getString()));
        int curveText = Math.max(tw("Curve: " + CommonComponents.optionStatus(true).getString()), tw("Curve: " + CommonComponents.optionStatus(false).getString()));
        int modeText = Math.max(tw("2D"), tw("3D"));
        int pad = 12, gap = 4, cogW = 20;
        for (;; pad -= 2) {
            int tabsW = modeText + pad + 2;
            for (String n : names) tabsW += tw(n) + pad + 2;
            int view = mode3d ? curveText + pad : colourText + curveText + 2 * pad;
            if (M + tabsW + gap + view + gap + cogW + gap + M <= width || pad <= 4) break;
            if (gap > 2) gap--;
        }
        int tx = M;
        CycleButton<Boolean> mode = CycleButton.builder((Boolean v) -> Component.literal(v ? "3D" : "2D"), mode3d)
                .withValues(List.of(false, true)).displayOnlyValue()
                .create(tx, 6, modeText + pad, 20, Component.literal("Shapes"), (b, v) -> setMode(v));
        mode.setTooltip(Tooltip.create(Component.literal("Switches the tabs between flat shapes and 3D shapes.")));
        reverse.put(mode, () -> setMode(!mode3d));
        addRenderableWidget(mode);
        tx += modeText + pad + 2;
        for (int k = 0; k < names.length; k++) {
            Tab t = tabs[k];
            int w = tw(names[k]) + pad;
            Button b = Button.builder(Component.literal(names[k]), btn -> switchTab(t)).bounds(tx, 6, w, 20).build();
            b.active = tab != t;
            if (t == Tab.COUNT) b.setTooltip(Tooltip.create(Component.literal("How many of each block and orientation the shape uses.")));
            addRenderableWidget(b);
            tx += w + 2;
        }
        int curveW = curveText + pad, colourW = colourText + pad, cogX = width - M - cogW;
        if (!mode3d) addRenderableWidget(cycler(List.of(PreviewTexture.Colors.values()), colors, colourName,
                cogX - gap - curveW - gap - colourW, 6, colourW, "Colour", v -> { colors = v; textureDirty = true; }));
        addRenderableWidget(toggle(showCurve, cogX - gap - curveW, 6, curveW, "Curve", v -> { showCurve = v; textureDirty = true; }));
        addRenderableWidget(new CogButton(cogX, 6, cogW, 20, () -> minecraft.gui.setScreen(new SettingsScreen(this))));
        if (mode3d) {
            int faceText = Math.max(tw("Match colours to: Side"), tw("Match colours to: Top")), room = cx1() - cx0(), p = 12;
            while (p > 4 && colourText + faceText + 2 * p + 4 > room) p -= 2;
            addRenderableWidget(cycler(List.of(PreviewTexture.Colors.values()), colors, colourName, cx0(), cy1() - 20, colourText + p, "Colour", v -> colors = v));
            CycleButton<Boolean> face = cycler(List.of(false, true), S.topColours, v -> Component.literal(v ? "Top" : "Side"),
                    cx0() + colourText + p + 4, cy1() - 20, faceText + p, "Match colours to", v -> S.topColours = v);
            face.setTooltip(Tooltip.create(Component.literal("Which face of a block its colour is taken from: for this picture, for the shape in the world and for Match a colour.")));
            addRenderableWidget(face);
        }

        switch (tab) {
            case ELLIPSE -> { initEllipse(); presetButtons(); }
            case EQUATION -> { initEquation(); presetButtons(); }
            case BEZIER -> { initBezier(); presetButtons(); }
            case ELLIPSOID -> { initEllipsoid(); presetButtons(); }
            case TORUS -> { initTorus(); presetButtons(); }
            case EQUATION3 -> { initEquation3(); presetButtons(); }
            case BEZIER3 -> { initBezier3(); presetButtons(); }
            case SURFACE -> { initSurface(); presetButtons(); }
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
        if (S.is3d()) {
            orient.active = false;
            orient.setTooltip(Tooltip.create(Component.literal("A 3D shape is turned in the world, with the rotate and tip keys.")));
        }
        addRenderableWidget(orient);
        x += orientText + bpad + bgap;
        labels.add(new Label(x, by + 6, S.floor ? "Height" : "Depth"));
        x += labelW;
        EditBox depthField = spin(x, by, fieldW, 20, String.valueOf(S.depth), v -> {
            int d = clampInt(v, 1, 64, S.depth);
            if (d != S.depth) { S.depth = d; dirty = true; }   // walls look different when more than one deep
        }, () -> S.depth, 1, 1, 64, () -> !S.is3d(), true);
        depthField.setTooltip(Tooltip.create(Component.literal(S.is3d() ? "A 3D shape has its own depth. Resize it in the world."
                : S.floor ? "How many layers the floor is stacked up." : "How many blocks deep the shape is built.")));
        if (S.is3d()) depthField.setEditable(false);
        x += fieldW + bgap;
        CycleButton<Boolean> replace = toggle(S.overwrite, x, by, replaceText + bpad, "Replace", v -> S.overwrite = v);
        replace.setTooltip(Tooltip.create(Component.literal(
                "On: the shape replaces blocks already in its way. Off: it only fills air and things like grass, water and snow layers.")));
        addRenderableWidget(replace);
        x += replaceText + bpad + bgap;
        CycleButton<Boolean> carve = toggle(S.carve, x, by, carveText + bpad, "Carve", v -> S.carve = v);
        carve.setTooltip(Tooltip.create(Component.literal(
                S.is3d() ? "Clears existing blocks from the space the shape encloses: inside a hollow ellipsoid, or the other side of a filled equation. Filled shapes, a torus, surfaces and curves don't carve."
                : "Clears existing blocks from the space the shape encloses: inside a thin or thick ellipse, or the other side of a filled equation. Filled ellipses, lines and Bézier curves don't carve.")));
        addRenderableWidget(carve);
        int aw = actionText + bpad + 8, bx = width - M - 3 * aw - 2 * bgap;
        Button place = Button.builder(Component.literal("Place"), b -> place()).bounds(bx, by, aw, 20).build();
        boolean placeable = Editor.isActive() || !mode3d || RadialScreen.offers(S.gen);
        place.active = placeable;
        place.setTooltip(Tooltip.create(Component.literal(!placeable ? "This shape can't be put in the world yet. Use Export instead."
                : Editor.isActive() ? "Back to the shape in the world, with the changes made here."
                : "Puts the shape in the world as a hologram you can move and resize before placing it.")));
        addRenderableWidget(place);
        addRenderableWidget(Button.builder(Component.literal("Export"), b -> export()).bounds(bx + aw + bgap, by, aw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(bx + 2 * (aw + bgap), by, aw, 20).build());
    }

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
        tab = tabFor(g);
        mode3d = S.is3d();
        remember();
        pointScroll = 0;
        dirty = true; autoFit = true; fit3 = true;
        rebuildWidgets();
        flash("Loaded preset \"" + p.name + "\"");
    }

    private void switchTab(Tab t) {
        tab = t;
        Gen g = genFor(t);
        if (g != null) {
            S.gen = g;
            remember();
            dirty = true; autoFit = true; fit3 = true;
            if (mode3d) pointScroll = 0;
        }
        rebuildWidgets();
    }

    private static void remember() { if (S.is3d()) last3d = S.gen; else last2d = S.gen; }

    /** Switches the tabs between the 2D and the 3D shapes, going back to the shape that set of tabs was last on. */
    private void setMode(boolean three) {
        if (three == mode3d) return;
        mode3d = three;
        // Both solvers share one thread, so a 3D solve left running would hold up the 2D shape.
        if (!three && cancel3 != null) cancel3.set(true);
        S.gen = three ? last3d : last2d;
        if (tab != Tab.BLOCKS && tab != Tab.COUNT) tab = tabFor(S.gen);
        pointScroll = 0; countScroll = 0;
        dirty = true; textureDirty = true; autoFit = true; fit3 = true;
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


    private static boolean isInequality(String src) { return dev.curvegen.core.edit.Edit2D.isInequality(src); }

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
        listCount = n;
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
    private int listTop, listVisible, listCount;
    private final List<EditBox[]> pointFields = new ArrayList<>();
    private boolean syncingPoints;

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
        // On short screens the rows close up, so the last one stays clear of the buttons along the bottom.
        int rows = BlockChoices.FAMILIES.length + 1, pitch = Math.min(ROW, (height - 30 - top()) / rows), h = Math.min(20, pitch - 1);
        for (int k = 0; k < BlockChoices.FAMILIES.length; k++) {
            Family f = BlockChoices.FAMILIES[k];
            int y = top() + k * pitch;
            if (f == Family.FULL) {
                labels.add(new Label(M + 4, y + (h - 8) / 2, "Use"));
            } else {
                boolean on = S.allows(f);
                Button use = Button.builder(Component.literal(on ? "Use" : "Off"), b -> { S.allow(f, !S.allows(f)); dirty = true; rebuildWidgets(); })
                        .bounds(M, y, 26, h).build();
                if (S.floor && !S.is3d() && (f == Family.SLAB || f == Family.STAIRS)) {
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
            }))).bounds(M + 48, y, PANEL_W - 48, h).build();
            pick.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(BlockChoices.familyName(f) + ": " + block.getName().getString())));
            addRenderableWidget(pick);
            icons.add(new Icon(M + 29, y + (h - 16) / 2, new ItemStack(block)));
        }
        addRenderableWidget(Button.builder(Component.literal("Match a colour…"), b -> minecraft.gui.setScreen(
                new ColorPickerScreen(this, BlockChoices.pickedColor >= 0 ? BlockChoices.pickedColor : 0x8E6B4A, rgb -> {
                    BlockChoices.autoSelect(rgb);
                    S.fullConnects = BlockChoices.fullBlockConnects();
                    dirty = true; textureDirty = true;
                    rebuildWidgets();
                }))).bounds(M, top() + (rows - 1) * pitch, PANEL_W, h).build());
        hint = "Choose a block per piece type, or match them all to one colour.";
        hintY = top() + rows * pitch + 2;
    }

    // ---------- block count (the web version's "Materials") ----------
    private int countScroll, countContentH;

    /** One line of the list, with its texts already cut to fit. */
    private record CountRow(boolean header, String text, String count, int countW, int state, boolean dim, String tip, boolean solid) {}
    /** The rows last built, and the result and depth they were built for. Every other input rebuilds the widgets, which clears them. */
    private List<CountRow> countRows;
    private Object countResult;
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
            case SHELF -> out.addAll(floor ? List.of(Pieces.F_SH_L, Pieces.F_SH_R, Pieces.F_SH_U, Pieces.F_SH_D)
                                           : List.of(Pieces.SH_L, Pieces.SH_R));
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
            case CHAIN -> { out.add(Pieces.CHAIN_H); out.add(Pieces.CHAIN_V); }
            case ROD -> { for (int st = Pieces.ROD_U; st <= Pieces.ROD_R; st++) out.add(st); }
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
        Object from = mode3d ? solved3 : result;
        if (countRows != null && countResult == from && countDepth == S.depth) return countRows;
        List<CountRow> rows = new ArrayList<>();
        countRows = rows; countResult = from; countDepth = S.depth;
        if (mode3d) { countRows3(rows); return rows; }
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
            rows.add(new CountRow(true, font.plainSubstrByWidth(title, PANEL_W - nw - 6), n, nw, -1, unused || !S.allows(f), blockName, false));
            for (var e : merged.entrySet()) {
                int st = e.getValue()[0], count = e.getValue()[1];
                n = String.valueOf(count);
                nw = tw(n);
                String name = e.getKey();
                int room = PANEL_W - 20 - nw - 6;
                if (tw(name) > room) name = font.plainSubstrByWidth(name, room - tw("…")) + "…";
                rows.add(new CountRow(false, name, n, nw, st, count == 0, Pieces.NAME[st] + ": " + blockName + " ×" + count, false));
            }
        }
        return rows;
    }

    /** A 3D shape's rows: by piece type, then one row for each way a builder would tell its states apart. */
    private void countRows3(List<CountRow> rows) {
        if (solved3 == null || solved3.error != null) return;
        int[] counts = solved3.result.counts();
        for (Family f : BlockChoices.FAMILIES) {
            int total = 0;
            for (Count3.Group g : Count3.GROUPS) if (g.family() == f) total += g.count(counts);
            String blockName = BlockChoices.CHOICE.get(f).getName().getString();
            String n = String.valueOf(total);
            int nw = tw(n);
            String title = BlockChoices.familyName(f) + (S.allows(f) ? "" : " (off)");
            rows.add(new CountRow(true, font.plainSubstrByWidth(title, PANEL_W - nw - 6), n, nw, -1, !S.allows(f), blockName, true));
            for (Count3.Group g : Count3.GROUPS) {
                if (g.family() != f) continue;
                int count = g.count(counts);
                n = String.valueOf(count);
                nw = tw(n);
                String name = g.name();
                int room = PANEL_W - 20 - nw - 6;
                if (tw(name) > room) name = font.plainSubstrByWidth(name, room - tw("…")) + "…";
                String full = f == Family.FULL ? g.name() : BlockChoices.familyName(f) + ", " + Character.toLowerCase(g.name().charAt(0)) + g.name().substring(1);
                rows.add(new CountRow(false, name, n, nw, g.icon(), count == 0, full + ": " + blockName + " ×" + count, true));
            }
        }
    }

    /** A 3D piece's icon: its boxes seen from the south, in the colour the picture gives its family. */
    private void drawIcon3(GuiGraphicsExtractor ctx, int s, int x, int y, boolean dim) {
        ctx.fill(x - 1, y - 1, x + ICON + 1, y + ICON + 1, 0x40FFFFFF);
        ctx.fill(x, y, x + ICON, y + ICON, PreviewTexture.BG);
        int fill = 0xFF000000 | Preview3.palette(colors, BlockChoices.CHOICE, S.topColours)[Pieces3.FAMILY[s].ordinal()];
        if (dim) fill = (fill & 0xFFFFFF) | 0x58000000;
        for (int[] b : Pieces3.BOXES[s]) {
            int x0 = Math.round(b[0] * ICON / 16f), x1 = Math.max(x0 + 1, Math.round(b[3] * ICON / 16f));
            int y0 = Math.round((16 - b[4]) * ICON / 16f), y1 = Math.max(y0 + 1, Math.round((16 - b[1]) * ICON / 16f));
            ctx.fill(x + x0, y + y0, x + x1, y + y1, fill);
        }
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
                if (r.solid) drawIcon3(ctx, r.state, x0 + 1, y + 1, r.dim);
                else drawIcon(ctx, r.state, x0 + 1, y + 1, r.dim);
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

    // ---------- 3D tabs ----------
    /** A field kept in step with the settings: what it should show, whether it applies, and its tooltips either way. */
    private static final class Bind {
        final EditBox field; final Supplier<String> text; final BooleanSupplier on; final String tip, offTip;
        Boolean was;
        Bind(EditBox field, Supplier<String> text, BooleanSupplier on, String tip, String offTip) {
            this.field = field; this.text = text; this.on = on; this.tip = tip; this.offTip = offTip;
        }
    }
    private final List<Bind> binds = new ArrayList<>();
    /** Run every frame, for controls that depend on what was just typed. */
    private final List<Runnable> live = new ArrayList<>();
    private int pitch3, h3;
    private static final BooleanSupplier ALWAYS = () -> true;

    /**
     * Lays out a 3D tab's rows. On a short screen they close up, so that {@code rows} of them and {@code reserve}
     * pixels more fit above the preset buttons.
     */
    private void rows3(int rows, int reserve) {
        pitch3 = Math.max(14, Math.min(ROW, (presetRowY() - 2 - top() - reserve) / rows));
        h3 = Math.min(20, pitch3 - 2);
    }
    private int r3(int n) { return top() + n * pitch3; }
    private void label3(int x, int row, String text) { labels.add(new Label(x, r3(row) + (h3 - 8) / 2, text)); }
    private <W extends AbstractWidget> W short3(W w) { w.setHeight(h3); return addRenderableWidget(w); }

    private void bind(EditBox f, Supplier<String> text, BooleanSupplier on, String tip, String offTip) {
        Bind b = new Bind(f, text, on, tip, offTip);
        binds.add(b);
        sync(b);
    }

    private void sync(Bind b) {
        boolean on = b.on.getAsBoolean();
        if (b.was == null || b.was != on) {
            b.was = on;
            b.field.setEditable(on);
            String tip = on ? b.tip : b.offTip;
            b.field.setTooltip(tip == null ? null : Tooltip.create(Component.literal(tip)));
        }
        String want = b.text.get();
        if (!b.field.isFocused() && !b.field.getValue().equals(want)) b.field.setValue(want);
    }

    /** Keeps fields in step with settings that something else changed: a dragged point, a locked size, the torus's box. */
    private void syncBinds() {
        syncingPoints = true;
        for (Bind b : binds) sync(b);
        syncingPoints = false;
        for (Runnable r : live) r.run();
    }

    private EditBox whole(int x, int row, int w, IntSupplier get, IntConsumer set, int min, int max, BooleanSupplier on, String tip, String offTip) {
        EditBox f = spin(x, r3(row), w, h3, String.valueOf(get.getAsInt()), v -> {
            if (syncingPoints || !on.getAsBoolean()) return;
            int n = clampInt(v, min, max, get.getAsInt());
            if (n != get.getAsInt()) { set.accept(n); dirty = true; }
        }, get::getAsInt, 1, min, max, on, true);
        bind(f, () -> String.valueOf(get.getAsInt()), on, tip, offTip);
        return f;
    }

    private EditBox thickness(int row, DoubleSupplier get, DoubleConsumer set, BooleanSupplier on, String offTip) {
        label3(M, row, "Thickness");
        EditBox f = spin(M + 60, r3(row), PANEL_W - 60, h3, fmt(get.getAsDouble()), v -> {
            if (syncingPoints || !on.getAsBoolean()) return;
            double n = clampNum(v, 0.0625, 50, get.getAsDouble());
            if (n != get.getAsDouble()) { set.accept(n); dirty = true; }
        }, get, 0.25, 0.0625, 50, on, false);
        bind(f, () -> fmt(get.getAsDouble()), on, null, offTip);
        return f;
    }

    /** Three sizes on one row, each with a one-letter name. */
    private void sizes3(int row, String[] names, String[] tips, IntSupplier[] get, IntConsumer[] set, BooleanSupplier[] on, String offTip) {
        for (int k = 0; k < 3; k++) {
            int x = M + k * 51;
            label3(x, row, names[k]);
            whole(x + 9, row, 39, get[k], set[k], 1, Shape3.MAX_SIZE, on[k], tips[k], offTip);
        }
    }

    private static final String[] WHD = {"W", "H", "D"};
    private static final String VIEW_HINT = " Drag the picture to turn it, right-drag to move it and scroll to zoom.";

    private void initEllipsoid() {
        rows3(3, 46);
        sizes3(0, WHD, new String[]{"Width, from west to east.", "Height.", "Depth, from north to south."},
                new IntSupplier[]{() -> S.e3W, () -> S.e3H, () -> S.e3D}, new IntConsumer[]{v -> S.e3W = v, v -> S.e3H = v, v -> S.e3D = v},
                new BooleanSupplier[]{ALWAYS, ALWAYS, ALWAYS}, null);
        short3(cycler(List.of(EllipseMode.values()), S.e3Mode, m -> Component.literal(switch (m) {
                    case THIN -> "Thin"; case FILLED -> "Filled"; case OUTWARDS -> "Thick outwards";
                    case INWARDS -> "Thick inwards"; case MIDDLE -> "Thick middle"; }),
                M, r3(1), PANEL_W, "Shape", v -> { S.e3Mode = v; dirty = true; rebuildWidgets(); }));
        thickness(2, () -> S.e3T, v -> S.e3T = v, () -> S.e3Mode == EllipseMode.OUTWARDS || S.e3Mode == EllipseMode.INWARDS || S.e3Mode == EllipseMode.MIDDLE,
                "Only the thick shapes have a thickness.");
        hint = switch (S.e3Mode) {
            case THIN -> "A hollow shell: the outer surface follows the ellipsoid.";
            case FILLED -> "Everything inside the ellipsoid.";
            case OUTWARDS -> "A shell that grows outwards from the ellipsoid.";
            case INWARDS -> "A shell that grows inwards from the ellipsoid.";
            case MIDDLE -> "A shell centred on the ellipsoid.";
        } + VIEW_HINT;
        hintY = r3(3) + 2;
    }

    private static int[] torus() { return new int[]{S.tRing, S.tTube, S.tW, S.tH, S.tD}; }
    private static void torus(int[] v) { S.tRing = v[0]; S.tTube = v[1]; S.tW = v[2]; S.tH = v[3]; S.tD = v[4]; }

    private void initTorus() {
        rows3(3, 46);
        sizes3(0, WHD, new String[]{"Width of the box the ring is stretched to fill.", "Height of the box the ring is stretched to fill.",
                        "Depth of the box the ring is stretched to fill."},
                new IntSupplier[]{() -> S.tW, () -> S.tH, () -> S.tD}, new IntConsumer[]{v -> S.tW = v, v -> S.tH = v, v -> S.tD = v},
                new BooleanSupplier[]{ALWAYS, ALWAYS, ALWAYS}, null);
        // The ring and the tube scale the box, so a number half typed would spoil it for the digits that follow.
        // Each keystroke starts again from the sizes as they were before the field was clicked.
        int[][] before = {torus(), torus()};
        label3(M, 1, "Ring");
        EditBox ring = whole(M + 24, 1, 48, () -> S.tRing, v -> { torus(before[0]); Edit3D.setRing(S, v); }, 1, Shape3.MAX_SIZE, ALWAYS,
                "How far across the round ring is. Changing it scales the box's width and depth with it.", null);
        label3(M + 78, 1, "Tube");
        EditBox tube = whole(M + 104, 1, 46, () -> S.tTube, v -> { torus(before[1]); Edit3D.setTube(S, v); }, 1, Shape3.MAX_SIZE, ALWAYS,
                "How thick the tube is, up to the ring's size. Changing it scales the box's height with it.", null);
        live.add(() -> {
            if (!ring.isFocused()) before[0] = torus();
            if (!tube.isFocused()) before[1] = torus();
        });
        short3(cycler(List.of(false, true), S.tHollow, v -> Component.literal(v ? "Hollow" : "Filled"), M, r3(2), PANEL_W, "Shape",
                v -> { S.tHollow = v; dirty = true; }));
        hint = "Ring and tube describe a round ring. The box stretches it." + VIEW_HINT;
        hintY = r3(3) + 2;
    }

    private EditBox text3(int x, int row, int w, int max, String value, String name, Consumer<String> onChange) {
        EditBox f = new EditBox(font, x, r3(row), w, h3, Component.literal(name));
        f.setMaxLength(max);
        f.setValue(value);
        f.setResponder(onChange);
        return addRenderableWidget(f);
    }

    private void initEquation3() {
        rows3(8, 0);
        text3(M, 0, PANEL_W, 256, S.src3, "Equation", v -> { S.src3 = v; dirty = true; })
                .setTooltip(Tooltip.create(Component.literal("Anything in x, y and z with =, < or >, where z is height. Without one of those it means z = what you typed.")));
        String[] axis = {"x", "y", "z"};
        List<Supplier<String>> from = List.of(() -> S.x3min, () -> S.y3min, () -> S.z3min), to = List.of(() -> S.x3max, () -> S.y3max, () -> S.z3max);
        List<Consumer<String>> setFrom = List.of(v -> S.x3min = v, v -> S.y3min = v, v -> S.z3min = v), setTo = List.of(v -> S.x3max = v, v -> S.y3max = v, v -> S.z3max = v);
        for (int k = 0; k < 3; k++) {
            int a = k;
            label3(M, 1 + k, axis[k]);
            text3(M + 10, 1 + k, 62, 64, from.get(k).get(), axis[k] + " from", v -> { setFrom.get(a).accept(v); dirty = true; });
            label3(M + 76, 1 + k, "to");
            text3(M + 90, 1 + k, 60, 64, to.get(k).get(), axis[k] + " to", v -> { setTo.get(a).accept(v); dirty = true; });
        }
        // With the same scale on every axis, the depth and height come from the width and the ranges.
        BooleanSupplier free = () -> !S.q3Lock;
        sizes3(4, new String[]{"W", "D", "H"}, new String[]{"Width in blocks, along x.", "Depth in blocks, along y.", "Height in blocks, along z."},
                new IntSupplier[]{() -> S.q3W, () -> S.q3Lock ? live3().nz() : S.q3D, () -> S.q3Lock ? live3().ny() : S.q3H},
                new IntConsumer[]{v -> S.q3W = v, v -> S.q3D = v, v -> S.q3H = v}, new BooleanSupplier[]{ALWAYS, free, free},
                "Same scale works this out from the width and the ranges.");
        short3(toggle(S.q3Lock, M, r3(5), PANEL_W, "Same scale", v -> {
            // Unlocked, the box starts out the size it was, so the shape doesn't jump.
            if (!v && live3().error() == null) { S.q3D = live3().nz(); S.q3H = live3().ny(); }
            S.q3Lock = v; dirty = true;
        }));
        CycleButton<Eq3Mode> shape = short3(cycler(List.of(Eq3Mode.values()), S.q3Mode, m -> Component.literal(switch (m) {
                    case SURFACE -> "Surface"; case BELOW -> "Fill below"; case ABOVE -> "Fill above"; }),
                M, r3(6), PANEL_W, "Shape", v -> { S.q3Mode = v; dirty = true; }));
        boolean[] was = {false};
        Runnable inequality = () -> {
            boolean ineq = isInequality(S.src3);
            if (ineq == was[0] && shape.active == !ineq) return;
            was[0] = ineq;
            shape.active = !ineq;
            shape.setTooltip(ineq ? Tooltip.create(Component.literal("Your inequality sets the shape. Use = to choose it here.")) : null);
        };
        inequality.run();
        live.add(inequality);
        thickness(7, () -> S.q3T, v -> S.q3T = v, () -> !isInequality(S.src3) && S.q3Mode == Eq3Mode.SURFACE, "Only a surface has a thickness.");
    }

    private void initBezier3() {
        rows3(5, 16 + 3 * POINT_ROW);
        sizes3(0, WHD, new String[]{"Width of the box the curve is in.", "Height of the box the curve is in.", "Depth of the box the curve is in."},
                new IntSupplier[]{() -> S.b3W, () -> S.b3H, () -> S.b3D}, new IntConsumer[]{v -> S.b3W = v, v -> S.b3H = v, v -> S.b3D = v},
                new BooleanSupplier[]{ALWAYS, ALWAYS, ALWAYS}, null);
        CycleButton<BzMode> shape = short3(cycler(List.of(BzMode.values()), BzMode.LINE, m -> Component.literal(m == BzMode.LINE ? "Line" : "Filled"),
                M, r3(1), PANEL_W, "Shape", v -> { }));
        shape.active = false;
        shape.setTooltip(Tooltip.create(Component.literal("A 3D curve is always a line. Only a 2D curve can be filled.")));
        thickness(2, () -> S.b3T, v -> S.b3T = v, ALWAYS, null);
        short3(toggle(S.snap, M, r3(3), PANEL_W, "Snap to half blocks", v -> S.snap = v));
        int n = S.pts3.size();
        Button add = Button.builder(Component.literal("Add point"), b -> {
            if (Bezier3.insert(S.pts3, 0.5) >= 0) { dirty = true; rebuildWidgets(); }
        }).bounds(M, r3(4), 73, h3).build();
        add.active = n < Bezier3.MAX_POINTS;
        add.setTooltip(Tooltip.create(Component.literal(add.active ? "Adds a point without changing the curve. The points between the ends all move."
                : "A curve can have " + Bezier3.MAX_POINTS + " points at most.")));
        Button rem = Button.builder(Component.literal("Remove"), b -> {
            if (S.pts3.size() > 2) {
                List<double[]> fewer = Bezier3.reduce(S.pts3);
                S.pts3.clear(); S.pts3.addAll(fewer);
                dirty = true; rebuildWidgets();
            }
        }).bounds(M + 77, r3(4), 73, h3).build();
        rem.active = n > 2;
        rem.setTooltip(Tooltip.create(Component.literal(rem.active ? "Takes a point out and keeps the curve as close as one point fewer allows. Use × to remove one point and leave the rest."
                : "A curve needs at least two points.")));
        addRenderableWidget(add);
        addRenderableWidget(rem);
        pointList3(5, S.pts3, 0);
    }

    private void initSurface() {
        rows3(4, 16 + 3 * POINT_ROW);
        sizes3(0, WHD, new String[]{"Width of the box the surface is in.", "Height of the box the surface is in.", "Depth of the box the surface is in."},
                new IntSupplier[]{() -> S.sW, () -> S.sH, () -> S.sD}, new IntConsumer[]{v -> S.sW = v, v -> S.sH = v, v -> S.sD = v},
                new BooleanSupplier[]{ALWAYS, ALWAYS, ALWAYS}, null);
        thickness(1, () -> S.sT, v -> S.sT = v, ALWAYS, null);
        short3(toggle(S.snap, M, r3(2), PANEL_W, "Snap to half blocks", v -> S.snap = v));
        String tip = " of control points, from " + Bezier3.MIN_GRID + " to " + Bezier3.MAX_GRID + ". Adding one leaves the surface as it is. Removing one keeps it as close as it can.";
        label3(M, 3, "Rows");
        whole(M + 26, 3, 40, () -> S.sRows, v -> grid(v, S.sCols), Bezier3.MIN_GRID, Bezier3.MAX_GRID, ALWAYS, "Rows" + tip, null);
        label3(M + 69, 3, "Columns");
        whole(M + 110, 3, 40, () -> S.sCols, v -> grid(S.sRows, v), Bezier3.MIN_GRID, Bezier3.MAX_GRID, ALWAYS, "Columns" + tip, null);
        pointList3(4, S.sPts, S.sCols);
    }

    /** Adds or removes rows and columns of a surface until it has this many. */
    private void grid(int rows, int cols) {
        while (S.sRows < rows && Bezier3.addRow(S, 0.5) >= 0) { }
        while (S.sRows > rows && Bezier3.removeRow(S)) { }
        while (S.sCols < cols && Bezier3.addColumn(S, 0.5) >= 0) { }
        while (S.sCols > cols && Bezier3.removeColumn(S)) { }
        relist = true;
    }

    /**
     * The point list of a curve or a surface under {@code row}: x, y and z in blocks from the box's lowest corner,
     * scrollable when it doesn't fit. A surface's points are named by row and column and can't be removed one by one.
     */
    private void pointList3(int row, List<double[]> pts, int cols) {
        boolean grid = cols > 0;
        int n = pts.size(), x0 = M + (grid ? 15 : 13), fw = grid ? 44 : 38, step = fw + (grid ? 1 : 2);
        for (int a = 0; a < 3; a++) labels.add(new Label(x0 + a * step + fw / 2 - 3, r3(row) + 1, String.valueOf("xyz".charAt(a))));
        listCount = n;
        listTop = r3(row) + 11;
        listVisible = Math.max(1, (presetRowY() - 4 - listTop) / POINT_ROW);
        pointScroll = Math.max(0, Math.min(pointScroll, n - listVisible));
        for (int k = pointScroll; k < Math.min(n, pointScroll + listVisible); k++) {
            int y = listTop + (k - pointScroll) * POINT_ROW, idx = k;
            String name = grid ? (k / cols + 1) + "," + (k % cols + 1) : String.valueOf(k + 1);
            labels.add(new Label(M, y + 4, name));
            for (int axis = 0; axis < 3; axis++) {
                int a = axis;
                EditBox f = new EditBox(font, x0 + a * step, y, fw, 16, Component.literal("Point " + name + " " + "xyz".charAt(a)));
                f.setMaxLength(12);
                f.setValue(coord(pts.get(k)[a]));
                f.setResponder(v -> {
                    if (syncingPoints || idx >= pts.size()) return;
                    try {
                        double d = Double.parseDouble(v.trim());
                        if (Double.isFinite(d) && d != pts.get(idx)[a]) { pts.get(idx)[a] = d; dirty = true; }
                    } catch (NumberFormatException ignored) { }
                });
                addRenderableWidget(f);
                bind(f, () -> idx < pts.size() ? coord(pts.get(idx)[a]) : "", ALWAYS, null, null);
            }
            if (grid) continue;
            Button x = Button.builder(Component.literal("×"), b -> {
                if (pts.size() > 2 && idx < pts.size()) { pts.remove(idx); dirty = true; rebuildWidgets(); }
            }).bounds(M + 133, y, 17, 16).build();
            x.active = n > 2;
            x.setTooltip(Tooltip.create(Component.literal(n > 2 ? "Remove point " + (k + 1) : "A curve needs at least two points")));
            addRenderableWidget(x);
        }
    }

    // ---------- the 3D preview ----------
    /** A solve of a 3D shape: the blocks, their faces, and how many there are. With an {@code error} the rest is null. */
    record Solved3(Solver3.Result result, Mesh3 mesh, int blocks, String error) {}

    private final Preview3 preview3 = new Preview3();
    private Solved3 solved3;
    private Future<Solved3> job3;
    private AtomicBoolean cancel3;
    /** When solves started being dropped because the shape kept changing, or 0. After {@link #DROP_FOR} the next one runs to its end. */
    private long droppingSince;
    private static final long DROP_FOR = 400;
    private boolean fit3 = true, relist, orbiting;
    private int[] fitted;
    /** The shape as the settings stand, and the settings it was made from. */
    private Shape3 live3;
    private ShapeSettings liveFor;
    /** Where a dragged point was grabbed: the cursor's offset from it on the screen, and its depth. */
    private double[] grab;

    /** The shape the settings describe right now. Its outline and box are drawn at once, while the blocks catch up. */
    private Shape3 live3() {
        if (liveFor == null || !S.same(liveFor)) {
            liveFor = S.copy();
            live3 = Shape3.of(liveFor);
            preview3.wires(live3.error() == null ? live3.wireframe() : List.of(), live3.pad());
        }
        return live3;
    }

    private void poll3() {
        if (relist) { relist = false; rebuildWidgets(); }
        Shape3 shape = live3();
        syncBinds();
        if (job3 != null && job3.isDone()) {
            Solved3 r = null;
            try { r = job3.get(); } catch (Exception e) { flash("Couldn't build this shape: " + e.getMessage()); }
            // A solve dropped because the screen was covered leaves nothing behind, so the shape is solved again.
            if (r == null && cancel3.get()) dirty = true;
            job3 = null;
            if (r != null) {
                solved3 = r;
                droppingSince = 0;
                preview3.mesh(r.mesh, r.result == null ? 0 : r.result.shape().pad());
            }
        }
        if (dirty) {
            long now = System.currentTimeMillis();
            if (job3 == null) {
                dirty = false;
                ShapeSettings copy = S.copy();
                AtomicBoolean cancelled = cancel3 = new AtomicBoolean();
                job3 = EXEC.submit(() -> solve3(copy, cancelled::get));
            } else if (!cancel3.get()) {
                // A newer shape supersedes the one being solved, unless that has kept happening for a while.
                if (droppingSince == 0) droppingSince = now;
                if (now - droppingSince < DROP_FOR) cancel3.set(true);
            }
        }
        preview3.palette(Preview3.palette(colors, BlockChoices.CHOICE, S.topColours));
        preview3.showWires(showCurve);
        // The box the player set, without the room a thickness adds: a thicker shape keeps the view it has.
        int pad = shape.pad();
        int[] dims = {shape.nx() - 2 * pad, shape.ny() - 2 * pad, shape.nz() - 2 * pad, cx1() - cx0(), py1() - cy0()};
        if (fit3 || !Arrays.equals(dims, fitted)) {
            fit3 = false; fitted = dims;
            preview3.fit(shape.nx(), shape.ny(), shape.nz(), pad, cx0(), cy0() + 12, cx1(), py1());
        }
    }

    /** Runs on the worker thread. Returns null once the solve is no longer wanted. */
    static Solved3 solve3(ShapeSettings copy, BooleanSupplier cancelled) {
        Shape3 shape = Shape3.of(copy);
        if (shape.error() != null) return new Solved3(null, null, 0, shape.error());
        Solver3.Result r = Solver3.solve(shape, copy, cancelled);
        if (r == null) return null;
        int blocks = 0;
        for (int p = 1; p < Pieces3.COUNT; p++) blocks += r.counts()[p];
        return new Solved3(r, new Mesh3(r), blocks, null);
    }

    /** The control points the tab shows in the picture, or null. */
    private List<double[]> points3() { return tab == Tab.BEZIER3 ? S.pts3 : tab == Tab.SURFACE ? S.sPts : null; }

    private void drawHandles3(GuiGraphicsExtractor ctx, int mx, int my) {
        List<double[]> pts = points3();
        boolean grid = tab == Tab.SURFACE, inside = mx >= cx0() && mx < cx1() && my >= cy0() + 12 && my < py1();
        int hover = dragPoint >= 0 ? dragPoint : inside && !orbiting && !panning ? preview3.cam.pick(pts, mx, my, grid ? 5 : 6) : -1;
        for (int k = 0; k < pts.size(); k++) {
            double[] p = pts.get(k), s = preview3.cam.project(p[0], p[1], p[2]);
            int x = (int) Math.round(s[0]), y = (int) Math.round(s[1]), r = grid ? 3 : 5;
            ctx.fill(x - r, y - r, x + r + 1, y + r + 1, 0xFFFFFFFF);
            ctx.fill(x - r + 1, y - r + 1, x + r, y + r, k == hover ? 0xFFFFB84D : 0xFF3F7BE0);
            if (grid) continue;
            String n = String.valueOf(k + 1);
            ctx.text(font, n, x - font.width(n) / 2 + 1, y - 3, 0xFFFFFFFF, false);
        }
        if (grid && hover >= 0 && dragPoint < 0)
            ctx.setTooltipForNextFrame(font, Component.literal("Row " + (hover / S.sCols + 1) + ", column " + (hover % S.sCols + 1)), mx, my);
    }

    private boolean clicked3(double mx, double my, int button, boolean doubled) {
        setFocused(null);
        List<double[]> pts = points3();
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            int k = pts == null ? -1 : preview3.cam.pick(pts, mx, my, tab == Tab.SURFACE ? 5 : 6);
            if (k >= 0) {
                double[] p = pts.get(k), s = preview3.cam.project(p[0], p[1], p[2]);
                dragPoint = k;
                grab = new double[]{s[0] - mx, s[1] - my, s[2]};
                // The list scrolls to the point being dragged, so its numbers can be watched.
                if (k < pointScroll || k >= pointScroll + listVisible) { pointScroll = Math.max(0, k - listVisible / 2); relist = true; }
            } else if (doubled) fit3 = true;
            else orbiting = true;
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_RIGHT || button == InputConstants.MOUSE_BUTTON_MIDDLE) { panning = true; return true; }
        return false;
    }

    private boolean dragged3(double mx, double my, double dx, double dy) {
        List<double[]> pts = points3();
        if (dragPoint >= 0) {
            if (pts == null || dragPoint >= pts.size()) return true;
            // The point follows the cursor in the plane that faces the camera, and stays in its box.
            double[] p = preview3.cam.unproject(mx + grab[0], my + grab[1], grab[2]);
            int[] box = tab == Tab.BEZIER3 ? new int[]{S.b3W, S.b3H, S.b3D} : new int[]{S.sW, S.sH, S.sD};
            for (int a = 0; a < 3; a++) {
                if (S.snap) p[a] = Math.round(p[a] * 2) / 2.0;
                p[a] = Math.max(0, Math.min(box[a], p[a]));
            }
            pts.set(dragPoint, p);
            dirty = true;
            return true;
        }
        if (orbiting) { preview3.cam.orbit(dx, dy); return true; }
        if (panning) { preview3.cam.pan(dx, dy); return true; }
        return false;
    }

    private void export3() {
        if (solved3 == null || job3 != null || dirty) { flash("Still working on the shape, try again in a moment."); return; }
        if (solved3.error != null) { flash("Fix the shape first: " + solved3.error); return; }
        Solver3.Result r = solved3.result;
        BlockState[] states = BlockChoices.statesFor3();
        List<EditShape.Placed> blocks = new ArrayList<>(solved3.blocks);
        for (int y = 0, c = 0; y < r.ny(); y++)
            for (int z = 0; z < r.nz(); z++)
                for (int x = 0; x < r.nx(); x++, c++)
                    if (r.grid()[c] != Pieces3.AIR) blocks.add(new EditShape.Placed(x, y, z, states[r.grid()[c]]));
        if (blocks.isEmpty()) { flash("The shape is empty, so there's nothing to export."); return; }
        try {
            String author = minecraft.player != null ? minecraft.player.getName().getString() : "Curve Generator";
            Path file = LitematicExporter.export(blocks, LitematicExporter.defaultName(S.gen.name().toLowerCase(java.util.Locale.ROOT)), author);
            flash("Exported to schematics/" + file.getFileName());
            Placement.say(Component.literal("Exported to schematics/" + file.getFileName()));
        } catch (Exception e) {
            flash("Export failed: " + e.getMessage());
        }
    }

    // ---------- widgets ----------
    /** The settings button: a cogwheel drawn by hand on an ordinary button, so it stays crisp at any scale. */
    private static final class CogButton extends AbstractButton {
        private static final String[] COG = {
                "....###....",
                ".##.###.##.",
                ".#########.",
                "..#######..",
                "####...####",
                "####...####",
                "####...####",
                "..#######..",
                ".#########.",
                ".##.###.##.",
                "....###....",
        };
        private final Runnable action;

        CogButton(int x, int y, int w, int h, Runnable action) {
            super(x, y, w, h, Component.literal("Settings"));
            this.action = action;
            setTooltip(Tooltip.create(Component.literal("Settings")));
        }

        @Override public void onPress(InputWithModifiers input) { action.run(); }

        @Override
        protected void extractContents(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
            extractDefaultSprite(ctx);
            int n = COG.length, x0 = getX() + (width - n) / 2, y0 = getY() + (height - n) / 2;
            for (int pass = 0; pass < 2; pass++) {   // a shadow first, as button text has
                int c = pass == 0 ? 0xFF3F3F3F : active ? 0xFFFFFFFF : 0xFFA0A0A0, o = 1 - pass;
                for (int r = 0; r < n; r++)
                    for (int k = 0; k < n; k++)
                        if (COG[r].charAt(k) == '#') ctx.fill(x0 + k + o, y0 + r + o, x0 + k + o + 1, y0 + r + o + 1, c);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
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
        exportHologram();
        if (mode3d) { poll3(); return; }
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
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        for (Label l : labels) ctx.text(font, l.text, l.x, l.y, 0xFFC8CED6);
        for (Icon ic : icons) ctx.item(ic.stack, ic.x, ic.y);
        int hintLimit = tab == Tab.BLOCKS || tab == Tab.COUNT ? height - 30 : presetRowY() - 4;
        if (hint != null && hintY + 18 < hintLimit) ctx.textWithWordWrap(font, Component.literal(hint), M, hintY, PANEL_W, 0xFF9AA5B3, false);
        if (listCount > listVisible) {
            int h = listVisible * POINT_ROW - 2, bar = Math.max(6, h * listVisible / listCount);
            int by = listTop + (h - bar) * pointScroll / Math.max(1, listCount - listVisible);
            ctx.fill(M + PANEL_W + 1, listTop, M + PANEL_W + 3, listTop + h, 0x30FFFFFF);
            ctx.fill(M + PANEL_W + 1, by, M + PANEL_W + 3, by + bar, 0xA0FFFFFF);
        }

        if (tab == Tab.COUNT) drawCount(ctx, mouseX, mouseY);

        int x0 = cx0(), y0 = cy0(), x1 = cx1(), y1 = py1();
        ctx.fill(x0, y0, x1, y1, 0xFF15181D);
        ctx.enableScissor(x0, y0, x1, y1);
        if (mode3d) {
            preview3.draw(ctx, x0, y0, x1, y1);
            if (points3() != null) drawHandles3(ctx, mouseX, mouseY);
        } else if (result != null && preview.id() != null) {
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
        if (mode3d) {
            if (solved3 == null) line = "Working…";
            else if (solved3.error != null) { line = solved3.error; color = 0xFFFF8098; }
            else {
                Solver3.Result r = solved3.result;
                double pct = r.volume() > 0 ? r.err() / r.volume() * 100 : 0;
                String size = solved3.blocks + " blocks in " + r.nx() + "×" + r.ny() + "×" + r.nz(), busy = job3 != null || dirty ? "  Updating…" : "";
                line = size + String.format(", mismatch %.1f blocks³ (%.1f%%)", r.err(), pct) + busy;
                // On a narrow screen the share alone says how close the blocks are.
                if (tw(line) > x1 - x0 - 6) line = size + String.format(", mismatch %.1f%%", pct) + busy;
            }
        }
        else if (result == null) line = "Working…";
        else if (result.target().error != null) { line = result.target().error; color = 0xFFFF8098; }
        else {
            int total = 0;
            for (int p = 1; p < Pieces.COUNT; p++) total += result.counts()[p];
            double pct = result.area() > 0 ? result.err() / result.area() * 100 : 0;
            String size = (result.floor() ? "Seen from above: " : "") + total + " pieces on " + result.nx() + "×" + result.ny(), busy = job != null ? "  Updating…" : "";
            line = size + String.format(", mismatch %.2f blocks² (%.1f%%)", result.err(), pct) + busy;
            if (tw(line) > x1 - x0 - 6) line = size + String.format(", mismatch %.1f%%", pct) + busy;
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
        if (super.mouseClicked(event, doubled)) return true;
        boolean inCanvas = mx >= cx0() && mx < cx1() && my >= cy0() && my < py1();
        if (mode3d) return inCanvas && clicked3(mx, my, button, doubled);
        if (!inCanvas || result == null) return false;
        setFocused(null);
        if (button == InputConstants.MOUSE_BUTTON_LEFT && S.gen == Gen.BEZIER && tab == Tab.BEZIER) {
            for (int k = S.pts.size() - 1; k >= 0; k--) {
                float[] h = handleScreen(k);
                if (Math.abs(mx - h[0]) <= 6 && Math.abs(my - h[1]) <= 6) { dragPoint = k; return true; }
            }
        }
        if (button == InputConstants.MOUSE_BUTTON_RIGHT || button == InputConstants.MOUSE_BUTTON_MIDDLE || button == InputConstants.MOUSE_BUTTON_LEFT) { panning = true; return true; }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double mx = event.x(), my = event.y();
        if (mode3d) return dragged3(mx, my, dx, dy) || super.mouseDragged(event, dx, dy);
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
        dragPoint = -1; panning = false; orbiting = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (tab == Tab.COUNT && mx < M + PANEL_W + 4 && my >= top()) {
            countScroll = Math.max(0, Math.min(Math.max(0, countContentH - (height - 30 - top())), countScroll - (int) Math.round(v * 24)));
            return true;
        }
        if (listCount > listVisible && mx < M + PANEL_W && my >= listTop) {
            pointScroll = Math.max(0, Math.min(listCount - listVisible, pointScroll - (int) Math.signum(v)));
            rebuildWidgets();
            return true;
        }
        if (mode3d) {
            if (mx < cx0() || mx >= cx1() || my < cy0() || my >= py1()) return super.mouseScrolled(mx, my, h, v);
            preview3.cam.zoom(mx, my, Math.pow(1.15, v));
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
        // With a hologram already out, the changes made here show on it as soon as the screen closes.
        if (Editor.isActive()) { onClose(); return; }
        if (S.is3d()) {
            if (!Placement.canPlace(minecraft)) Placement.say(Component.literal("You'll need operator permissions to place this."));
            onClose();
            Editor.start();
            return;
        }
        if (!ready()) return;
        if (Layout.of(result).isEmpty()) { flash("The shape is empty, so there's nothing to place."); return; }
        if (!Placement.canPlace(minecraft)) Placement.say(Component.literal("You'll need operator permissions to place this."));
        onClose();
        Editor.start();
    }

    /** Set by Export while a hologram is out and its blocks are still catching up with a change made here. */
    private boolean exporting;

    /** A hologram exports as it stands in the world, turned and tipped, as the radial menu's Export writes it. */
    private void exportHologram() {
        if (!exporting) return;
        if (!Editor.isActive()) { exporting = false; return; }
        if (Editor.catchingUp()) { flash("Exporting as soon as the shape in the world is ready…"); return; }
        exporting = false;
        String message = Editor.export();
        flash(message);
        Placement.say(Component.literal(message));
    }

    private void export() {
        if (Editor.isActive()) { exporting = true; exportHologram(); return; }
        if (S.is3d()) { export3(); return; }
        if (!ready()) return;
        Layout layout = Layout.of(result);
        if (layout.isEmpty()) { flash("The shape is empty, so there's nothing to export."); return; }
        String kind = switch (S.gen) { case ELLIPSE -> "ellipse"; case EQUATION -> "equation"; case BEZIER -> "bezier"; default -> "shape"; };
        try {
            String author = minecraft.player != null ? minecraft.player.getName().getString() : "Curve Generator";
            Path file = LitematicExporter.export(layout, S.depth, S.floor, LitematicExporter.defaultName(kind) + (S.floor ? "_floor" : ""), author);
            flash("Exported to schematics/" + file.getFileName());
            Placement.say(Component.literal("Exported to schematics/" + file.getFileName()));
        } catch (Exception e) {
            flash("Export failed: " + e.getMessage());
        }
    }

    private void flash(String msg) { status = msg; statusUntil = System.currentTimeMillis() + 4000; }

    @Override
    public void removed() {
        preview.close();
        preview3.close();
        if (cancel3 != null) cancel3.set(true);
        super.removed();
    }
}
