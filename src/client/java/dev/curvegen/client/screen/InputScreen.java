package dev.curvegen.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.UnaryOperator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * A small box over the game for typing one value: the number box that holding a move key or choosing a number in the
 * radial menu opens, and the one-line text box for an equation. Enter applies it and Escape leaves things as they were.
 */
public class InputScreen extends Screen {
    private final String initial;
    private final int wantW;
    private final boolean number;
    /** What is wrong with a text, or null when it can be applied. */
    private final UnaryOperator<String> check;
    private final Consumer<String> onConfirm;
    private EditBox field;
    private String problem;
    /** A key that was down when the box opened. Until it comes up, the game repeats it, and the box ignores it. */
    private KeyMapping held;

    private static final int H = 58;
    private static final String HINT = "Enter applies it, Escape cancels.";

    private InputScreen(String title, String initial, int wantW, boolean number, UnaryOperator<String> check, Consumer<String> onConfirm) {
        super(Component.literal(title));
        this.initial = initial; this.wantW = wantW; this.number = number; this.check = check; this.onConfirm = onConfirm;
    }

    /** A box for a number from {@code min} to {@code max}, whole if {@code whole} is set. */
    public static InputScreen number(String title, String initial, boolean whole, double min, double max, DoubleConsumer onConfirm) {
        String range = (whole ? "A whole number" : "A number") + " from " + fmt(min) + " to " + fmt(max) + ".";
        return new InputScreen(title, initial, 150, true, v -> parse(v, whole, min, max) == null ? range : null,
                v -> onConfirm.accept(parse(v, whole, min, max)));
    }

    /** Has the box ignore a key that is still down from before it opened, until it's let go. */
    public InputScreen ignoring(KeyMapping key) {
        held = key;
        return this;
    }

    /** A box for a line of text. {@code check} returns what is wrong with a text, or null when it's fine. */
    public static InputScreen text(String title, String initial, UnaryOperator<String> check, Consumer<String> onConfirm) {
        return new InputScreen(title, initial, 300, false, check, onConfirm);
    }

    private static Double parse(String text, boolean whole, double min, double max) {
        try {
            double v = Double.parseDouble(text.trim().replace(',', '.'));
            return Double.isFinite(v) && v >= min && v <= max && (!whole || v == Math.rint(v)) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A number as the boxes and wedges show it: no more decimals than it needs, up to four. */
    public static String fmt(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.ROOT, "%.4f", v).replaceAll("0+$", "");
    }

    private int w() { return Math.min(width - 12, Math.max(wantW, Math.max(font.width(title), font.width(HINT)) + 16)); }
    private int x0() { return (width - w()) / 2; }
    private int y0() { return (height - H) / 2; }

    @Override
    protected void init() {
        String text = field != null ? field.getValue() : initial;
        field = new EditBox(font, x0() + 8, y0() + 20, w() - 16, 20, title);
        field.setMaxLength(256);
        field.setValue(text);
        field.setCursorPosition(0);               // select it all, so typing replaces what's there
        field.setHighlightPos(text.length());
        field.setResponder(v -> problem = check.apply(v));
        addRenderableWidget(field);
        setInitialFocus(field);
        problem = check.apply(text);
    }

    private void submit() {
        if (problem != null) return;
        String value = field.getValue();
        minecraft.gui.setScreen(null);
        onConfirm.accept(value);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (held != null && held.matches(event)) return true;
        if (event.isConfirmation()) { submit(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (held != null && held.matches(event)) held = null;
        return super.keyReleased(event);
    }

    /** The key can also come up without the box seeing it. */
    @Override
    public void tick() {
        if (held == null) return;
        InputConstants.Key key = InputConstants.getKey(held.saveString());
        if (key.getType() != InputConstants.Type.KEYBOARD || !InputConstants.isKeyDown(key.getValue())) held = null;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (held != null || number && "0123456789.,-".indexOf(event.codepoint()) < 0) return true;
        return super.charTyped(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        int x = x0(), y = y0(), w = w();
        ctx.fill(x - 1, y - 1, x + w + 1, y + H + 1, 0xFF5A6472);
        ctx.fill(x, y, x + w, y + H, 0xFF1F252C);
        ctx.text(font, font.plainSubstrByWidth(title.getString(), w - 16), x + 8, y + 7, 0xFFFFFFFF);
        String note = problem != null ? problem : HINT;
        ctx.text(font, font.plainSubstrByWidth(note, w - 16), x + 8, y + 45, problem != null ? 0xFFFF8098 : 0xFF9AA5B3, false);
        super.extractRenderState(ctx, mx, my, delta);
    }

    /** The game stays in view behind the box. */
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) { }

    @Override
    public boolean isPauseScreen() { return false; }
}
