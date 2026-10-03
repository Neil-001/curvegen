package dev.curvegen.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curvegen.client.screen.CurveScreen;
import dev.curvegen.core.ShapeSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** The client side that doesn't depend on the mod loader. The loader's client entrypoint, in {@code dev.curvegen.client.fabric} or {@code dev.curvegen.client.neoforge}, hooks it up. */
public final class CurveGenClient {
    private CurveGenClient() {}

    /** Shape settings live for the whole session, so reopening the screen picks up where you left off. */
    public static final ShapeSettings SETTINGS = new ShapeSettings();

    public static ClientPlatform platform;

    /** The heading for the keys in the Controls screen. Each loader registers it in its own way. */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath("curvegen", "main"));
    public static KeyMapping OPEN, CONFIRM, CANCEL, ROTATE, FORWARD, BACK, LOCK, UNDO;
    /** Moves along one world axis each, in {@link Direction} order. Unbound until the player assigns them. */
    private static final KeyMapping[] MOVE = new KeyMapping[6];
    /** Every key, in the order the Controls screen lists them. */
    private static final List<KeyMapping> KEYS = new ArrayList<>();

    private static KeyMapping key(String name, int code) {
        KeyMapping k = new KeyMapping("key.curvegen." + name, InputConstants.Type.KEYSYM, code, CATEGORY);
        KEYS.add(k);
        return k;
    }

    /** Creates the keys and returns them for the loader to register, along with {@link #CATEGORY}. */
    public static List<KeyMapping> init(ClientPlatform loader) {
        platform = loader;
        OPEN = key("open", GLFW.GLFW_KEY_G);
        CONFIRM = key("confirm", GLFW.GLFW_KEY_ENTER);
        CANCEL = key("cancel", GLFW.GLFW_KEY_BACKSPACE);
        ROTATE = key("rotate", GLFW.GLFW_KEY_R);
        FORWARD = key("forward", GLFW.GLFW_KEY_PAGE_UP);
        BACK = key("back", GLFW.GLFW_KEY_PAGE_DOWN);
        for (Direction d : Direction.values()) MOVE[d.ordinal()] = key(d.getName(), GLFW.GLFW_KEY_UNKNOWN);
        LOCK = key("lock", GLFW.GLFW_KEY_K);
        UNDO = key("undo", GLFW.GLFW_KEY_Z);
        return KEYS;
    }

    /** Call at the end of every client tick. */
    public static void tick(Minecraft mc) {
        while (OPEN.consumeClick()) mc.gui.setScreen(new CurveScreen());
        while (UNDO.consumeClick()) Placement.undo();
        if (Placement.isActive()) {
            while (CONFIRM.consumeClick()) Placement.confirm();
            while (CANCEL.consumeClick()) Placement.cancel();
            while (ROTATE.consumeClick()) Placement.rotate();
            while (FORWARD.consumeClick()) Placement.moveWithView(true);
            while (BACK.consumeClick()) Placement.moveWithView(false);
            for (Direction d : Direction.values())
                while (MOVE[d.ordinal()].consumeClick()) Placement.move(d);
            while (LOCK.consumeClick()) Placement.toggleLock();
        } else {
            // Drain presses so they don't fire later when placement starts.
            while (CONFIRM.consumeClick() || CANCEL.consumeClick() || ROTATE.consumeClick()
                    || FORWARD.consumeClick() || BACK.consumeClick() || LOCK.consumeClick()) { }
            for (KeyMapping k : MOVE) while (k.consumeClick()) { }
        }
        Placement.tick(mc);
    }
}
