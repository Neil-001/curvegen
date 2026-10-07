package dev.curvegen.client.screen;

import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.ModSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
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
        super(Component.literal("Curve Generator settings"));
        this.parent = parent;
    }

    private int row(int n) { return 30 + n * ROW; }

    @Override
    protected void init() {
        labels.clear();
        // Two columns: keys and the radial menu on the left, handles and the hologram on the right.
        colW = Math.min(220, (width - 2 * M - GAP) / 2);
        int left = (width - 2 * colW - GAP) / 2, right = left + colW + GAP;

        number(left, 0, "Hold time (seconds)", "How long to hold a move key before the number box opens.",
                () -> ModSettings.holdSeconds, v -> ModSettings.holdSeconds = v, 0.5, ModSettings.HOLD_MIN, ModSettings.HOLD_MAX, 1, false);
        number(left, 1, "Bar delay (seconds)", "How long to hold a move key before its progress bar shows.",
                () -> ModSettings.barDelaySeconds, v -> ModSettings.barDelaySeconds = v, 0.1, 0, ModSettings.BAR_DELAY_MAX, 1, false);
        CycleButton<Boolean> radial = cycler(List.of(false, true), ModSettings.radialToggle, v -> Component.literal(v ? "Press" : "Hold"),
                left, row(2), colW, "Radial menu", v -> ModSettings.radialToggle = v);
        radial.setTooltip(Tooltip.create(Component.literal(
                "Hold: the menu stays open while you hold its key. Press: one press opens it and another closes it.")));
        addRenderableWidget(radial);
        Button order = Button.builder(Component.literal("Wedge order: " + (ModSettings.radialOrder.isEmpty() ? "Default" : "Custom") + "…"),
                b -> minecraft.gui.setScreen(new WedgeOrderScreen(this))).bounds(left, row(3), colW, 20).build();
        order.setTooltip(Tooltip.create(Component.literal("Choose where each wedge of the radial menu goes.")));
        addRenderableWidget(order);

        number(right, 0, "Handle size (blocks)", "How big the handles you drag are.",
                () -> ModSettings.handleSize, v -> ModSettings.handleSize = v, 0.05, ModSettings.HANDLE_MIN, ModSettings.HANDLE_MAX, 1, false);
        number(right, 1, "Pick radius (blocks)", "How close to a handle you must look to grab it.",
                () -> ModSettings.pickRadius, v -> ModSettings.pickRadius = v, 0.05, ModSettings.PICK_MIN, ModSettings.PICK_MAX, 1, false);
        number(right, 2, "Hologram opacity (%)", "How solid the hologram's blocks look.",
                () -> ModSettings.hologramOpacity, v -> ModSettings.hologramOpacity = v, 5, 10, 100, 100, true);
        number(right, 3, "Hologram block limit", "Shapes with more blocks than this show only their outline, to keep the game smooth.",
                () -> ModSettings.hologramBlockLimit, v -> ModSettings.hologramBlockLimit = (int) v, 5000, 0, ModSettings.BLOCK_LIMIT_MAX, 1, true);
        CycleButton<Boolean> invert = toggle(ModSettings.invertDragScroll, right, row(4), colW, "Invert drag scroll", v -> ModSettings.invertDragScroll = v);
        invert.setTooltip(Tooltip.create(Component.literal("Reverses which way scrolling moves a handle while you drag it.")));
        addRenderableWidget(invert);

        // Bottom bar: each button as wide as its text needs.
        int by = height - 26, pad = 16;
        int keysW = tw("Key bindings…") + pad, resetW = tw("Reset to defaults") + pad, doneW = tw("Done") + pad + 8;
        Button keys = Button.builder(Component.literal("Key bindings…"), b -> openKeys()).bounds(left, by, keysW, 20).build();
        keys.setTooltip(Tooltip.create(Component.literal("Change the mod's keys in the game's key binds screen.")));
        addRenderableWidget(keys);
        int end = right + colW;
        addRenderableWidget(Button.builder(Component.literal("Reset to defaults"), b -> {
            ModSettings.reset();
            rebuildWidgets();
        }).bounds(end - doneW - 4 - resetW, by, resetW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(end - doneW, by, doneW, 20).build());
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

    /** Opens the game's key binds screen. It has no way to show one category, so this scrolls its list to the mod's heading. */
    private void openKeys() {
        KeyBindsScreen keys = new KeyBindsScreen(this, minecraft.options);
        minecraft.gui.setScreen(keys);
        String heading = CurveGenClient.CATEGORY.label().getString();
        for (GuiEventListener child : keys.children()) {
            if (!(child instanceof KeyBindsList list)) continue;
            int y = 0;
            for (KeyBindsList.Entry e : list.children()) {
                if (e instanceof KeyBindsList.CategoryEntry && e.children().stream()
                        .anyMatch(w -> w instanceof AbstractWidget name && name.getMessage().getString().equals(heading))) {
                    list.setScrollAmount(y);
                    return;
                }
                y += e.getHeight();
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        ctx.centeredText(font, title, width / 2, 11, 0xFFFFFFFF);
        for (Label l : labels) ctx.text(font, l.text, l.x, l.y, 0xFFC8CED6);
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
