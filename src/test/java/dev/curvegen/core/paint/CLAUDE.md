# Paint tests

- To measure repaint times, temporarily remove `@Disabled` from the timing methods and run `./gradlew :fabric:test --tests '*Paint2DTiming' --tests '*Paint3Timing' --info`. Time paint after preparing the solve. The ordinary 3D runs time mapping setup separately from repainting, and the 3D hole runs measure settling with paint and time face listing separately.
- The wheel targets are 10 ms through 64 blocks across in 2D and 3D, and 40 ms through 200 in 2D and 128 in 3D. The two `repaintingIsQuick` tests warm up before measuring and use a loose bound to catch regressions across machines. The lattice opens four cells out of nine and exercises dense holes; sparse hole cases can use the local settling path.
- For tiling seams, sample away from cell boundaries so floating-point ties at a cell edge cannot change the expected answer. In mix frequency tests, patches have fewer independent picks than scatter. Allow more variation as patch size grows. For two equally weighted scatter entries, the expected same-entry run length is two blocks.
- The per-loop rotation test sets the angle close to 45° so the two loop lengths push it to opposite sides of the quarter-turn threshold.
- `TestWorld` gives sand a colour but excludes it from the allowed blocks. Use that fixture to test the closest-colour fallback for a named block that cannot be placed.
