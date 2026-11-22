# Tests against Minecraft

- Construct states through the client mapping so the rule tests cover what placement sends. Bootstrap supplies blocks without data pack tags. Bind the wall, fence and leaf tags before asking blocks to update.
- Compare reachable states to Minecraft geometry and sturdy faces. A wall with no sides and no post is unreachable. For fences, chains and end rods, check containment in the outline because the solver scores visible geometry.
- Update neighbours in every direction and include a control that changes, so a mock that skips updates cannot pass. Use persistent leaves to avoid scheduled decay ticks.
- Cover aligned and per-layer holes and compare the no-hole path with direct placement. Include 2D posts inherited at several depths and a flat crossing.
- Keep fixtures for legacy block ID lists, custom full-block properties and materials that cannot be painted as-is. Check upright exports from the south and flat exports from above.
