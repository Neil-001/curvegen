# NeoForge client

- Clear `ColorIndex` on client resource reload because the active resource packs supply the block textures. Use the world-aware model methods with `BlockAndTintGetter.EMPTY` and `BlockPos.ZERO` so colours can be indexed without a world.
- Check `hud.isHidden()` before drawing the placement and capture HUD. NeoForge can run custom layers with the HUD hidden.
- `MouseButton.Pre` also fires for screens. Keep clicks in the world, with neither a screen nor an overlay open, and give capture priority over the editor. `MouseScrollingEvent` handles in-world scrolls. Cancel either event only when the mod uses it.
- Feed `InputEvent.Key` to `StepHold` for fresh presses, since key mapping clicks include repeats.
- Use the system chat event with `Placement.hidesFeedback` to filter replies to the placement fallback. The packet mixin supplies the send order that filter relies on.
