# NeoForge client mixin

Observe signed and unsigned command packets at `ClientCommonPacketListenerImpl.send` without cancelling them. NeoForge has no client event for outgoing commands. `Placement` uses send order to distinguish replies to its fallback commands from the player's.
