package dev.curvegen.client.neoforge;

import dev.curvegen.CurveGen;
import dev.curvegen.client.ClientPlatform;
import dev.curvegen.client.ColorIndex;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.Placement;
import dev.curvegen.client.edit.Editor;
import dev.curvegen.client.edit.StepHold;
import dev.curvegen.client.screen.SettingsScreen;
import dev.curvegen.net.PlaceBlocksPayload;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
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
        container.registerExtensionPoint(IConfigScreenFactory.class, (_, modList) -> new SettingsScreen(modList));
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.registerCategory(CurveGenClient.CATEGORY);
            keys.forEach(event::register);
        });
        // Block colours come from the active resource packs, so recompute them whenever packs change.
        modBus.addListener(AddClientReloadListenersEvent.class, event -> event.addListener(
                Identifier.fromNamespaceAndPath("curvegen", "block_colours"), (ResourceManagerReloadListener) _ -> ColorIndex.clear()));
        // NeoForge draws modded layers even with the HUD hidden (F1). Fabric doesn't, so this checks for it.
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(
                Identifier.fromNamespaceAndPath("curvegen", "placement"), (graphics, _) -> {
                    if (!Minecraft.getInstance().gui.hud.isHidden()) Editor.renderHud(graphics);
                }));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, _ -> CurveGenClient.tick(Minecraft.getInstance()));
        // The editor takes a click or a scroll only when it uses it. Both events fire for screens too, which it leaves alone.
        NeoForge.EVENT_BUS.addListener(InputEvent.MouseButton.Pre.class, event -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() == null && mc.gui.overlay() == null
                    && Editor.mouseButton(event.getButton(), event.getAction() == 1)) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(InputEvent.MouseScrollingEvent.class, event -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() == null && mc.gui.overlay() == null && Editor.mouseScroll(event.getScrollDeltaY())) event.setCanceled(true);
        });
        // Hold-to-type tells a new press from the repeats of a held key by seeing it come up, screen or no screen.
        NeoForge.EVENT_BUS.addListener(InputEvent.Key.class, event -> StepHold.keyEvent(event.getKeyEvent(), event.getAction()));
        // Keeps the replies to the mod's own /setblock commands out of chat, on a server without the mod.
        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.System.class, event -> {
            if (Placement.hidesFeedback(event.getMessage(), event.isOverlay())) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class,
                event -> Editor.render(event.getSubmitNodeCollector(), event.getPoseStack(), event.getLevelRenderState().cameraRenderState.pos));
    }

    @Override public boolean canSendToServer() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(PlaceBlocksPayload.ID);
    }
    @Override public void sendToServer(PlaceBlocksPayload payload) { ClientPacketDistributor.sendToServer(payload); }
    @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    @Override public Path gameDir() { return FMLPaths.GAMEDIR.get(); }
    // The colours are worked out without a world, so NeoForge's methods get an empty one.
    @Override public void modelParts(BlockStateModel model, BlockState state, RandomSource random, List<BlockStateModelPart> out) {
        model.collectParts(BlockAndTintGetter.EMPTY, BlockPos.ZERO, state, random, out);
    }
    @Override public Material.Baked particleMaterial(BlockStateModel model, BlockState state) {
        return model.particleMaterial(BlockAndTintGetter.EMPTY, BlockPos.ZERO, state);
    }
}
