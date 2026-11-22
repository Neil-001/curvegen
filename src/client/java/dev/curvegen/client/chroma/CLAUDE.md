# Chroma Studio integration

- Check for API version 2 after the loader finds Chroma Studio. Releases 1.0 and 1.1 lack `apiVersion`.
- Read saved sources when their chooser opens. Sum the weights of duplicate palette cells when converting to a mix.
- Build matchers on the client thread and capture the index for worker use. Match subsets with distances from the existing matcher. Fall back to Curve Generator's colour matching when Chroma has no match. An index version change can change painted blocks and must invalidate the paint result.
