package dev.curvegen.client.screen;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.PresetStore;
import dev.curvegen.client.PresetStore.Preset;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.PresetData;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.Solver;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

/** Every saved preset for one shape tab, with search, a live preview, and pin / rename / delete. */
public class PresetsScreen extends Screen {
    private final Screen parent;
    private final ShapeSettings.Gen gen;
    private final BiConsumer<Preset, Boolean> onLoad;   // the preset, and whether to load its blocks

    private TextFieldWidget search;
    private String query = "";
    private List<Preset> shown = List.of();
    private Preset selected, previewed, hovered;
    private int scroll;
    private Preset lastClicked;
    private long lastClickTime;
    private ButtonWidget previewButton, loadButton;
    private CyclingButtonWidget<Boolean> blocksButton;
    /** Whether to load the blocks of presets that have them. */
    private boolean loadBlocks = true;

    private final PreviewTexture texture = new PreviewTexture();
    private Preset textureFor;
    /** A preview is solved per preset, with or without its blocks. */
    private record Key(Preset preset, boolean blocks) {}
    private final Map<Key, Solver.Result> results = new HashMap<>();
    private final Map<Key, Future<Solver.Result>> jobs = new HashMap<>();

    private static final int ROW = 20, ICON = 12;
    // 9×9 pixel icons
    private static final String[] PIN = {
            "..#####..", "..#####..", "...###...", "...###...", ".#######.", "#########", "....#....", "....#....", "....#...."};
    private static final String[] PENCIL = {
            "......##.", ".....#..#", "....#..#.", "...#..#..", "..#..#...", ".#..#....", "#.##.....", "##.......", "#........"};
    private static final String[] TRASH = {
            "...###...", "#########", ".#######.", ".#.#.#.#.", ".#.#.#.#.", ".#.#.#.#.", ".#.#.#.#.", ".#.#.#.#.", "..#####.."};

