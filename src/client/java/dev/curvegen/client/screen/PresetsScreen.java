package dev.curvegen.client.screen;

import static dev.curvegen.client.Lang.tr;

import dev.curvegen.client.Lang;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.PresetStore.Preset;
import dev.curvegen.client.PresetStore;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.PresetData;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.Solver;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/** Every saved preset for one shape tab, with search, a live preview, and pin / rename / delete. */
public class PresetsScreen extends Screen {
    private final Screen parent;
    private final ShapeSettings.Gen gen;
    private final BiConsumer<Preset, Boolean> onLoad;   // the preset, and whether to load its blocks

    private EditBox search;
    private String query = "";
    private List<Preset> shown = List.of();
    private Preset selected, previewed, hovered;
    private int scroll;
    private Preset lastClicked;
    private long lastClickTime;
    private Button previewButton, loadButton;
    private CycleButton<Boolean> blocksButton;
    /** Whether to load the blocks of presets that have them. */
    private boolean loadBlocks = true;

    private final PreviewTexture texture = new PreviewTexture();
    private Preset textureFor;
    /** A preview is solved per preset, with or without its blocks. */
    private record Key(Preset preset, boolean blocks) {}
    private final Map<Key, Solver.Result> results = new HashMap<>();
    private final Map<Key, Future<Solver.Result>> jobs = new HashMap<>();
    /** A 3D preset's picture is solved the same way, and a solve is dropped when the mouse has moved on to another preset. */
    private record Job3(Future<CurveScreen.Solved3> future, AtomicBoolean cancelled) {}
    private final Map<Key, CurveScreen.Solved3> solved3 = new HashMap<>();
    private final Map<Key, Job3> jobs3 = new HashMap<>();
    private final Preview3 picture = new Preview3();
    private Key pictureFor;
    private int[] pictureIn;
    private boolean turning;

    private static final int ROW = 20, ICON = 12;
    // 9×9 pixel icons
    private static final String[] PIN = {
            "..#####..", "..#####..", "...###...", "...###...", ".#######.", "#########", "....#....", "....#....", "....#...."};
    private static final String[] PENCIL = {
            "......##.", ".....#..#", "....#..#.", "...#..#..", "..#..#...", ".#..#....", "#.##.....", "##.......", "#........"};
    private static final String[] TRASH = {
            "...###...", "#########", ".#######.", ".#.#.#.#.", ".#.#.#.#.", ".#.#.#.#.", ".#.#.#.#.", ".#.#.#.#.", "..#####.."};

    public PresetsScreen(Screen parent, ShapeSettings.Gen gen, BiConsumer<Preset, Boolean> onLoad) {
        super(Component.literal(switch (gen) { case ELLIPSE -> tr("curvegen.screen.load_a_preset_ellipse"); case EQUATION -> tr("curvegen.screen.load_a_preset_equation"); case BEZIER -> tr("curvegen.screen.load_a_preset_bezier_curve"); case ELLIPSOID -> tr("curvegen.screen.load_a_preset_ellipsoid"); case TORUS -> tr("curvegen.screen.load_a_preset_torus"); case EQUATION3 -> tr("curvegen.screen.load_a_preset_3d_equation"); case BEZIER3 -> tr("curvegen.screen.load_a_preset_3d_bezier_curve"); case SURFACE -> tr("curvegen.screen.load_a_preset_bezier_surface"); }));
        this.parent = parent; this.gen = gen; this.onLoad = onLoad;
    }

    // ---------- layout ----------
    private int listX() { return 8; }
    private int listW() { return Math.max(170, Math.min(260, (int) (width * 0.42))); }
    private int listTop() { return 50; }
    private int listBottom() { return height - 34; }
    private int visibleRows() { return Math.max(1, (listBottom() - listTop()) / ROW); }
    private int pvX0() { return listX() + listW() + 10; }
    private int pvY0() { return 26; }
    private int pvX1() { return width - 8; }
    private int pvY1() { return height - 34; }

