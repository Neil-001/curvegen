package dev.curvegen.neoforge;

import dev.curvegen.CurveGen;
import dev.curvegen.net.PlaceBlocksPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NeoForge's common entrypoint. */
@Mod(CurveGen.MOD_ID)
public class CurveGenNeoForge {
    public CurveGenNeoForge(IEventBus modBus) {
        // Optional, so players can still join a server that doesn't have the mod, and the other way round.
        // NeoForge runs play payload handlers on the server thread unless told otherwise.
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1").optional().playToServer(
                PlaceBlocksPayload.ID, PlaceBlocksPayload.CODEC, (payload, context) -> CurveGen.place((ServerPlayer) context.player(), payload)));
    }
}
