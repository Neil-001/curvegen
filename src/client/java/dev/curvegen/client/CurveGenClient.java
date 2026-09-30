package dev.curvegen.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curvegen.client.screen.CurveScreen;
import dev.curvegen.core.ShapeSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.glfw.GLFW;

public class CurveGenClient implements ClientModInitializer {
    /** Shape settings live for the whole session, so reopening the screen picks up where you left off. */
    public static final ShapeSettings SETTINGS = new ShapeSettings();

    private static final String CAT = "category.curvegen";
    public static KeyMapping OPEN, CONFIRM, CANCEL, ROTATE, RAISE, LOWER, LOCK, UNDO;

    private static KeyMapping key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.curvegen." + name, InputConstants.Type.KEYSYM, code, CAT));
    }

    @Override
    public void onInitializeClient() {
        OPEN = key("open", GLFW.GLFW_KEY_G);
        CONFIRM = key("confirm", GLFW.GLFW_KEY_ENTER);
        CANCEL = key("cancel", GLFW.GLFW_KEY_BACKSPACE);
        ROTATE = key("rotate", GLFW.GLFW_KEY_R);
        RAISE = key("raise", GLFW.GLFW_KEY_PAGE_UP);
        LOWER = key("lower", GLFW.GLFW_KEY_PAGE_DOWN);
        LOCK = key("lock", GLFW.GLFW_KEY_K);
        UNDO = key("undo", GLFW.GLFW_KEY_Z);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN.consumeClick()) mc.setScreen(new CurveScreen());
            while (UNDO.consumeClick()) Placement.undo();
            if (Placement.isActive()) {
                while (CONFIRM.consumeClick()) Placement.confirm();
                while (CANCEL.consumeClick()) Placement.cancel();
                while (ROTATE.consumeClick()) Placement.rotate();
                while (RAISE.consumeClick()) Placement.raise(1);
                while (LOWER.consumeClick()) Placement.raise(-1);
                while (LOCK.consumeClick()) Placement.toggleLock();
            } else {
                // Drain presses so they don't fire later when placement starts.
                while (CONFIRM.consumeClick() || CANCEL.consumeClick() || ROTATE.consumeClick()
                        || RAISE.consumeClick() || LOWER.consumeClick() || LOCK.consumeClick()) { }
            }
            Placement.tick(mc);
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(Placement::render);
        // Block colours come from the active resource packs, so recompute them whenever packs change.
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() { return ResourceLocation.fromNamespaceAndPath("curvegen", "block_colours"); }
            @Override public void onResourceManagerReload(ResourceManager manager) { ColorIndex.clear(); }
        });
        HudRenderCallback.EVENT.register((dc, tickCounter) -> Placement.renderHud(dc));
    }
}
