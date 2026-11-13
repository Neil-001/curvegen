package dev.curvegen.core.paint;

import dev.curvegen.core.paint.Pattern.Cell;

/**
 * The distinct cells a source paints with, each with how much of the source it makes up. A {@link Pattern}'s count is
 * how many of its cells hold the entry, a {@link Mix}'s is its weight. {@link Ladder} works out each entry's block per
 * piece type, and {@link Paint#connects} weighs the entries to decide whether fences attach.
 */
public interface Palette {
    int paletteSize();
    Cell cell(int index);
    /** How much of the source the entry makes up. Never negative. */
    int count(int index);
}
