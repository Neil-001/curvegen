package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.Settle2D;
import dev.curvegen.core.paint.Ladder.Why;
import java.util.List;

/**
 * A painted shape: for each of its blocks, which block it takes. It never changes once made.
 *
 * <p>{@code slots} holds, per block, an index into {@code blocks}, or -1 for the Blocks tab's choice for its piece
 * type. A shape says what a slot is: a 2D shape's is {@code (k * ny + j) * nx + i} over {@code layers} layers, where
 * a single layer stands for every layer. A 3D shape's is the block's place among the solve's blocks in grid order,
 * air left out, over one layer ({@link Painter3}). A full block's entry is a block state with its properties; turn it by
 * {@code quarters[entry]} quarter turns anticlockwise as the pattern is seen, which is how the pattern was turned
 * where that entry lies. Every other entry is a block id. The same block can be two entries, turned differently.
 *
 * <p>{@code why} counts blocks by piece type and {@link Why}, and {@code counts} counts them by piece type and entry,
 * where count {@code [f][0]} is the Blocks tab's choice and {@code [f][e + 1]} entry e. Both count every layer.
 *
 * <p>With "Empty spots: Open", a block on an empty pattern cell is a hole: its slot is {@link #OPEN}, and {@code why}
 * and {@code counts} leave it out. {@code open} counts the holes in every layer. Holes are taken out after the solve,
 * and the blocks beside them settle as the game would settle them. A 2D shape's settled blocks are in {@code settled},
 * null when it has no holes. A 3D shape's are in its painter's result ({@link Solve3Painter.Done}), and its slots
 * already leave the holes out.
 */
public record Painted(int[] slots, int layers, List<String> blocks, int[] quarters, long[][] why, long[][] counts, long open, Settle2D settled) {
    /** The slot of a block left open. */
    public static final int OPEN = -2;

    public Painted(int[] slots, int layers, List<String> blocks, int[] quarters, long[][] why, long[][] counts) {
        this(slots, layers, blocks, quarters, why, counts, 0, null);
    }

    public int at(int slot) { return slots[slot]; }

    /** How many blocks were painted in all, holes left out. */
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
