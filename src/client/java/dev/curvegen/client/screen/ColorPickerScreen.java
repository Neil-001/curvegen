package dev.curvegen.client.screen;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.ColorIndex;
import dev.curvegen.core.Pieces.Family;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Choose a colour; every piece type then uses the block whose texture is closest to it. */
public class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final IntConsumer onApply;
    private float hue, sat, val;
    private int dragging; // 0 none, 1 square, 2 hue bar
    private EditBox hex;
    private boolean updatingHex;
    private final Map<Family, Block> preview = new EnumMap<>(Family.class);

    private static final int SQ = 120;

    public ColorPickerScreen(Screen parent, int rgb, IntConsumer onApply) {
        super(Component.literal("Match blocks to a colour"));
        this.parent = parent; this.onApply = onApply;
        float[] hsv = rgbToHsv(rgb);
        hue = hsv[0]; sat = hsv[1]; val = hsv[2];
    }

    private int sqX() { return width / 2 - 170; }
    private int sqY() { return 36; }
    private int hueX() { return sqX() + SQ + 8; }

    @Override
    protected void init() {
        hex = new EditBox(font, sqX(), sqY() + SQ + 10, 80, 20, Component.literal("Hex colour"));
        hex.setMaxLength(7);
        hex.setResponder(v -> {
            if (updatingHex) return;
            String h = v.startsWith("#") ? v.substring(1) : v;
            if (h.length() == 6) try {
                float[] hsv = rgbToHsv(Integer.parseInt(h, 16));
                hue = hsv[0]; sat = hsv[1]; val = hsv[2];
                refreshPreview();
            } catch (NumberFormatException ignored) { }
        });
        addRenderableWidget(hex);
        syncHex();
        int by = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Use these blocks"), b -> { onApply.accept(rgb()); onClose(); })
                .bounds(width / 2 - 124, by, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width / 2 + 4, by, 120, 20).build());
        refreshPreview();
    }

    private int rgb() { return hsvToRgb(hue, sat, val); }

    private void syncHex() {
        updatingHex = true;
        hex.setValue(String.format(Locale.ROOT, "#%06X", rgb()));
        updatingHex = false;
    }

    private void refreshPreview() {
        int c = rgb();
        for (Family f : BlockChoices.FAMILIES) preview.put(f, BlockChoices.closest(f, c));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        super.extractRenderState(ctx, mx, my, delta);
        ctx.text(font, title, sqX(), 16, 0xFFFFFFFF);
        int x0 = sqX(), y0 = sqY();
        // saturation → x, value → y: one vertical gradient per column
        for (int i = 0; i < SQ; i++) {
            int topC = 0xFF000000 | hsvToRgb(hue, i / (float) (SQ - 1), 1f);
            ctx.fillGradient(x0 + i, y0, x0 + i + 1, y0 + SQ, topC, 0xFF000000);
        }
        int hx = hueX();
        for (int j = 0; j < SQ; j++) ctx.fill(hx, y0 + j, hx + 12, y0 + j + 1, 0xFF000000 | hsvToRgb(j / (float) SQ, 1f, 1f));
        int px = x0 + Math.round(sat * (SQ - 1)), py = y0 + Math.round((1 - val) * (SQ - 1));
        ctx.outline(px - 3, py - 3, 7, 7, 0xFFFFFFFF);
        int hy = y0 + Math.round(hue * SQ);
        ctx.fill(hx - 2, hy - 1, hx + 14, hy + 1, 0xFFFFFFFF);
        ctx.fill(x0 + 86, y0 + SQ + 10, x0 + 106, y0 + SQ + 30, 0xFF000000 | rgb());

        int lx = hx + 30, ly = y0;
        ctx.text(font, "Closest blocks", lx, ly, 0xFFC8CED6, false);
        ly += 14;
        for (Family f : BlockChoices.FAMILIES) {
            Block b = preview.get(f);
            if (b == null) continue;
            ctx.fill(lx, ly, lx + 18, ly + 18, 0xFF000000 | ColorIndex.of(b));
            ctx.item(new ItemStack(b), lx + 1, ly + 1);
            ctx.text(font, BlockChoices.familyName(f), lx + 24, ly + 1, 0xFF9AA5B3, false);
            ctx.text(font, font.plainSubstrByWidth(b.getName().getString(), width - lx - 30), lx + 24, ly + 10, 0xFFFFFFFF, false);
            ly += 22;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        if (super.mouseClicked(event, doubled)) return true;
        if (inRect(mx, my, sqX(), sqY(), SQ, SQ)) { dragging = 1; pick(mx, my); return true; }
        if (inRect(mx, my, hueX() - 2, sqY(), 16, SQ)) { dragging = 2; pick(mx, my); return true; }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double mx = event.x(), my = event.y();
        if (dragging != 0) { pick(mx, my); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != 0) { dragging = 0; refreshPreview(); }
        return super.mouseReleased(event);
    }

    private void pick(double mx, double my) {
        if (dragging == 1) {
            sat = clamp01((float) (mx - sqX()) / (SQ - 1));
            val = 1 - clamp01((float) (my - sqY()) / (SQ - 1));
        } else if (dragging == 2) {
            hue = Math.min(0.999f, clamp01((float) (my - sqY()) / SQ));
        }
        syncHex();
        refreshPreview();
    }

    private static boolean inRect(double x, double y, int rx, int ry, int w, int h) { return x >= rx && x < rx + w && y >= ry && y < ry + h; }
    private static float clamp01(float v) { return Math.max(0, Math.min(1, v)); }

    static int hsvToRgb(float h, float s, float v) {
        float r, g, b;
        int i = (int) Math.floor(h * 6);
        float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        switch (((i % 6) + 6) % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min, h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6;
        else if (max == g) h = (b - r) / d + 2;
        else h = (r - g) / d + 4;
        h /= 6;
        if (h < 0) h += 1;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
