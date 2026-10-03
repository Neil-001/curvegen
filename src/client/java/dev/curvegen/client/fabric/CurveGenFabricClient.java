package dev.curvegen.client.fabric;

import dev.curvegen.client.ClientPlatform;
import dev.curvegen.client.ColorIndex;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.Placement;
import dev.curvegen.net.PlaceBlocksPayload;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Fabric's client entrypoint. */
public class CurveGenFabricClient implements ClientModInitializer, ClientPlatform {
    @Override
    public void onInitializeClient() {
        CurveGenClient.init(this).forEach(KeyMappingHelper::registerKeyMapping);
        ClientTickEvents.END_CLIENT_TICK.register(CurveGenClient::tick);
        LevelRenderEvents.END_MAIN.register(ctx -> Placement.render(ctx.poseStack(), ctx.levelState().cameraRenderState.pos));
        // Block colours come from the active resource packs, so recompute them whenever packs change.
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Identifier.fromNamespaceAndPath("curvegen", "block_colours"),
                (ResourceManagerReloadListener) manager -> ColorIndex.clear());
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("curvegen", "placement"), (dc, tickCounter) -> Placement.renderHud(dc));
    }

    @Override public boolean canSendToServer() { return ClientPlayNetworking.canSend(PlaceBlocksPayload.ID); }
    @Override public void sendToServer(PlaceBlocksPayload payload) { ClientPlayNetworking.send(payload); }
    @Override public Path configDir() { return FabricLoader.getInstance().getConfigDir(); }
    @Override public Path gameDir() { return FabricLoader.getInstance().getGameDir(); }
}
