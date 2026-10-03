package dev.curvegen;

import dev.curvegen.net.PlaceBlocksPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** What both sides share. The mod loader's entrypoint, in {@code dev.curvegen.fabric}, hooks it up. */
public final class CurveGen {
    private CurveGen() {}

    public static final String MOD_ID = "curvegen";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** How far from the player a placement may reach. */
    public static final int MAX_DISTANCE = 512;

    /**
     * Applies one placement batch on a server (or the integrated server in singleplayer). Only operators may place
     * (permission level 2, the same as /setblock). Call it on the server thread.
     */
    public static void place(ServerPlayer player, PlaceBlocksPayload payload) {
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            if (payload.last())
                player.displayClientMessage(Component.literal("Curve Generator: placing needs operator permissions (level 2). You can still export to Litematica.").withStyle(ChatFormatting.RED), false);
            return;
        }
        ServerLevel world = player.level();
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
    }
}
