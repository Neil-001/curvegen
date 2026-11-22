# Placement payload

The wire format uses raw block-state IDs, so the client and server must agree on their mapping. Fabric's registry sync remaps those IDs. ZigZag encoding keeps nearby negative offsets compact as VarInts. Keep the batch cap on both ends to bound packet size and the decoder's allocations.
