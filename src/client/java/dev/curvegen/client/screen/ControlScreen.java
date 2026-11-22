package dev.curvegen.client.screen;

import static dev.curvegen.client.Lang.tr;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curvegen.core.edit.Stepper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
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

abstract class ControlScreen extends Screen {
    protected final Map<AbstractWidget, Runnable> reverse = new HashMap<>();

    protected ControlScreen(Component title) { super(title); }

    protected int tw(String s) { return font.width(s); }

    static String coord(double v) {
        String t = String.format(java.util.Locale.ROOT, "%.3f", v);
        return t.contains(".") ? t.replaceAll("0+$", "").replaceAll("\\.$", "") : t;
    }

    static final int ARROW_SLOT = 9;

    private record Spinner(Arrow up, Arrow down, DoubleSupplier value, double min, double max, BooleanSupplier enabled) {}
    private final List<Spinner> spinners = new ArrayList<>();

    private record Wheel(EditBox field, int x0, int x1, IntConsumer turn, BooleanSupplier enabled) {}
    private final List<Wheel> wheels = new ArrayList<>();
    private final Set<EditBox> focusOnly = new HashSet<>();

    private static final class Arrow extends AbstractButton {
        private final boolean up;
        private final Runnable action;

        Arrow(int x, int y, int w, int h, boolean up, Runnable action) {
            super(x, y, w, h, Component.translatable(up ? "curvegen.screen.increase" : "curvegen.screen.decrease"));
            this.up = up; this.action = action;
        }

        @Override public void onPress(InputWithModifiers input) { action.run(); }

        @Override
        protected void extractContents(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY();
            ctx.fill(x, y, x + width, y + height, !active ? 0xFF22262C : isHovered() ? 0xFF55606E : 0xFF3A424D);
            int c = active ? 0xFFE4E9EF : 0xFF4E5560, cx = x + width / 2, cy = y + height / 2;
            for (int r = 0; r < 3; r++) {
                int half = up ? r : 2 - r, yy = cy - 1 + r - (up ? 1 : 0);
                ctx.fill(cx - half, yy, cx + half + 1, yy + 1, c);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
    }

    protected AbstractButton arrow(int x, int y, int w, int h, boolean up, Runnable action) { return new Arrow(x, y, w, h, up, action); }

    protected EditBox spin(int x, int y, int w, int h, String value, Consumer<String> onChange,
                           DoubleSupplier current, double step, double min, double max, BooleanSupplier enabled, boolean integer) {
        return spin(new EditBox(font, x, y, w - ARROW_SLOT, h, Component.empty()), value, onChange, current, step, min, max, enabled, integer);
    }

    protected EditBox spin(EditBox f, String value, Consumer<String> onChange, DoubleSupplier current,
                           double step, double min, double max, BooleanSupplier enabled, boolean integer) {
        f.setMaxLength(64);
        f.setValue(value);
        f.setResponder(onChange);
        addRenderableWidget(f);
        arrows(f, f.getRight() + 1, f.getY(), f.getHeight(), current, step, min, max, enabled, integer);
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
        wheels.add(new Wheel(f, f.getX(), ax + ARROW_SLOT - 1, dir -> nudge(f, current, dir * step, min, max, integer), enabled));
    }

    protected void wheel(EditBox f, IntConsumer turn, BooleanSupplier enabled) {
        wheels.add(new Wheel(f, f.getX(), f.getX() + f.getWidth(), turn, enabled));
    }

    protected void wheelNeedsFocus(EditBox f) { focusOnly.add(f); }

    protected static void write(EditBox f, double v, boolean integer) {
        f.setValue(integer ? String.valueOf(Math.round(v)) : coord(v));
    }

    protected boolean wheelTurned(double mx, double my, double v) {
        if (v == 0) return false;
        for (Wheel w : wheels) {
            EditBox f = w.field;
            if (!f.visible || mx < w.x0 || mx >= w.x1 || my < f.getY() || my >= f.getY() + f.getHeight()) continue;
            if (!w.enabled.getAsBoolean() || focusOnly.contains(f) && !f.isFocused()) return false;
            w.turn.accept(v > 0 ? 1 : -1);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        return wheelTurned(mx, my, v) || super.mouseScrolled(mx, my, h, v);
    }

    private void nudge(EditBox f, DoubleSupplier current, double step, double min, double max, boolean integer) {
        write(f, Stepper.step(current.getAsDouble(), step, min, max), integer);
    }

    private static void refresh(Spinner sp) {
        boolean on = sp.enabled.getAsBoolean();
        double v = sp.value.getAsDouble();
        sp.up.active = on && v < sp.max - 1e-9;
        sp.down.active = on && v > sp.min + 1e-9;
    }

    protected <T> CycleButton<T> cycler(List<T> values, T initial, Function<T, Component> names,
                                        int x, int y, int w, String label, Consumer<T> onChange) {
        CycleButton<T> b = CycleButton.builder(names, initial).withValues(values)
                .create(x, y, w, 20, Component.literal(label), (_, v) -> onChange.accept(v));
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

    @Override
    protected void clearWidgets() {
        super.clearWidgets();
        reverse.clear();
        spinners.clear();
        wheels.clear();
        focusOnly.clear();
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
                    e.getValue().run();
                    return true;
                }
            }
        return super.mouseClicked(event, doubled);
    }
}
