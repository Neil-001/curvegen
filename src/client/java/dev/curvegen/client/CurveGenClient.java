package dev.curvegen.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curvegen.client.screen.CurveScreen;
import dev.curvegen.core.ShapeSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.lwjgl.glfw.GLFW;

public class CurveGenClient implements ClientModInitializer {
    /** Shape settings live for the whole session, so reopening the screen picks up where you left off. */
    public static final ShapeSettings SETTINGS = new ShapeSettings();

    private static final KeyMapping.Category CAT = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("curvegen", "main"));
    public static KeyMapping OPEN, CONFIRM, CANCEL, ROTATE, FORWARD, BACK, LOCK, UNDO;
    /** Moves along one world axis each, in {@link Direction} order. Unbound until the player assigns them. */
    private static final KeyMapping[] MOVE = new KeyMapping[6];

    private static KeyMapping key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.curvegen." + name, InputConstants.Type.KEYSYM, code, CAT));
    }

    @Override
    public void onInitializeClient() {
        OPEN = key("open", GLFW.GLFW_KEY_G);
        CONFIRM = key("confirm", GLFW.GLFW_KEY_ENTER);
        CANCEL = key("cancel", GLFW.GLFW_KEY_BACKSPACE);
        ROTATE = key("rotate", GLFW.GLFW_KEY_R);
        FORWARD = key("forward", GLFW.GLFW_KEY_PAGE_UP);
        BACK = key("back", GLFW.GLFW_KEY_PAGE_DOWN);
        for (Direction d : Direction.values()) MOVE[d.ordinal()] = key(d.getName(), GLFW.GLFW_KEY_UNKNOWN);
        LOCK = key("lock", GLFW.GLFW_KEY_K);
        UNDO = key("undo", GLFW.GLFW_KEY_Z);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN.consumeClick()) mc.setScreen(new CurveScreen());
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
        });
        WorldRenderEvents.END_MAIN.register(Placement::render);
        // Block colours come from the active resource packs, so recompute them whenever packs change.
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(Identifier.fromNamespaceAndPath("curvegen", "block_colours"),
                (ResourceManagerReloadListener) manager -> ColorIndex.clear());
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("curvegen", "placement"), (dc, tickCounter) -> Placement.renderHud(dc));
    }
}
