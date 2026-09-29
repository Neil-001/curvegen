package dev.curvegen.client.screen;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.ColorIndex;
import dev.curvegen.core.Pieces.Family;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Pick the block used for one piece type. */
public class BlockPickerScreen extends Screen {
    private enum Sort { NAME, COLOUR }

    private final Screen parent;
    private final Family family;
    private final Consumer<Block> onPick;
    private final List<Block> all;
    private List<Block> shown = new ArrayList<>();
    private String query = "";
    private static Sort sort = Sort.NAME;
    private int scroll;

    private static final int CELL = 20, M = 8;

    public BlockPickerScreen(Screen parent, Family family, Consumer<Block> onPick) {
        super(Text.literal("Choose " + BlockChoices.familyName(family).toLowerCase(Locale.ROOT)));
        this.parent = parent; this.family = family; this.onPick = onPick;
        this.all = BlockChoices.candidates(family);
    }

    /** Colour the list is sorted against: the colour you picked, or the current block's own colour. */
    private int reference() {
        return BlockChoices.pickedColor >= 0 ? BlockChoices.pickedColor : ColorIndex.of(BlockChoices.CHOICE.get(family));
    }

    @Override
    protected void init() {
        TextFieldWidget search = new TextFieldWidget(textRenderer, M, 24, 180, 20, Text.literal("Search"));
        search.setPlaceholder(Text.literal("Search blocks"));
        search.setText(query);
        search.setChangedListener(v -> { query = v; scroll = 0; refilter(); });
        addDrawableChild(search);
        setInitialFocus(search);
        addDrawableChild(CyclingButtonWidget.<Sort>builder(s -> Text.literal(s == Sort.NAME ? "Name" : "Closest colour"))
                .values(Sort.values()).initially(sort)
                .build(M + 186, 24, 140, 20, Text.literal("Sort"), (b, v) -> { sort = v; refilter(); }));
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close()).dimensions(width - M - 80, height - 26, 80, 20).build());
        refilter();
    }

    private void refilter() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        shown = new ArrayList<>();
        for (Block b : all)
            if (q.isEmpty() || b.getName().getString().toLowerCase(Locale.ROOT).contains(q)
                    || Registries.BLOCK.getId(b).getPath().contains(q)) shown.add(b);
        if (sort == Sort.COLOUR) {
            int ref = reference();
            shown.sort(Comparator.comparingDouble(b -> ColorIndex.distance(ColorIndex.of(b), ref)));
        }
    }

    private int cols() { return Math.max(1, (width - 2 * M) / CELL); }
    private int gridTop() { return 52; }
    private int gridBottom() { return height - 32; }
    private int visibleRows() { return Math.max(1, (gridBottom() - gridTop()) / CELL); }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawTextWithShadow(textRenderer, title, M, 8, 0xFFFFFF);
        String info = shown.size() + " of " + all.size() + " blocks";
        ctx.drawText(textRenderer, info, width - M - textRenderer.getWidth(info), 30, 0x9AA5B3, false);

        int cols = cols(), top = gridTop();
        Block current = BlockChoices.CHOICE.get(family);
        Block hovered = null;
        int maxScroll = Math.max(0, (shown.size() + cols - 1) / cols - visibleRows());
        scroll = Math.min(scroll, maxScroll);
        for (int n = scroll * cols; n < shown.size(); n++) {
            int r = n / cols - scroll, c = n % cols;
            if (r >= visibleRows()) break;
            int x = M + c * CELL, y = top + r * CELL;
            Block b = shown.get(n);
            int rgb = ColorIndex.of(b);
            ctx.fill(x, y, x + CELL - 2, y + CELL - 2, 0xFF000000 | rgb);
            ctx.fill(x + 1, y + 1, x + CELL - 3, y + CELL - 3, 0xC0202428);
            if (b == current) ctx.drawBorder(x - 1, y - 1, CELL, CELL, 0xFFFFB84D);
            boolean over = mx >= x && mx < x + CELL - 2 && my >= y && my < y + CELL - 2;
            if (over) { ctx.fill(x, y, x + CELL - 2, y + CELL - 2, 0x40FFFFFF); hovered = b; }
            ctx.drawItem(new ItemStack(b), x + 1, y + 1);
        }
        if (shown.isEmpty()) ctx.drawText(textRenderer, "No blocks match your search.", M, top + 4, 0x9AA5B3, false);
        if (maxScroll > 0) {
            int h = gridBottom() - top, bar = Math.max(10, h * visibleRows() / (maxScroll + visibleRows()));
            int by = top + (h - bar) * scroll / maxScroll;
            ctx.fill(width - 4, by, width - 2, by + bar, 0x80FFFFFF);
        }
        if (hovered != null)
            ctx.drawTooltip(textRenderer, Text.literal(hovered.getName().getString() + "  #" + String.format("%06X", ColorIndex.of(hovered))), mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int cols = cols(), c = (int) ((mx - M) / CELL), r = (int) ((my - gridTop()) / CELL);
        if (mx < M || c >= cols || my < gridTop() || r >= visibleRows()) return false;
        int n = (r + scroll) * cols + c;
        if (n >= 0 && n < shown.size()) { onPick.accept(shown.get(n)); close(); return true; }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, scroll - (int) Math.signum(v));
        return true;
    }

    @Override
    public void close() { client.setScreen(parent); }
}
