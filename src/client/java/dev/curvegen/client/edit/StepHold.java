package dev.curvegen.client.edit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.CurveGenClient.StepKey;
import dev.curvegen.client.ModSettings;
import dev.curvegen.client.screen.InputScreen;
import dev.curvegen.core.edit.HoldTimer;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Hold-to-type for the keys that move or resize the hologram. A press acts at once by one block. Held, a progress bar
 * fills under the crosshair, and when it's full the number box opens. The amount typed there replaces the press's one
 * block, as a single undo step.
 */
public final class StepHold {
    private StepHold() {}

    /** The furthest a typed amount goes, either way. */
    private static final int MAX_AMOUNT = 1000;

    private static final HoldTimer TIMER = new HoldTimer();
    /** The undo step the held key's press recorded, or null if it changed nothing. */
    private static Object step;

    /** Call every client tick, with or without a hologram. */
    public static void tick(Minecraft mc) {
        List<StepKey> keys = CurveGenClient.STEP_KEYS;
        int pressed = -1, spent = TIMER.spent(k -> physicallyDown(keys.get(k).key()));
        for (int k = 0; k < keys.size(); k++) {
            boolean clicked = false;
            while (keys.get(k).key().consumeClick()) clicked = k != spent;   // the key that opened the box is still repeating
            // A held key repeats, so another key's press counts before its own.
            if (clicked && (pressed < 0 || pressed == TIMER.key())) pressed = k;
        }
        if (!Editor.isActive() || mc.gui.screen() != null) { TIMER.reset(); return; }

        int held = TIMER.key();
        switch (TIMER.update(pressed, held >= 0 && keys.get(held).key().isDown(), Util.getMillis(), ModSettings.holdSeconds)) {
            case STEP -> {
                Object before = Editor.lastStep();
                keys.get(TIMER.key()).action().accept(1);
                step = Editor.lastStep() == before ? null : Editor.lastStep();
            }
            case OPEN -> {
                StepKey key = keys.get(TIMER.key());
                Object pressStep = step;
                TIMER.opened();
                mc.gui.setScreen(InputScreen.number(Component.translatable(key.key().getName()).getString() + ": how many blocks?",
                        "1", true, -MAX_AMOUNT, MAX_AMOUNT, typed -> {
                            int n = (int) typed;
                            // The press already moved one block. Take that back if it's still the last edit. If other
                            // edits came after it, count it instead. Undo and redo call the hold off, so it wasn't undone.
                            if (pressStep == null || Editor.retract(pressStep)) key.action().accept(n);
                            else key.action().accept(n - 1);
                        }).ignoring(key.key()));
            }
            default -> { }
        }
    }

    /** Whether the key itself is down, whatever screen is open. A mouse button doesn't repeat, so it counts as up. */
    private static boolean physicallyDown(KeyMapping mapping) {
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        return key.getType() == InputConstants.Type.KEYBOARD && InputConstants.isKeyDown(key.getValue());
    }

    /** Calls off the hold in progress. The key that is down does nothing more until it's pressed again. */
    static void cancel() { TIMER.cancel(); }

    /** The progress bar, just under the crosshair. */
    static void renderBar(GuiGraphicsExtractor dc) {
        double f = TIMER.progress(Util.getMillis(), ModSettings.barDelaySeconds, ModSettings.holdSeconds);
        if (f < 0) return;
        int w = 40, x = (dc.guiWidth() - w) / 2, y = dc.guiHeight() / 2 + 10;
        dc.fill(x - 1, y - 1, x + w + 1, y + 4, 0xC0000000);
        dc.fill(x, y, x + (int) Math.round(w * f), y + 3, 0xFFFFFFFF);
    }
}
