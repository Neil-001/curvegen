package dev.curvegen.client.screen;

import static dev.curvegen.client.Lang.tr;

import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.ModSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/** The mod's settings. Each change applies straight away and is saved when the screen closes. */
public class SettingsScreen extends ControlScreen {
    private static final int M = 6, ROW = 22, GAP = 8, FIELD_W = 62;

    private final Screen parent;
    private record Label(int x, int y, String text) {}
    private final List<Label> labels = new ArrayList<>();
    private int colW;

    /** {@code parent} is the screen to go back to, or null to return to the game. */
    public SettingsScreen(Screen parent) {
        super(Component.translatable("curvegen.screen.curve_generator_settings"));
        this.parent = parent;
    }

    private int row(int n) { return 30 + n * ROW; }

    @Override
    protected void init() {
        labels.clear();
        // Two columns: keys, the radial menu and the on-screen text on the left, handles and the hologram on the right.
        colW = Math.min(220, (width - 2 * M - GAP) / 2);
        int left = (width - 2 * colW - GAP) / 2, right = left + colW + GAP;

        number(left, 0, tr("curvegen.screen.hold_time_seconds"), tr("curvegen.screen.how_long_to_hold_a_move_key_before_the"),
                () -> ModSettings.holdSeconds, v -> ModSettings.holdSeconds = v, 0.5, ModSettings.HOLD_MIN, ModSettings.HOLD_MAX, 1, false);
        number(left, 1, tr("curvegen.screen.bar_delay_seconds"), tr("curvegen.screen.how_long_to_hold_a_move_key_before_its"),
                () -> ModSettings.barDelaySeconds, v -> ModSettings.barDelaySeconds = v, 0.1, 0, ModSettings.BAR_DELAY_MAX, 1, false);
        CycleButton<Boolean> radial = cycler(List.of(false, true), ModSettings.radialToggle, v -> Component.translatable(v ? "curvegen.screen.press" : "curvegen.screen.hold"),
                left, row(2), colW, tr("curvegen.screen.radial_menu"), v -> ModSettings.radialToggle = v);
        radial.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.hold_the_menu_stays_open_while_you_hold_its")));
        addRenderableWidget(radial);
        CycleButton<Boolean> ring = cycler(List.of(false, true), ModSettings.radialRound, v -> Component.translatable(v ? "curvegen.screen.round" : "curvegen.screen.stretched"),
                left, row(3), colW, tr("curvegen.screen.ring_shape"), v -> ModSettings.radialRound = v);
        ring.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.stretched_the_ring_in_the_middle_of_the_radial")));
        addRenderableWidget(ring);
        Button order = Button.builder(Component.translatable(ModSettings.radialOrder.isEmpty() ? "curvegen.screen.wedge_order_default" : "curvegen.screen.wedge_order_custom"),
                b -> minecraft.setScreen(new WedgeOrderScreen(this))).bounds(left, row(4), colW, 20).build();
        order.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.choose_where_each_wedge_of_the_radial_menu_goes")));
        addRenderableWidget(order);
        CycleButton<Boolean> hints = toggle(ModSettings.showKeyHints, left, row(5), colW, tr("curvegen.screen.key_hints"), v -> ModSettings.showKeyHints = v);
        hints.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.lists_a_shape_s_keys_on_screen_while_you")));
        addRenderableWidget(hints);
        CycleButton<Boolean> info = toggle(ModSettings.showShapeInfo, left, row(6), colW, tr("curvegen.screen.shape_info"), v -> ModSettings.showShapeInfo = v);
        info.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.shows_a_shape_s_kind_size_and_block_counts")));
        addRenderableWidget(info);

        number(right, 0, tr("curvegen.screen.handle_size_blocks"), tr("curvegen.screen.how_big_the_handles_you_drag_are"),
                () -> ModSettings.handleSize, v -> ModSettings.handleSize = v, 0.05, ModSettings.HANDLE_MIN, ModSettings.HANDLE_MAX, 1, false);
        number(right, 1, tr("curvegen.screen.pick_radius_blocks"), tr("curvegen.screen.how_close_to_a_handle_you_must_look_to"),
                () -> ModSettings.pickRadius, v -> ModSettings.pickRadius = v, 0.05, ModSettings.PICK_MIN, ModSettings.PICK_MAX, 1, false);
        number(right, 2, tr("curvegen.screen.hologram_opacity"), tr("curvegen.screen.how_solid_the_hologram_s_blocks_look"),
                () -> ModSettings.hologramOpacity, v -> ModSettings.hologramOpacity = v, 5, 10, 100, 100, true);
        number(right, 3, tr("curvegen.screen.hologram_block_limit"), tr("curvegen.screen.shapes_with_more_blocks_than_this_show_only_their"),
                () -> ModSettings.hologramBlockLimit, v -> ModSettings.hologramBlockLimit = (int) v, 5000, 0, ModSettings.BLOCK_LIMIT_MAX, 1, true);
        CycleButton<Boolean> invert = toggle(ModSettings.invertDragScroll, right, row(4), colW, tr("curvegen.screen.invert_drag_scroll"), v -> ModSettings.invertDragScroll = v);
        invert.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.reverses_which_way_scrolling_moves_a_handle_while_you")));
        addRenderableWidget(invert);

        // Bottom bar: each button as wide as its text needs.
        int by = height - 26, pad = 16;
        int keysW = tw(tr("curvegen.screen.key_bindings")) + pad, resetW = tw(tr("curvegen.screen.reset_to_defaults")) + pad, doneW = tw(tr("curvegen.screen.done")) + pad + 8;
        Button keys = Button.builder(Component.translatable("curvegen.screen.key_bindings"), b -> openKeys()).bounds(left, by, keysW, 20).build();
        keys.setTooltip(Tooltip.create(Component.translatable("curvegen.screen.change_the_mod_s_keys_in_the_game_s")));
        addRenderableWidget(keys);
        int end = right + colW;
        addRenderableWidget(Button.builder(Component.translatable("curvegen.screen.reset_to_defaults"), b -> {
            ModSettings.reset();
            rebuildWidgets();
        }).bounds(end - doneW - 4 - resetW, by, resetW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("curvegen.screen.done"), b -> onClose()).bounds(end - doneW, by, doneW, 20).build());
    }

