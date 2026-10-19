package dev.curvegen.client.fabric;

import dev.curvegen.client.ClientPlatform;
import dev.curvegen.client.ColorIndex;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.Placement;
import dev.curvegen.client.edit.Editor;
import dev.curvegen.client.edit.Hologram;
import dev.curvegen.net.PlaceBlocksPayload;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Fabric's client entrypoint. */
public class CurveGenFabricClient implements ClientModInitializer, ClientPlatform {
    @Override
    public void onInitializeClient() {
        KeyMapping.Category.register(CurveGenClient.CATEGORY.id());
        CurveGenClient.init(this).forEach(KeyBindingHelper::registerKeyBinding);
        ClientTickEvents.END_CLIENT_TICK.register(CurveGenClient::tick);
        // The editor draws after entities, before water, and keeps what goes over the water for the end of the main pass.
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> Editor.render(ctx.matrices(), ctx.worldState().cameraRenderState.pos));
        WorldRenderEvents.END_MAIN.register(ctx -> Hologram.drawAfterWater());
        // Block colours come from the active resource packs, so recompute them whenever packs change.
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(Identifier.fromNamespaceAndPath("curvegen", "block_colours"),
                (ResourceManagerReloadListener) manager -> ColorIndex.clear());
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("curvegen", "placement"), (dc, tick) -> Editor.renderHud(dc));
        // Keeps the replies to the mod's own /setblock commands out of chat, on a server without the mod.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !Placement.hidesFeedback(message, overlay));
    }

    @Override public boolean canSendToServer() { return ClientPlayNetworking.canSend(PlaceBlocksPayload.ID); }
    @Override public void sendToServer(PlaceBlocksPayload payload) { ClientPlayNetworking.send(payload); }
    @Override public Path configDir() { return FabricLoader.getInstance().getConfigDir(); }
    @Override public Path gameDir() { return FabricLoader.getInstance().getGameDir(); }
    @Override public void modelParts(BlockStateModel model, BlockState state, RandomSource random, List<BlockModelPart> out) {
        model.collectParts(random, out);
    }
    @Override public TextureAtlasSprite particleIcon(BlockStateModel model, BlockState state) { return model.particleIcon(); }
}
