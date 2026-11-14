package dev.curvegen.core.paint;

import dev.curvegen.core.paint.Pattern.Cell;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A weighted set of blocks to paint with, laid out by {@link MixLayout}. It never changes once made. Anything can
 * supply one: {@link #of(Pattern)} takes a saved pattern's blocks, weighted by how often each appears, and a palette
 * from another mod can hand in its own blocks and weights. Entries are cells as a pattern holds them, so a full block
 * keeps its state (a log's axis) and an image's colour-only cell works too. None is empty.
 *
 * <p>As text ({@link #encode}): "name" and "blocks", the entries space separated, each a cell as {@link Pattern}
 * writes it followed by {@code *} and its weight: {@code minecraft:stone_bricks*5 #12ab34*2}. A name starting
 * {@link Pattern#BUNDLED} is a translation key, as a bundled pattern's is.
 */
public final class Mix implements Palette {
    public final String name;
    private final Cell[] cells;
    private final int[] weights;

    /**
     * @param weights each entry's share, all above 0. Entries must be distinct and none empty, and there must be at
     *                least one.
     */
    public Mix(String name, List<Cell> cells, int[] weights) {
        if (cells.isEmpty() || cells.size() != weights.length || name == null) throw new IllegalArgumentException();
        for (int k = 0; k < weights.length; k++)
            if (weights[k] <= 0 || cells.get(k).empty() || cells.indexOf(cells.get(k)) != k) throw new IllegalArgumentException();
        this.name = name;
        this.cells = cells.toArray(new Cell[0]);
        this.weights = weights.clone();
    }

    /** A pattern's blocks, each weighted by how many cells hold it. Empty cells are left out. Null when every cell is empty. */
    public static Mix of(Pattern p) {
        List<Cell> cells = new ArrayList<>();
        List<Integer> w = new ArrayList<>();
        for (int k = 0; k < p.paletteSize(); k++) {
            if (p.cell(k).empty() || p.count(k) == 0) continue;
            cells.add(p.cell(k));
            w.add(p.count(k));
        }
        if (cells.isEmpty()) return null;
        return new Mix(p.name, cells, w.stream().mapToInt(Integer::intValue).toArray());
    }

    @Override public int paletteSize() { return cells.length; }
    @Override public Cell cell(int index) { return cells[index]; }
    @Override public int count(int index) { return weights[index]; }

    public Map<String, String> encode() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("name", name);
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < cells.length; k++) b.append(k == 0 ? "" : " ").append(cells[k].text()).append('*').append(weights[k]);
        d.put("blocks", b.toString());
        return d;
    }

    /** Reads {@link #encode}'s text back, or returns null when it isn't a whole, valid mix. */
    public static Mix decode(Map<String, String> d) {
        if (d == null || d.get("blocks") == null) return null;
        try {
            List<Cell> cells = new ArrayList<>();
            String[] parts = d.get("blocks").trim().split(" +");
            int[] w = new int[parts.length];
            for (int k = 0; k < parts.length; k++) {
                int star = parts[k].lastIndexOf('*');
                if (star < 0) return null;
                Cell c = Cell.parse(parts[k].substring(0, star));
                if (c == null || c.empty()) return null;
                cells.add(c);
                w[k] = Integer.parseInt(parts[k].substring(star + 1));
            }
            String name = d.get("name");
            return new Mix(name == null ? "" : name, cells, w);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Mix m && m.name.equals(name) && Arrays.equals(m.cells, cells) && Arrays.equals(m.weights, weights);
    }

    @Override
    public int hashCode() { return (name.hashCode() * 31 + Arrays.hashCode(cells)) * 31 + Arrays.hashCode(weights); }
}
