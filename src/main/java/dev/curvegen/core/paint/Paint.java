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
 * <p>Each source has its own fields here, with copy, set, same, capture and apply.
 */
public final class Paint {
    public enum Source { OFF, PATTERN, MIX, GRADIENT }
    /** How a mix lays out its blocks: each on its own, or in patches. */
    public enum Arrange { SCATTER, PATCHES }
    /** Where a 2D shape more than one block deep shows its pattern: the face the 2D picture shows, or round its edge. */
    public enum On { FACE, EDGE }
    public enum Fit { REPEAT, REPEAT_ALONG, STRETCH }
    /**
     * What a pattern's empty cells become: the Blocks tab's choice, or holes. Only a truly empty cell opens (air in a
     * capture, a clear pixel in an image); a captured block painting can't use always fills.
     */
    public enum Empty { FILL, OPEN }
    /** Which way a gradient runs: bottom to top, along the shape, across it, or outwards from its middle. */
    public enum Flow { UP, ALONG, ACROSS, OUT }
    /** Whether a gradient's steps meet in straight lines or scatter into each other. */
    public enum Edges { SHARP, RAGGED }
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

    /** The gradient to paint with, copied from Chroma Studio, or null until one is chosen: paint is then off. */
    public Gradient gradient;
    public Flow flow = Flow.UP;
    public boolean reverse;
    public Edges edges = Edges.RAGGED;
    /** "One material per colour" for a gradient. On by default. */
    public boolean gradientOneMaterial = true;

    /** Whether the shape is painted: a source is chosen, and for a gradient, a gradient too. */
    public boolean on() { return source != Source.OFF && (source != Source.GRADIENT || gradient != null); }

    /** The fit a shape uses: the chosen one, or Repeat along for a line and Repeat for anything else. */
    public Fit fitFor(boolean line) { return fit != null ? fit : line ? Fit.REPEAT_ALONG : Fit.REPEAT; }

    /** The mapping a 3D shape uses: the chosen one, or From above for a shape a pattern can't wrap round, a 3D equation. */
    public Mapping mappingFor(boolean wraps) { return mapping == Mapping.WRAP && !wraps ? Mapping.ABOVE : mapping; }

    /** The way a gradient runs on a shape: the chosen one, or Bottom to top along or across a shape it can't wrap round, a 3D equation. */
    public Flow flowFor(boolean wraps) { return !wraps && (flow == Flow.ALONG || flow == Flow.ACROSS) ? Flow.UP : flow; }

    /** Edge only applies to a shape more than one block deep. */
    public On onFor(int depth) { return depth > 1 ? on : On.FACE; }

    /** Whether "One material per colour" is on for the current source. */
    public boolean oneMaterial() {
        return switch (source) {
            case OFF, PATTERN -> patternOneMaterial;
            case MIX -> mixOneMaterial;
            case GRADIENT -> gradientOneMaterial;
        };
    }

    /**
     * Which of the pattern's cells are holes, by palette index: its empty cells, when painting with a pattern and
     * "Empty spots" is Open. Null when nothing opens, which leaves the shape exactly as Fill would.
     */
    public boolean[] holes() {
        if (source != Source.PATTERN || empty != Empty.OPEN || !hasHoles(pattern)) return null;
        boolean[] out = new boolean[pattern.paletteSize()];
        for (int k = 0; k < out.length; k++) out[k] = opens(pattern, k);
        return out;
    }

    /** Whether a pattern has a cell that "Empty spots: Open" makes a hole. */
    public static boolean hasHoles(Pattern p) {
        for (int k = 0; k < p.paletteSize(); k++) if (opens(p, k)) return true;
        return false;
    }

    private static boolean opens(Pattern p, int k) { return p.count(k) > 0 && p.cell(k).equals(Pattern.Cell.EMPTY); }

