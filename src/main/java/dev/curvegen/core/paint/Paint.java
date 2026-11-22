package dev.curvegen.core.paint;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public final class Paint {
    public enum Source { OFF, PATTERN, MIX, GRADIENT }
    public enum Arrange { SCATTER, PATCHES }
    public enum On { FACE, EDGE }
    public enum Fit { REPEAT, REPEAT_ALONG, STRETCH }
    public enum Empty { FILL, OPEN }
    public enum Flow { UP, ALONG, ACROSS, OUT }
    public enum Edges { SHARP, RAGGED }
    public enum Mapping { WRAP, ABOVE, FRONT, SIDE }

    public static final int MAX_SIZE = 8, MAX_SLIDE = 4096;

    public Source source = Source.OFF;
    public Pattern pattern = Patterns.STRIPES;
    public On on = On.FACE;
    public Mapping mapping = Mapping.WRAP;
    public Fit fit;
    public int size = 1;
    public int turn;
    public boolean mirror;
    public int along, across;
    public Empty empty = Empty.FILL;
    public boolean patternOneMaterial;

    public Mix mix = Mix.of(Patterns.MOSSY_MIX);
    public Arrange arrange = Arrange.PATCHES;
    public int patch = 4;
    public int seed;
    public boolean mixOneMaterial;

    public Gradient gradient;
    public Flow flow = Flow.UP;
    public boolean reverse;
    public Edges edges = Edges.RAGGED;
    public boolean gradientOneMaterial = true;

    public boolean on() { return source != Source.OFF && (source != Source.GRADIENT || gradient != null); }

    public Fit fitFor(boolean line) { return fit != null ? fit : line ? Fit.REPEAT_ALONG : Fit.REPEAT; }

    public Mapping mappingFor(boolean wraps) { return mapping == Mapping.WRAP && !wraps ? Mapping.ABOVE : mapping; }

    public Flow flowFor(boolean wraps) { return !wraps && (flow == Flow.ALONG || flow == Flow.ACROSS) ? Flow.UP : flow; }

    public On onFor(int depth) { return depth > 1 ? on : On.FACE; }

    public boolean oneMaterial() {
        return switch (source) {
            case OFF, PATTERN -> patternOneMaterial;
            case MIX -> mixOneMaterial;
            case GRADIENT -> gradientOneMaterial;
        };
    }

    public boolean[] holes() {
        if (source != Source.PATTERN || empty != Empty.OPEN || !hasHoles(pattern)) return null;
        boolean[] out = new boolean[pattern.paletteSize()];
        for (int k = 0; k < out.length; k++) out[k] = opens(pattern, k);
        return out;
    }

    public static boolean hasHoles(Pattern p) {
        for (int k = 0; k < p.paletteSize(); k++) if (opens(p, k)) return true;
        return false;
    }

    private static boolean opens(Pattern p, int k) { return p.count(k) > 0 && p.cell(k).equals(Pattern.Cell.EMPTY); }

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

    public static Boolean connects(Palette p, Predicate<String> connects) {
        long yes = 0, no = 0;
        for (int k = 0; k < p.paletteSize(); k++) {
            String b = p.cell(k).block();
            if (b == null) continue;
            if (connects.test(b)) yes += p.count(k); else no += p.count(k);
        }
        return yes + no == 0 ? null : yes >= no;
    }

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

    public void apply(Map<String, String> d) {
        if (d.containsKey("source")) source = enumOf(Source.class, d.get("source"), Source.OFF);
        on = enumOf(On.class, d.get("on"), on);
        mapping = enumOf(Mapping.class, d.get("mapping"), mapping);
        if (d.containsKey("fit")) fit = enumOf(Fit.class, d.get("fit"), fit);
        size = integer(d, "size", 1, MAX_SIZE, size);
        turn = integer(d, "turn", 0, 359, turn);
        mirror = bool(d, "mirror", mirror);
        along = integer(d, "along", -MAX_SLIDE, MAX_SLIDE, along);
        across = integer(d, "across", -MAX_SLIDE, MAX_SLIDE, across);
        empty = enumOf(Empty.class, d.get("empty"), empty);
        patternOneMaterial = bool(d, "patternOneMaterial", patternOneMaterial);
        arrange = enumOf(Arrange.class, d.get("arrange"), arrange);
        patch = integer(d, "patch", MixLayout.MIN_PATCH, MixLayout.MAX_PATCH, patch);
        seed = integer(d, "seed", Integer.MIN_VALUE, Integer.MAX_VALUE, seed);
        mixOneMaterial = bool(d, "mixOneMaterial", mixOneMaterial);
        flow = enumOf(Flow.class, d.get("flow"), flow);
        reverse = bool(d, "reverse", reverse);
        edges = enumOf(Edges.class, d.get("edges"), edges);
        gradientOneMaterial = bool(d, "gradientOneMaterial", gradientOneMaterial);
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
        String v = d.get(k);
        if (v == null) return def;
        try { return Math.max(lo, Math.min(hi, Integer.parseInt(v.trim()))); } catch (NumberFormatException _) { return def; }
    }

    private static boolean bool(Map<String, String> d, String k, boolean def) {
        String v = d.get(k);
        if (v == null) return def;
        v = v.trim();
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        return def;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> c, String v, E def) {
        if (v == null) return def;
        try { return Enum.valueOf(c, v.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException _) { return def; }
    }
}
