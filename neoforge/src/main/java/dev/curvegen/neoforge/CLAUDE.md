# NeoForge server

Register the placement payload as optional so a client and server can connect when only one has Curve Generator. The registrar's default main-thread handler lets it call `CurveGen.place` directly. The server thread owns the world changes and the state shared across undo batches.
