package dev.curvegen.client.neoforge;

import dev.curvegen.CurveGen;
import dev.curvegen.client.ClientPlatform;
import dev.curvegen.client.ColorIndex;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.Placement;
import dev.curvegen.client.edit.Editor;
import dev.curvegen.client.edit.Hologram;
import dev.curvegen.client.edit.StepHold;
import dev.curvegen.client.screen.SettingsScreen;
import dev.curvegen.net.PlaceBlocksPayload;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** NeoForge's client entrypoint. */
@Mod(value = CurveGen.MOD_ID, dist = Dist.CLIENT)
public final class CurveGenNeoForgeClient implements ClientPlatform {
    public CurveGenNeoForgeClient(IEventBus modBus, ModContainer container) {
        List<KeyMapping> keys = CurveGenClient.init(this);
        // The mod list's Config button.
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, modList) -> new SettingsScreen(modList));
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> keys.forEach(event::register));
        // Block colours come from the active resource packs, so recompute them whenever packs change.
        modBus.addListener(RegisterClientReloadListenersEvent.class, event -> event.registerReloadListener(
                (ResourceManagerReloadListener) manager -> ColorIndex.clear()));
        // NeoForge draws modded layers even with the HUD hidden (F1). Fabric doesn't, so this checks for it.
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath("curvegen", "placement"), (graphics, tick) -> {
                    if (!Minecraft.getInstance().options.hideGui) Editor.renderHud(graphics);
                }));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> CurveGenClient.tick(Minecraft.getInstance()));
        // The editor takes a click or a scroll only when it uses it. Both events fire for screens too, which it leaves alone.
        NeoForge.EVENT_BUS.addListener(InputEvent.MouseButton.Pre.class, event -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null && mc.getOverlay() == null
                    && Editor.mouseButton(event.getButton(), event.getAction() == 1)) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(InputEvent.MouseScrollingEvent.class, event -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null && mc.getOverlay() == null && Editor.mouseScroll(event.getScrollDeltaY())) event.setCanceled(true);
        });
        // Hold-to-type tells a new press from the repeats of a held key by seeing it come up, screen or no screen.
        NeoForge.EVENT_BUS.addListener(InputEvent.Key.class, event -> StepHold.keyEvent(event.getKey(), event.getScanCode(), event.getAction()));
        // Keeps the replies to the mod's own /setblock commands out of chat, on a server without the mod.
        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.System.class, event -> {
            if (Placement.hidesFeedback(event.getMessage(), event.isOverlay())) event.setCanceled(true);
        });
        // The editor draws after entities, before water, and keeps what goes over the water for after it.
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES)
                Editor.render(event.getPoseStack() != null ? event.getPoseStack() : new PoseStack(), event.getCamera().getPosition());
            else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) Hologram.drawAfterWater();
        });
    }

    @Override public boolean canSendToServer() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(PlaceBlocksPayload.ID);
    }
    @Override public void sendToServer(PlaceBlocksPayload payload) { PacketDistributor.sendToServer(payload); }
    @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    @Override public Path gameDir() { return FMLPaths.GAMEDIR.get(); }
    // The colours are worked out without a world, so NeoForge's methods get no model data.
    @Override public List<BakedQuad> quads(BakedModel model, BlockState state, Direction side, RandomSource random) {
        return model.getQuads(state, side, random, ModelData.EMPTY, null);
    }
    @Override public TextureAtlasSprite particleIcon(BakedModel model, BlockState state) { return model.getParticleIcon(ModelData.EMPTY); }
}