    public PresetsScreen(Screen parent, ShapeSettings.Gen gen, BiConsumer<Preset, Boolean> onLoad) {
        super(Text.literal("Load a preset: " + switch (gen) { case ELLIPSE -> "ellipse"; case EQUATION -> "equation"; case BEZIER -> "Bézier curve"; }));
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
        search = new TextFieldWidget(textRenderer, listX(), 26, listW(), 20, Text.literal("Search"));
        search.setPlaceholder(Text.literal("Search presets"));
        search.setText(query);
        search.setChangedListener(v -> { query = v; scroll = 0; refilter(); });
        addDrawableChild(search);
        setInitialFocus(search);

        int by = height - 26, bw = Math.max(60, textRenderer.getWidth("Preview") + 20);
        int blocksW = textRenderer.getWidth("Blocks: OFF") + 20, x = width - 8 - 3 * bw - blocksW - 12;
        previewButton = ButtonWidget.builder(Text.literal("Preview"), b -> previewed = selected).dimensions(x, by, bw, 20).build();
        blocksButton = CyclingButtonWidget.onOffBuilder(loadBlocks).build(x + bw + 4, by, blocksW, 20, Text.literal("Blocks"),
                (b, v) -> { loadBlocks = v; textureFor = null; });
        loadButton = ButtonWidget.builder(Text.literal("Load"), b -> { if (selected != null) load(selected); })
                .dimensions(x + bw + blocksW + 8, by, bw, 20).build();
        addDrawableChild(previewButton);
        addDrawableChild(blocksButton);
        addDrawableChild(loadButton);
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close()).dimensions(width - 8 - bw, by, bw, 20).build());
        refilter();
    }

    private void refilter() {
        shown = PresetStore.list(gen).stream().filter(p -> PresetStore.matches(p, query)).toList();
        scroll = Math.max(0, Math.min(scroll, shown.size() - visibleRows()));
        if (selected != null && !shown.contains(selected)) selected = null;
    }

    private void load(Preset p) {
        client.setScreen(parent);
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

    private Solver.Result resultFor(Preset p) {
        Key k = new Key(p, withBlocks(p));
        Solver.Result r = results.get(k);
        if (r != null) return r;
        Future<Solver.Result> job = jobs.get(k);
        if (job == null) { ShapeSettings s = settingsFor(p); jobs.put(k, CurveScreen.EXEC.submit(() -> Solver.run(s))); return null; }
        if (!job.isDone()) return null;
        jobs.remove(k);
        try { r = job.get(); } catch (Exception e) { return null; }
        results.put(k, r);
        return r;
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
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int hr = rowAt(mx, my);
        hovered = hr >= 0 ? shown.get(hr) : null;
        previewButton.active = selected != null;
        loadButton.active = selected != null;
        boolean hasBlocks = selected != null && selected.blocks != null;
        blocksButton.active = hasBlocks;
        blocksButton.setValue(loadBlocks && (selected == null || hasBlocks));
        blocksButton.setTooltip(Tooltip.of(Text.literal(selected == null ? "Select a preset first."
                : hasBlocks ? "Also load the preset's block for each piece type, and whether it's used."
                : "This preset was saved without blocks.")));
        super.render(ctx, mx, my, delta);
        ctx.drawTextWithShadow(textRenderer, title, listX(), 10, 0xFFFFFF);

        // list
        int x0 = listX(), x1 = x0 + listW(), top = listTop();
        ctx.fill(x0 - 1, top - 1, x1 + 1, listBottom() + 1, 0x80000000);
        if (shown.isEmpty()) {
            String msg = query.isBlank() ? "No presets yet. Use Save preset in the generator to add one." : "No presets match your search.";
            ctx.drawTextWrapped(textRenderer, Text.literal(msg), x0 + 6, top + 6, listW() - 12, 0x9AA5B3);
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
            if (textRenderer.getWidth(name) > room) name = textRenderer.trimToWidth(name, room - textRenderer.getWidth("…")) + "…";
            ctx.drawText(textRenderer, name, x0 + 5, y + 6, 0xE4E9EF, false);
            int iy = y + (ROW - ICON) / 2;
            if (icons) {
                int over = iconAt(i, mx, my);
                for (int k = 0; k < 3; k++) {
                    int ix = iconX(k);
                    boolean on = over == k;
                    if (on) ctx.fill(ix, iy, ix + ICON, iy + ICON, k == 2 ? 0x50FF3355 : 0x30FFFFFF);
                    int c = k == 2 && on ? 0xFFFF5566 : k == 0 && p.pinned ? 0xFFFFC04D : on ? 0xFFFFFFFF : 0xFFB8C0CA;
                    drawArt(ctx, k == 0 ? PIN : k == 1 ? PENCIL : TRASH, ix, iy, c);
                    if (on) tip = k == 0 ? (p.pinned ? "Unpin" : "Pin to the top") : k == 1 ? "Rename" : "Delete";
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
        ctx.drawBorder(px0 - 1, py0 - 1, px1 - px0 + 2, py1 - py0 + 2, 0xFF3A424D);
        Preset target = hovered != null ? hovered : previewed;
        if (target == null) {
            ctx.drawTextWrapped(textRenderer, Text.literal("Hover a preset to preview it, or select one and press Preview."),
                    px0 + 8, py0 + 8, px1 - px0 - 16, 0x9AA5B3);
        } else {
            Solver.Result r = resultFor(target);
            int infoH = 24;
            if (r == null) ctx.drawText(textRenderer, "Working…", px0 + 8, py0 + 8, 0x9AA5B3, false);
            else if (r.target().error != null)
                ctx.drawTextWrapped(textRenderer, Text.literal(r.target().error), px0 + 8, py0 + 8, px1 - px0 - 16, 0xFF8098);
            else {
                if (textureFor != target || texture.id() == null) { texture.update(r, CurveScreen.colors, true, true, choiceFor(target)); textureFor = target; }
                float aw = px1 - px0 - 12, ah = py1 - py0 - 12 - infoH;
                float z = Math.min(aw / r.nx(), ah / r.ny());
                float ox = px0 + (px1 - px0 - r.nx() * z) / 2f, oy = py0 + 6 + (ah - r.ny() * z) / 2f;
                ctx.enableScissor(px0, py0, px1, py1);
                var m = ctx.getMatrices();
                m.push();
                m.translate(ox, oy, 0);
                float s = z / texture.sub;
                m.scale(s, s, 1);
                ctx.drawTexture(texture.id(), 0, 0, 0f, 0f, texture.width, texture.height, texture.width, texture.height);
                m.pop();
                ctx.disableScissor();
            }
            ctx.fill(px0, py1 - infoH, px1, py1, 0xB0000000);
            ctx.drawText(textRenderer, textRenderer.trimToWidth(target.name, px1 - px0 - 12), px0 + 6, py1 - infoH + 3, 0xFFFFFF, false);
            String desc = PresetData.defaultName(settingsFor(target), gen);
            ctx.drawText(textRenderer, textRenderer.trimToWidth(desc, px1 - px0 - 12), px0 + 6, py1 - infoH + 13, 0x9AA5B3, false);
        }
        if (tip != null) ctx.drawTooltip(textRenderer, Text.literal(tip), mx, my);
    }

    private static void drawArt(DrawContext ctx, String[] art, int x, int y, int color) {
        int ox = x + (ICON - 9) / 2, oy = y + (ICON - 9) / 2;
        for (int r = 0; r < art.length; r++)
            for (int c = 0; c < art[r].length(); c++)
                if (art[r].charAt(c) == '#') ctx.fill(ox + c, oy + r, ox + c + 1, oy + r + 1, color);
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int row = rowAt(mx, my);
        if (row < 0 || button != 0) return false;
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

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (mx < pvX0()) {
            scroll = Math.max(0, Math.min(Math.max(0, shown.size() - visibleRows()), scroll - (int) Math.signum(v)));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && selected != null) { load(selected); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private void rename(Preset p) {
        client.setScreen(new NameDialogScreen(this, "Rename preset", p.name, name -> {
            if (name.isEmpty()) return new NameDialogScreen.Check(false, "Rename", "Type a name for the preset.");
            Preset other = PresetStore.find(gen, name);
            if (other != null && other != p) return new NameDialogScreen.Check(false, "Rename", "Another preset already has this name.");
            return new NameDialogScreen.Check(true, "Rename", null);
        }, name -> { PresetStore.rename(p, name); selected = p; refilter(); }));
    }

    private void delete(Preset p) {
        client.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                PresetStore.delete(p);
                if (selected == p) selected = null;
                if (previewed == p) previewed = null;
                results.keySet().removeIf(k -> k.preset() == p);
                refilter();
            }
            client.setScreen(this);
        }, Text.literal("Are you sure?"),
                Text.literal("Delete the preset \"" + p.name + "\"? This can't be undone."),
                Text.literal("Delete"), ScreenTexts.CANCEL));
    }

    @Override
    public void removed() {
        texture.close();
        textureFor = null;
        super.removed();
    }

    @Override
    public void close() { client.setScreen(parent); }

    @Override
    public boolean shouldPause() { return parent.shouldPause(); }
}