    @Override
    protected void init() {
        search = new EditBox(font, listX(), 26, listW(), 20, Component.translatable("curvegen.screen.search"));
        search.setHint(Component.translatable("curvegen.screen.search_presets"));
        search.setValue(query);
        search.setResponder(v -> { query = v; scroll = 0; refilter(); });
        addRenderableWidget(search);
        setInitialFocus(search);

        int by = height - 26, bw = Math.max(60, font.width(tr("curvegen.screen.preview")) + 20);
        int blocksW = font.width(CommonComponents.optionNameValue(Component.translatable("curvegen.screen.blocks"), CommonComponents.optionStatus(false))) + 20, x = width - 8 - 3 * bw - blocksW - 12;
        previewButton = Button.builder(Component.translatable("curvegen.screen.preview"), b -> previewed = selected).bounds(x, by, bw, 20).build();
        blocksButton = CycleButton.onOffBuilder(loadBlocks).create(x + bw + 4, by, blocksW, 20, Component.translatable("curvegen.screen.blocks"),
                (button, v) -> { loadBlocks = v; textureFor = null; pictureFor = null; });
        loadButton = Button.builder(Component.translatable("curvegen.screen.load"), b -> { if (selected != null) load(selected); })
                .bounds(x + bw + blocksW + 8, by, bw, 20).build();
        addRenderableWidget(previewButton);
        addRenderableWidget(blocksButton);
        addRenderableWidget(loadButton);
        addRenderableWidget(Button.builder(Component.translatable("curvegen.screen.cancel"), b -> onClose()).bounds(width - 8 - bw, by, bw, 20).build());
        refilter();
    }

    private void refilter() {
        shown = PresetStore.list(gen).stream().filter(p -> PresetStore.matches(p, query)).toList();
        scroll = Math.max(0, Math.min(scroll, shown.size() - visibleRows()));
        if (selected != null && !shown.contains(selected)) selected = null;
    }

    private void load(Preset p) {
        minecraft.setScreen(parent);
        onLoad.accept(p, withBlocks(p));
    }

    /** Whether loading (and previewing) this preset uses its blocks: only if it has some and the Blocks option is on. */
    private boolean withBlocks(Preset p) { return loadBlocks && p.blocks != null; }

    // ---------- preview ----------
    /** The block choices the preview of p uses. */
    private Map<Family, Block> choiceFor(Preset p) {
        Map<Family, Block> choice = new EnumMap<>(BlockChoices.CHOICE);
        if (withBlocks(p)) BlockChoices.apply(p.blocks, CurveGenClient.SETTINGS.copy(), choice);
        return choice;
    }

    private ShapeSettings settingsFor(Preset p) {
        ShapeSettings s = CurveGenClient.SETTINGS.copy();   // current orientation and depth, and pieces unless it has its own
        PresetData.apply(p.data, gen, s);
        if (withBlocks(p)) BlockChoices.apply(p.blocks, s, new EnumMap<>(BlockChoices.CHOICE));
        return s;
    }

    private boolean is3d() { return ShapeSettings.is3d(gen); }

    private Solver.Result resultFor(Preset p) {
        Key k = new Key(p, withBlocks(p));
        Solver.Result r = results.get(k);
        if (r != null) return r;
        Future<Solver.Result> job = jobs.get(k);
        if (job == null) { ShapeSettings s = settingsFor(p); jobs.put(k, CurveScreen.EXEC.submit(() -> Solver.run(s))); return null; }
        if (!job.isDone()) return null;
        jobs.remove(k);
        try { r = job.get(); } catch (Exception ignored) { return null; }
        results.put(k, r);
        return r;
    }

    private CurveScreen.Solved3 solved3For(Preset p) {
        Key k = new Key(p, withBlocks(p));
        jobs3.entrySet().removeIf(e -> {
            if (e.getKey().equals(k)) return false;
            e.getValue().cancelled.set(true);
            return true;
        });
        CurveScreen.Solved3 r = solved3.get(k);
        if (r != null) return r;
        Job3 job = jobs3.get(k);
        if (job == null) {
            ShapeSettings s = settingsFor(p);
            AtomicBoolean cancelled = new AtomicBoolean();
            jobs3.put(k, new Job3(CurveScreen.EXEC.submit(() -> CurveScreen.solve3(s, cancelled::get)), cancelled));
            return null;
        }
        if (!job.future.isDone()) return null;
        jobs3.remove(k);
        try { r = job.future.get(); } catch (Exception ignored) { return null; }
        if (r != null) solved3.put(k, r);
        return r;
    }

