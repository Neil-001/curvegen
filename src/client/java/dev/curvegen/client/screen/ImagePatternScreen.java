package dev.curvegen.client.screen;

import static dev.curvegen.client.Lang.tr;

import dev.curvegen.client.ImageFile;
import dev.curvegen.client.PatternStore;
import dev.curvegen.core.paint.ImagePattern;
import dev.curvegen.core.paint.Pattern;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * A small dialog over the pattern library for a picture dropped onto it: the new pattern's name and its width in blocks,
 * with the height that gives. Saving scales the picture ({@link ImagePattern}) and adds it to the library. The picture
 * is freed when the dialog closes, whichever way.
 */
class ImagePatternScreen extends ControlScreen {
    private static final int W = 240, H = 136;
    private final PatternLibraryScreen parent;
    private final ImageFile image;
    private final String initialName;
    /** Given the saved pattern, or null with a message when the picture made no pattern. */
    private final Consumer<Pattern> onSaved;
    private final int maxWidth;
    private int cells;
    private EditBox name, widthField;
    private Button save;
    private String note;
    private boolean noteOk;

    ImagePatternScreen(PatternLibraryScreen parent, ImageFile image, String name, Consumer<Pattern> onSaved) {
        super(Component.translatable("curvegen.image.title"));
        this.parent = parent; this.image = image; this.initialName = name; this.onSaved = onSaved;
        maxWidth = ImagePattern.maxWidth(image.width, image.height);
        cells = Math.min(maxWidth, Math.min(image.width, 64));
    }

    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2; }

    @Override
    protected void init() {
        int x = x0() + 10, y = y0() + 24;
        String text = name != null ? name.getValue() : initialName;
        name = new EditBox(font, x, y, W - 20, 20, Component.translatable("curvegen.image.title"));
        name.setMaxLength(80);
        name.setValue(text);
        name.setCursorPosition(0);
        name.setHighlightPos(text.length());
        name.setResponder(_ -> refresh());
        addRenderableWidget(name);
        setInitialFocus(name);

        int fw = 56;
        widthField = spin(x0() + W - 10 - fw, y + 26, fw, 20, String.valueOf(cells), v -> {
            try { cells = Integer.parseInt(v.trim()); } catch (NumberFormatException _) { cells = -1; }
            refresh();
        }, () -> cells, 1, 1, maxWidth, () -> true, true);
        widthField.setTooltip(Tooltip.create(Component.translatable("curvegen.image.width_tip", maxWidth, Pattern.MAX)));

        int by = y0() + H - 28, bw = (W - 30) / 2;
        save = Button.builder(Component.translatable("curvegen.screen.save"), _ -> submit()).bounds(x, by, bw, 20).build();
        addRenderableWidget(save);
        addRenderableWidget(Button.builder(Component.translatable("curvegen.screen.cancel"), _ -> onClose()).bounds(x + bw + 10, by, bw, 20).build());
        refresh();
    }

    private boolean widthOk() { return cells >= 1 && cells <= maxWidth; }

    private void refresh() {
        String n = name.getValue().trim();
        boolean taken = !n.isEmpty() && PatternStore.find(n) != null;
        note = n.isEmpty() ? tr("curvegen.capture.type_a_name") : !widthOk() ? tr("curvegen.image.width_range", maxWidth)
                : taken ? tr("curvegen.capture.name_taken") : null;
        noteOk = taken && widthOk();
        save.active = !n.isEmpty() && widthOk();
        // Greyed, it says why, as the note under the fields does.
        save.setTooltip(save.active ? null : Tooltip.create(Component.literal(note)));
        save.setMessage(Component.translatable(taken ? "curvegen.capture.replace" : "curvegen.screen.save"));
    }

    private void submit() {
        if (!save.active) return;
        Pattern p = image.pattern(name.getValue().trim(), cells);
        minecraft.gui.setScreen(parent);
        onSaved.accept(p == null ? null : PatternStore.save(p).pattern);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation()) { submit(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        parent.extractBackground(ctx, -1, -1, delta);
        parent.extractRenderState(ctx, -1, -1, delta);
        ctx.nextStratum();
        ctx.fill(0, 0, width, height, 0xA0000000);
        int x = x0(), y = y0();
        ctx.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xFF5A6472);
        ctx.fill(x, y, x + W, y + H, 0xFF1F252C);
        ctx.text(font, title, x + 10, y + 9, 0xFFFFFFFF);
        int fieldX = widthField.getX();
        ctx.text(font, font.plainSubstrByWidth(tr("curvegen.image.width"), fieldX - x - 16), x + 10, y + 56, 0xFFE4E9EF, false);
        if (widthOk()) {
            String size = tr("curvegen.image.size", cells, ImagePattern.heightFor(image.width, image.height, cells));
            ctx.text(font, font.plainSubstrByWidth(size, W - 20), x + 10, y + 78, 0xFF9AA5B3, false);
        }
        if (note != null) ctx.text(font, font.plainSubstrByWidth(note, W - 20), x + 10, y + 92, noteOk ? 0xFFE0C07A : 0xFFFF8098, false);
        super.extractRenderState(ctx, mx, my, delta);   // the controls, with the arrows greyed at the ends
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) { }

    @Override
    public void removed() {
        image.close();
        super.removed();
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return parent.isPauseScreen(); }
}
