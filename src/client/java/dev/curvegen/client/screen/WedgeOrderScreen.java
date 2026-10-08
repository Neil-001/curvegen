package dev.curvegen.client.screen;

import dev.curvegen.client.ModSettings;
import dev.curvegen.core.edit.Radial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Puts the wedges of the radial menu's two top-level menus in the player's order. */
public class WedgeOrderScreen extends ControlScreen {
    private static final int M = 6, GAP = 8, ROW = 22, TOP = 50;

    private final Screen parent;
    private List<String> start, edit;
    private record Label(int x, int y, String text, int color) {}
    private final List<Label> labels = new ArrayList<>();

    public WedgeOrderScreen(Screen parent) {
        super(Component.literal("Radial menu wedge order"));
        this.parent = parent;
        start = Radial.order(Radial.START, ModSettings.radialOrder);
        edit = Radial.order(Radial.EDIT, ModSettings.radialOrder);
    }

    @Override
    protected void init() {
        labels.clear();
        int colW = Math.min(200, (width - 2 * M - GAP) / 2), left = (width - 2 * colW - GAP) / 2, right = left + colW + GAP;
        column(left, colW, "Before a shape is out", start);
        column(right, colW, "While a shape is out", edit);

        int by = height - 26, pad = 16, resetW = tw("Default order") + pad, doneW = tw("Done") + pad + 8, end = right + colW;
        boolean custom = !ModSettings.radialOrder.isEmpty();
        Button reset = Button.builder(Component.literal("Default order"), _ -> {
            start = new ArrayList<>(Radial.START); edit = new ArrayList<>(Radial.EDIT);
            store();
        }).bounds(end - doneW - 4 - resetW, by, resetW, 20).build();
        reset.active = custom;
        reset.setTooltip(Tooltip.create(Component.literal(custom ? "Put every wedge back where it started." : "The wedges are already in their default order.")));
        addRenderableWidget(reset);
        addRenderableWidget(Button.builder(Component.literal("Done"), _ -> onClose()).bounds(end - doneW, by, doneW, 20).build());
    }

    private void column(int x, int w, String heading, List<String> ids) {
        labels.add(new Label(x, TOP - 12, heading, 0xFFFFFFFF));
        for (int k = 0; k < ids.size(); k++) {
            int y = TOP + k * ROW, row = k;
            labels.add(new Label(x + 4, y + 6, (k + 1) + "  " + Radial.name(ids.get(k)), 0xFFC8CED6));
            AbstractButton up = arrow(x + w - 30, y, 14, 20, true, () -> move(ids, row, -1));
            AbstractButton down = arrow(x + w - 14, y, 14, 20, false, () -> move(ids, row, 1));
            up.active = k > 0;
            down.active = k < ids.size() - 1;
            up.setTooltip(Tooltip.create(Component.literal(up.active ? "Move this wedge earlier." : "This wedge is already first.")));
            down.setTooltip(Tooltip.create(Component.literal(down.active ? "Move this wedge later." : "This wedge is already last.")));
            addRenderableWidget(up);
            addRenderableWidget(down);
        }
    }

    private void move(List<String> ids, int row, int by) {
        // The lists from Radial.order can be changed in place.
        Collections.swap(ids, row, row + by);
        store();
    }

    private void store() {
        ModSettings.radialOrder = Radial.merge(start, edit);
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        ctx.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        ctx.centeredText(font, "The first wedge is at the top and the rest follow clockwise.", width / 2, 21, 0xFF9AA5B3);
        for (Label l : labels) ctx.text(font, l.text, l.x, l.y, l.color);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(0, 0, width, height, 0x80101317);
    }

    @Override
    public void removed() {
        ModSettings.save();
        super.removed();
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }
}
