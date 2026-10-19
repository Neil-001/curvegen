package dev.curvegen.client.screen;

import static dev.curvegen.client.Lang.tr;

import dev.curvegen.client.ModSettings;
import dev.curvegen.core.edit.Radial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
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
        super(Component.translatable("curvegen.screen.radial_menu_wedge_order"));
        this.parent = parent;
        start = Radial.order(Radial.START, ModSettings.radialOrder);
        edit = Radial.order(Radial.EDIT, ModSettings.radialOrder);
    }

    @Override
    protected void init() {
        labels.clear();
        int colW = Math.min(200, (width - 2 * M - GAP) / 2), left = (width - 2 * colW - GAP) / 2, right = left + colW + GAP;
        column(left, colW, tr("curvegen.screen.before_a_shape_is_out"), start);
        column(right, colW, tr("curvegen.screen.while_a_shape_is_out"), edit);

        int by = height - 26, pad = 16, resetW = tw(tr("curvegen.screen.default_order")) + pad, doneW = tw(tr("curvegen.screen.done")) + pad + 8, end = right + colW;
        boolean custom = !ModSettings.radialOrder.isEmpty();
        Button reset = Button.builder(Component.translatable("curvegen.screen.default_order"), b -> {
            start = new ArrayList<>(Radial.START); edit = new ArrayList<>(Radial.EDIT);
            store();
        }).bounds(end - doneW - 4 - resetW, by, resetW, 20).build();
        reset.active = custom;
        reset.setTooltip(Tooltip.create(Component.translatable(custom ? "curvegen.screen.put_every_wedge_back_where_it_started" : "curvegen.screen.the_wedges_are_already_in_their_default_order")));
        addRenderableWidget(reset);
        addRenderableWidget(Button.builder(Component.translatable("curvegen.screen.done"), b -> onClose()).bounds(end - doneW, by, doneW, 20).build());
    }

    private void column(int x, int w, String heading, List<String> ids) {
        labels.add(new Label(x, TOP - 12, heading, 0xFFFFFFFF));
        for (int k = 0; k < ids.size(); k++) {
            int y = TOP + k * ROW, row = k;
            labels.add(new Label(x + 4, y + 6, (k + 1) + "  " + tr(Radial.name(ids.get(k))), 0xFFC8CED6));
            AbstractButton up = arrow(x + w - 30, y, 14, 20, true, () -> move(ids, row, -1));
            AbstractButton down = arrow(x + w - 14, y, 14, 20, false, () -> move(ids, row, 1));
            up.active = k > 0;
            down.active = k < ids.size() - 1;
            up.setTooltip(Tooltip.create(Component.translatable(up.active ? "curvegen.screen.move_this_wedge_earlier" : "curvegen.screen.this_wedge_is_already_first")));
            down.setTooltip(Tooltip.create(Component.translatable(down.active ? "curvegen.screen.move_this_wedge_later" : "curvegen.screen.this_wedge_is_already_last")));
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
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        ctx.drawCenteredString(font, tr("curvegen.screen.the_first_wedge_is_at_the_top_and_the"), width / 2, 21, 0xFF9AA5B3);
        for (Label l : labels) ctx.drawString(font, l.text, l.x, l.y, l.color);
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(0, 0, width, height, 0x80101317);
    }

    @Override
    public void removed() {
        ModSettings.save();
        super.removed();
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