    /** Draws a solved 3D preset, seen from the same side each time until the player turns it. */
    private void draw3(GuiGraphics ctx, Preset target, CurveScreen.Solved3 r, int x0, int y0, int x1, int y1) {
        Key k = new Key(target, withBlocks(target));
        int[] in = {x0, y0, x1, y1};
        // A resized window moves the picture's place, and the view goes with it.
        if (!k.equals(pictureFor) || !java.util.Arrays.equals(in, pictureIn)) {
            pictureFor = k; pictureIn = in;
            var shape = r.result().shape();
            picture.mesh(r.mesh(), shape.pad());
            picture.wires(List.of(), 0);
            picture.palette(Preview3.palette(CurveScreen.colors, choiceFor(target), CurveGenClient.SETTINGS.topColours));
            picture.fit(shape.nx(), shape.ny(), shape.nz(), shape.pad(), x0, y0, x1, y1);
        }
        ctx.enableScissor(x0, y0, x1, y1);
        picture.draw(ctx, x0, y0, x1, y1);
        ctx.disableScissor();
    }

    // ---------- rendering ----------
    private int rowAt(double mx, double my) {
        if (mx < listX() || mx >= listX() + listW() || my < listTop() || my >= listTop() + visibleRows() * ROW) return -1;
        int i = scroll + (int) ((my - listTop()) / ROW);
        return i < shown.size() ? i : -1;
    }

    /** Which icon (0 pin, 1 rename, 2 delete) is under the mouse in a row whose icons are showing, or -1. */
    private int iconAt(int row, double mx, double my) {
        if (row < 0) return -1;
        Preset p = shown.get(row);
        if (p != hovered && p != selected) return -1;
        int y = listTop() + (row - scroll) * ROW + (ROW - ICON) / 2;
        for (int k = 0; k < 3; k++) {
            int x = iconX(k);
            if (mx >= x && mx < x + ICON && my >= y && my < y + ICON) return k;
        }
        return -1;
    }

    private int iconX(int k) { return listX() + listW() - 4 - (3 - k) * (ICON + 2); }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        int hr = rowAt(mx, my);
        hovered = hr >= 0 ? shown.get(hr) : null;
        previewButton.active = selected != null;
        loadButton.active = selected != null;
        boolean hasBlocks = selected != null && selected.blocks != null;
        blocksButton.active = hasBlocks;
        blocksButton.setValue(loadBlocks && (selected == null || hasBlocks));
        blocksButton.setTooltip(Tooltip.create(Component.literal(selected == null ? tr("curvegen.screen.select_a_preset_first")
                : hasBlocks ? tr("curvegen.screen.also_load_the_preset_s_block_for_each_piece")
                : tr("curvegen.screen.this_preset_was_saved_without_blocks"))));
        super.render(ctx, mx, my, delta);
        ctx.drawString(font, title, listX(), 10, 0xFFFFFFFF);

