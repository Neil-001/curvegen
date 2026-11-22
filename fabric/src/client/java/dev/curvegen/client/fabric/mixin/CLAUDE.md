# Fabric client mixins

- Observe signed and unsigned command packets at `ClientCommonPacketListenerImpl.send` without cancelling them. Fabric's command event misses `sendUnattendedCommand`, which chat links can use. The placement reply filter needs both paths in send order.
- Fabric's input events are scoped to screens. Observe `KeyboardHandler.keyPress` for fresh in-world presses and let vanilla continue handling them.
