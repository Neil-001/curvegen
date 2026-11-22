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

public final class CurveGen {
    private CurveGen() {}

    public static final String MOD_ID = "curvegen";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final int MAX_DISTANCE = 512;

    private static final Map<UUID, List<BlockPos>> RESTORED = new HashMap<>();

    public static void place(ServerPlayer player, PlaceBlocksPayload payload) {
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            if (payload.last())
                player.sendOverlayMessage(Component.translatable("curvegen.placement.permission").withStyle(ChatFormatting.RED));
            return;
        }
        ServerLevel world = player.level();
        int flags = payload.undo() ? Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS : Block.UPDATE_ALL;
        if (!payload.undo()) RESTORED.remove(player.getUUID());
        List<BlockPos> restored = payload.undo() ? RESTORED.computeIfAbsent(player.getUUID(), _ -> new ArrayList<>()) : null;
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
            String key = payload.undo() ? "curvegen.placement.undone" : "curvegen.placement.placed";
            if (skipped > 0) key += ".skipped";
            player.sendOverlayMessage(Component.translatable(key, payload.total(), skipped));
        }
    }
}
