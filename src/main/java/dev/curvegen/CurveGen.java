package dev.curvegen;

import dev.curvegen.net.PlaceBlocksPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/** What both sides share. The mod loader's entrypoint, in {@code dev.curvegen.fabric} or {@code dev.curvegen.neoforge}, hooks it up. */
public final class CurveGen {
    private CurveGen() {}

    public static final String MOD_ID = "curvegen";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** How far from the player a placement may reach. */
    public static final int MAX_DISTANCE = 512;
    /** Per player, the positions an undo has restored so far. An undo arrives in batches, and the updates wait for the last one. */
    private static final Map<UUID, List<BlockPos>> RESTORED = new HashMap<>();

    /**
     * Applies one placement batch on a server (or the integrated server in singleplayer). Only operators may place
     * (permission level 2, the same as /setblock). Call it on the server thread.
     */
    public static void place(ServerPlayer player, PlaceBlocksPayload payload) {
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            if (payload.last())
                player.sendSystemMessage(Component.literal("Curve Generator: placing needs operator permissions (level 2). You can still export to Litematica.").withStyle(ChatFormatting.RED));
            return;
        }
        ServerLevel world = player.level();
        // Undo first puts every block back exactly as it was, without block updates. With them, a block restored before
        // the one it stands on or hangs from would break again. The updates run once the last batch is in.
        int flags = payload.undo() ? Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS : Block.UPDATE_ALL;
        if (!payload.undo()) RESTORED.remove(player.getUUID());   // drops the list of an undo that never finished
        List<BlockPos> restored = payload.undo() ? RESTORED.computeIfAbsent(player.getUUID(), id -> new ArrayList<>()) : null;
        int skipped = 0;
        for (int i = 0; i < payload.states().length; i++) {
            BlockPos pos = payload.origin().offset(payload.offsets()[3 * i], payload.offsets()[3 * i + 1], payload.offsets()[3 * i + 2]);
            BlockState state = Block.stateById(payload.states()[i]);
            if (!world.isInWorldBounds(pos) || !world.isLoaded(pos)
                    || !pos.closerThan(player.blockPosition(), MAX_DISTANCE)) { skipped++; continue; }
            world.setBlock(pos, state, flags);
            if (restored != null) restored.add(pos);
        }
        if (payload.last() && restored != null) {
            // Now that everything is back, let the surroundings react as they would to a normal block change: fences and
            // panes reconnect, grass under restored snow turns snowy, and sand left on the removed shape falls.
            RESTORED.remove(player.getUUID());
            for (BlockPos pos : restored) {
                BlockState state = world.getBlockState(pos);
                world.updateNeighborsAt(pos, state.getBlock());
                state.updateIndirectNeighbourShapes(world, pos, Block.UPDATE_ALL);
                state.updateNeighbourShapes(world, pos, Block.UPDATE_ALL);
                state.updateIndirectNeighbourShapes(world, pos, Block.UPDATE_ALL);
            }
        }
        if (payload.last()) {
            String what = payload.undo() ? "Undid the last placement" : "Placed " + payload.total() + " blocks";
            player.sendSystemMessage(Component.literal("Curve Generator: " + what + (skipped > 0 ? " (" + skipped + " outside loaded chunks or build height were skipped in the last batch)" : "") + "."));
        }
    }
}
