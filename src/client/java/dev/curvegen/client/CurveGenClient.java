package dev.curvegen.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curvegen.client.chroma.Chroma;
import dev.curvegen.client.edit.Capture;
import dev.curvegen.client.edit.Editor;
import dev.curvegen.client.edit.StepHold;
import dev.curvegen.client.screen.CurveScreen;
import dev.curvegen.client.screen.RadialScreen;
import dev.curvegen.core.ShapeSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

public final class CurveGenClient {
    private CurveGenClient() {}

    public static final ShapeSettings SETTINGS = new ShapeSettings();

    public static ClientPlatform platform;

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath("curvegen", "main"));
    public static KeyMapping OPEN, RADIAL, CONFIRM, CANCEL, ROTATE, TIP, FORWARD, BACK, BUMP_OUT, BUMP_IN, LOCK, UNDO, REDO, REPLACE, CARVE, ADD_POINT, REMOVE_POINT;
    private static final KeyMapping[] MOVE = new KeyMapping[6];
    private static final List<KeyMapping> KEYS = new ArrayList<>();

    public record StepKey(KeyMapping key, IntConsumer action) {}
    public static final List<StepKey> STEP_KEYS = new ArrayList<>();

    private static KeyMapping key(String name, int code) {
        KeyMapping k = new KeyMapping("key.curvegen." + name, InputConstants.Type.KEYBOARD, code, CATEGORY);
        KEYS.add(k);
        return k;
    }

    private static KeyMapping step(String name, int code, IntConsumer action) {
        KeyMapping k = key(name, code);
        STEP_KEYS.add(new StepKey(k, action));
        return k;
    }

    public static List<KeyMapping> init(ClientPlatform loader) {
        platform = loader;
        ModSettings.load();
        Chroma.init();
        PaintStore.load();
        OPEN = key("open", InputConstants.KEY_G);
        RADIAL = key("radial", InputConstants.KEY_V);
        CONFIRM = key("confirm", InputConstants.KEY_RETURN);
        CANCEL = key("cancel", InputConstants.KEY_BACKSPACE);
        LOCK = key("lock", InputConstants.KEY_K);
        ROTATE = key("rotate", InputConstants.KEY_R);
        TIP = key("tip", InputConstants.KEY_U);
        FORWARD = step("forward", InputConstants.KEY_PAGEUP, Editor::nudgeWithView);
        BACK = step("back", InputConstants.KEY_PAGEDOWN, n -> Editor.nudgeWithView(-n));
        for (Direction d : Direction.values()) MOVE[d.ordinal()] = step(d.getName(), InputConstants.UNKNOWN.getValue(), n -> Editor.nudge(d, n));
        BUMP_OUT = step("bump_out", InputConstants.KEY_HOME, Editor::bump);
        BUMP_IN = step("bump_in", InputConstants.KEY_END, n -> Editor.bump(-n));
        ADD_POINT = key("add_point", InputConstants.KEY_INSERT);
        REMOVE_POINT = key("remove_point", InputConstants.KEY_DELETE);
        UNDO = key("undo", InputConstants.KEY_Z);
        REDO = key("redo", InputConstants.KEY_Y);
        REPLACE = key("replace", InputConstants.KEY_H);
        CARVE = key("carve", InputConstants.KEY_J);
        return KEYS;
    }

    public static void tick(Minecraft mc) {
        while (OPEN.consumeClick()) mc.gui.setScreen(new CurveScreen());
        while (UNDO.consumeClick()) { if (Editor.isActive()) Editor.undo(); else Placement.undo(); }
        while (REDO.consumeClick()) { if (Editor.isActive()) Editor.redo(); else Placement.redo(); }
        while (RADIAL.consumeClick()) if (mc.gui.screen() == null && mc.player != null) mc.gui.setScreen(new RadialScreen());
        StepHold.tick(mc);
        for (KeyMapping k : KEYS) {
            if (k == OPEN || k == UNDO || k == REDO || k == RADIAL) continue;
            while (k.consumeClick()) {
                if (k == CANCEL && Capture.isActive()) Capture.back();
                else if (Editor.isActive()) press(k);
            }
        }
        Placement.tick(mc);
        Capture.tick(mc);
        Editor.tick(mc);
        PaintStore.tick();
    }

    private static void press(KeyMapping k) {
        if (k == CONFIRM) Editor.confirm();
        else if (k == CANCEL) Editor.cancel();
        else if (k == LOCK) Editor.toggleLock();
        else if (k == ROTATE) Editor.rotate();
        else if (k == TIP) Editor.tip();
        else if (k == REPLACE) Editor.toggleReplace();
        else if (k == CARVE) Editor.toggleCarve();
        else if (k == ADD_POINT) Editor.addPoint();
        else if (k == REMOVE_POINT) Editor.removePoint();
    }
}