        // list
        int x0 = listX(), x1 = x0 + listW(), top = listTop();
        ctx.fill(x0 - 1, top - 1, x1 + 1, listBottom() + 1, 0x80000000);
        if (shown.isEmpty()) {
            String msg = query.isBlank() ? tr("curvegen.screen.no_presets_yet_use_save_preset_in_the_generator") : tr("curvegen.screen.no_presets_match_your_search");
            ctx.drawWordWrap(font, Component.literal(msg), x0 + 6, top + 6, listW() - 12, 0xFF9AA5B3, false);
        }
        String tip = null;
        for (int i = scroll; i < shown.size() && i < scroll + visibleRows(); i++) {
            Preset p = shown.get(i);
            int y = top + (i - scroll) * ROW;
            boolean sel = p == selected, hov = p == hovered, icons = sel || hov;
            if (sel) ctx.fill(x0, y, x1, y + ROW, 0x603F7BE0);
            else if (hov) ctx.fill(x0, y, x1, y + ROW, 0x20FFFFFF);
            int room = listW() - 8 - (icons || p.pinned ? 3 * (ICON + 2) + 4 : 0);
            String name = p.name;
            if (font.width(name) > room) name = font.plainSubstrByWidth(name, room - font.width("…")) + "…";
            ctx.drawString(font, name, x0 + 5, y + 6, 0xFFE4E9EF, false);
            int iy = y + (ROW - ICON) / 2;
            if (icons) {
                int over = iconAt(i, mx, my);
                for (int k = 0; k < 3; k++) {
                    int ix = iconX(k);
                    boolean on = over == k;
                    if (on) ctx.fill(ix, iy, ix + ICON, iy + ICON, k == 2 ? 0x50FF3355 : 0x30FFFFFF);
                    int c = k == 2 && on ? 0xFFFF5566 : k == 0 && p.pinned ? 0xFFFFC04D : on ? 0xFFFFFFFF : 0xFFB8C0CA;
                    drawArt(ctx, k == 0 ? PIN : k == 1 ? PENCIL : TRASH, ix, iy, c);
                    if (on) tip = k == 0 ? (p.pinned ? tr("curvegen.screen.unpin") : tr("curvegen.screen.pin_to_the_top")) : k == 1 ? tr("curvegen.screen.rename") : tr("curvegen.screen.delete");
                }
            } else if (p.pinned) {
                drawArt(ctx, PIN, iconX(0), iy, 0xFFFFC04D);   // pinned presets always show their pin
            }
        }
        if (shown.size() > visibleRows()) {
            int h = visibleRows() * ROW, bar = Math.max(8, h * visibleRows() / shown.size());
            int by = top + (h - bar) * scroll / (shown.size() - visibleRows());
            ctx.fill(x1 + 2, by, x1 + 4, by + bar, 0xA0FFFFFF);
        }

        // preview
        int px0 = pvX0(), py0 = pvY0(), px1 = pvX1(), py1 = pvY1();
        ctx.fill(px0, py0, px1, py1, 0xFF15181D);
        ctx.renderOutline(px0 - 1, py0 - 1, px1 - px0 + 2, py1 - py0 + 2, 0xFF3A424D);
        Preset target = hovered != null ? hovered : previewed;
        if (target == null) {
            ctx.drawWordWrap(font, Component.translatable("curvegen.screen.hover_a_preset_to_preview_it_or_select_one"),
                    px0 + 8, py0 + 8, px1 - px0 - 16, 0xFF9AA5B3, false);
        } else {
            Solver.Result r = is3d() ? null : resultFor(target);
            CurveScreen.Solved3 r3 = is3d() ? solved3For(target) : null;
            int infoH = 24;
            if (is3d()) {
                if (r3 == null) ctx.drawString(font, tr("curvegen.screen.working"), px0 + 8, py0 + 8, 0xFF9AA5B3, false);
                else if (r3.error() != null) ctx.drawWordWrap(font, Component.literal(Lang.text(r3.error())), px0 + 8, py0 + 8, px1 - px0 - 16, 0xFFFF8098, false);
                else draw3(ctx, target, r3, px0, py0, px1, py1 - infoH);
            }
            else if (r == null) ctx.drawString(font, tr("curvegen.screen.working"), px0 + 8, py0 + 8, 0xFF9AA5B3, false);
            else if (r.target().error != null)
                ctx.drawWordWrap(font, Component.literal(Lang.text(r.target().error)), px0 + 8, py0 + 8, px1 - px0 - 16, 0xFFFF8098, false);
            else {
                if (textureFor != target || texture.id() == null) { texture.update(r, CurveScreen.colors, true, true, choiceFor(target)); textureFor = target; }
                float aw = px1 - px0 - 12, ah = py1 - py0 - 12 - infoH;
                float z = Math.min(aw / r.nx(), ah / r.ny());
                float ox = px0 + (px1 - px0 - r.nx() * z) / 2f, oy = py0 + 6 + (ah - r.ny() * z) / 2f;
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
            ctx.fill(px0, py1 - infoH, px1, py1, 0xB0000000);
            ctx.drawString(font, font.plainSubstrByWidth(target.name, px1 - px0 - 12), px0 + 6, py1 - infoH + 3, 0xFFFFFFFF, false);
            String desc = Lang.text(PresetData.defaultName(settingsFor(target), gen));
            ctx.drawString(font, font.plainSubstrByWidth(desc, px1 - px0 - 12), px0 + 6, py1 - infoH + 13, 0xFF9AA5B3, false);
        }
        if (tip != null) ctx.setTooltipForNextFrame(font, Component.literal(tip), mx, my);
    }

