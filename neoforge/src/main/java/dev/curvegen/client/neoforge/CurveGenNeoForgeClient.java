package dev.curvegen.client.neoforge;

import dev.curvegen.CurveGen;
import dev.curvegen.client.ClientPlatform;
import dev.curvegen.client.ColorIndex;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.Placement;
import dev.curvegen.client.screen.SettingsScreen;
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
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge's client entrypoint. */
@Mod(value = CurveGen.MOD_ID, dist = Dist.CLIENT)
public final class CurveGenNeoForgeClient implements ClientPlatform {
    public CurveGenNeoForgeClient(IEventBus modBus, ModContainer container) {
        List<KeyMapping> keys = CurveGenClient.init(this);
        // The mod list's Config button.
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, modList) -> new SettingsScreen(modList));
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
                    if (!Minecraft.getInstance().gui.hud.isHidden()) Placement.renderHud(graphics);
                }));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> CurveGenClient.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class,
                event -> Placement.render(event.getSubmitNodeCollector(), event.getPoseStack(), event.getLevelRenderState().cameraRenderState.pos));
    }

    @Override public boolean canSendToServer() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(PlaceBlocksPayload.ID);
    }
    @Override public void sendToServer(PlaceBlocksPayload payload) { ClientPacketDistributor.sendToServer(payload); }
    @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    @Override public Path gameDir() { return FMLPaths.GAMEDIR.get(); }
}
