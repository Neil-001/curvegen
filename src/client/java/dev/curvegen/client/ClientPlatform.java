package dev.curvegen.client;

import dev.curvegen.net.PlaceBlocksPayload;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public interface ClientPlatform {
    boolean canSendToServer();

    void sendToServer(PlaceBlocksPayload payload);

    Path configDir();

    boolean isModLoaded(String id);

    Path gameDir();

    void modelParts(BlockStateModel model, BlockState state, RandomSource random, List<BlockStateModelPart> out);

    Material.Baked particleMaterial(BlockStateModel model, BlockState state);
}
