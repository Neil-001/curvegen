package dev.curvegen.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curvegen.client.edit.Editor;
import dev.curvegen.client.screen.CurveScreen;
import dev.curvegen.core.ShapeSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/** The client side that doesn't depend on the mod loader. The loader's client entrypoint, in {@code dev.curvegen.client.fabric} or {@code dev.curvegen.client.neoforge}, hooks it up. */
public final class CurveGenClient {
    private CurveGenClient() {}

    /** Shape settings live for the whole session, so reopening the screen picks up where you left off. */
    public static final ShapeSettings SETTINGS = new ShapeSettings();

    public static ClientPlatform platform;

    /** The heading for the keys in the Controls screen. Each loader registers it in its own way. */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath("curvegen", "main"));
    public static KeyMapping OPEN, CONFIRM, CANCEL, ROTATE, TIP, FORWARD, BACK, BUMP_OUT, BUMP_IN, LOCK, UNDO, REDO, REPLACE, CARVE, ADD_POINT, REMOVE_POINT;
    /** Moves along one world axis each, in {@link Direction} order. Unbound until the player assigns them. */
    private static final KeyMapping[] MOVE = new KeyMapping[6];
    /** Every key, in the order the Controls screen lists them. */
    private static final List<KeyMapping> KEYS = new ArrayList<>();

    /**
     * A key that moves or resizes the hologram by a number of blocks: every nudge and bump key. A tap passes 1.
     * The hold progress bar and the number box belong here: they would call {@code action} with the typed amount
     * instead, and a negative amount goes the other way.
     */
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

    /** Loads the settings, creates the keys and returns them for the loader to register, along with {@link #CATEGORY}. */
    public static List<KeyMapping> init(ClientPlatform loader) {
        platform = loader;
        ModSettings.load();
        OPEN = key("open", InputConstants.KEY_G);
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

    /** Call at the end of every client tick. */
    public static void tick(Minecraft mc) {
        while (OPEN.consumeClick()) mc.gui.setScreen(new CurveScreen());
        // Z takes back an edit while there's a hologram, and the last placement otherwise.
        while (UNDO.consumeClick()) { if (Editor.isActive()) Editor.undo(); else Placement.undo(); }
        for (KeyMapping k : KEYS) {
            if (k == OPEN || k == UNDO) continue;
            while (k.consumeClick()) if (Editor.isActive()) press(k);   // otherwise drained, so it doesn't fire when a hologram appears
        }
        Placement.tick(mc);
        Editor.tick(mc);
    }

    private static void press(KeyMapping k) {
        for (StepKey s : STEP_KEYS) if (s.key() == k) { s.action().accept(1); return; }
        if (k == CONFIRM) Editor.confirm();
        else if (k == CANCEL) Editor.cancel();
        else if (k == LOCK) Editor.toggleLock();
        else if (k == ROTATE) Editor.rotate();
        else if (k == TIP) Editor.tip();
        else if (k == REDO) Editor.redo();
        else if (k == REPLACE) Editor.toggleReplace();
        else if (k == CARVE) Editor.toggleCarve();
        else if (k == ADD_POINT) Editor.addPoint();
        else if (k == REMOVE_POINT) Editor.removePoint();
    }
}
