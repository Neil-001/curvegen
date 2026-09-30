package dev.curvegen.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

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
    private TextFieldWidget field;
    private ButtonWidget confirm;
    private Check state;

    private static final int W = 240, OPTION_H = 26;

    public NameDialogScreen(Screen parent, String title, String initial, Function<String, Check> check, Consumer<String> onConfirm) {
        this(parent, title, initial, check, null, null, (name, opt) -> onConfirm.accept(name));
    }

    /** With an on/off option under the name field, on at first; onConfirm gets the name and the option. */
    public NameDialogScreen(Screen parent, String title, String initial, Function<String, Check> check,
                            String optionLabel, String optionTip, BiConsumer<String, Boolean> onConfirm) {
        super(Text.literal(title));
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
        String text = field != null ? field.getText() : initial;
        field = new TextFieldWidget(textRenderer, x, y, W - 20, 20, Text.literal("Preset name"));
        field.setMaxLength(80);
        field.setText(text);
        field.setSelectionStart(0);               // select it all, so typing replaces the suggestion
        field.setSelectionEnd(text.length());
        field.setChangedListener(v -> refresh());
        addDrawableChild(field);
        setInitialFocus(field);
        if (optionLabel != null) {
            CyclingButtonWidget<Boolean> opt = CyclingButtonWidget.onOffBuilder(option)
                    .build(x, y + 26, W - 20, 20, Text.literal(optionLabel), (b, v) -> option = v);
            if (optionTip != null) opt.setTooltip(Tooltip.of(Text.literal(optionTip)));
            addDrawableChild(opt);
        }
        int by = y0() + h() - 28, bw = (W - 30) / 2;
        confirm = ButtonWidget.builder(Text.literal("Save"), b -> submit()).dimensions(x, by, bw, 20).build();
        addDrawableChild(confirm);
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close()).dimensions(x + bw + 10, by, bw, 20).build());
        refresh();
    }

    private void refresh() {
        state = check.apply(field.getText().trim());
        confirm.active = state.allowed();
        confirm.setMessage(Text.literal(state.confirmLabel()));
    }

    private void submit() {
        if (!state.allowed()) return;
        client.setScreen(parent);
        onConfirm.accept(field.getText().trim(), option);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { submit(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        parent.render(ctx, -1, -1, delta);      // the screen underneath, not interactive
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400);  // above everything the parent drew, items included
        ctx.fill(0, 0, width, height, 0xA0000000);
        int x = x0(), y = y0();
        ctx.fill(x - 1, y - 1, x + W + 1, y + h() + 1, 0xFF5A6472);
        ctx.fill(x, y, x + W, y + h(), 0xFF1F252C);
        ctx.drawTextWithShadow(textRenderer, title, x + 10, y + 9, 0xFFFFFF);
        if (state != null && state.note() != null)
            ctx.drawText(textRenderer, textRenderer.trimToWidth(state.note(), W - 20), x + 10, noteY(), state.allowed() ? 0xE0C07A : 0xFF8098, false);
        for (var d : children()) if (d instanceof net.minecraft.client.gui.Drawable dr) dr.render(ctx, mx, my, delta);
        ctx.getMatrices().pop();
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) { }   // the parent already drew one

    @Override
    public void close() { client.setScreen(parent); }
}
