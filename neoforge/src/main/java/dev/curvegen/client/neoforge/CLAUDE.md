# NeoForge client

- Use the world-aware model methods with `BlockAndTintGetter.EMPTY` and `BlockPos.ZERO` so colour matching works outside a world. Clear the colour index when resource packs reload.
- NeoForge can run custom layers with the HUD hidden and mouse events with a screen open. Check both explicitly.
- Use the system chat event to filter fallback replies. The packet mixin supplies the send order the filter needs.
