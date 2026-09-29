package dev.curvegen;

import dev.curvegen.net.PlaceBlocksPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Common entrypoint. On a server (or the integrated server in singleplayer) it accepts
 * placement batches from operators only (permission level 2, the same as /setblock).
 */
public class CurveGen implements ModInitializer {
    public static final String MOD_ID = "curvegen";
    /** How far from the player a placement may reach. */
    public static final int MAX_DISTANCE = 512;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(PlaceBlocksPayload.ID, PlaceBlocksPayload.CODEC);

        // Fabric runs play payload handlers on the server thread.
        ServerPlayNetworking.registerGlobalReceiver(PlaceBlocksPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (!player.hasPermissionLevel(2)) {
                if (payload.last())
                    player.sendMessage(Text.literal("Curve Generator: placing needs operator permissions (level 2). You can still export to Litematica.").formatted(Formatting.RED), false);
                return;
            }
            ServerWorld world = player.getServerWorld();
            int skipped = 0;
            for (int i = 0; i < payload.states().length; i++) {
                BlockPos pos = payload.origin().add(payload.offsets()[3 * i], payload.offsets()[3 * i + 1], payload.offsets()[3 * i + 2]);
                BlockState state = Block.getStateFromRawId(payload.states()[i]);
                if (!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos)
                        || !pos.isWithinDistance(player.getBlockPos(), MAX_DISTANCE)) { skipped++; continue; }
                world.setBlockState(pos, state, Block.NOTIFY_ALL);
            }
            if (payload.last()) {
                String what = payload.undo() ? "Undid the last placement" : "Placed " + payload.total() + " blocks";
                player.sendMessage(Text.literal("Curve Generator: " + what + (skipped > 0 ? " (" + skipped + " outside loaded chunks or build height were skipped in the last batch)" : "") + "."), false);
            }
        });
    }
}
