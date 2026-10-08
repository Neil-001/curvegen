package dev.curvegen.client;

import dev.curvegen.net.PlaceBlocksPayload;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the client needs from the mod loader. The loader's client entrypoint passes one to {@link CurveGenClient#init}.
 *
 * <p>Each loader also hooks the mouse while no screen is open: it passes button presses and releases to
 * {@code Editor.mouseButton} and wheel movement to {@code Editor.mouseScroll}, and keeps the event from the game
 * when they return true. It passes every key event to {@code StepHold.keyEvent}, and keeps a system message out of
 * chat when {@code Placement.hidesFeedback} returns true.
 */
public interface ClientPlatform {
    /** Whether the server we're connected to accepts placement packets, which means it has the mod. */
    boolean canSendToServer();

    void sendToServer(PlaceBlocksPayload payload);

    Path configDir();

    Path gameDir();

    /**
     * A block's model parts and its particle texture, without a world. NeoForge deprecates the game's own methods for
     * these in favour of ones that take a world and a position, so each loader calls its own.
     */
    void modelParts(BlockStateModel model, BlockState state, RandomSource random, List<BlockStateModelPart> out);

    Material.Baked particleMaterial(BlockStateModel model, BlockState state);
}
