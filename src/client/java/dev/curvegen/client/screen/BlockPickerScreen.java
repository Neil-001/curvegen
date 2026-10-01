package dev.curvegen.client.screen;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.ColorIndex;
import dev.curvegen.client.Compat;
import dev.curvegen.core.Pieces.Family;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
//? if >=1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}

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
        super(Component.literal("Choose " + BlockChoices.familyName(family).toLowerCase(Locale.ROOT)));
        this.parent = parent; this.family = family; this.onPick = onPick;
        this.all = BlockChoices.candidates(family);
    }

    /** Colour the list is sorted against: the colour you picked, or the current block's own colour. */
    private int reference() {
        return BlockChoices.pickedColor >= 0 ? BlockChoices.pickedColor : ColorIndex.of(BlockChoices.CHOICE.get(family));
    }

    @Override
    protected void init() {
        EditBox search = new EditBox(font, M, 24, 180, 20, Component.literal("Search"));
        search.setHint(Component.literal("Search blocks"));
        search.setValue(query);
        search.setResponder(v -> { query = v; scroll = 0; refilter(); });
        addRenderableWidget(search);
        setInitialFocus(search);
        //? if >=1.21.11 {
        addRenderableWidget(CycleButton.<Sort>builder(s -> Component.literal(s == Sort.NAME ? "Name" : "Closest colour"), sort).withValues(Sort.values())
        //?} else
        //addRenderableWidget(CycleButton.<Sort>builder(s -> Component.literal(s == Sort.NAME ? "Name" : "Closest colour")).withValues(Sort.values()).withInitialValue(sort)
                .create(M + 186, 24, 140, 20, Component.literal("Sort"), (b, v) -> { sort = v; refilter(); }));
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width - M - 80, height - 26, 80, 20).build());
        refilter();
    }

    private void refilter() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        shown = new ArrayList<>();
        for (Block b : all)
            if (q.isEmpty() || b.getName().getString().toLowerCase(Locale.ROOT).contains(q)
                    || BuiltInRegistries.BLOCK.getKey(b).getPath().contains(q)) shown.add(b);
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
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawString(font, title, M, 8, 0xFFFFFFFF);
        String info = shown.size() + " of " + all.size() + " blocks";
        ctx.drawString(font, info, width - M - font.width(info), 30, 0xFF9AA5B3, false);

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
            if (b == current) Compat.outline(ctx, x - 1, y - 1, CELL, CELL, 0xFFFFB84D);
            boolean over = mx >= x && mx < x + CELL - 2 && my >= y && my < y + CELL - 2;
            if (over) { ctx.fill(x, y, x + CELL - 2, y + CELL - 2, 0x40FFFFFF); hovered = b; }
            ctx.renderItem(new ItemStack(b), x + 1, y + 1);
        }
        if (shown.isEmpty()) ctx.drawString(font, "No blocks match your search.", M, top + 4, 0xFF9AA5B3, false);
        if (maxScroll > 0) {
            int h = gridBottom() - top, bar = Math.max(10, h * visibleRows() / (maxScroll + visibleRows()));
            int by = top + (h - bar) * scroll / maxScroll;
            ctx.fill(width - 4, by, width - 2, by + bar, 0x80FFFFFF);
        }
        if (hovered != null)
            Compat.tooltip(ctx, font, Component.literal(hovered.getName().getString() + "  #" + String.format("%06X", ColorIndex.of(hovered))), mx, my);
    }

    @Override
    //? if >=1.21.9 {
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
    //?} else
    //public boolean mouseClicked(double mx, double my, int button) {
        //? if >=1.21.9 {
        if (super.mouseClicked(event, doubled)) return true;
        //?} else
        //if (super.mouseClicked(mx, my, button)) return true;
        int cols = cols(), c = (int) ((mx - M) / CELL), r = (int) ((my - gridTop()) / CELL);
        if (mx < M || c >= cols || my < gridTop() || r >= visibleRows()) return false;
        int n = (r + scroll) * cols + c;
        if (n >= 0 && n < shown.size()) { onPick.accept(shown.get(n)); onClose(); return true; }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, scroll - (int) Math.signum(v));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
