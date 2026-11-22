# Server placement

- Apply placement batches on the server thread, including in singleplayer. Require the same permission as `/setblock` so the payload and command fallback have the same operator requirement.
- Restore undo batches with block side effects suppressed so an attached block can be restored before its support. Track restored positions per player and notify neighbours when the final batch arrives. Then fences and panes can reconnect, grass can react to snow, and unsupported sand can fall. A new placement clears that player's unfinished undo list.
- Keep completion messages short enough for a single line above the hotbar, where Minecraft cannot wrap them.
