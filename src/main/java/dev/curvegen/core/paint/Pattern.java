package dev.curvegen.core.paint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Pattern implements Palette {
    public static final int MAX = 128;
    public static final String BUNDLED = "curvegen.pattern.";

    public record Cell(String state, int rgb) {
        public static final Cell EMPTY = new Cell(null, -1);
        public static final Cell UNUSABLE = new Cell(null, -2);

        public static Cell block(String state) { return new Cell(state, -1); }
        public static Cell colour(int rgb) { return new Cell(null, rgb & 0xFFFFFF); }

        public boolean empty() { return state == null && rgb < 0; }

        public String block() {
            if (state == null) return null;
            int b = state.indexOf('[');
            return b < 0 ? state : state.substring(0, b);
        }

        String text() {
            if (state != null) return state;
            if (rgb == UNUSABLE.rgb) return "?";
            return rgb < 0 ? "-" : String.format(Locale.ROOT, "#%06x", rgb);
        }

        public static Cell parse(String t) {
            if (t.equals("-")) return EMPTY;
            if (t.equals("?")) return UNUSABLE;
            if (t.startsWith("#") && t.length() == 7) {
                try { return colour(Integer.parseInt(t.substring(1), 16)); } catch (NumberFormatException _) { return null; }
            }
            return t.matches("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+(\\[[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*])?") ? block(t) : null;
        }
    }

    public final String name;
    public final int width, height;
    private final Cell[] palette;
    private final short[] cells;
    private final int[] counts;

    public Pattern(String name, int width, int height, List<Cell> palette, int[] grid) {
        if (width < 1 || height < 1 || width > MAX || height > MAX || grid.length != width * height || palette.isEmpty())
            throw new IllegalArgumentException();
        this.name = name; this.width = width; this.height = height;
        this.palette = palette.toArray(new Cell[0]);
        cells = new short[grid.length];
        counts = new int[this.palette.length];
        for (int k = 0; k < grid.length; k++) {
            if (grid[k] < 0 || grid[k] >= this.palette.length) throw new IllegalArgumentException();
            cells[k] = (short) grid[k];
            counts[grid[k]]++;
        }
    }

    public static Pattern of(String name, Cell[][] rowsTopFirst) {
        int h = rowsTopFirst.length, w = rowsTopFirst[0].length;
        List<Cell> palette = new ArrayList<>();
        Map<Cell, Integer> index = new HashMap<>();
        int[] grid = new int[w * h];
        for (int r = 0; r < h; r++)
            for (int x = 0; x < w; x++)
                grid[(h - 1 - r) * w + x] = index.computeIfAbsent(rowsTopFirst[r][x], c -> { palette.add(c); return palette.size() - 1; });
        return new Pattern(name, w, h, palette, grid);
    }

    public int at(int x, int y) { return cells[y * width + x]; }
    @Override public Cell cell(int index) { return palette[index]; }
    @Override public int paletteSize() { return palette.length; }
    @Override public int count(int index) { return counts[index]; }

    public boolean colourOnly() {
        boolean any = false;
        for (int k = 0; k < palette.length; k++) {
            if (counts[k] == 0 || palette[k].empty()) continue;
            if (palette[k].state() != null) return false;
            any = true;
        }
        return any;
    }

    public boolean sameCells(Pattern o) {
        if (o.width != width || o.height != height) return false;
        for (int k = 0; k < cells.length; k++) if (!palette[cells[k]].equals(o.palette[o.cells[k]])) return false;
        return true;
    }

    public Pattern renamed(String newName) {
        int[] grid = new int[cells.length];
        for (int k = 0; k < grid.length; k++) grid[k] = cells[k];
        return new Pattern(newName, width, height, List.of(palette), grid);
    }

    public Map<String, String> encode() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("name", name);
        d.put("width", String.valueOf(width));
        d.put("height", String.valueOf(height));
        StringBuilder p = new StringBuilder();
        for (Cell c : palette) p.append(p.isEmpty() ? "" : " ").append(c.text());
        d.put("palette", p.toString());
        StringBuilder rows = new StringBuilder();
        for (int y = height - 1; y >= 0; y--) {
            if (y < height - 1) rows.append('/');
            for (int x = 0; x < width; x++) rows.append(x == 0 ? "" : ",").append(cells[y * width + x]);
        }
        d.put("rows", rows.toString());
        return d;
    }

    public static Pattern decode(Map<String, String> d) {
        if (d == null) return null;
        try {
            int w = Integer.parseInt(d.get("width").trim()), h = Integer.parseInt(d.get("height").trim());
            if (w < 1 || h < 1 || w > MAX || h > MAX) return null;
            List<Cell> palette = new ArrayList<>();
            for (String t : d.get("palette").trim().split(" +")) {
                Cell c = Cell.parse(t);
                if (c == null || palette.contains(c)) return null;
                palette.add(c);
            }
            String[] rows = d.get("rows").trim().split("/");
            if (rows.length != h) return null;
            int[] grid = new int[w * h];
            for (int r = 0; r < h; r++) {
                String[] v = rows[r].split(",");
                if (v.length != w) return null;
                for (int x = 0; x < w; x++) grid[(h - 1 - r) * w + x] = Integer.parseInt(v[x].trim());
            }
            String name = d.getOrDefault("name", "");
            return new Pattern(name == null ? "" : name, w, h, palette, grid);
        } catch (RuntimeException _) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Pattern p && p.width == width && p.height == height && p.name.equals(name)
                && Arrays.equals(p.palette, palette) && Arrays.equals(p.cells, cells);
    }

    @Override
    public int hashCode() { return (name.hashCode() * 31 + Arrays.hashCode(palette)) * 31 + Arrays.hashCode(cells); }
}
