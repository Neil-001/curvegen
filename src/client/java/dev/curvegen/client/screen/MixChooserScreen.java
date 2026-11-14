package dev.curvegen.client.screen;

import static dev.curvegen.client.Lang.tr;

import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.MixSources;
import dev.curvegen.client.PaintBlocks;
import dev.curvegen.client.PatternStore;
import dev.curvegen.core.paint.Mix;
import dev.curvegen.core.paint.MixLayout;
import dev.curvegen.core.paint.Paint.Arrange;
import dev.curvegen.core.paint.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/**
 * Chooses the blocks a mix paints with, from the saved patterns and Chroma Studio's palettes in one list, under a
 * heading each, with the current mix first when it is in neither. A pattern gives its blocks, each weighted by how
 * often it appears, and its empty cells are left out. Each row shows the blocks scattered in their shares; hovering
 * one lists them. Choosing one hands it to {@code onPick} and goes back.
 */
public class MixChooserScreen extends Screen {
    private static final int M = 8, ROW = 22, SWATCH = 18, TOP = 26;
    private final Screen parent;
    private final Consumer<Mix> onPick;
    /** A heading, a note or a mix to choose. Only a row with a mix can be chosen. */
    private record Row(String text, boolean heading, Mix mix) {
        static Row of(Mix m) { return new Row(null, false, m); }
    }
    private final List<Row> rows = new ArrayList<>();
    private int scroll;

    public MixChooserScreen(Screen parent, Consumer<Mix> onPick) {
        super(Component.translatable("curvegen.paint.mix_title"));
        this.parent = parent; this.onPick = onPick;
        List<Mix> fromPatterns = new ArrayList<>();
        List<Pattern> patterns = new ArrayList<>(MixSources.patterns.get());
        // The pattern being painted with may be one of the player's own, loaded from a preset or the paint file.
        Pattern own = CurveGenClient.SETTINGS.paint.pattern;
        if (!patterns.contains(own)) patterns.addFirst(own);
        for (Pattern p : patterns) {
            Mix m = Mix.of(p);
            if (m != null && !fromPatterns.contains(m)) fromPatterns.add(m);
        }
        List<Mix> palettes = MixSources.palettes.get();
        Mix current = CurveGenClient.SETTINGS.paint.mix;
        if (!fromPatterns.contains(current) && (palettes == null || !palettes.contains(current))) rows.add(Row.of(current));
        rows.add(new Row(tr("curvegen.paint.mix_patterns"), true, null));
        for (Mix m : fromPatterns) rows.add(Row.of(m));
        rows.add(new Row(tr("curvegen.paint.mix_palettes"), true, null));
        if (palettes == null || palettes.isEmpty())
            rows.add(new Row(tr(palettes == null ? "curvegen.paint.mix_palettes_none" : "curvegen.paint.mix_palettes_empty"), false, null));
        else for (Mix m : palettes) rows.add(Row.of(m));
    }

    /** A mix's name as the player sees it: one taken from a bundled pattern is translated. */
    static String name(Mix m) { return PatternStore.shownName(m.name); }

