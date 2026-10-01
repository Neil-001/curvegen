package dev.curvegen.client.screen;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A small dialog on top of the previous screen: a name field, an optional on/off option, and confirm or cancel. */
public class NameDialogScreen extends Screen {
    /** What the dialog says about a name: whether it can be used, what the confirm button says, and an optional note. */
    public record Check(boolean allowed, String confirmLabel, String note) {}

    private final Screen parent;
    private final String initial;
    private final Function<String, Check> check;
    private final BiConsumer<String, Boolean> onConfirm;
    private final String optionLabel, optionTip;   // null: no option
    private boolean option = true;
    private EditBox field;
    private Button confirm;
    private Check state;

    private static final int W = 240, OPTION_H = 26;

    public NameDialogScreen(Screen parent, String title, String initial, Function<String, Check> check, Consumer<String> onConfirm) {
        this(parent, title, initial, check, null, null, (name, opt) -> onConfirm.accept(name));
    }

    /** With an on/off option under the name field, on at first; onConfirm gets the name and the option. */
    public NameDialogScreen(Screen parent, String title, String initial, Function<String, Check> check,
                            String optionLabel, String optionTip, BiConsumer<String, Boolean> onConfirm) {
        super(Component.literal(title));
        this.parent = parent; this.initial = initial; this.check = check; this.onConfirm = onConfirm;
        this.optionLabel = optionLabel; this.optionTip = optionTip;
    }

    private int h() { return optionLabel != null ? 96 + OPTION_H : 96; }
    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - h()) / 2; }
    /** Top of the note line, below the name field and the option. */
    private int noteY() { return y0() + 48 + (optionLabel != null ? OPTION_H : 0); }

    @Override
    protected void init() {
        int x = x0() + 10, y = y0() + 24;
        String text = field != null ? field.getValue() : initial;
        field = new EditBox(font, x, y, W - 20, 20, Component.literal("Preset name"));
        field.setMaxLength(80);
        field.setValue(text);
        field.setCursorPosition(0);               // select it all, so typing replaces the suggestion
        field.setHighlightPos(text.length());
        field.setResponder(v -> refresh());
        addRenderableWidget(field);
        setInitialFocus(field);
        if (optionLabel != null) {
            CycleButton<Boolean> opt = CycleButton.onOffBuilder(option)
                    .create(x, y + 26, W - 20, 20, Component.literal(optionLabel), (b, v) -> option = v);
            if (optionTip != null) opt.setTooltip(Tooltip.create(Component.literal(optionTip)));
            addRenderableWidget(opt);
        }
        int by = y0() + h() - 28, bw = (W - 30) / 2;
        confirm = Button.builder(Component.literal("Save"), b -> submit()).bounds(x, by, bw, 20).build();
        addRenderableWidget(confirm);
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + bw + 10, by, bw, 20).build());
        refresh();
    }

    private void refresh() {
        state = check.apply(field.getValue().trim());
        confirm.active = state.allowed();
        confirm.setMessage(Component.literal(state.confirmLabel()));
    }

    private void submit() {
        if (!state.allowed()) return;
        minecraft.setScreen(parent);
        onConfirm.accept(field.getValue().trim(), option);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { submit(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        parent.renderBackground(ctx, -1, -1, delta);   // the screen underneath, not interactive
        parent.render(ctx, -1, -1, delta);
        ctx.nextStratum();   // above everything the parent drew, items included
        ctx.fill(0, 0, width, height, 0xA0000000);
        int x = x0(), y = y0();
        ctx.fill(x - 1, y - 1, x + W + 1, y + h() + 1, 0xFF5A6472);
        ctx.fill(x, y, x + W, y + h(), 0xFF1F252C);
        ctx.drawString(font, title, x + 10, y + 9, 0xFFFFFFFF);
        if (state != null && state.note() != null)
            ctx.drawString(font, font.plainSubstrByWidth(state.note(), W - 20), x + 10, noteY(), state.allowed() ? 0xFFE0C07A : 0xFFFF8098, false);
        for (var d : children()) if (d instanceof net.minecraft.client.gui.components.Renderable dr) dr.render(ctx, mx, my, delta);
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mx, int my, float delta) { }   // the parent already drew one

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
