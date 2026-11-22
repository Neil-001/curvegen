# Server placement

- Apply batches on the server thread, including in singleplayer. Match `/setblock` permissions so payload and fallback placement have the same operator requirement.
- Suppress block side effects during undo so dependents can be restored before their supports. Notify neighbours after the final batch so connections and updates settle. Track pending restores per player and clear them on a new placement.
- Keep completion messages to one overlay line. Minecraft cannot wrap them.
