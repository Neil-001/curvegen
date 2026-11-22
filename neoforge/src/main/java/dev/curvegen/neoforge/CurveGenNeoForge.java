package dev.curvegen.neoforge;

import dev.curvegen.CurveGen;
import dev.curvegen.net.PlaceBlocksPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(CurveGen.MOD_ID)
public class CurveGenNeoForge {
    public CurveGenNeoForge(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1").optional().playToServer(
                PlaceBlocksPayload.ID, PlaceBlocksPayload.CODEC, (payload, context) -> CurveGen.place((ServerPlayer) context.player(), payload)));
    }
}
