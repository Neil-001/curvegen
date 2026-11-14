package dev.curvegen.client.screen;

import static dev.curvegen.client.Lang.tr;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.ColorIndex;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.ImageFile;
import dev.curvegen.client.PaintBlocks;
import dev.curvegen.client.PatternStore;
import dev.curvegen.client.PatternStore.Saved;
import dev.curvegen.client.chroma.Chroma;
import dev.curvegen.client.edit.Capture;
import dev.curvegen.client.edit.Editor;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.paint.Paint;
import dev.curvegen.core.paint.Pattern;
import dev.curvegen.core.paint.PatternNames;
import dev.curvegen.core.paint.Patterns;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/**
 * The pattern library: every saved pattern with its thumbnail, search, and pin, rename and delete, beside a picture
 * of the current shape painted with the pattern under the mouse, or with the current one. Clicking a pattern chooses
 * it. "Capture new…" closes the menus to capture one from the world ({@link Capture}), and once it's named chooses it
 * and opens what {@code reopen} gives. The current pattern shows first, marked as not saved, when the library doesn't
 * hold it, as after loading a preset that carries its own.
 *
 * <p>A picture file dropped onto the library becomes a pattern of colours ({@link ImagePatternScreen}), when Chroma
 * Studio is installed to match them well. A line beside "Capture new…" says so, greyed without it, and says why after
 * a drop that made nothing.
 */
public class PatternLibraryScreen extends Screen {
    private static final ShapeSettings S = CurveGenClient.SETTINGS;
    private static final int M = 8, ROW = 22, SWATCH = 18, ICON = 12, TOP = 50;
    private final Screen parent;
    private final Consumer<Pattern> onPick;
    private final Supplier<Screen> reopen;

    private EditBox search;
    private String query = "";
    /** The rows: saved patterns, and null for the current pattern when it isn't saved. */
    private List<Saved> shown = List.of();
    private Pattern unsaved;
    private int scroll;
    private final PaintPicture picture = new PaintPicture();
    private Button captureButton, cancelButton;
    /**
     * Why the last drop made no pattern, shown in place of the hint until the next one, or null, and the same with the
     * file's whole name for its tooltip.
     */
    private String dropProblem, dropProblemTip;

    /**
     * @param onPick given the chosen pattern, before the library goes back to {@code parent}
     * @param reopen the screen to open after a capture started here, or null to open none
     */
    public PatternLibraryScreen(Screen parent, Consumer<Pattern> onPick, Supplier<Screen> reopen) {
        super(Component.translatable("curvegen.paint.choose_title"));
        this.parent = parent; this.onPick = onPick; this.reopen = reopen;
    }

    /** What a pattern suits, for its tooltip, or null. */
    private static String tip(Pattern p) {
        return p.sameCells(Patterns.TWO_LANE_ROAD) ? tr("curvegen.paint.two_lane_road_tip") : p.sameCells(Patterns.LATTICE) ? tr("curvegen.paint.lattice_tip") : null;
    }

    /** A cell's colour, opaque: its block's, or its own without one, or 0 for an empty cell. */
    static int colour(Pattern.Cell c) {
        Block b = c.block() == null ? null : PaintBlocks.block(c.block());
        return b != null ? 0xFF000000 | ColorIndex.of(b) : c.rgb() >= 0 ? 0xFF000000 | c.rgb() : 0;
    }