    private static void drawArt(GuiGraphics ctx, String[] art, int x, int y, int color) {
        int ox = x + (ICON - 9) / 2, oy = y + (ICON - 9) / 2;
        for (int r = 0; r < art.length; r++)
            for (int c = 0; c < art[r].length(); c++)
                if (art[r].charAt(c) == '#') ctx.fill(ox + c, oy + r, ox + c + 1, oy + r + 1, color);
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y(); int button = event.button();
        if (super.mouseClicked(event, doubled)) return true;
        if (is3d() && previewed != null && mx >= pvX0() && mx < pvX1() && my >= pvY0() && my < pvY1()) { turning = true; return true; }
        int row = rowAt(mx, my);
        if (row < 0 || button != InputConstants.MOUSE_BUTTON_LEFT) return false;
        Preset p = shown.get(row);
        switch (iconAt(row, mx, my)) {
            case 0 -> { PresetStore.togglePin(p); selected = p; refilter(); return true; }
            case 1 -> { rename(p); return true; }
            case 2 -> { delete(p); return true; }
            default -> { }
        }
        long now = System.currentTimeMillis();
        if (p == lastClicked && now - lastClickTime < 350) { load(p); return true; }   // double click
        lastClicked = p; lastClickTime = now;
        selected = p;
        setFocused(null);
        return true;
    }

    /** Dragging a 3D preset's picture turns it. */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (turning) { picture.cam.orbit(dx, dy); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        turning = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (mx < pvX0()) {
            scroll = Math.max(0, Math.min(Math.max(0, shown.size() - visibleRows()), scroll - (int) Math.signum(v)));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && selected != null) { load(selected); return true; }
        return super.keyPressed(event);
    }

    private void rename(Preset p) {
        minecraft.setScreen(new NameDialogScreen(this, tr("curvegen.screen.rename_preset"), p.name, name -> {
            if (name.isEmpty()) return new NameDialogScreen.Check(false, tr("curvegen.screen.rename"), tr("curvegen.screen.type_a_name_for_the_preset"));
            Preset other = PresetStore.find(gen, name);
            if (other != null && other != p) return new NameDialogScreen.Check(false, tr("curvegen.screen.rename"), tr("curvegen.screen.another_preset_already_has_this_name"));
            return new NameDialogScreen.Check(true, tr("curvegen.screen.rename"), null);
        }, name -> { PresetStore.rename(p, name); selected = p; refilter(); }));
    }

    private void delete(Preset p) {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                PresetStore.delete(p);
                if (selected == p) selected = null;
                if (previewed == p) previewed = null;
                results.keySet().removeIf(k -> k.preset() == p);
                solved3.keySet().removeIf(k -> k.preset() == p);
                refilter();
            }
            minecraft.setScreen(this);
        }, Component.translatable("curvegen.screen.are_you_sure"),
                Component.translatable("curvegen.screen.delete_the_preset_s_this_can_t_be_undone", p.name),
                Component.translatable("curvegen.screen.delete"), CommonComponents.GUI_CANCEL));
    }

    @Override
    public void removed() {
        texture.close();
        textureFor = null;
        picture.close();
        for (Job3 j : jobs3.values()) j.cancelled.set(true);
        jobs3.clear();
        super.removed();
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }
}
