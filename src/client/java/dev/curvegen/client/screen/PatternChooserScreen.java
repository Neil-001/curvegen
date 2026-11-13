package dev.curvegen.client.screen;

import static dev.curvegen.client.Lang.tr;

import dev.curvegen.client.ColorIndex;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.PaintBlocks;
import dev.curvegen.core.paint.Pattern;
import dev.curvegen.core.paint.Patterns;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/**
 * Chooses the pattern to paint with: the bundled ones, and the current one when it isn't among them. Each row shows
 * the pattern's own colours and its name. Choosing one hands it to {@code onPick} and goes back.
 */
public class PatternChooserScreen extends Screen {
    private static final int M = 8, ROW = 22, SWATCH = 18, TOP = 26;
    private final Screen parent;
    private final Consumer<Pattern> onPick;
    private final List<Pattern> shown = new ArrayList<>();
    private int scroll;

    public PatternChooserScreen(Screen parent, Consumer<Pattern> onPick) {
        super(Component.translatable("curvegen.paint.choose_title"));
        this.parent = parent; this.onPick = onPick;
        Pattern current = CurveGenClient.SETTINGS.paint.pattern;
        if (!Patterns.BUNDLED.contains(current)) shown.add(current);
        shown.addAll(Patterns.BUNDLED);
    }

    /** A pattern's name as the player sees it: a bundled pattern's is translated. */
    static String name(Pattern p) { return p.name.startsWith(Pattern.BUNDLED) ? tr(p.name) : p.name; }

    /** What a pattern suits, for its tooltip, or null. */
    private static String tip(Pattern p) { return p.equals(Patterns.TWO_LANE_ROAD) ? tr("curvegen.paint.two_lane_road_tip") : null; }

    /**
     * Draws a pattern in a {@code size}-pixel square, top row at the top, from its cells' block colours or their own
     * colours. Empty cells are left dark.
     */
    static void drawSwatch(GuiGraphicsExtractor ctx, Pattern p, int x, int y, int size) {
        ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0x40FFFFFF);
        ctx.fill(x, y, x + size, y + size, PreviewTexture.BG);
        int[] colour = new int[p.paletteSize()];
        for (int k = 0; k < colour.length; k++) {
            Pattern.Cell c = p.cell(k);
            Block b = c.block() == null ? null : PaintBlocks.block(c.block());
            colour[k] = b != null ? 0xFF000000 | ColorIndex.of(b) : c.rgb() >= 0 ? 0xFF000000 | c.rgb() : 0;
        }
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

    private int listW() { return Math.min(320, width - 2 * M); }
    private int listX() { return (width - listW()) / 2; }
    private int listBottom() { return height - 32; }
    private int visible() { return Math.max(1, (listBottom() - TOP) / ROW); }

    @Override
    protected void init() {
        int w = Math.max(80, font.width(tr("curvegen.screen.cancel")) + 24);
        addRenderableWidget(Button.builder(Component.translatable("curvegen.screen.cancel"), _ -> onClose()).bounds(width - M - w, height - 26, w, 20).build());
        scroll = Math.max(0, Math.min(scroll, shown.size() - visible()));
    }

    private int rowAt(double mx, double my) {
        if (mx < listX() || mx >= listX() + listW() || my < TOP || my >= TOP + visible() * ROW) return -1;
        int i = scroll + (int) ((my - TOP) / ROW);
        return i < shown.size() ? i : -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        super.extractRenderState(ctx, mx, my, delta);
        ctx.text(font, title, listX(), 10, 0xFFFFFFFF);
        int x0 = listX(), x1 = x0 + listW(), hover = rowAt(mx, my);
        ctx.fill(x0 - 1, TOP - 1, x1 + 1, listBottom() + 1, 0x80000000);
        Pattern current = CurveGenClient.SETTINGS.paint.pattern;
        for (int i = scroll; i < shown.size() && i < scroll + visible(); i++) {
            Pattern p = shown.get(i);
            int y = TOP + (i - scroll) * ROW;
            if (p.equals(current)) ctx.fill(x0, y, x1, y + ROW, 0x603F7BE0);
            else if (i == hover) ctx.fill(x0, y, x1, y + ROW, 0x20FFFFFF);
            drawSwatch(ctx, p, x0 + 3, y + (ROW - SWATCH) / 2, SWATCH);
            String n = name(p), size = p.width + "×" + p.height;
            int sw = font.width(size), room = listW() - SWATCH - 14 - sw - 8;
            if (font.width(n) > room) n = font.plainSubstrByWidth(n, room - font.width("…")) + "…";
            ctx.text(font, n, x0 + SWATCH + 9, y + 7, 0xFFE4E9EF, false);
            ctx.text(font, size, x1 - 5 - sw, y + 7, 0xFF9AA5B3, false);
        }
        if (shown.size() > visible()) {
            int h = visible() * ROW, bar = Math.max(8, h * visible() / shown.size());
            int by = TOP + (h - bar) * scroll / (shown.size() - visible());
            ctx.fill(x1 + 2, by, x1 + 4, by + bar, 0xA0FFFFFF);
        }
        String tip = hover >= 0 ? tip(shown.get(hover)) : null;
        if (tip != null) ctx.setTooltipForNextFrame(font.split(Component.literal(tip), 200), mx, my);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        int row = rowAt(event.x(), event.y());
        if (row < 0) return false;
        onPick.accept(shown.get(row));
        onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, Math.min(Math.max(0, shown.size() - visible()), scroll - (int) Math.signum(v)));
        return true;
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }
}
