package dev.curvegen.client.neoforge.mixin;

import dev.curvegen.client.Placement;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge has no event for a command the client sends. The mod hides the replies to its own {@code /setblock}
 * commands on a server without it, and tells them from the player's by the order the commands went out in. It only watches.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
abstract class ClientCommonPacketListenerImplMixin {
    @Inject(method = "send", at = @At("HEAD"))
    private void curvegen$send(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ServerboundChatCommandPacket || packet instanceof ServerboundChatCommandSignedPacket) Placement.commandSent();
    }
}
