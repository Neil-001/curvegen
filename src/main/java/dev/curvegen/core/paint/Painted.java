package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.paint.Ladder.Why;
import java.util.List;

/**
 * A painted shape: for each of its blocks, which block it takes. It never changes once made.
 *
 * <p>{@code slots} holds, per block, an index into {@code blocks}, or -1 for the Blocks tab's choice for its piece
 * type. A shape says what a slot is: a 2D shape's is {@code (k * ny + j) * nx + i} over {@code layers} layers, where
 * a single layer stands for every layer. A full block's entry is a block state with its properties; turn it by
 * {@code quarters} quarter turns anticlockwise about the pattern's normal. Every other entry is a block id.
 *
 * <p>{@code why} counts blocks by piece type and {@link Why}, and {@code counts} counts them by piece type and entry,
 * where count {@code [f][0]} is the Blocks tab's choice and {@code [f][e + 1]} entry e. Both count every layer.
 */
public record Painted(int[] slots, int layers, List<String> blocks, int quarters, long[][] why, long[][] counts) {
    public int at(int slot) { return slots[slot]; }

    /** How many blocks were painted in all. */
    public long total() {
        long n = 0;
        for (long[] f : why) for (long v : f) n += v;
        return n;
    }

    /** How many blocks took the closest colour. */
    public long closest() { return sum(Why.CLOSEST); }

    /** How many blocks fell back to the Blocks tab's choice because nothing fit or they'd have joined differently. */
    public long fellBack() { return sum(Why.GAP) + sum(Why.BLOCKS_TAB); }

    public long sum(Why w) {
        long n = 0;
        for (long[] f : why) n += f[w.ordinal()];
        return n;
    }

    public long count(Family f, Why w) { return why[f.ordinal()][w.ordinal()]; }
}
