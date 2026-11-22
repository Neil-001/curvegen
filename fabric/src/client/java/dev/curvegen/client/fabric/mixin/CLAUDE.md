# Fabric client mixins

- Observe signed and unsigned command packets at `ClientCommonPacketListenerImpl.send` without cancelling them. Fabric's command event hooks `sendCommand`, and chat links can use `sendUnattendedCommand`. `Placement` needs both paths in send order so it can distinguish its fallback replies from the player's.
- Fabric's input events are scoped to screens. Observe keyboard input at `KeyboardHandler.keyPress` so hold-to-type can distinguish a fresh press from repeats. Let vanilla continue handling the key.
- Hook mouse buttons and scrolling while the game window receives input with neither a screen nor an overlay open. Give capture priority for clicks and cancel vanilla input only when capture or the editor handles it.
