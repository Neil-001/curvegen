package dev.curvegen.client.screen;

import static dev.curvegen.client.Lang.tr;

import dev.curvegen.client.ColorIndex;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.PaintBlocks;
import dev.curvegen.client.chroma.Chroma;
import dev.curvegen.core.paint.Gradient;
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
 * Chooses a gradient saved in Chroma Studio. The current one comes first when Chroma Studio no longer has it, as
 * after loading a preset whose gradient was since deleted there. Each row shows the steps' blocks from start to end;
 * hovering one lists them. Choosing one hands a copy of it to {@code onPick} and goes back.
 */
public class GradientChooserScreen extends Screen {
    private static final int M = 8, ROW = 22, SWATCH_W = 48, SWATCH_H = 16, TOP = 26;
    private final Screen parent;
    private final Consumer<Gradient> onPick;
    private final List<Gradient> rows = new ArrayList<>();
    /** What the list says when there is nothing to choose, or null. */
    private final String empty;
    private int scroll;

    public GradientChooserScreen(Screen parent, Consumer<Gradient> onPick) {
        super(Component.translatable("curvegen.paint.gradient_title"));
        this.parent = parent; this.onPick = onPick;
        List<Gradient> saved = Chroma.gradients();
        Gradient current = CurveGenClient.SETTINGS.paint.gradient;
        if (current != null && !saved.contains(current)) rows.add(current);
        rows.addAll(saved);
        empty = rows.isEmpty() ? tr("curvegen.paint.gradient_none") : null;
    }

    private static int colour(Gradient g, int step) {
        String id = g.cell(step).block();
        Block b = id == null ? null : PaintBlocks.block(id);
        return 0xFF000000 | (b != null ? ColorIndex.of(b) : g.cell(step).rgb());
    }

    /** Draws a gradient's steps side by side, first on the left, in a {@code w} by {@code h} box. */
    static void drawSwatch(GuiGraphicsExtractor ctx, Gradient g, int x, int y, int w, int h) {
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x40FFFFFF);
        int n = g.paletteSize();
        for (int k = 0; k < n; k++) ctx.fill(x + k * w / n, y, x + (k + 1) * w / n, y + h, colour(g, k));
    }

    /**
     * The gradient's steps' blocks, for its tooltip. A block painting can't place, which Chroma Studio's own filter may
     * let a gradient hold, is marked, and a line after the list says those take the closest colour.
     */
    private static String tip(Gradient g) {
        StringBuilder s = new StringBuilder(g.name).append('\n').append(tr("curvegen.paint.gradient_steps", g.paletteSize()));
        boolean any = false;
        for (int k = 0; k < g.paletteSize(); k++) {
            String id = g.cell(k).block();
            Block b = PaintBlocks.block(id);
            s.append('\n').append(b != null ? b.getName().getString() : id);
            if (g.steps().get(k).byHand()) s.append(' ').append(tr("curvegen.paint.gradient_by_hand"));
            if (!PaintBlocks.placeable(id)) { s.append(' ').append(tr("curvegen.paint.gradient_unplaceable")); any = true; }
        }
        if (any) s.append('\n').append('\n').append(tr("curvegen.paint.gradient_unplaceable_tip"));
        return s.toString();
    }

    private int listW() { return Math.min(320, width - 2 * M); }
    private int listX() { return (width - listW()) / 2; }
    private int listBottom() { return height - 32; }
    private int visible() { return Math.max(1, (listBottom() - TOP) / ROW); }

    @Override
    protected void init() {
        int w = Math.max(80, font.width(tr("curvegen.screen.cancel")) + 24);
        addRenderableWidget(Button.builder(Component.translatable("curvegen.screen.cancel"), _ -> onClose()).bounds(width - M - w, height - 26, w, 20).build());
        scroll = Math.max(0, Math.min(scroll, rows.size() - visible()));
    }

    private int rowAt(double mx, double my) {
        if (mx < listX() || mx >= listX() + listW() || my < TOP || my >= TOP + visible() * ROW) return -1;
        int i = scroll + (int) ((my - TOP) / ROW);
        return i < rows.size() ? i : -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        super.extractRenderState(ctx, mx, my, delta);
        ctx.text(font, title, listX(), 10, 0xFFFFFFFF);
        int x0 = listX(), x1 = x0 + listW(), hover = rowAt(mx, my);
        ctx.fill(x0 - 1, TOP - 1, x1 + 1, listBottom() + 1, 0x80000000);
        if (empty != null) {
            int ly = TOP + 7;
            for (var line : font.split(Component.literal(empty), listW() - 8)) { ctx.text(font, line, x0 + 4, ly, 0xFF9AA5B3, false); ly += 10; }
        }
        Gradient current = CurveGenClient.SETTINGS.paint.gradient;
        for (int i = scroll; i < rows.size() && i < scroll + visible(); i++) {
            Gradient g = rows.get(i);
            int y = TOP + (i - scroll) * ROW;
            if (g.equals(current)) ctx.fill(x0, y, x1, y + ROW, 0x603F7BE0);
            else if (i == hover) ctx.fill(x0, y, x1, y + ROW, 0x20FFFFFF);
            drawSwatch(ctx, g, x0 + 3, y + (ROW - SWATCH_H) / 2, SWATCH_W, SWATCH_H);
            String n = g.name, count = tr("curvegen.paint.gradient_steps", g.paletteSize());
            int sw = font.width(count), room = listW() - SWATCH_W - 14 - sw - 8;
            if (font.width(n) > room) n = font.plainSubstrByWidth(n, room - font.width("…")) + "…";
            ctx.text(font, n, x0 + SWATCH_W + 9, y + 7, 0xFFE4E9EF, false);
            ctx.text(font, count, x1 - 5 - sw, y + 7, 0xFF9AA5B3, false);
        }
        if (rows.size() > visible()) {
            int h = visible() * ROW, bar = Math.max(8, h * visible() / rows.size());
            int by = TOP + (h - bar) * scroll / (rows.size() - visible());
            ctx.fill(x1 + 2, by, x1 + 4, by + bar, 0xA0FFFFFF);
        }
        if (hover >= 0) ctx.setTooltipForNextFrame(font.split(Component.literal(tip(rows.get(hover))), 220), mx, my);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        int row = rowAt(event.x(), event.y());
        if (row < 0) return false;
        onPick.accept(rows.get(row));
        onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visible()), scroll - (int) Math.signum(v)));
        return true;
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }
}
