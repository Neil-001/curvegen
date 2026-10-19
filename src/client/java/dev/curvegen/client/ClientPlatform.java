package dev.curvegen.client;

import dev.curvegen.net.PlaceBlocksPayload;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the client needs from the mod loader. The loader's client entrypoint passes one to {@link CurveGenClient#init}.
 *
 * <p>Each loader also hooks the mouse while no screen is open: it passes button presses and releases to
 * {@code Editor.mouseButton} and wheel movement to {@code Editor.mouseScroll}, and keeps the event from the game
 * when they return true. It passes every key event to {@code StepHold.keyEvent}, keeps a system message out of
 * chat when {@code Placement.hidesFeedback} returns true, and calls {@code Placement.commandSent} for every command
 * packet the client sends.
 */
public interface ClientPlatform {
    /** Whether the server we're connected to accepts placement packets, which means it has the mod. */
    boolean canSendToServer();

    void sendToServer(PlaceBlocksPayload payload);

    Path configDir();

    Path gameDir();

    /**
     * A block model's quads on one side, or those on no side for null, and its particle texture, without a world.
     * NeoForge extends the game's methods for these with model data, so each loader calls its own.
     */
    List<BakedQuad> quads(BakedModel model, BlockState state, Direction side, RandomSource random);

    TextureAtlasSprite particleIcon(BakedModel model, BlockState state);
}
