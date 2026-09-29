package dev.curvegen.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Function;

/** A small dialog on top of the previous screen: a name field, and confirm or cancel. */
public class NameDialogScreen extends Screen {
    /** What the dialog says about a name: whether it can be used, what the confirm button says, and an optional note. */
    public record Check(boolean allowed, String confirmLabel, String note) {}

    private final Screen parent;
    private final String initial;
    private final Function<String, Check> check;
    private final Consumer<String> onConfirm;
    private TextFieldWidget field;
    private ButtonWidget confirm;
    private Check state;

    private static final int W = 240, H = 96;

    public NameDialogScreen(Screen parent, String title, String initial, Function<String, Check> check, Consumer<String> onConfirm) {
        super(Text.literal(title));
        this.parent = parent; this.initial = initial; this.check = check; this.onConfirm = onConfirm;
    }

    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2; }

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
        int by = y0() + H - 28, bw = (W - 30) / 2;
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
        onConfirm.accept(field.getText().trim());
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
        ctx.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xFF5A6472);
        ctx.fill(x, y, x + W, y + H, 0xFF1F252C);
        ctx.drawTextWithShadow(textRenderer, title, x + 10, y + 9, 0xFFFFFF);
        if (state != null && state.note() != null)
            ctx.drawText(textRenderer, textRenderer.trimToWidth(state.note(), W - 20), x + 10, y + 48, state.allowed() ? 0xE0C07A : 0xFF8098, false);
        for (var d : children()) if (d instanceof net.minecraft.client.gui.Drawable dr) dr.render(ctx, mx, my, delta);
        ctx.getMatrices().pop();
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) { }   // the parent already drew one

    @Override
    public void close() { client.setScreen(parent); }
}
