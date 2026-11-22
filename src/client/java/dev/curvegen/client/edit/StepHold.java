package dev.curvegen.client.edit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curvegen.client.Lang;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.CurveGenClient.StepKey;
import dev.curvegen.client.ModSettings;
import dev.curvegen.client.screen.InputScreen;
import dev.curvegen.core.edit.HoldTimer;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class StepHold {
    private StepHold() {}

    private static final int MAX_AMOUNT = 1000;

    private static final HoldTimer TIMER = new HoldTimer();
    private static Object step;

    public static void tick(Minecraft mc) {
        List<StepKey> keys = CurveGenClient.STEP_KEYS;
        for (int k = 0; k < keys.size(); k++) {
            boolean clicked = false;
            while (keys.get(k).key().consumeClick()) clicked = true;
            if (clicked && !onKeyboard(keys.get(k).key())) TIMER.press(k);
        }
        int pressed = TIMER.takePress();
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
                mc.gui.setScreen(InputScreen.number(Lang.tr("curvegen.editor.step_amount", Component.translatable(key.key().getName()).getString()),
                        "1", true, -MAX_AMOUNT, MAX_AMOUNT, typed -> {
                            int n = (int) typed;
                            if (pressStep == null || Editor.retract(pressStep)) key.action().accept(n);
                            else key.action().accept(n - 1);
                        }).ignoring(key.key()));
            }
            default -> { }
        }
    }

    public static void keyEvent(KeyEvent event, int action) {
        if (action != 1 || Minecraft.getInstance().gui.screen() != null) return;
        List<StepKey> keys = CurveGenClient.STEP_KEYS;
        for (int k = 0; k < keys.size(); k++) if (onKeyboard(keys.get(k).key()) && keys.get(k).key().matches(event)) TIMER.press(k);
    }

    private static boolean onKeyboard(KeyMapping mapping) {
        return InputConstants.getKey(mapping.saveString()).getType() == InputConstants.Type.KEYBOARD;
    }

    static void cancel() { TIMER.cancel(); }

    static void renderBar(GuiGraphicsExtractor dc) {
        double f = TIMER.progress(Util.getMillis(), ModSettings.barDelaySeconds, ModSettings.holdSeconds);
        if (f < 0) return;
        int w = 40, x = (dc.guiWidth() - w) / 2, y = dc.guiHeight() / 2 + 10;
        dc.fill(x - 1, y - 1, x + w + 1, y + 4, 0xC0000000);
        dc.fill(x, y, x + (int) Math.round(w * f), y + 3, 0xFFFFFFFF);
    }
}
