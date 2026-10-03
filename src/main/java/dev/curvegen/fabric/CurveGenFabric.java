package dev.curvegen.fabric;

import dev.curvegen.CurveGen;
import dev.curvegen.net.PlaceBlocksPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Fabric's common entrypoint. */
public class CurveGenFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(PlaceBlocksPayload.ID, PlaceBlocksPayload.CODEC);
        // Fabric runs play payload handlers on the server thread.
        ServerPlayNetworking.registerGlobalReceiver(PlaceBlocksPayload.ID, (payload, context) -> CurveGen.place(context.player(), payload));
    }
}
