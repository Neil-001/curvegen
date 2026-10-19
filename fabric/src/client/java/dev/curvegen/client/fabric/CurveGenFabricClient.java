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
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Fabric's client entrypoint. */
public class CurveGenFabricClient implements ClientModInitializer, ClientPlatform {
    @Override
    public void onInitializeClient() {
        CurveGenClient.init(this).forEach(KeyBindingHelper::registerKeyBinding);
        ClientTickEvents.END_CLIENT_TICK.register(CurveGenClient::tick);
        // The editor draws after entities, before water, and keeps what goes over the water for after it.
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> Editor.render(ctx.matrixStack(), ctx.camera().getPosition()));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> Hologram.drawAfterWater());
        // Block colours come from the active resource packs, so recompute them whenever packs change.
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() { return ResourceLocation.fromNamespaceAndPath("curvegen", "block_colours"); }
            @Override public void onResourceManagerReload(ResourceManager manager) { ColorIndex.clear(); }
        });
        HudRenderCallback.EVENT.register((dc, tick) -> {
            if (!Minecraft.getInstance().options.hideGui) Editor.renderHud(dc);
        });
        // Keeps the replies to the mod's own /setblock commands out of chat, on a server without the mod.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !Placement.hidesFeedback(message, overlay));
    }

    @Override public boolean canSendToServer() { return ClientPlayNetworking.canSend(PlaceBlocksPayload.ID); }
    @Override public void sendToServer(PlaceBlocksPayload payload) { ClientPlayNetworking.send(payload); }
    @Override public Path configDir() { return FabricLoader.getInstance().getConfigDir(); }
    @Override public Path gameDir() { return FabricLoader.getInstance().getGameDir(); }
    @Override public List<BakedQuad> quads(BakedModel model, BlockState state, Direction side, RandomSource random) {
        return model.getQuads(state, side, random);
    }
    @Override public TextureAtlasSprite particleIcon(BakedModel model, BlockState state) { return model.getParticleIcon(); }
}
