package dev.curvegen.core.paint;

import dev.curvegen.core.paint.Pattern.Cell;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A gradient saved in Chroma Studio, copied in whole so it paints the same once Chroma Studio has forgotten it. It never
 * changes once made. Its steps run from the gradient's start to its end; each has the colour it stands for and the full
 * block the player saw for it there, hand swaps included. The block is kept as text, so a block this game lacks is kept
 * too, and painting treats it as it treats any unknown block.
 *
 * <p>As a {@link Palette} it has one entry per step, in order, each a cell holding both the step's block and its ideal
 * colour, so {@link Ladder} gives a full block that block and other pieces that block's material or else the piece
 * type's block closest to the ideal colour. Every step counts the same.
 *
 * <p>As text ({@link #encode}): "name", "stops" and "steps", each space separated. A stop is {@code #rrggbb}, or
 * {@code #rrggbb@} and its block for a block stop. A step is {@code #rrggbb@} and its block, then {@code !} when the
 * player chose the block by hand: {@code #8a6f4c@minecraft:oak_log[axis=y]!}.
 */
public final class Gradient implements Palette {
    /** One stop: a colour, and the block it came from for a block stop, or null. */
    public record Stop(int rgb, String block) {}

    /** One step: the colour it stands for, the block chosen for it, and whether the player chose that block by hand. */
    public record Step(int ideal, String block, boolean byHand) {}

    public final String name;
    private final List<Stop> stops;
    private final List<Step> steps;
    private final Cell[] cells;

    /** There must be at least one step, and every step needs a block. */
    public Gradient(String name, List<Stop> stops, List<Step> steps) {
        if (name == null || steps.isEmpty()) throw new IllegalArgumentException();
        this.name = name;
        this.stops = List.copyOf(stops);
        this.steps = List.copyOf(steps);
        cells = new Cell[steps.size()];
        for (int k = 0; k < cells.length; k++) {
            Step s = steps.get(k);
            if (s.block == null || Cell.parse(s.block) == null || Cell.parse(s.block).state() == null) throw new IllegalArgumentException();
            cells[k] = new Cell(s.block, s.ideal & 0xFFFFFF);
        }
    }

    public List<Stop> stops() { return stops; }
    public List<Step> steps() { return steps; }

    @Override public int paletteSize() { return cells.length; }
    @Override public Cell cell(int index) { return cells[index]; }
    @Override public int count(int index) { return 1; }

    public Map<String, String> encode() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("name", name);
        StringBuilder b = new StringBuilder();
        for (Stop s : stops) b.append(b.isEmpty() ? "" : " ").append(hex(s.rgb)).append(s.block == null ? "" : "@" + s.block);
        d.put("stops", b.toString());
        b.setLength(0);
        for (Step s : steps) b.append(b.isEmpty() ? "" : " ").append(hex(s.ideal)).append('@').append(s.block).append(s.byHand ? "!" : "");
        d.put("steps", b.toString());
        return d;
    }

    /** Reads {@link #encode}'s text back, or returns null when it isn't a whole, valid gradient. */
    public static Gradient decode(Map<String, String> d) {
        if (d == null || d.get("steps") == null) return null;
        try {
            List<Stop> stops = new ArrayList<>();
            String st = d.getOrDefault("stops", "").trim();
            if (!st.isEmpty())
                for (String t : st.split(" +")) {
                    int at = t.indexOf('@');
                    String block = at < 0 ? null : t.substring(at + 1);
                    if (block != null && !isBlock(block)) return null;
                    stops.add(new Stop(colour(at < 0 ? t : t.substring(0, at)), block));
                }
            List<Step> steps = new ArrayList<>();
            for (String t : d.get("steps").trim().split(" +")) {
                int at = t.indexOf('@');
                if (at < 0) return null;
                boolean hand = t.endsWith("!");
                String block = t.substring(at + 1, hand ? t.length() - 1 : t.length());
                if (!isBlock(block)) return null;
                steps.add(new Step(colour(t.substring(0, at)), block, hand));
            }
            String name = d.get("name");
            return new Gradient(name == null ? "" : name, stops, steps);
        } catch (RuntimeException _) {
            return null;
        }
    }

    private static boolean isBlock(String t) {
        Cell c = Cell.parse(t);
        return c != null && c.state() != null;
    }

    private static String hex(int rgb) { return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF); }

    private static int colour(String t) {
        if (!t.startsWith("#") || t.length() != 7) throw new IllegalArgumentException();
        return Integer.parseInt(t.substring(1), 16);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Gradient g && g.name.equals(name) && g.stops.equals(stops) && g.steps.equals(steps);
    }

    @Override
    public int hashCode() { return (name.hashCode() * 31 + stops.hashCode()) * 31 + steps.hashCode(); }
}
