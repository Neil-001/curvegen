# Fabric client

- Keep Mod Menu imports in `ModMenuIntegration`. The separate entrypoint lets Curve Generator run when Mod Menu is absent.
- Clear `ColorIndex` on client resource reload because the active resource packs supply the block textures.
- Use `ALLOW_GAME` with `Placement.hidesFeedback` to filter replies to the placement fallback. The packet mixin supplies the send order that filter relies on.
