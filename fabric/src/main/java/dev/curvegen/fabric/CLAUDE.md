# Fabric server

Fabric runs serverbound play payload handlers on the server thread, so the receiver can call `CurveGen.place` directly. That thread owns the world changes and the state shared across undo batches.
