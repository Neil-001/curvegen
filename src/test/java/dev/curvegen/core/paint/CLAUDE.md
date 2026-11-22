# Paint tests

- For timings, temporarily enable the timing methods and run `./gradlew :fabric:test --tests '*Paint2DTiming' --tests '*Paint3Timing' --info`. Exclude the solve and report 3D mapping setup and face listing separately. For hole cases, include settling in repaint time.
- Wheel repaint targets are 10 ms up to 64 blocks across and 40 ms up to 200 in 2D or 128 in 3D. The regression checks use looser bounds across machines. Keep both dense and sparse hole cases.
- Sample seams away from cell boundaries to avoid floating-point ties. Allow more frequency variation for larger mix patches because they have fewer independent picks. The per-loop rotation test uses angles near the quarter-turn threshold to detect a difference between strips.
- Use `TestWorld`'s sand to test colour fallback for a known colour on a block that cannot be placed.