    /** Draws a mix in a {@code size}-pixel square: its blocks' colours scattered two pixels at a time, in their shares. */
    static void drawSwatch(GuiGraphics ctx, Mix m, int x, int y, int size) {
        ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0x40FFFFFF);
        int[] colour = new int[m.paletteSize()];
        for (int k = 0; k < colour.length; k++) colour[k] = PatternLibraryScreen.colour(m.cell(k)) | 0xFF000000;   // black for a cell with neither
        MixLayout scatter = new MixLayout(m, Arrange.SCATTER, MixLayout.MIN_PATCH, 0);
        for (int py = 0; py < size; py += 2)
            for (int px = 0; px < size; px += 2)
                ctx.fill(x + px, y + py, x + Math.min(size, px + 2), y + Math.min(size, py + 2), colour[scatter.at(px, py, 0)]);
    }

    /** The mix's blocks with their shares, most first, for its tooltip. */
    private static String tip(Mix m) {
        long total = 0;
        List<Integer> order = new ArrayList<>();
        for (int k = 0; k < m.paletteSize(); k++) { total += m.count(k); order.add(k); }
        order.sort((a, b) -> Integer.compare(m.count(b), m.count(a)));
        StringBuilder s = new StringBuilder(name(m));
        for (int k : order) {
            Pattern.Cell c = m.cell(k);
            Block b = c.block() == null ? null : PaintBlocks.block(c.block());
            String what = b != null ? b.getName().getString() : c.block() != null ? c.block() : String.format(java.util.Locale.ROOT, "#%06x", c.rgb());
            s.append('\n').append(tr("curvegen.paint.mix_share", what, Math.round(100.0 * m.count(k) / total)));
        }
        return s.toString();
    }

    private int listW() { return Math.min(320, width - 2 * M); }
    private int listX() { return (width - listW()) / 2; }
    private int listBottom() { return height - 32; }
    private int visible() { return Math.max(1, (listBottom() - TOP) / ROW); }

    @Override
    protected void init() {
        int w = Math.max(80, font.width(tr("curvegen.screen.cancel")) + 24);
        addRenderableWidget(Button.builder(Component.translatable("curvegen.screen.cancel"), b -> onClose()).bounds(width - M - w, height - 26, w, 20).build());
        scroll = Math.max(0, Math.min(scroll, rows.size() - visible()));
    }

    private int rowAt(double mx, double my) {
        if (mx < listX() || mx >= listX() + listW() || my < TOP || my >= TOP + visible() * ROW) return -1;
        int i = scroll + (int) ((my - TOP) / ROW);
        return i < rows.size() ? i : -1;
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawString(font, title, listX(), 10, 0xFFFFFFFF);
        int x0 = listX(), x1 = x0 + listW(), hover = rowAt(mx, my);
        ctx.fill(x0 - 1, TOP - 1, x1 + 1, listBottom() + 1, 0x80000000);
        Mix current = CurveGenClient.SETTINGS.paint.mix;
        for (int i = scroll; i < rows.size() && i < scroll + visible(); i++) {
            Row row = rows.get(i);
            int y = TOP + (i - scroll) * ROW;
            if (row.mix == null) {
                String t = font.plainSubstrByWidth(row.text, listW() - 10 - (row.heading ? 0 : SWATCH + 5));
                ctx.drawString(font, t, x0 + (row.heading ? 4 : SWATCH + 9), y + 7, row.heading ? 0xFFC8CED6 : 0xFF808A96, false);
                continue;
            }
            Mix m = row.mix;
            if (m.equals(current)) ctx.fill(x0, y, x1, y + ROW, 0x603F7BE0);
            else if (i == hover) ctx.fill(x0, y, x1, y + ROW, 0x20FFFFFF);
            drawSwatch(ctx, m, x0 + 3, y + (ROW - SWATCH) / 2, SWATCH);
            String n = name(m), count = m.paletteSize() == 1 ? tr("curvegen.paint.mix_count_one") : tr("curvegen.paint.mix_count", m.paletteSize());
            int sw = font.width(count), room = listW() - SWATCH - 14 - sw - 8;
            if (font.width(n) > room) n = font.plainSubstrByWidth(n, room - font.width("…")) + "…";
            ctx.drawString(font, n, x0 + SWATCH + 9, y + 7, 0xFFE4E9EF, false);
            ctx.drawString(font, count, x1 - 5 - sw, y + 7, 0xFF9AA5B3, false);
        }
        if (rows.size() > visible()) {
            int h = visible() * ROW, bar = Math.max(8, h * visible() / rows.size());
            int by = TOP + (h - bar) * scroll / (rows.size() - visible());
            ctx.fill(x1 + 2, by, x1 + 4, by + bar, 0xA0FFFFFF);
        }
        Row over = hover >= 0 ? rows.get(hover) : null;
        String tip = over == null || over.heading ? null : over.mix != null ? tip(over.mix) : over.text;
        if (tip != null) ctx.setTooltipForNextFrame(font.split(Component.literal(tip), 220), mx, my);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        int row = rowAt(event.x(), event.y());
        if (row < 0 || rows.get(row).mix == null) return false;
        onPick.accept(rows.get(row).mix);
        onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visible()), scroll - (int) Math.signum(v)));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }
}
