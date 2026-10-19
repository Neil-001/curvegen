package dev.curvegen.client.screen;

import static dev.curvegen.client.Lang.tr;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.Lang;
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
import dev.curvegen.core.Message;
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
import dev.curvegen.core.edit.HandleMath;
import dev.curvegen.core.edit.Stepper;
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
import net.minecraft.client.gui.GuiGraphics;
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
    private static final String[] NAMES_2D = {"curvegen.screen.ellipse", "curvegen.screen.equation", "curvegen.screen.bezier", "curvegen.screen.blocks", "curvegen.screen.count"},
            NAMES_3D = {"curvegen.screen.ellipsoid", "curvegen.screen.torus", "curvegen.screen.equation", "curvegen.screen.bezier", "curvegen.screen.surface", "curvegen.screen.blocks", "curvegen.screen.count"};

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
        Thread t = new Thread(r, "curvegen.preview_solver");
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
        super(Component.translatable("curvegen.screen.title"));
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
        String[] names = java.util.Arrays.stream(mode3d ? NAMES_3D : NAMES_2D).map(key -> tr(key)).toArray(String[]::new);
        Function<PreviewTexture.Colors, Component> colourName = c -> Component.literal(switch (c) {
            case STONE -> tr("curvegen.screen.plain"); case PIECES -> tr("curvegen.screen.piece_types"); case BLOCKS -> tr("curvegen.screen.block_colours"); });
        int colourText = 0;
        for (PreviewTexture.Colors c : PreviewTexture.Colors.values()) colourText = Math.max(colourText, tw(CommonComponents.optionNameValue(Component.translatable("curvegen.screen.colour"), colourName.apply(c)).getString()));
        int curveText = Math.max(tw(CommonComponents.optionNameValue(Component.translatable("curvegen.screen.curve"), CommonComponents.optionStatus(true)).getString()), tw(CommonComponents.optionNameValue(Component.translatable("curvegen.screen.curve"), CommonComponents.optionStatus(false)).getString()));
        int modeText = Math.max(tw(tr("curvegen.screen.2d")), tw(tr("curvegen.screen.3d")));
        int pad = 12, gap = 4, cogW = 20;
        for (;; pad -= 2) {
            int tabsW = modeText + pad + 2;
            for (String n : names) tabsW += tw(n) + pad + 2;
            int view = mode3d ? curveText + pad : colourText + curveText + 2 * pad;
            if (M + tabsW + gap + view + gap + cogW + gap + M <= width || pad <= 4) break;
            if (gap > 2) gap--;
        }
        int tx = M;
        CycleButton<Boolean> mode = CycleButton.builder((Boolean v) -> Component.translatable(v ? "curvegen.screen.3d" : "curvegen.screen.2d"), mode3d)
                .withValues(List.of(false, true)).displayOnlyValue()
                .create(tx, 6, modeText + pad, 20, Component.translatable("curvegen.screen.shapes"), (button, v) -> setMode(v));
        mode.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.switches_the_tabs_between_flat_shapes_and_3d_shapes")));
        reverse.put(mode, () -> setMode(!mode3d));
        addRenderableWidget(mode);
        tx += modeText + pad + 2;
        for (int k = 0; k < names.length; k++) {
            Tab t = tabs[k];
            int w = tw(names[k]) + pad;
            Button b = Button.builder(Component.literal(names[k]), button -> switchTab(t)).bounds(tx, 6, w, 20).build();
            b.active = tab != t;
            if (t == Tab.COUNT) b.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.how_many_of_each_block_and_orientation_the_shape")));
            addRenderableWidget(b);
            tx += w + 2;
        }
        int curveW = curveText + pad, colourW = colourText + pad, cogX = width - M - cogW;
        if (!mode3d) addRenderableWidget(cycler(List.of(PreviewTexture.Colors.values()), colors, colourName,
                cogX - gap - curveW - gap - colourW, 6, colourW, tr("curvegen.screen.colour"), v -> { colors = v; textureDirty = true; }));
        addRenderableWidget(toggle(showCurve, cogX - gap - curveW, 6, curveW, tr("curvegen.screen.curve"), v -> { showCurve = v; textureDirty = true; }));
        addRenderableWidget(new CogButton(cogX, 6, cogW, 20, () -> minecraft.setScreen(new SettingsScreen(this))));
        if (mode3d) {
            int faceText = Math.max(tw(tr("curvegen.screen.match_colours_to_side")), tw(tr("curvegen.screen.match_colours_to_top"))), room = cx1() - cx0(), p = 12;
            while (p > 4 && colourText + faceText + 2 * p + 4 > room) p -= 2;
            addRenderableWidget(cycler(List.of(PreviewTexture.Colors.values()), colors, colourName, cx0(), cy1() - 20, colourText + p, tr("curvegen.screen.colour"), v -> colors = v));
            CycleButton<Boolean> face = cycler(List.of(false, true), S.topColours, v -> Component.translatable(v ? "curvegen.screen.top" : "curvegen.screen.side"),
                    cx0() + colourText + p + 4, cy1() - 20, faceText + p, tr("curvegen.screen.match_colours_to"), v -> S.topColours = v);
            face.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.which_face_of_a_block_its_colour_is_taken")));
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
        int orientText = Math.max(tw(tr("curvegen.screen.upright")), tw(tr("curvegen.screen.flat"))), labelW = Math.max(tw(tr("curvegen.screen.depth")), tw(tr("curvegen.screen.height"))) + 2, fieldW = 30;
        int replaceText = Math.max(tw(CommonComponents.optionNameValue(Component.translatable("curvegen.screen.replace"), Component.literal(on)).getString()), tw(CommonComponents.optionNameValue(Component.translatable("curvegen.screen.replace"), Component.literal(off)).getString())), carveText = Math.max(tw(CommonComponents.optionNameValue(Component.translatable("curvegen.screen.carve"), Component.literal(on)).getString()), tw(CommonComponents.optionNameValue(Component.translatable("curvegen.screen.carve"), Component.literal(off)).getString()));
        int actionText = Math.max(tw(tr("curvegen.screen.place")), Math.max(tw(tr("curvegen.screen.export")), tw(tr("curvegen.screen.done"))));
        int bpad = 12, bgap = 4;
        for (;; bpad -= 2) {
            int left = M + orientText + bpad + bgap + labelW + fieldW + bgap + replaceText + bpad + bgap + carveText + bpad;
            int right = 3 * (actionText + bpad + 8) + 2 * bgap;
            if (left + bgap + right + M <= width || bpad <= 4) break;
            if (bgap > 2) bgap--;
        }
        int x = M;
        CycleButton<Boolean> orient = CycleButton.builder((Boolean v) -> Component.translatable(v ? "curvegen.screen.flat" : "curvegen.screen.upright"), S.floor)
                .withValues(List.of(false, true)).displayOnlyValue()
                .create(x, by, orientText + bpad, 20, Component.translatable("curvegen.screen.build"), (button, v) -> setFloor(v));
        orient.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.upright_builds_a_wall_drawn_from_the_side_flat")));
        reverse.put(orient, () -> setFloor(!S.floor));
        if (S.is3d()) {
            orient.active = false;
            orient.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.a_3d_shape_is_turned_in_the_world_with")));
        }
        addRenderableWidget(orient);
        x += orientText + bpad + bgap;
        labels.add(new Label(x, by + 6, S.floor ? tr("curvegen.screen.height") : tr("curvegen.screen.depth")));
        x += labelW;
        EditBox depthField = spin(x, by, fieldW, 20, String.valueOf(S.depth), v -> {
            int d = clampInt(v, 1, 64, S.depth);
            if (d != S.depth) { S.depth = d; dirty = true; }   // walls look different when more than one deep
        }, () -> S.depth, 1, 1, 64, () -> !S.is3d(), true);
        depthField.setTooltip(Tooltip.create(Component.literal(S.is3d() ? tr("curvegen.screen.a_3d_shape_has_its_own_depth_resize_it")
                : S.floor ? tr("curvegen.screen.how_many_layers_the_floor_is_stacked_up") : tr("curvegen.screen.how_many_blocks_deep_the_shape_is_built"))));
        if (S.is3d()) depthField.setEditable(false);
        x += fieldW + bgap;
        CycleButton<Boolean> replace = toggle(S.overwrite, x, by, replaceText + bpad, tr("curvegen.screen.replace"), v -> S.overwrite = v);
        replace.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.on_the_shape_replaces_blocks_already_in_its_way")));
        addRenderableWidget(replace);
        x += replaceText + bpad + bgap;
        CycleButton<Boolean> carve = toggle(S.carve, x, by, carveText + bpad, tr("curvegen.screen.carve"), v -> S.carve = v);
        carve.setTooltip(Tooltip.create(Component.literal(
                S.is3d() ? tr("curvegen.screen.carve_3d_tooltip")
                : tr("curvegen.screen.carve_2d_tooltip"))));
        addRenderableWidget(carve);
        int aw = actionText + bpad + 8, bx = width - M - 3 * aw - 2 * bgap;
        Button place = Button.builder(Component.translatable("curvegen.screen.place"), b -> place()).bounds(bx, by, aw, 20).build();
        boolean placeable = Editor.isActive() || !mode3d || RadialScreen.offers(S.gen);
        place.active = placeable;
        place.setTooltip(Tooltip.create(Component.literal(!placeable ? tr("curvegen.screen.this_shape_can_t_be_put_in_the_world")
                : Editor.isActive() ? tr("curvegen.screen.back_to_the_shape_in_the_world_with_the")
                : tr("curvegen.screen.puts_the_shape_in_the_world_as_a_hologram"))));
        addRenderableWidget(place);
        addRenderableWidget(Button.builder(Component.translatable("curvegen.screen.export"), b -> export()).bounds(bx + aw + bgap, by, aw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("curvegen.screen.done"), b -> onClose()).bounds(bx + 2 * (aw + bgap), by, aw, 20).build());
    }

    // ---------- presets ----------
    /** Top of the Save/Load preset row, the last thing in the left panel. */
    private int presetRowY() { return height - 52; }

    private void presetButtons() {
        int y = presetRowY(), w = (PANEL_W - 4) / 2;
        Button save = Button.builder(Component.translatable("curvegen.screen.save_preset"), b -> savePreset()).bounds(M, y, w, 20).build();
        save.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.save_this_tab_s_shape_settings_under_a_name")));
        addRenderableWidget(save);
        Button load = Button.builder(Component.translatable("curvegen.screen.load_preset"),
                b -> minecraft.setScreen(new PresetsScreen(this, S.gen, this::loadPreset))).bounds(M + w + 4, y, w, 20).build();
        load.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.browse_preview_and_load_saved_shapes")));
        addRenderableWidget(load);
    }

    private void savePreset() {
        Gen g = S.gen;
        String suggestion = PresetStore.unique(g, Lang.text(PresetData.defaultName(S, g)));
        minecraft.setScreen(new NameDialogScreen(this, tr("curvegen.screen.save_preset"), suggestion, name -> {
            if (name.isEmpty()) return new NameDialogScreen.Check(false, tr("curvegen.screen.save"), tr("curvegen.screen.type_a_name_for_the_preset"));
            if (PresetStore.find(g, name) != null) return new NameDialogScreen.Check(true, tr("curvegen.screen.replace"), tr("curvegen.screen.a_preset_with_this_name_exists_saving_replaces_it"));
            return new NameDialogScreen.Check(true, tr("curvegen.screen.save"), null);
        }, tr("curvegen.screen.save_blocks"), tr("curvegen.screen.also_save_the_block_for_each_piece_type_and"), (name, withBlocks) -> {
            PresetStore.save(g, name, S, withBlocks);
            flash(tr("curvegen.screen.saved_preset_s", name));
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
        flash(tr("curvegen.screen.loaded_preset_s", p.name));
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
                    case THIN -> tr("curvegen.screen.thin"); case FILLED -> tr("curvegen.screen.filled"); case OUTWARDS -> tr("curvegen.screen.thick_outwards");
                    case INWARDS -> tr("curvegen.screen.thick_inwards"); case MIDDLE -> tr("curvegen.screen.thick_middle"); }),
                M, row(1), PANEL_W, tr("curvegen.screen.shape"), v -> { S.eMode = v; dirty = true; rebuildWidgets(); }));
        labels.add(new Label(M, row(2) + 6, tr("curvegen.screen.thickness")));
        boolean thick = S.eMode == EllipseMode.OUTWARDS || S.eMode == EllipseMode.INWARDS || S.eMode == EllipseMode.MIDDLE;
        EditBox t = spin(M + 60, row(2), PANEL_W - 60, 20, fmt(S.eT), v -> { S.eT = clampNum(v, 0.0625, 50, S.eT); dirty = true; },
                () -> S.eT, 0.25, 0.0625, 50, () -> thick, false);
        t.setEditable(thick);
        hint = switch (S.eMode) {
            case THIN -> tr("curvegen.screen.a_hollow_shell_the_outer_surface_follows_the_ellipse");
            case FILLED -> tr("curvegen.screen.everything_inside_the_ellipse");
            case OUTWARDS -> tr("curvegen.screen.a_wall_that_grows_outwards_from_the_ellipse");
            case INWARDS -> tr("curvegen.screen.a_wall_that_grows_inwards_from_the_ellipse");
            case MIDDLE -> tr("curvegen.screen.a_wall_centred_on_the_ellipse");
        };
        hintY = row(3) + 2;
    }

    private void initEquation() {
        EditBox eq = new EditBox(font, M, row(0), PANEL_W, 20, Component.translatable("curvegen.screen.equation"));
        eq.setMaxLength(256);
        eq.setValue(S.src);
        eq.setResponder(v -> { S.src = v; dirty = true; updateEquationControls(); });
        addRenderableWidget(eq);
        labels.add(new Label(M, row(1) + 6, "x"));
        addRenderableWidget(text(M + 10, row(1), 62, S.xmin, v -> { S.xmin = v; dirty = true; }));
        labels.add(new Label(M + 76, row(1) + 6, tr("curvegen.screen.to")));
        addRenderableWidget(text(M + 90, row(1), 60, S.xmax, v -> { S.xmax = v; dirty = true; }));
        labels.add(new Label(M, row(2) + 6, "y"));
        addRenderableWidget(text(M + 10, row(2), 62, S.ymin, v -> { S.ymin = v; dirty = true; }));
        labels.add(new Label(M + 76, row(2) + 6, tr("curvegen.screen.to")));
        addRenderableWidget(text(M + 90, row(2), 60, S.ymax, v -> { S.ymax = v; dirty = true; }));
        labels.add(new Label(M, row(3) + 6, tr("curvegen.screen.w")));
        spin(M + 10, row(3), 62, 20, String.valueOf(S.qW), v -> { S.qW = clampInt(v, 1, 400, S.qW); dirty = true; }, () -> S.qW, 1, 1, 400, () -> true, true);
        labels.add(new Label(M + 76, row(3) + 6, tr("curvegen.screen.h")));
        qHField = spin(M + 90, row(3), 60, 20, String.valueOf(S.qH), v -> { if (!S.qLock) { S.qH = clampInt(v, 1, 400, S.qH); dirty = true; } },
                () -> S.qH, 1, 1, 400, () -> !S.qLock, true);
        qHField.setEditable(!S.qLock);
        addRenderableWidget(toggle(S.qLock, M, row(4), PANEL_W, tr("curvegen.screen.same_scale"), v -> { S.qLock = v; qHField.setEditable(!v); dirty = true; }));
        eqShape = cycler(List.of(EqMode.values()), S.qMode, m -> Component.literal(switch (m) {
                    case LINE -> tr("curvegen.screen.line"); case UNDER -> tr("curvegen.screen.fill_under"); case OVER -> tr("curvegen.screen.fill_over"); }),
                M, row(5), PANEL_W, tr("curvegen.screen.shape"), v -> { S.qMode = v; dirty = true; updateEquationControls(); });
        addRenderableWidget(eqShape);
        labels.add(new Label(M, row(6) + 6, tr("curvegen.screen.line_width")));
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
        eqShape.setTooltip(ineq ? Tooltip.create(Component.translatable("curvegen.screen.your_inequality_sets_the_shape_use_to_choose_it")) : null);
        if (qLWField != null) qLWField.setEditable(!ineq && S.qMode == EqMode.LINE);
    }

    /** A filled Bézier shape ignores line width. */
    private void updateBezierControls() {
        boolean line = S.bMode == BzMode.LINE;
        bLWField.setEditable(line);
        bLWField.setTooltip(line ? null : Tooltip.create(Component.translatable("curvegen.screen.filled_shapes_don_t_use_a_line_width")));
    }

    private void initBezier() {
        spin(M, row(0), 64, 20, String.valueOf(S.bW), v -> { S.bW = clampInt(v, 1, 400, S.bW); dirty = true; autoFit = true; }, () -> S.bW, 1, 1, 400, () -> true, true);
        labels.add(new Label(M + 71, row(0) + 6, "×"));
        spin(M + 86, row(0), 64, 20, String.valueOf(S.bH), v -> { S.bH = clampInt(v, 1, 400, S.bH); dirty = true; autoFit = true; }, () -> S.bH, 1, 1, 400, () -> true, true);
        addRenderableWidget(cycler(List.of(BzMode.values()), S.bMode, m -> Component.literal(m == BzMode.LINE ? tr("curvegen.screen.line") : tr("curvegen.screen.filled")),
                M, row(1), PANEL_W, tr("curvegen.screen.shape"), v -> { S.bMode = v; dirty = true; updateBezierControls(); }));
        labels.add(new Label(M, row(2) + 6, tr("curvegen.screen.line_width")));
        bLWField = spin(M + 60, row(2), PANEL_W - 60, 20, fmt(S.bLW), v -> { S.bLW = clampNum(v, 0.0625, 50, S.bLW); dirty = true; },
                () -> S.bLW, 0.25, 0.0625, 50, () -> S.bMode == BzMode.LINE, false);
        updateBezierControls();
        addRenderableWidget(toggle(S.snap, M, row(3), PANEL_W, tr("curvegen.screen.snap_to_half_blocks"), v -> S.snap = v));
        Button add = Button.builder(Component.translatable("curvegen.screen.add_point"), b -> {
            int n = S.pts.size();
            double[] a = S.pts.get(n - 2), c = S.pts.get(n - 1);
            S.pts.add(n - 1, new double[]{(a[0] + c[0]) / 2, (a[1] + c[1]) / 2});
            dirty = true; rebuildWidgets();
        }).bounds(M, row(4), 73, 20).build();
        add.active = S.pts.size() < 10;
        Button rem = Button.builder(Component.translatable("curvegen.screen.remove"), b -> {
            S.pts.remove(S.pts.size() - 2 >= 1 ? S.pts.size() - 2 : S.pts.size() - 1);
            dirty = true; rebuildWidgets();
        }).bounds(M + 77, row(4), 73, 20).build();
        rem.active = S.pts.size() > 2;
        addRenderableWidget(add);
        addRenderableWidget(rem);
        hint = tr("curvegen.screen.drag_the_numbered_handles_in_the_preview");
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
            EditBox fx = new EditBox(font, M + 17, y, 50 - ARROW_SLOT, 16, Component.translatable("curvegen.screen.point_s_x", k + 1));
            EditBox fy = new EditBox(font, M + 78, y, 50 - ARROW_SLOT, 16, Component.translatable("curvegen.screen.point_s_y", k + 1));
            Button x = Button.builder(Component.literal("×"), b -> {
                if (S.pts.size() > 2) { S.pts.remove(idx); dirty = true; rebuildWidgets(); }
            }).bounds(M + 132, y, 18, 16).build();
            x.active = n > 2;
            x.setTooltip(Tooltip.create(Component.literal(n > 2 ? tr("curvegen.screen.remove_point_s", k + 1) : tr("curvegen.screen.a_curve_needs_at_least_two_points"))));
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
                wheelNeedsFocus(f);
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
                labels.add(new Label(M + 4, y + (h - 8) / 2, tr("curvegen.screen.use")));
            } else {
                boolean on = S.allows(f);
                Button use = Button.builder(Component.translatable(on ? "curvegen.screen.use" : "curvegen.screen.off"), b -> { S.allow(f, !S.allows(f)); dirty = true; rebuildWidgets(); })
                        .bounds(M, y, 26, h).build();
                if (S.floor && !S.is3d() && (f == Family.SLAB || f == Family.STAIRS)) {
                    use.active = false;
                    use.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.from_above_s_look_like_full_blocks_so_flat", BlockChoices.familyName(f))));
                }
                addRenderableWidget(use);
            }
            Block block = BlockChoices.CHOICE.get(f);
            String name = font.plainSubstrByWidth(block.getName().getString(), PANEL_W - 54 - 8);
            Button pick = Button.builder(Component.literal(name), b -> minecraft.setScreen(new BlockPickerScreen(this, f, chosen -> {
                BlockChoices.CHOICE.put(f, chosen);
                S.fullConnects = BlockChoices.fullBlockConnects();
                dirty = true; textureDirty = true;
                rebuildWidgets();
            }))).bounds(M + 48, y, PANEL_W - 48, h).build();
            pick.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(BlockChoices.familyName(f) + ": " + block.getName().getString())));
            addRenderableWidget(pick);
            icons.add(new Icon(M + 29, y + (h - 16) / 2, new ItemStack(block)));
        }
        addRenderableWidget(Button.builder(Component.translatable("curvegen.screen.match_a_colour"), b -> minecraft.setScreen(
                new ColorPickerScreen(this, BlockChoices.pickedColor >= 0 ? BlockChoices.pickedColor : 0x8E6B4A, rgb -> {
                    BlockChoices.autoSelect(rgb);
                    S.fullConnects = BlockChoices.fullBlockConnects();
                    dirty = true; textureDirty = true;
                    rebuildWidgets();
                }))).bounds(M, top() + (rows - 1) * pitch, PANEL_W, h).build());
        hint = tr("curvegen.screen.choose_a_block_per_piece_type_or_match_them");
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
            for (int st : statesFor(f, floor)) merged.computeIfAbsent(Pieces.SHORT_NAME[st], key -> new int[]{st, 0})[1] += result.counts()[st] * mult;
            int total = 0;
            for (int[] m : merged.values()) total += m[1];
            boolean unused = floor && (f == Family.SLAB || f == Family.STAIRS);
            String blockName = BlockChoices.CHOICE.get(f).getName().getString();
            String n = String.valueOf(total);
            int nw = tw(n);
            String title = unused ? tr("curvegen.screen.family_unused_flat", BlockChoices.familyName(f)) : S.allows(f) ? BlockChoices.familyName(f) : tr("curvegen.screen.family_off", BlockChoices.familyName(f));
            rows.add(new CountRow(true, font.plainSubstrByWidth(title, PANEL_W - nw - 6), n, nw, -1, unused || !S.allows(f), blockName, false));
            for (var e : merged.entrySet()) {
                int st = e.getValue()[0], count = e.getValue()[1];
                n = String.valueOf(count);
                nw = tw(n);
                String name = tr(e.getKey());
                int room = PANEL_W - 20 - nw - 6;
                if (tw(name) > room) name = font.plainSubstrByWidth(name, room - tw("…")) + "…";
                rows.add(new CountRow(false, name, n, nw, st, count == 0, tr("curvegen.screen.piece_count", tr(Pieces.NAME[st]), blockName, count), false));
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
            String title = S.allows(f) ? BlockChoices.familyName(f) : tr("curvegen.screen.family_off", BlockChoices.familyName(f));
            rows.add(new CountRow(true, font.plainSubstrByWidth(title, PANEL_W - nw - 6), n, nw, -1, !S.allows(f), blockName, true));
            for (Count3.Group g : Count3.GROUPS) {
                if (g.family() != f) continue;
                int count = g.count(counts);
                n = String.valueOf(count);
                nw = tw(n);
                String name = Lang.text(g.name());
                int room = PANEL_W - 20 - nw - 6;
                if (tw(name) > room) name = font.plainSubstrByWidth(name, room - tw("…")) + "…";
                String full = f == Family.FULL ? Lang.text(g.name()) : tr("curvegen.screen.piece_family_state", BlockChoices.familyName(f), Lang.text(g.name()));
                rows.add(new CountRow(false, name, n, nw, g.icon(), count == 0, tr("curvegen.screen.piece_count", full, blockName, count), true));
            }
        }
    }

    /** A 3D piece's icon: its boxes seen from the south, in the colour the picture gives its family. */
    private void drawIcon3(GuiGraphics ctx, int s, int x, int y, boolean dim) {
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

    private void drawCount(GuiGraphics ctx, int mx, int my) {
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
                ctx.drawString(font, r.text, x0, y, c, false);
                ctx.drawString(font, r.count, x1 - r.countW, y, c, false);
                ctx.fill(x0, y + 10, x1, y + 11, 0x40FFFFFF);
                if (my >= y && my < y + 11 && mx >= x0 && mx < x1 && my >= top && my < bottom) tip = r.tip;
            } else {
                if (r.solid) drawIcon3(ctx, r.state, x0 + 1, y + 1, r.dim);
                else drawIcon(ctx, r.state, x0 + 1, y + 1, r.dim);
                int c = r.dim ? 0xFF5C6470 : 0xFFC8CED6;
                ctx.drawString(font, r.text, x0 + 20, y + 4, c, false);
                ctx.drawString(font, r.count, x1 - r.countW, y + 4, r.dim ? 0xFF5C6470 : 0xFFFFFFFF, false);
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
    private void drawIcon(GuiGraphics ctx, int p, int x, int y, boolean dim) {
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
        label3(M, row, tr("curvegen.screen.thickness"));
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

    private static String[] whd() { return new String[]{tr("curvegen.screen.w"), tr("curvegen.screen.h"), tr("curvegen.screen.d")}; }

    private void initEllipsoid() {
        rows3(3, 46);
        sizes3(0, whd(), new String[]{tr("curvegen.screen.width_from_west_to_east"), tr("curvegen.screen.height_tooltip"), tr("curvegen.screen.depth_from_north_to_south")},
                new IntSupplier[]{() -> S.e3W, () -> S.e3H, () -> S.e3D}, new IntConsumer[]{v -> S.e3W = v, v -> S.e3H = v, v -> S.e3D = v},
                new BooleanSupplier[]{ALWAYS, ALWAYS, ALWAYS}, null);
        short3(cycler(List.of(EllipseMode.values()), S.e3Mode, m -> Component.literal(switch (m) {
                    case THIN -> tr("curvegen.screen.thin"); case FILLED -> tr("curvegen.screen.filled"); case OUTWARDS -> tr("curvegen.screen.thick_outwards");
                    case INWARDS -> tr("curvegen.screen.thick_inwards"); case MIDDLE -> tr("curvegen.screen.thick_middle"); }),
                M, r3(1), PANEL_W, tr("curvegen.screen.shape"), v -> { S.e3Mode = v; dirty = true; rebuildWidgets(); }));
        thickness(2, () -> S.e3T, v -> S.e3T = v, () -> S.e3Mode == EllipseMode.OUTWARDS || S.e3Mode == EllipseMode.INWARDS || S.e3Mode == EllipseMode.MIDDLE,
                tr("curvegen.screen.only_the_thick_shapes_have_a_thickness"));
        hint = switch (S.e3Mode) {
            case THIN -> tr("curvegen.screen.a_hollow_shell_the_outer_surface_follows_the_ellipsoid");
            case FILLED -> tr("curvegen.screen.everything_inside_the_ellipsoid");
            case OUTWARDS -> tr("curvegen.screen.a_shell_that_grows_outwards_from_the_ellipsoid");
            case INWARDS -> tr("curvegen.screen.a_shell_that_grows_inwards_from_the_ellipsoid");
            case MIDDLE -> tr("curvegen.screen.a_shell_centred_on_the_ellipsoid");
        };
        hintY = r3(3) + 2;
    }

    private static int[] torus() { return new int[]{S.tRing, S.tTube, S.tW, S.tH, S.tD}; }
    private static void torus(int[] v) { S.tRing = v[0]; S.tTube = v[1]; S.tW = v[2]; S.tH = v[3]; S.tD = v[4]; }

    private void initTorus() {
        rows3(3, 46);
        sizes3(0, whd(), new String[]{tr("curvegen.screen.width_of_the_box_the_ring_is_stretched_to"), tr("curvegen.screen.height_of_the_box_the_ring_is_stretched_to"),
                        tr("curvegen.screen.depth_of_the_box_the_ring_is_stretched_to")},
                new IntSupplier[]{() -> S.tW, () -> S.tH, () -> S.tD}, new IntConsumer[]{v -> S.tW = v, v -> S.tH = v, v -> S.tD = v},
                new BooleanSupplier[]{ALWAYS, ALWAYS, ALWAYS}, null);
        // The ring and the tube scale the box, so a number half typed would spoil it for the digits that follow.
        // Each keystroke starts again from the sizes as they were before the field was clicked.
        int[][] before = {torus(), torus()};
        label3(M, 1, tr("curvegen.screen.ring"));
        EditBox ring = whole(M + 24, 1, 48, () -> S.tRing, v -> { torus(before[0]); Edit3D.setRing(S, v); }, 1, Shape3.MAX_SIZE, ALWAYS,
                tr("curvegen.screen.how_far_across_the_round_ring_is_changing_it"), null);
        label3(M + 78, 1, tr("curvegen.screen.tube"));
        EditBox tube = whole(M + 104, 1, 46, () -> S.tTube, v -> { torus(before[1]); Edit3D.setTube(S, v); }, 1, Shape3.MAX_SIZE, ALWAYS,
                tr("curvegen.screen.how_thick_the_tube_is_up_to_the_ring"), null);
        live.add(() -> {
            if (!ring.isFocused()) before[0] = torus();
            if (!tube.isFocused()) before[1] = torus();
        });
        short3(cycler(List.of(false, true), S.tHollow, v -> Component.translatable(v ? "curvegen.screen.hollow" : "curvegen.screen.filled"), M, r3(2), PANEL_W, tr("curvegen.screen.shape"),
                v -> { S.tHollow = v; dirty = true; }));
        hint = tr("curvegen.screen.ring_and_tube_describe_a_round_ring_the_box");
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
        text3(M, 0, PANEL_W, 256, S.src3, tr("curvegen.screen.equation"), v -> { S.src3 = v; dirty = true; })
                .setTooltip(Tooltip.create(Component.translatable("curvegen.screen.anything_in_x_y_and_z_with_or_where")));
        String[] axis = {"x", "y", "z"};
        List<Supplier<String>> from = List.of(() -> S.x3min, () -> S.y3min, () -> S.z3min), to = List.of(() -> S.x3max, () -> S.y3max, () -> S.z3max);
        List<Consumer<String>> setFrom = List.of(v -> S.x3min = v, v -> S.y3min = v, v -> S.z3min = v), setTo = List.of(v -> S.x3max = v, v -> S.y3max = v, v -> S.z3max = v);
        for (int k = 0; k < 3; k++) {
            int a = k;
            label3(M, 1 + k, axis[k]);
            text3(M + 10, 1 + k, 62, 64, from.get(k).get(), tr("curvegen.screen.s_from", axis[k]), v -> { setFrom.get(a).accept(v); dirty = true; });
            label3(M + 76, 1 + k, tr("curvegen.screen.to"));
            text3(M + 90, 1 + k, 60, 64, to.get(k).get(), tr("curvegen.screen.s_to", axis[k]), v -> { setTo.get(a).accept(v); dirty = true; });
        }
        // With the same scale on every axis, the depth and height come from the width and the ranges.
        BooleanSupplier free = () -> !S.q3Lock;
        sizes3(4, new String[]{tr("curvegen.screen.w"), tr("curvegen.screen.d"), tr("curvegen.screen.h")}, new String[]{tr("curvegen.screen.width_in_blocks_along_x"), tr("curvegen.screen.depth_in_blocks_along_y"), tr("curvegen.screen.height_in_blocks_along_z")},
                new IntSupplier[]{() -> S.q3W, () -> S.q3Lock ? live3().nz() : S.q3D, () -> S.q3Lock ? live3().ny() : S.q3H},
                new IntConsumer[]{v -> S.q3W = v, v -> S.q3D = v, v -> S.q3H = v}, new BooleanSupplier[]{ALWAYS, free, free},
                tr("curvegen.screen.same_scale_works_this_out_from_the_width_and"));
        short3(toggle(S.q3Lock, M, r3(5), PANEL_W, tr("curvegen.screen.same_scale"), v -> {
            // Unlocked, the box starts out the size it was, so the shape doesn't jump.
            if (!v && live3().error() == null) { S.q3D = live3().nz(); S.q3H = live3().ny(); }
            S.q3Lock = v; dirty = true;
        }));
        CycleButton<Eq3Mode> shape = short3(cycler(List.of(Eq3Mode.values()), S.q3Mode, m -> Component.literal(switch (m) {
                    case SURFACE -> tr("curvegen.screen.surface"); case BELOW -> tr("curvegen.screen.fill_below"); case ABOVE -> tr("curvegen.screen.fill_above"); }),
                M, r3(6), PANEL_W, tr("curvegen.screen.shape"), v -> { S.q3Mode = v; dirty = true; }));
        boolean[] was = {false};
        Runnable inequality = () -> {
            boolean ineq = isInequality(S.src3);
            if (ineq == was[0] && shape.active == !ineq) return;
            was[0] = ineq;
            shape.active = !ineq;
            shape.setTooltip(ineq ? Tooltip.create(Component.translatable("curvegen.screen.your_inequality_sets_the_shape_use_to_choose_it")) : null);
        };
        inequality.run();
        live.add(inequality);
        thickness(7, () -> S.q3T, v -> S.q3T = v, () -> !isInequality(S.src3) && S.q3Mode == Eq3Mode.SURFACE, tr("curvegen.screen.only_a_surface_has_a_thickness"));
    }

    private void initBezier3() {
        rows3(5, 16 + 3 * POINT_ROW);
        sizes3(0, whd(), new String[]{tr("curvegen.screen.width_of_the_box_the_curve_is_in"), tr("curvegen.screen.height_of_the_box_the_curve_is_in"), tr("curvegen.screen.depth_of_the_box_the_curve_is_in")},
                new IntSupplier[]{() -> S.b3W, () -> S.b3H, () -> S.b3D}, new IntConsumer[]{v -> S.b3W = v, v -> S.b3H = v, v -> S.b3D = v},
                new BooleanSupplier[]{ALWAYS, ALWAYS, ALWAYS}, null);
        CycleButton<BzMode> shape = short3(cycler(List.of(BzMode.values()), BzMode.LINE, m -> Component.literal(m == BzMode.LINE ? tr("curvegen.screen.line") : tr("curvegen.screen.filled")),
                M, r3(1), PANEL_W, tr("curvegen.screen.shape"), v -> { }));
        shape.active = false;
        shape.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.a_3d_curve_is_always_a_line_only_a")));
        thickness(2, () -> S.b3T, v -> S.b3T = v, ALWAYS, null);
        short3(toggle(S.snap, M, r3(3), PANEL_W, tr("curvegen.screen.snap_to_half_blocks"), v -> S.snap = v));
        int n = S.pts3.size();
        Button add = Button.builder(Component.translatable("curvegen.screen.add_point"), b -> {
            if (Bezier3.insert(S.pts3, 0.5) >= 0) { dirty = true; rebuildWidgets(); }
        }).bounds(M, r3(4), 73, h3).build();
        add.active = n < Bezier3.MAX_POINTS;
        add.setTooltip(Tooltip.create(Component.literal(add.active ? tr("curvegen.screen.adds_a_point_without_changing_the_curve_the_points")
                : tr("curvegen.screen.a_curve_can_have_s_points_at_most", Bezier3.MAX_POINTS))));
        Button rem = Button.builder(Component.translatable("curvegen.screen.remove"), b -> {
            if (S.pts3.size() > 2) {
                List<double[]> fewer = Bezier3.reduce(S.pts3);
                S.pts3.clear(); S.pts3.addAll(fewer);
                dirty = true; rebuildWidgets();
            }
        }).bounds(M + 77, r3(4), 73, h3).build();
        rem.active = n > 2;
        rem.setTooltip(Tooltip.create(Component.literal(rem.active ? tr("curvegen.screen.takes_a_point_out_and_keeps_the_curve_as")
                : tr("curvegen.screen.curve_minimum_points_tooltip"))));
        addRenderableWidget(add);
        addRenderableWidget(rem);
        pointList3(5, S.pts3, 0);
    }

    private void initSurface() {
        rows3(4, 16 + 3 * POINT_ROW);
        sizes3(0, whd(), new String[]{tr("curvegen.screen.width_of_the_box_the_surface_is_in"), tr("curvegen.screen.height_of_the_box_the_surface_is_in"), tr("curvegen.screen.depth_of_the_box_the_surface_is_in")},
                new IntSupplier[]{() -> S.sW, () -> S.sH, () -> S.sD}, new IntConsumer[]{v -> S.sW = v, v -> S.sH = v, v -> S.sD = v},
                new BooleanSupplier[]{ALWAYS, ALWAYS, ALWAYS}, null);
        thickness(1, () -> S.sT, v -> S.sT = v, ALWAYS, null);
        short3(toggle(S.snap, M, r3(2), PANEL_W, tr("curvegen.screen.snap_to_half_blocks"), v -> S.snap = v));
        label3(M, 3, tr("curvegen.screen.rows"));
        whole(M + 26, 3, 40, () -> S.sRows, v -> grid(v, S.sCols), Bezier3.MIN_GRID, Bezier3.MAX_GRID, ALWAYS, tr("curvegen.screen.surface_rows_hint", Bezier3.MIN_GRID, Bezier3.MAX_GRID), null);
        label3(M + 69, 3, tr("curvegen.screen.columns"));
        whole(M + 110, 3, 40, () -> S.sCols, v -> grid(S.sRows, v), Bezier3.MIN_GRID, Bezier3.MAX_GRID, ALWAYS, tr("curvegen.screen.surface_columns_hint", Bezier3.MIN_GRID, Bezier3.MAX_GRID), null);
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
                EditBox f = new EditBox(font, x0 + a * step, y, fw, 16, Component.translatable("curvegen.screen.point_s_s", name, "xyz".charAt(a)));
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
                // No room for arrows, so the wheel is the only way to step these: a block, or to the next half block with Snap on.
                wheel(f, dir -> {
                    if (idx >= pts.size()) return;
                    double v = pts.get(idx)[a], max = (tab == Tab.BEZIER3 ? new int[]{S.b3W, S.b3H, S.b3D} : new int[]{S.sW, S.sH, S.sD})[a];
                    write(f, S.snap ? Stepper.next(v, 0.5, dir, 0, max) : Stepper.step(v, dir, 0, max), false);
                }, ALWAYS);
                wheelNeedsFocus(f);
            }
            if (grid) continue;
            Button x = Button.builder(Component.literal("×"), b -> {
                if (pts.size() > 2 && idx < pts.size()) { pts.remove(idx); dirty = true; rebuildWidgets(); }
            }).bounds(M + 133, y, 17, 16).build();
            x.active = n > 2;
            x.setTooltip(Tooltip.create(Component.literal(n > 2 ? tr("curvegen.screen.remove_point_s", k + 1) : tr("curvegen.screen.a_curve_needs_at_least_two_points"))));
            addRenderableWidget(x);
        }
    }

    // ---------- the 3D preview ----------
    /** A solve of a 3D shape: the blocks, their faces, and how many there are. With an {@code error} the rest is null. */
    record Solved3(Solver3.Result result, Mesh3 mesh, int blocks, Message error) {}

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
            try { r = job3.get(); } catch (Exception e) { flash(tr("curvegen.screen.couldn_t_build_this_shape_s", e.getMessage())); }
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

    /** A colour for point {@code k}'s handle: fainter while another point is dragged, so the dragged one stands out. */
    private int handle(int argb, int k) { return dragPoint >= 0 && k != dragPoint ? HandleMath.dimmed(argb) : argb; }

    private void drawHandles3(GuiGraphics ctx, int mx, int my) {
        List<double[]> pts = points3();
        boolean grid = tab == Tab.SURFACE, inside = mx >= cx0() && mx < cx1() && my >= cy0() + 12 && my < py1();
        int hover = dragPoint >= 0 ? dragPoint : inside && !orbiting && !panning ? preview3.cam.pick(pts, mx, my, grid ? 5 : 6) : -1;
        for (int k = 0; k < pts.size(); k++) {
            double[] p = pts.get(k), s = preview3.cam.project(p[0], p[1], p[2]);
            int x = (int) Math.round(s[0]), y = (int) Math.round(s[1]), r = grid ? 3 : 5;
            ctx.fill(x - r, y - r, x + r + 1, y + r + 1, handle(0xFFFFFFFF, k));
            ctx.fill(x - r + 1, y - r + 1, x + r, y + r, handle(k == hover ? 0xFFFFB84D : 0xFF3F7BE0, k));
            if (grid) continue;
            String n = String.valueOf(k + 1);
            ctx.drawString(font, n, x - font.width(n) / 2 + 1, y - 3, handle(0xFFFFFFFF, k), false);
        }
        if (grid && hover >= 0 && dragPoint < 0)
            ctx.setTooltipForNextFrame(font, Component.translatable("curvegen.screen.row_s_column_s", hover / S.sCols + 1, hover % S.sCols + 1), mx, my);
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
        if (solved3 == null || job3 != null || dirty) { flash(tr("curvegen.screen.still_working_on_the_shape_try_again_in_a")); return; }
        if (solved3.error != null) { flash(tr("curvegen.screen.fix_the_shape_first_s", Lang.text(solved3.error))); return; }
        Solver3.Result r = solved3.result;
        BlockState[] states = BlockChoices.statesFor3();
        List<EditShape.Placed> blocks = new ArrayList<>(solved3.blocks);
        for (int y = 0, c = 0; y < r.ny(); y++)
            for (int z = 0; z < r.nz(); z++)
                for (int x = 0; x < r.nx(); x++, c++)
                    if (r.grid()[c] != Pieces3.AIR) blocks.add(new EditShape.Placed(x, y, z, states[r.grid()[c]]));
        if (blocks.isEmpty()) { flash(tr("curvegen.screen.empty_export")); return; }
        try {
            String author = minecraft.player != null ? minecraft.player.getName().getString() : tr("key.category.curvegen.main");
            Path file = LitematicExporter.export(blocks, LitematicExporter.defaultName(S.gen.name().toLowerCase(java.util.Locale.ROOT)), author);
            flash(tr("curvegen.screen.exported_to_schematics_s", file.getFileName().toString()));
            Placement.say(Component.translatable("curvegen.screen.exported_to_schematics_s", file.getFileName().toString()));
        } catch (Exception e) {
            flash(tr("curvegen.screen.export_failed_s", e.getMessage()));
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
            super(x, y, w, h, Component.translatable("curvegen.screen.settings"));
            this.action = action;
            setTooltip(Tooltip.create(Component.translatable("curvegen.screen.settings")));
        }

        @Override public void onPress(InputWithModifiers input) { action.run(); }

        @Override
        protected void renderContents(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
            renderDefaultSprite(ctx);
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
        try { return Math.max(lo, Math.min(hi, Integer.parseInt(v.trim()))); } catch (NumberFormatException ignored) { return d; }
    }
    private static double clampNum(String v, double lo, double hi, double d) {
        try { double x = Double.parseDouble(v.trim()); return x > 0 ? Math.max(lo, Math.min(hi, x)) : d; } catch (NumberFormatException ignored) { return d; }
    }
    private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    // ---------- solving ----------
    private void poll() {
        exportHologram();
        if (mode3d) { poll3(); return; }
        if (job != null && job.isDone()) {
            try { result = job.get(); } catch (Exception e) { flash(tr("curvegen.screen.couldn_t_build_this_shape_s", e.getMessage())); }
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
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        poll();
        super.render(ctx, mouseX, mouseY, delta);
        for (Label l : labels) ctx.drawString(font, l.text, l.x, l.y, 0xFFC8CED6);
        for (Icon ic : icons) ctx.renderItem(ic.stack, ic.x, ic.y);
        int hintLimit = tab == Tab.BLOCKS || tab == Tab.COUNT ? height - 30 : presetRowY() - 4;
        if (hint != null && hintY + 18 < hintLimit) ctx.drawWordWrap(font, Component.literal(hint), M, hintY, PANEL_W, 0xFF9AA5B3, false);
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
        ctx.renderOutline(x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, 0xFF3A424D);

        // status line across the top of the canvas
        String line; int color = 0xFFDDE3EA;
        if (mode3d) {
            if (solved3 == null) line = tr("curvegen.screen.working");
            else if (solved3.error != null) { line = Lang.text(solved3.error); color = 0xFFFF8098; }
            else {
                Solver3.Result r = solved3.result;
                double pct = r.volume() > 0 ? r.err() / r.volume() * 100 : 0;
                boolean busy = job3 != null || dirty;
                line = tr("curvegen.screen.status_3d", solved3.blocks, r.nx(), r.ny(), r.nz(), String.format(java.util.Locale.ROOT, "%.1f", r.err()), String.format(java.util.Locale.ROOT, "%.1f", pct));
                if (busy) line = tr("curvegen.screen.status_updating", line);
                // On a narrow screen the share alone says how close the blocks are.
                if (tw(line) > x1 - x0 - 6) {
                    line = tr("curvegen.screen.status_3d_short", solved3.blocks, r.nx(), r.ny(), r.nz(), String.format(java.util.Locale.ROOT, "%.1f", pct));
                    if (busy) line = tr("curvegen.screen.status_updating", line);
                }
            }
        }
        else if (result == null) line = tr("curvegen.screen.working");
        else if (result.target().error != null) { line = Lang.text(result.target().error); color = 0xFFFF8098; }
        else {
            int total = 0;
            for (int p = 1; p < Pieces.COUNT; p++) total += result.counts()[p];
            double pct = result.area() > 0 ? result.err() / result.area() * 100 : 0;
            line = tr("curvegen.screen.status_2d", total, result.nx(), result.ny(), String.format(java.util.Locale.ROOT, "%.2f", result.err()), String.format(java.util.Locale.ROOT, "%.1f", pct));
            if (result.floor()) line = tr("curvegen.screen.status_above", line);
            if (job != null) line = tr("curvegen.screen.status_updating", line);
            if (tw(line) > x1 - x0 - 6) {
                line = tr("curvegen.screen.status_2d_short", total, result.nx(), result.ny(), String.format(java.util.Locale.ROOT, "%.1f", pct));
                if (result.floor()) line = tr("curvegen.screen.status_above", line);
                if (job != null) line = tr("curvegen.screen.status_updating", line);
            }
        }
        if (status != null && System.currentTimeMillis() < statusUntil) { line = status; color = 0xFFFFE08A; }
        ctx.fill(x0, y0, x1, y0 + 12, 0xB0000000);
        ctx.drawString(font, font.plainSubstrByWidth(line, x1 - x0 - 6), x0 + 3, y0 + 2, color, false);
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(0, 0, width, height, 0x80101317);
    }

    private int[] cellAt(double mx, double my) {
        if (result == null || mx < cx0() || mx >= cx1() || my < cy0() + 12 || my >= cy1()) return null;
        int i = (int) Math.floor((mx - panX) / zoom), jr = (int) Math.floor((my - panY) / zoom);
        if (i < 0 || jr < 0 || i >= result.nx() || jr >= result.ny()) return null;
        return new int[]{i, result.ny() - 1 - jr};
    }

    private void drawHover(GuiGraphics ctx, int mx, int my) {
        int[] c = cellAt(mx, my);
        if (c == null) return;
        int sx = Math.round(panX + c[0] * zoom), sy = Math.round(panY + (result.ny() - 1 - c[1]) * zoom), sz = Math.max(2, Math.round(zoom));
        ctx.renderOutline(sx, sy, sz, sz, 0xFF6EA0FF);
        int p = result.at(c[0], c[1]);
        String txt = p == Pieces.EMPTY ? tr("curvegen.screen.hover_empty", c[0] + 1, c[1] + 1, tr(Pieces.NAME[p])) : tr("curvegen.screen.hover_block", c[0] + 1, c[1] + 1, tr(Pieces.NAME[p]), BlockChoices.blockFor(p).getName().getString());
        if (result.target().hasMath) {
            var t = result.target();
            txt = tr("curvegen.screen.hover_math", txt, String.format(java.util.Locale.ROOT, "%.2f", t.mx0 + (c[0] + .5) * t.msx), String.format(java.util.Locale.ROOT, "%.2f", t.my0 + (c[1] + .5) * t.msy));
        }
        int y = cy1() - 12;
        ctx.fill(cx0(), y, cx1(), cy1(), 0xB0000000);
        ctx.drawString(font, font.plainSubstrByWidth(txt, cx1() - cx0() - 6), cx0() + 3, y + 2, 0xFFDDE3EA, false);
    }

    private float[] handleScreen(int k) {
        double[] p = S.pts.get(k);
        return new float[]{panX + (float) p[0] * zoom, panY + (float) (result.ny() - p[1]) * zoom};
    }

    private void drawHandles(GuiGraphics ctx) {
        float[] prev = null;
        for (int k = 0; k < S.pts.size(); k++) {
            float[] h = handleScreen(k);
            if (prev != null) dashedLine(ctx, prev[0], prev[1], h[0], h[1]);
            prev = h;
        }
        for (int k = 0; k < S.pts.size(); k++) {
            float[] h = handleScreen(k);
            int x = Math.round(h[0]), y = Math.round(h[1]);
            ctx.fill(x - 5, y - 5, x + 6, y + 6, handle(0xFFFFFFFF, k));
            ctx.fill(x - 4, y - 4, x + 5, y + 5, handle(k == dragPoint ? 0xFFFFB84D : 0xFF3F7BE0, k));
            String n = String.valueOf(k + 1);
            ctx.drawString(font, n, x - font.width(n) / 2 + 1, y - 3, handle(0xFFFFFFFF, k), false);
        }
    }

    private void dashedLine(GuiGraphics ctx, float x1, float y1, float x2, float y2) {
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
        if (wheelTurned(mx, my, v)) return true;
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
        if (result == null || job != null || dirty) { flash(tr("curvegen.screen.still_working_on_the_shape_try_again_in_a")); return false; }
        if (result.target().error != null) { flash(tr("curvegen.screen.fix_the_equation_first")); return false; }
        return true;
    }

    private void place() {
        // With a hologram already out, the changes made here show on it as soon as the screen closes.
        if (Editor.isActive()) { onClose(); return; }
        if (S.is3d()) {
            if (!Placement.canPlace(minecraft)) Placement.say(Component.translatable("curvegen.screen.you_ll_need_operator_permissions_to_place_this"));
            onClose();
            Editor.start();
            return;
        }
        if (!ready()) return;
        if (Layout.of(result).isEmpty()) { flash(tr("curvegen.screen.empty_placement")); return; }
        if (!Placement.canPlace(minecraft)) Placement.say(Component.translatable("curvegen.screen.you_ll_need_operator_permissions_to_place_this"));
        onClose();
        Editor.start();
    }

    /** Set by Export while a hologram is out and its blocks are still catching up with a change made here. */
    private boolean exporting;

    /** A hologram exports as it stands in the world, turned and tipped, as the radial menu's Export writes it. */
    private void exportHologram() {
        if (!exporting) return;
        if (!Editor.isActive()) { exporting = false; return; }
        if (Editor.catchingUp()) { flash(tr("curvegen.screen.exporting_as_soon_as_the_shape_in_the_world")); return; }
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
        if (layout.isEmpty()) { flash(tr("curvegen.screen.empty_export")); return; }
        String kind = switch (S.gen) { case ELLIPSE -> "ellipse"; case EQUATION -> "equation"; case BEZIER -> "bezier"; default -> "shape"; };
        try {
            String author = minecraft.player != null ? minecraft.player.getName().getString() : tr("key.category.curvegen.main");
            Path file = LitematicExporter.export(layout, S.depth, S.floor, LitematicExporter.defaultName(kind) + (S.floor ? "_floor" : ""), author);
            flash(tr("curvegen.screen.exported_to_schematics_s", file.getFileName().toString()));
            Placement.say(Component.translatable("curvegen.screen.exported_to_schematics_s", file.getFileName().toString()));
        } catch (Exception e) {
            flash(tr("curvegen.screen.export_failed_s", e.getMessage()));
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