    /**
     * Draws a pattern flat in a {@code size}-pixel square, top row at the top, from its cells' block colours or their
     * own colours. Empty cells are left dark.
     */
    static void drawSwatch(GuiGraphicsExtractor ctx, Pattern p, int x, int y, int size) {
        ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0x40FFFFFF);
        ctx.fill(x, y, x + size, y + size, PreviewTexture.BG);
        int[] colour = new int[p.paletteSize()];
        for (int k = 0; k < colour.length; k++) colour[k] = colour(p.cell(k));
        int cell = size / Math.max(p.width, p.height);
        if (cell >= 1) {
            // Whole pixels per cell, centred, so small patterns stay crisp.
            int ox = x + (size - p.width * cell) / 2, oy = y + (size - p.height * cell) / 2;
            for (int r = 0; r < p.height; r++)
                for (int c = 0; c < p.width; c++) {
                    int argb = colour[p.at(c, p.height - 1 - r)];
                    if (argb != 0) ctx.fill(ox + c * cell, oy + r * cell, ox + (c + 1) * cell, oy + (r + 1) * cell, argb);
                }
            return;
        }
        // A pattern bigger than the square: each pixel takes the cell under its centre.
        int n = Math.max(p.width, p.height), dw = size * p.width / n, dh = size * p.height / n;
        int ox = x + (size - dw) / 2, oy = y + (size - dh) / 2;
        for (int py = 0; py < dh; py++)
            for (int px = 0; px < dw; px++) {
                int argb = colour[p.at(px * p.width / dw, p.height - 1 - py * p.height / dh)];
                if (argb != 0) ctx.fill(ox + px, oy + py, ox + px + 1, oy + py + 1, argb);
            }
    }

    // ---------- layout ----------
    private int listX() { return M; }
    private int listW() { return Math.max(150, Math.min(240, (int) (width * 0.42))); }
    private int listBottom() { return height - 32; }
    private int visible() { return Math.max(1, (listBottom() - TOP) / ROW); }
    private int rows() { return shown.size() + (unsaved != null ? 1 : 0); }
    private int pvX0() { return listX() + listW() + 10; }

    @Override
    protected void init() {
        search = new EditBox(font, listX(), 26, listW(), 20, Component.translatable("curvegen.screen.search"));
        search.setHint(Component.translatable("curvegen.patterns.search"));
        search.setValue(query);
        search.setResponder(v -> { query = v; scroll = 0; refilter(); });
        addRenderableWidget(search);
        setInitialFocus(search);

        int by = height - 26;
        int cw = Math.max(80, font.width(tr("curvegen.screen.cancel")) + 24);
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("curvegen.screen.cancel"), _ -> onClose()).bounds(width - M - cw, by, cw, 20).build());
        int nw = Math.min(listW(), font.width(tr("curvegen.patterns.capture")) + 24);
        Button capture = Button.builder(Component.translatable("curvegen.patterns.capture"), _ -> capture()).bounds(listX(), by, nw, 20).build();
        capture.setTooltip(Tooltip.create(Component.translatable("curvegen.patterns.capture_tip")));
        captureButton = addRenderableWidget(capture);
        refilter();
    }

    /** Lists the saved patterns that match the search again, after the library changed. */
    private void refilter() {
        Pattern current = S.paint.pattern;
        List<Saved> all = PatternStore.list();
        unsaved = all.stream().anyMatch(s -> s.pattern.sameCells(current)) || !PatternNames.matches(PatternStore.shownName(current), query) ? null : current;
        shown = all.stream().filter(s -> PatternNames.matches(s.name(), query)).toList();
        scroll = Math.max(0, Math.min(scroll, rows() - visible()));
    }

    /** The pattern in a row. */
    private Pattern patternAt(int row) { return unsaved != null ? row == 0 ? unsaved : shown.get(row - 1).pattern : shown.get(row).pattern; }

    /** The saved pattern in a row, or null for the current pattern that isn't saved. */
    private Saved savedAt(int row) { return unsaved != null ? row == 0 ? null : shown.get(row - 1) : shown.get(row); }

    private int rowAt(double mx, double my) {
        if (mx < listX() || mx >= listX() + listW() || my < TOP || my >= TOP + visible() * ROW) return -1;
        int i = scroll + (int) ((my - TOP) / ROW);
        return i < rows() ? i : -1;
    }

    private int iconX(int k) { return listX() + listW() - 4 - (3 - k) * (ICON + 2); }

    /** Which icon (0 pin, 1 rename, 2 delete) is under the mouse in a hovered row of a saved pattern, or -1. */
    private int iconAt(int row, double mx, double my) {
        if (row < 0 || savedAt(row) == null) return -1;
        int y = TOP + (row - scroll) * ROW + (ROW - ICON) / 2;
        for (int k = 0; k < 3; k++) {
            int x = iconX(k);
            if (mx >= x && mx < x + ICON && my >= y && my < y + ICON) return k;
        }
        return -1;
    }

    // ---------- the picture ----------
    /** The current settings, painted with {@code p}: what choosing it would look like. */
    private static ShapeSettings with(Pattern p) {
        ShapeSettings s = S.copy();
        use(s, p);
        return s;
    }

    /**
     * Paints {@code s} with {@code p}, making Pattern the source whatever it was, and works out again whether fences,
     * panes and walls attach to full blocks, which follows the pattern.
     */
    static void use(ShapeSettings s, Pattern p) {
        s.paint.pattern = p;
        s.paint.source = Paint.Source.PATTERN;
        s.fullConnects = BlockChoices.fullConnects(s);
    }

    // ---------- rendering ----------
    /** Darkened as the main screen's is, so the hint between the bottom buttons reads over a bright sky. */
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(0, 0, width, height, 0x80101317);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        int hover = rowAt(mx, my);
        Pattern target = hover >= 0 ? patternAt(hover) : S.paint.pattern;
        picture.poll(with(target));
        super.extractRenderState(ctx, mx, my, delta);
        ctx.text(font, title, listX(), 10, 0xFFFFFFFF);

        int x0 = listX(), x1 = x0 + listW();
        ctx.fill(x0 - 1, TOP - 1, x1 + 1, listBottom() + 1, 0x80000000);
        if (rows() == 0) ctx.textWithWordWrap(font, Component.translatable(query.isBlank() ? "curvegen.patterns.none" : "curvegen.patterns.no_match"),
                x0 + 6, TOP + 6, listW() - 12, 0xFF9AA5B3, false);
        Pattern current = S.paint.pattern;
        String tip = null;
        for (int i = scroll; i < rows() && i < scroll + visible(); i++) {
            Pattern p = patternAt(i);
            Saved saved = savedAt(i);
            int y = TOP + (i - scroll) * ROW;
            boolean hov = i == hover, icons = hov && saved != null;
            if (p.sameCells(current)) ctx.fill(x0, y, x1, y + ROW, 0x603F7BE0);
            else if (hov) ctx.fill(x0, y, x1, y + ROW, 0x20FFFFFF);
            drawSwatch(ctx, p, x0 + 3, y + (ROW - SWATCH) / 2, SWATCH);
            String n = saved != null ? saved.name() : tr("curvegen.patterns.unsaved", PatternStore.shownName(p));
            String size = p.width + "×" + p.height;
            int nameX = x0 + SWATCH + 9, right = icons || saved != null && saved.pinned ? iconX(0) - 4 : x1 - 5 - font.width(size) - 6;
            String whole = null;   // the whole name, for the tooltip, when the row cuts it
            if (font.width(n) > right - nameX) { whole = n; n = font.plainSubstrByWidth(n, right - nameX - font.width("…")) + "…"; }
            ctx.text(font, n, nameX, y + 7, 0xFFE4E9EF, false);
            int iy = y + (ROW - ICON) / 2;
            if (icons) {
                int over = iconAt(i, mx, my);
                for (int k = 0; k < 3; k++) {
                    int ix = iconX(k);
                    boolean on = over == k;
                    if (on) ctx.fill(ix, iy, ix + ICON, iy + ICON, k == 2 ? 0x50FF3355 : 0x30FFFFFF);
                    int c = k == 2 && on ? 0xFFFF5566 : k == 0 && saved.pinned ? 0xFFFFC04D : on ? 0xFFFFFFFF : 0xFFB8C0CA;
                    PresetsScreen.drawArt(ctx, k == 0 ? PresetsScreen.PIN : k == 1 ? PresetsScreen.PENCIL : PresetsScreen.TRASH, ix, iy, c);
                    if (on) tip = k == 0 ? tr(saved.pinned ? "curvegen.screen.unpin" : "curvegen.screen.pin_to_the_top")
                            : tr(k == 1 ? "curvegen.screen.rename" : "curvegen.screen.delete");
                }
            } else {
                if (saved != null && saved.pinned) PresetsScreen.drawArt(ctx, PresetsScreen.PIN, iconX(0), iy, 0xFFFFC04D);
                else ctx.text(font, size, x1 - 5 - font.width(size), y + 7, 0xFF9AA5B3, false);
            }
            if (hov && tip == null) {
                tip = saved == null ? tr("curvegen.patterns.unsaved_tip") : tip(p);
                if (whole != null) tip = tip == null ? whole : whole + '\n' + '\n' + tip;
            }
        }
        if (rows() > visible()) {
            int h = visible() * ROW, bar = Math.max(8, h * visible() / rows());
            int by = TOP + (h - bar) * scroll / (rows() - visible());
            ctx.fill(x1 + 2, by, x1 + 4, by + bar, 0xA0FFFFFF);
        }

        // The picture, with the pattern's name and size under it.
        int px0 = pvX0(), py0 = 26, px1 = width - M, py1 = listBottom(), infoH = 14;
        picture.draw(ctx, font, S.is3d(), px0, py0, px1, py1 - infoH);
        ctx.fill(px0, py1 - infoH, px1, py1, 0xB0000000);
        String info = PatternStore.shownName(target) + "  " + target.width + "×" + target.height;
        // A name too long for the caption is cut with "…", and hovering the caption gives it whole.
        if (font.width(info) > px1 - px0 - 12) {
            ctx.text(font, font.plainSubstrByWidth(info, px1 - px0 - 12 - font.width("…")) + "…", px0 + 6, py1 - infoH + 3, 0xFFFFFFFF, false);
            if (tip == null && mx >= px0 && mx < px1 && my >= py1 - infoH && my < py1) tip = info;
        } else ctx.text(font, info, px0 + 6, py1 - infoH + 3, 0xFFFFFFFF, false);

        // Between the bottom buttons: that pictures can be dropped here, or why the last one made nothing.
        int hx0 = captureButton.getRight() + 8, hx1 = cancelButton.getX() - 8, hy = cancelButton.getY();
        if (hx1 - hx0 > 20) {
            boolean can = Chroma.available();
            String hint = dropProblem != null ? dropProblem : tr("curvegen.patterns.drop_hint");
            // One line, or two in the buttons' height when it's longer, the second cut with "…" if it still doesn't fit.
            int room = hx1 - hx0;
            TwoLines two = TwoLines.of(font, hint, room);
            List<String> lines = two.lines();
            int colour = dropProblem != null ? 0xFFFF8098 : can ? 0xFF9AA5B3 : 0xFF5C6470;
            for (int k = 0; k < lines.size(); k++)
                ctx.text(font, lines.get(k), hx0, lines.size() == 1 ? hy + 6 : hy + 1 + 10 * k, colour, false);
            if (mx >= hx0 && mx < hx0 + two.width(font) && my >= hy && my < hy + 20) {
                tip = dropProblem != null ? dropProblemTip : tr(can ? "curvegen.patterns.drop_hint_tip" : "curvegen.patterns.drop_needs");
                if (two.cut() && dropProblem == null) tip = hint + '\n' + '\n' + tip;
            }
        }
        if (tip != null) ctx.setTooltipForNextFrame(font.split(Component.literal(tip), 200), mx, my);
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        int row = rowAt(event.x(), event.y());
        if (row < 0 || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        Saved saved = savedAt(row);
        switch (iconAt(row, event.x(), event.y())) {
            case 0 -> { PatternStore.togglePin(saved); refilter(); return true; }
            case 1 -> { rename(saved); return true; }
            case 2 -> { delete(saved); return true; }
            default -> { }
        }
        // Chosen first, so the parent builds its controls from the new settings as it opens.
        onPick.accept(patternAt(row));
        minecraft.gui.setScreen(parent);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (mx < pvX0()) {
            scroll = Math.max(0, Math.min(Math.max(0, rows() - visible()), scroll - (int) Math.signum(v)));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    /** A picture file dropped on the library: asks for its width, then saves it as a pattern. */
    @Override
    public void onFilesDrop(List<Path> files) {
        if (!Chroma.available()) { problem("curvegen.patterns.drop_needs", null); return; }
        if (files.size() != 1) { problem("curvegen.patterns.drop_one", null); return; }
        String file = files.getFirst().getFileName().toString();
        ImageFile.Result read = ImageFile.read(files.getFirst());
        if (read.image() == null) {
            problem(read.problem() == ImageFile.Problem.TOO_LARGE ? "curvegen.patterns.drop_too_large" : "curvegen.patterns.drop_unreadable", file);
            return;
        }
        problem(null, null);
        int dot = file.lastIndexOf('.');
        String stem = (dot > 0 ? file.substring(0, dot) : file).trim();
        String name = PatternStore.unique(stem.isEmpty() ? tr("curvegen.image.default_name") : stem);
        minecraft.gui.setScreen(new ImagePatternScreen(this, read.image(), name, p -> {
            problem(p == null ? "curvegen.patterns.drop_clear" : null, file);
            scroll = 0;
            refilter();
        }));
    }

    /**
     * Shows the message {@code key} about the dropped {@code file}, or none for a null key. On the line a name over 80
     * pixels keeps its start and end with "…" between, so the rest of the message fits. The tooltip has it whole.
     */
    private void problem(String key, String file) {
        String shortFile = file;
        if (file != null && font.width(file) > 80) {
            String end = font.plainSubstrByWidth(file, 30, true);
            shortFile = font.plainSubstrByWidth(file, 80 - 30 - font.width("…")) + "…" + end;
        }
        // A message about no one file, such as Chroma Studio missing, takes no argument: a null one isn't allowed.
        dropProblem = key == null ? null : file == null ? tr(key) : tr(key, shortFile);
        dropProblemTip = key == null ? null : file == null ? tr(key) : tr(key, file);
    }

    private void rename(Saved s) {
        minecraft.gui.setScreen(new NameDialogScreen(this, tr("curvegen.patterns.rename_title"), s.name(), name -> {
            if (name.isBlank()) return new NameDialogScreen.Check(false, tr("curvegen.screen.rename"), tr("curvegen.capture.type_a_name"));
            Saved other = PatternStore.find(name);
            if (other != null && other != s) return new NameDialogScreen.Check(false, tr("curvegen.screen.rename"), tr("curvegen.patterns.name_used"));
            return new NameDialogScreen.Check(true, tr("curvegen.screen.rename"), null);
        }, name -> { PatternStore.rename(s, name); refilter(); }));
    }

    private void delete(Saved s) {
        minecraft.gui.setScreen(new ConfirmScreen(yes -> {
            if (yes) { PatternStore.delete(s); refilter(); }
            minecraft.gui.setScreen(this);
        }, Component.translatable("curvegen.screen.are_you_sure"),
                Component.translatable("curvegen.patterns.delete_confirm", s.name()),
                Component.translatable("curvegen.screen.delete"), CommonComponents.GUI_CANCEL));
    }

    /** Closes the menus and waits for the corners. Once the pattern is named it is chosen, and {@code reopen}'s screen opens. */
    private void capture() {
        Supplier<Screen> then = reopen;
        minecraft.gui.setScreen(null);
        Capture.start(p -> {
            choose(p);
            if (then != null) minecraft.gui.setScreen(then.get());
        });
    }

    /** Chooses a pattern from outside a screen, such as after a capture from the radial menu: one undo step on a hologram. */
    public static void choose(Pattern p) {
        Editor.edit(null, () -> use(S, p));
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
