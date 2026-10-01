package dev.curvegen;

import dev.curvegen.net.PlaceBlocksPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint. On a server (or the integrated server in singleplayer) it accepts
 * placement batches from operators only (permission level 2, the same as /setblock).
 */
public class CurveGen implements ModInitializer {
    public static final String MOD_ID = "curvegen";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** How far from the player a placement may reach. */
    public static final int MAX_DISTANCE = 512;

    /** Placing needs the same permission as /setblock: gamemaster, which is operator level 2. */
    public static boolean canPlace(Player player) {
        //? if >=1.21.11 {
        return player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        //?} else
        //return player.hasPermissions(2);
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(PlaceBlocksPayload.ID, PlaceBlocksPayload.CODEC);

        // Fabric runs play payload handlers on the server thread.
        ServerPlayNetworking.registerGlobalReceiver(PlaceBlocksPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!canPlace(player)) {
                if (payload.last())
                    player.displayClientMessage(Component.literal("Curve Generator: placing needs operator permissions (level 2). You can still export to Litematica.").withStyle(ChatFormatting.RED), false);
                return;
            }
            //? if >=1.21.6 {
            ServerLevel world = player.level();
            //?} else
            //ServerLevel world = player.serverLevel();
            int skipped = 0;
            for (int i = 0; i < payload.states().length; i++) {
                BlockPos pos = payload.origin().offset(payload.offsets()[3 * i], payload.offsets()[3 * i + 1], payload.offsets()[3 * i + 2]);
                BlockState state = Block.stateById(payload.states()[i]);
                if (!world.isInWorldBounds(pos) || !world.isLoaded(pos)
                        || !pos.closerThan(player.blockPosition(), MAX_DISTANCE)) { skipped++; continue; }
                world.setBlock(pos, state, Block.UPDATE_ALL);
            }
            if (payload.last()) {
                String what = payload.undo() ? "Undid the last placement" : "Placed " + payload.total() + " blocks";
                player.displayClientMessage(Component.literal("Curve Generator: " + what + (skipped > 0 ? " (" + skipped + " outside loaded chunks or build height were skipped in the last batch)" : "") + "."), false);
            }
        });
    }
}
