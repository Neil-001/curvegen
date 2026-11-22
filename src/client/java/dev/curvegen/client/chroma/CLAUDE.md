# Chroma Studio integration

- After the loader finds Chroma Studio, `ChromaBridge.supported` checks its API version before installing hooks. Releases 1.0 and 1.1 lack `apiVersion`. Missing hooks leave the gradient and palette features unavailable.
- Skip gradients CurveGen cannot use. Convert each palette to a mix with its original name and weights, summing duplicate cells and skipping unavailable blocks. Read these sources when their chooser opens.
- Cache matchers on the client thread by face, allowed blocks, and Chroma's index version. The returned matcher uses the captured index on worker threads. Match a subset with the distances from its existing matcher. Missing index data or a list with no known match uses CurveGen's colour fallback. An index version change can alter a paint result, so the editor repaints.
