# Core tests

- Reuse `Shapes3Cases` for common fixtures. The lattice models through-layer pattern holes. Exercise scattered holes and settling after a previous cut too.
- Keep `Rules3Test` and `ConnectionRulesTest` independent of the solver's lookup tables. Compare sparse and complete settling, and require some neighbours to change so the comparison exercises the affected region.
- Check every cell in each nonzero uniform run. Cover steep fields, jumps, poles and narrow domain edges. Compare seeded and independent lattice queries on folded or thin patches. Turning preserves sampling and volume, while the up direction can change piece selection.
- Exercise cancellation inside sampling, tall wall refinement and sparse settling. Keep the four-neighbour silhouette baseline and compare outline rectangles to pixels for every piece. Test overlapping lines behind the shape to catch repeated blending.
- Mutate every setting in `ShapeSettingsTest` to catch omissions from undo snapshots. Use `PaintTest` for individual paint fields.
- To time solving or previews, temporarily enable the timing test and run `./gradlew :fabric:test --tests '*Shapes3Timing*' --info` or use `'*Preview3Timing*'`. Measure face generation separately from drawing because moving the camera reuses the faces.