    /**
     * A labelled number field with arrows. The field shows the setting times {@code scale}, so an opacity of 0.45
     * reads as 45. Typed numbers are kept within the limits, and anything that isn't a number changes nothing.
     */
    private void number(int x, int n, String label, String tip, DoubleSupplier get, DoubleConsumer set,
                        double step, double min, double max, double scale, boolean integer) {
        labels.add(new Label(x, row(n) + 6, label));
        DoubleSupplier shown = () -> get.getAsDouble() * scale;
        String value = integer ? String.valueOf(Math.round(shown.getAsDouble())) : coord(shown.getAsDouble());
        spin(x + colW - FIELD_W, row(n), FIELD_W, 20, value, v -> {
            try {
                double d = Double.parseDouble(v.trim());
                if (Double.isFinite(d)) set.accept(Math.max(min, Math.min(max, integer ? Math.rint(d) : d)) / scale);
            } catch (NumberFormatException ignored) { }
        }, shown, step, min, max, () -> true, integer).setTooltip(Tooltip.create(Component.literal(tip)));
    }

    /**
     * Opens the game's key binds screen. It has no way to show one category, so this scrolls its list to the mod's
     * heading. 1.21.1's headings hold no widget with their text, so this finds the heading's row the way the list
     * builds it: every key, sorted, with a heading before each new category, every row 20 pixels high.
     */
    private void openKeys() {
        KeyBindsScreen keys = new KeyBindsScreen(this, minecraft.options);
        minecraft.setScreen(keys);
        KeyMapping[] mappings = minecraft.options.keyMappings.clone();
        Arrays.sort(mappings);
        int row = 0;
        String category = null;
        for (KeyMapping k : mappings) {
            if (!k.getCategory().equals(category)) {
                category = k.getCategory();
                if (category.equals(CurveGenClient.CATEGORY)) break;
                row++;
            }
            row++;
        }
        for (GuiEventListener child : keys.children())
            if (child instanceof KeyBindsList list) list.setScrollAmount(row * 20);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredString(font, title, width / 2, 11, 0xFFFFFFFF);
        for (Label l : labels) ctx.drawString(font, l.text, l.x, l.y, 0xFFC8CED6);
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
