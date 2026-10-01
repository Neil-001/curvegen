package dev.curvegen.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curvegen.client.screen.CurveScreen;
import dev.curvegen.core.ShapeSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.lwjgl.glfw.GLFW;

public class CurveGenClient implements ClientModInitializer {
    /** Shape settings live for the whole session, so reopening the screen picks up where you left off. */
    public static final ShapeSettings SETTINGS = new ShapeSettings();

    // Both forms use the translation key key.category.curvegen.main.
    //? if >=1.21.9 {
    private static final KeyMapping.Category CAT = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("curvegen", "main"));
    //?} else
    //private static final String CAT = "key.category.curvegen.main";
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
        // The hologram draws after translucent terrain. Fabric API has no event for that on 1.21.9 and 1.21.10,
        // so there LevelRendererMixin calls Placement.render.
        //? if >=1.21.11 {
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_MAIN.register(
                ctx -> Placement.render(ctx.matrices(), ctx.worldState().cameraRenderState.pos));
        //?} else if <1.21.9 {
        /*net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_TRANSLUCENT.register(
                ctx -> Placement.render(ctx.matrixStack(), ctx.camera().getPosition()));
        *///?}
        // Block colours come from the active resource packs, so recompute them whenever packs change.
        Identifier colours = Identifier.fromNamespaceAndPath("curvegen", "block_colours");
        ResourceManagerReloadListener clearColours = manager -> ColorIndex.clear();
        //? if >=1.21.9 {
        net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(colours, clearColours);
        //?} else {
        /*net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    @Override public Identifier getFabricId() { return colours; }
                    @Override public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                        clearColours.onResourceManagerReload(manager);
                    }
                });
        *///?}
        registerHud();
    }

    // 1.21.5 deprecates HudRenderCallback, but the replacement it offers exists only in that one version.
    //? if >=1.21.5 <1.21.6
    //@SuppressWarnings("deprecation")
    private static void registerHud() {
        //? if >=1.21.6 {
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("curvegen", "placement"), (dc, tickCounter) -> Placement.renderHud(dc));
        //?} else
        //net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((dc, tickCounter) -> Placement.renderHud(dc));
    }
}
