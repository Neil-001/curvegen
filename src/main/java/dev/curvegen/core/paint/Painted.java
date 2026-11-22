package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.Settle2D;
import dev.curvegen.core.paint.Ladder.Why;
import java.util.List;

public record Painted(int[] slots, int layers, List<String> blocks, int[] quarters, long[][] why, long[][] counts, long open, Settle2D settled) {
    public static final int OPEN = -2;

    public Painted(int[] slots, int layers, List<String> blocks, int[] quarters, long[][] why, long[][] counts) {
        this(slots, layers, blocks, quarters, why, counts, 0, null);
    }

    public int at(int slot) { return slots[slot]; }

    public long total() {
        long n = 0;
        for (long[] f : why) for (long v : f) n += v;
        return n;
    }

    public long closest() { return sum(Why.CLOSEST); }

    public long fellBack() { return sum(Why.GAP) + sum(Why.BLOCKS_TAB); }

    public long sum(Why w) {
        long n = 0;
        for (long[] f : why) n += f[w.ordinal()];
        return n;
    }

    public long count(Family f, Why w) { return why[f.ordinal()][w.ordinal()]; }
}