    /** What the current source paints with: the mix's blocks, the gradient's steps, or otherwise the pattern's cells. */
    public Palette palette() {
        return source == Source.MIX ? mix : source == Source.GRADIENT && gradient != null ? gradient : pattern;
    }

    public Paint copy() {
        Paint p = new Paint();
        p.set(this);
        return p;
    }

    public void set(Paint o) {
        source = o.source; pattern = o.pattern; on = o.on; mapping = o.mapping; fit = o.fit; size = o.size; turn = o.turn;
        mirror = o.mirror; along = o.along; across = o.across; empty = o.empty; patternOneMaterial = o.patternOneMaterial;
        mix = o.mix; arrange = o.arrange; patch = o.patch; seed = o.seed; mixOneMaterial = o.mixOneMaterial;
        gradient = o.gradient; flow = o.flow; reverse = o.reverse; edges = o.edges; gradientOneMaterial = o.gradientOneMaterial;
    }

    public boolean same(Paint o) {
        return source == o.source && pattern.equals(o.pattern) && on == o.on && mapping == o.mapping && fit == o.fit && size == o.size
                && turn == o.turn && mirror == o.mirror && along == o.along && across == o.across && empty == o.empty
                && patternOneMaterial == o.patternOneMaterial && mix.equals(o.mix) && arrange == o.arrange && patch == o.patch
                && seed == o.seed && mixOneMaterial == o.mixOneMaterial && java.util.Objects.equals(gradient, o.gradient)
                && flow == o.flow && reverse == o.reverse && edges == o.edges && gradientOneMaterial == o.gradientOneMaterial;
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

    /**
     * The settings as text, for the player's paint file and presets, with a copy of the pattern under "pattern.", the
     * mix under "mix." and the gradient, when there is one, under "gradient.".
     */
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
        d.put("flow", flow.name());
        d.put("reverse", String.valueOf(reverse));
        d.put("edges", edges.name());
        d.put("gradientOneMaterial", String.valueOf(gradientOneMaterial));
        for (var e : pattern.encode().entrySet()) d.put("pattern." + e.getKey(), e.getValue());
        for (var e : mix.encode().entrySet()) d.put("mix." + e.getKey(), e.getValue());
        if (gradient != null) for (var e : gradient.encode().entrySet()) d.put("gradient." + e.getKey(), e.getValue());
        return d;
    }

    /**
     * Takes settings from {@link #capture}'s text. Missing or unreadable values keep their current setting, and so
     * do the pattern, the mix and the gradient unless the text holds a whole, valid one. A source this version doesn't know turns paint off.
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
        flow = enumOf(Flow.class, d.get("flow"), flow);
        if (d.containsKey("reverse")) reverse = Boolean.parseBoolean(d.get("reverse"));
        edges = enumOf(Edges.class, d.get("edges"), edges);
        if (d.containsKey("gradientOneMaterial")) gradientOneMaterial = Boolean.parseBoolean(d.get("gradientOneMaterial"));
        Pattern read = Pattern.decode(under(d, "pattern."));
        if (read != null) pattern = read;
        Mix m = Mix.decode(under(d, "mix."));
        if (m != null) mix = m;
        Gradient g = Gradient.decode(under(d, "gradient."));
        if (g != null) gradient = g;
    }

    private static Map<String, String> under(Map<String, String> d, String prefix) {
        Map<String, String> p = new LinkedHashMap<>();
        for (var e : d.entrySet()) if (e.getKey().startsWith(prefix)) p.put(e.getKey().substring(prefix.length()), e.getValue());
        return p;
    }

    private static int integer(Map<String, String> d, String k, int lo, int hi, int def) {
        try { return Math.max(lo, Math.min(hi, Integer.parseInt(d.get(k).trim()))); } catch (Exception ignored) { return def; }
    }

    private static <E extends Enum<E>> E enumOf(Class<E> c, String v, E def) {
        try { return Enum.valueOf(c, v.trim().toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return def; }
    }
}
