package dev.curvegen.client.neoforge;

import dev.curvegen.CurveGen;
import dev.curvegen.client.ClientPlatform;
import dev.curvegen.client.ColorIndex;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.Placement;
import dev.curvegen.net.PlaceBlocksPayload;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge's client entrypoint. */
@Mod(value = CurveGen.MOD_ID, dist = Dist.CLIENT)
public final class CurveGenNeoForgeClient implements ClientPlatform {
    public CurveGenNeoForgeClient(IEventBus modBus) {
        List<KeyMapping> keys = CurveGenClient.init(this);
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.registerCategory(CurveGenClient.CATEGORY);
            keys.forEach(event::register);
        });
        // Block colours come from the active resource packs, so recompute them whenever packs change.
        modBus.addListener(AddClientReloadListenersEvent.class, event -> event.addListener(
                Identifier.fromNamespaceAndPath("curvegen", "block_colours"), (ResourceManagerReloadListener) manager -> ColorIndex.clear()));
        // NeoForge draws modded layers even with the HUD hidden (F1). Fabric doesn't, so this checks for it.
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(
                Identifier.fromNamespaceAndPath("curvegen", "placement"), (graphics, delta) -> {
                    if (!Minecraft.getInstance().options.hideGui) Placement.renderHud(graphics);
                }));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> CurveGenClient.tick(Minecraft.getInstance()));
        // The hologram draws in two passes, either side of translucent terrain. See Placement.render. The second is
        // the last stage of the main pass, where Fabric's END_MAIN also runs.
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentFeatures.class,
                event -> Placement.render(event.getPoseStack(), event.getLevelRenderState().cameraRenderState.pos, false));
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentParticles.class,
                event -> Placement.render(event.getPoseStack(), event.getLevelRenderState().cameraRenderState.pos, true));
    }

    @Override public boolean canSendToServer() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(PlaceBlocksPayload.ID);
    }
    @Override public void sendToServer(PlaceBlocksPayload payload) { ClientPacketDistributor.sendToServer(payload); }
    @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    @Override public Path gameDir() { return FMLPaths.GAMEDIR.get(); }
}
