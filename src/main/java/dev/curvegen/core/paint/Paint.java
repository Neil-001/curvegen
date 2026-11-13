package dev.curvegen.core.paint;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * How a shape is painted: one source at a time, chosen block by block after the solve. The settings belong to the
 * player rather than to one shape, and live in {@code ShapeSettings.paint}, so undo, "Last shape" and switching shape
 * carry them. A setting a shape can't use stays as chosen, and the shape reads it through {@link #fitFor} or
 * {@link #onFor}, which give the nearest valid one.
 *
 * <p>Each source has its own fields here, with copy, set, same, capture and apply. A later source (a gradient) adds
 * its own the same way.
 */
public final class Paint {
    public enum Source { OFF, PATTERN, MIX }
    /** How a mix lays out its blocks: each on its own, or in patches. */
    public enum Arrange { SCATTER, PATCHES }
    /** Where a 2D shape more than one block deep shows its pattern: the face the 2D picture shows, or round its edge. */
    public enum On { FACE, EDGE }
    public enum Fit { REPEAT, REPEAT_ALONG, STRETCH }
    /** What a pattern's empty cells become. Only FILL is built: they take the Blocks tab's choice. */
    public enum Empty { FILL, OPEN }
    /** How a pattern goes onto a 3D shape. 2D shapes don't use it. */
    public enum Mapping { WRAP, ABOVE, FRONT, SIDE }

    public static final int MAX_SIZE = 8, MAX_SLIDE = 4096;

    public Source source = Source.OFF;
    /** The pattern to paint with. Never null: it starts as the first bundled one. */
    public Pattern pattern = Patterns.STRIPES;
    public On on = On.FACE;
    public Mapping mapping = Mapping.WRAP;
    /** Null until the player picks one: each shape then takes its own default, from {@link #fitFor}. */
    public Fit fit;
    /** Blocks per pattern cell, each way. */
    public int size = 1;
    /** Degrees anticlockwise, 0 to 359. */
    public int turn;
    public boolean mirror;
    /** How far the pattern is moved along and across the shape, in blocks. */
    public int along, across;
    public Empty empty = Empty.FILL;
    /** "One material per colour" for a pattern. Off by default; a gradient will want its own, on by default. */
    public boolean patternOneMaterial;

    /** The mix to paint with. Never null: it starts as the bundled mossy pattern's blocks. */
    public Mix mix = Mix.of(Patterns.MOSSY_MIX);
    public Arrange arrange = Arrange.PATCHES;
    /** About how many blocks across a mix's patches are. */
    public int patch = 4;
    /** Which arrangement of the mix: Shuffle picks another. Saved with the shape, so preview and placing agree. */
    public int seed;
    /** "One material per colour" for a mix. Off by default. */
    public boolean mixOneMaterial;

    public boolean on() { return source != Source.OFF; }

    /** The fit a shape uses: the chosen one, or Repeat along for a line and Repeat for anything else. */
    public Fit fitFor(boolean line) { return fit != null ? fit : line ? Fit.REPEAT_ALONG : Fit.REPEAT; }

    /** Edge only applies to a shape more than one block deep. */
    public On onFor(int depth) { return depth > 1 ? on : On.FACE; }

    /** Whether "One material per colour" is on for the current source. */
    public boolean oneMaterial() { return source == Source.PATTERN ? patternOneMaterial : source == Source.MIX && mixOneMaterial; }

    /** What the current source paints with: the mix's blocks for a mix, and otherwise the pattern's cells. */
    public Palette palette() { return source == Source.MIX ? mix : pattern; }

    public Paint copy() {
        Paint p = new Paint();
        p.set(this);
        return p;
    }

    public void set(Paint o) {
        source = o.source; pattern = o.pattern; on = o.on; mapping = o.mapping; fit = o.fit; size = o.size; turn = o.turn;
        mirror = o.mirror; along = o.along; across = o.across; empty = o.empty; patternOneMaterial = o.patternOneMaterial;
        mix = o.mix; arrange = o.arrange; patch = o.patch; seed = o.seed; mixOneMaterial = o.mixOneMaterial;
    }

    public boolean same(Paint o) {
        return source == o.source && pattern.equals(o.pattern) && on == o.on && mapping == o.mapping && fit == o.fit && size == o.size
                && turn == o.turn && mirror == o.mirror && along == o.along && across == o.across && empty == o.empty
                && patternOneMaterial == o.patternOneMaterial && mix.equals(o.mix) && arrange == o.arrange && patch == o.patch
                && seed == o.seed && mixOneMaterial == o.mixOneMaterial;
    }

    /**
     * Whether fences, panes and walls should attach to the shape's full blocks, going by what most of the pattern's or
     * mix's blocks do. Null when it has no blocks, only colours or empty cells, so the Blocks tab's choice decides.
     */
    public static Boolean connects(Palette p, Predicate<String> connects) {
        long yes = 0, no = 0;
        for (int k = 0; k < p.paletteSize(); k++) {
            String b = p.cell(k).block();
            if (b == null) continue;
            if (connects.test(b)) yes += p.count(k); else no += p.count(k);
        }
        return yes + no == 0 ? null : yes >= no;
    }

    // ---------- as text ----------

    /** The settings as text, for the player's paint file and presets, with a copy of the pattern under "pattern." and the mix under "mix.". */
    public Map<String, String> capture() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("source", source.name());
        d.put("on", on.name());
        d.put("mapping", mapping.name());
        if (fit != null) d.put("fit", fit.name());
        d.put("size", String.valueOf(size));
        d.put("turn", String.valueOf(turn));
        d.put("mirror", String.valueOf(mirror));
        d.put("along", String.valueOf(along));
        d.put("across", String.valueOf(across));
        d.put("empty", empty.name());
        d.put("patternOneMaterial", String.valueOf(patternOneMaterial));
        d.put("arrange", arrange.name());
        d.put("patch", String.valueOf(patch));
        d.put("seed", String.valueOf(seed));
        d.put("mixOneMaterial", String.valueOf(mixOneMaterial));
        for (var e : pattern.encode().entrySet()) d.put("pattern." + e.getKey(), e.getValue());
        for (var e : mix.encode().entrySet()) d.put("mix." + e.getKey(), e.getValue());
        return d;
    }

    /**
     * Takes settings from {@link #capture}'s text. Missing or unreadable values keep their current setting, and so
     * do the pattern and the mix unless the text holds a whole, valid one. A source this version doesn't know turns paint off.
     */
    public void apply(Map<String, String> d) {
        if (d.containsKey("source")) source = enumOf(Source.class, d.get("source"), Source.OFF);
        on = enumOf(On.class, d.get("on"), on);
        mapping = enumOf(Mapping.class, d.get("mapping"), mapping);
        if (d.containsKey("fit")) fit = enumOf(Fit.class, d.get("fit"), fit);
        size = integer(d, "size", 1, MAX_SIZE, size);
        turn = integer(d, "turn", 0, 359, turn);
        if (d.containsKey("mirror")) mirror = Boolean.parseBoolean(d.get("mirror"));
        along = integer(d, "along", -MAX_SLIDE, MAX_SLIDE, along);
        across = integer(d, "across", -MAX_SLIDE, MAX_SLIDE, across);
        empty = enumOf(Empty.class, d.get("empty"), empty);
        if (d.containsKey("patternOneMaterial")) patternOneMaterial = Boolean.parseBoolean(d.get("patternOneMaterial"));
        arrange = enumOf(Arrange.class, d.get("arrange"), arrange);
        patch = integer(d, "patch", MixLayout.MIN_PATCH, MixLayout.MAX_PATCH, patch);
        seed = integer(d, "seed", Integer.MIN_VALUE, Integer.MAX_VALUE, seed);
        if (d.containsKey("mixOneMaterial")) mixOneMaterial = Boolean.parseBoolean(d.get("mixOneMaterial"));
        Pattern read = Pattern.decode(under(d, "pattern."));
        if (read != null) pattern = read;
        Mix m = Mix.decode(under(d, "mix."));
        if (m != null) mix = m;
    }

    private static Map<String, String> under(Map<String, String> d, String prefix) {
        Map<String, String> p = new LinkedHashMap<>();
        for (var e : d.entrySet()) if (e.getKey().startsWith(prefix)) p.put(e.getKey().substring(prefix.length()), e.getValue());
        return p;
    }

    private static int integer(Map<String, String> d, String k, int lo, int hi, int def) {
        try { return Math.max(lo, Math.min(hi, Integer.parseInt(d.get(k).trim()))); } catch (Exception _) { return def; }
    }

    private static <E extends Enum<E>> E enumOf(Class<E> c, String v, E def) {
        try { return Enum.valueOf(c, v.trim().toUpperCase(Locale.ROOT)); } catch (Exception _) { return def; }
    }
}
