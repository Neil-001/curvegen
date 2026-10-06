package dev.curvegen.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** A screen with the mod's own controls: number fields with arrows, and buttons that step backwards on a right-click. */
abstract class ControlScreen extends Screen {
    /** What a right-click does on buttons that step through options: step backwards. */
    protected final Map<AbstractWidget, Runnable> reverse = new HashMap<>();

    protected ControlScreen(Component title) { super(title); }

    protected int tw(String s) { return font.width(s); }

    static String coord(double v) {
        String t = String.format(java.util.Locale.ROOT, "%.3f", v);
        return t.contains(".") ? t.replaceAll("0+$", "").replaceAll("\\.$", "") : t;
    }

    /** Horizontal space the arrows take from a field: 8 px of arrows plus a 1 px gap. */
    static final int ARROW_SLOT = 9;

    private record Spinner(Arrow up, Arrow down, DoubleSupplier value, double min, double max, BooleanSupplier enabled) {}
    private final List<Spinner> spinners = new ArrayList<>();

    /** A small up or down arrow. Drawn by hand so it stays crisp at 8 pixels, and greyed out when it can't go further. */
    private static final class Arrow extends AbstractButton {
        private final boolean up;
        private final Runnable action;

        Arrow(int x, int y, int w, int h, boolean up, Runnable action) {
            super(x, y, w, h, Component.literal(up ? "Increase" : "Decrease"));
            this.up = up; this.action = action;
        }

        @Override public void onPress(InputWithModifiers input) { action.run(); }

        @Override
        protected void extractContents(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY();
            ctx.fill(x, y, x + width, y + height, !active ? 0xFF22262C : isHovered() ? 0xFF55606E : 0xFF3A424D);
            int c = active ? 0xFFE4E9EF : 0xFF4E5560, cx = x + width / 2, cy = y + height / 2;
            for (int r = 0; r < 3; r++) {          // a 5-pixel-wide triangle
                int half = up ? r : 2 - r, yy = cy - 1 + r - (up ? 1 : 0);
                ctx.fill(cx - half, yy, cx + half + 1, yy + 1, c);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
    }

    /**
     * A number field of total width w whose right edge holds up/down arrows (taken from the field, not added to it).
     * The arrows step the value within [min, max] and grey out at the ends or when the field doesn't apply.
     */
    protected EditBox spin(int x, int y, int w, int h, String value, Consumer<String> onChange,
                           DoubleSupplier current, double step, double min, double max, BooleanSupplier enabled, boolean integer) {
        EditBox f = new EditBox(font, x, y, w - ARROW_SLOT, h, Component.empty());
        f.setMaxLength(64);
        f.setValue(value);
        f.setResponder(onChange);
        addRenderableWidget(f);
        arrows(f, x + w - ARROW_SLOT + 1, y, h, current, step, min, max, enabled, integer);
        return f;
    }

    protected void arrows(EditBox f, int ax, int y, int h, DoubleSupplier current, double step, double min, double max,
                          BooleanSupplier enabled, boolean integer) {
        int top = h / 2;
        Arrow up = new Arrow(ax, y, ARROW_SLOT - 1, top, true, () -> nudge(f, current, step, min, max, integer));
        Arrow down = new Arrow(ax, y + top, ARROW_SLOT - 1, h - top, false, () -> nudge(f, current, -step, min, max, integer));
        addRenderableWidget(up);
        addRenderableWidget(down);
        Spinner sp = new Spinner(up, down, current, min, max, enabled);
        spinners.add(sp);
        refresh(sp);
    }

    /** Steps the value and writes it into the field, whose own listener then applies it (clamped to the limits). */
    private void nudge(EditBox f, DoubleSupplier current, double step, double min, double max, boolean integer) {
        double v = Math.max(min, Math.min(max, current.getAsDouble() + step));
        f.setValue(integer ? String.valueOf(Math.round(v)) : coord(v));
    }

    private static void refresh(Spinner sp) {
        boolean on = sp.enabled.getAsBoolean();
        double v = sp.value.getAsDouble();
        sp.up.active = on && v < sp.max - 1e-9;
        sp.down.active = on && v > sp.min + 1e-9;
    }

    /** A button that steps through options: click for the next one, right-click for the previous one. */
    protected <T> CycleButton<T> cycler(List<T> values, T initial, Function<T, Component> names,
                                        int x, int y, int w, String label, Consumer<T> onChange) {
        CycleButton<T> b = CycleButton.builder(names, initial).withValues(values)
                .create(x, y, w, 20, Component.literal(label), (btn, v) -> onChange.accept(v));
        reverse.put(b, () -> {
            T prev = values.get((values.indexOf(b.getValue()) - 1 + values.size()) % values.size());
            b.setValue(prev);
            onChange.accept(prev);
        });
        return b;
    }

    protected CycleButton<Boolean> toggle(boolean initial, int x, int y, int w, String label, Consumer<Boolean> onChange) {
        return cycler(List.of(true, false), initial, CommonComponents::optionStatus, x, y, w, label, onChange);
    }

    /** Runs before every rebuild, so the controls of the old layout don't linger. */
    @Override
    protected void clearWidgets() {
        super.clearWidgets();
        reverse.clear();
        spinners.clear();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        for (Spinner sp : spinners) refresh(sp);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT)
            for (var e : reverse.entrySet()) {
                AbstractWidget w = e.getKey();
                if (w.active && w.visible && w.isMouseOver(event.x(), event.y())) {
                    w.playDownSound(minecraft.getSoundManager());
                    e.getValue().run();   // may rebuild the screen, so stop looking straight away
                    return true;
                }
            }
        return super.mouseClicked(event, doubled);
    }
}
