package dev.curvegen.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A preset stores one shape tab's own settings as text (so it survives format changes gracefully):
 * the ellipse's size and shape, the equation and its ranges, or the Bézier grid and points, and the same for each 3D shape.
 * It can also store the block choices: each piece type's block and whether it's used.
 * Orientation and depth aren't part of a preset.
 */
public final class PresetData {
    private PresetData() {}

    public record Example(String name, Map<String, String> data) {}

    public static Map<String, String> capture(ShapeSettings s, ShapeSettings.Gen gen) {
        Map<String, String> d = new LinkedHashMap<>();
        switch (gen) {
            case ELLIPSE -> {
                d.put("width", String.valueOf(s.eW));
                d.put("height", String.valueOf(s.eH));
                d.put("shape", s.eMode.name());
                d.put("thickness", num(s.eT));
            }
            case EQUATION -> {
                d.put("equation", s.src);
                d.put("xFrom", s.xmin); d.put("xTo", s.xmax);
                d.put("yFrom", s.ymin); d.put("yTo", s.ymax);
                d.put("width", String.valueOf(s.qW));
                d.put("height", String.valueOf(s.qH));
                d.put("sameScale", String.valueOf(s.qLock));
                d.put("shape", s.qMode.name());
                d.put("lineWidth", num(s.qLW));
            }
            case BEZIER -> {
                d.put("width", String.valueOf(s.bW));
                d.put("height", String.valueOf(s.bH));
                d.put("points", points(s.pts));
                d.put("shape", s.bMode.name());
                d.put("lineWidth", num(s.bLW));
            }
            case ELLIPSOID -> {
                d.put("width", String.valueOf(s.e3W));
                d.put("height", String.valueOf(s.e3H));
                d.put("depth", String.valueOf(s.e3D));
                d.put("shape", s.e3Mode.name());
                d.put("thickness", num(s.e3T));
            }
            case TORUS -> {
                d.put("ring", String.valueOf(s.tRing));
                d.put("tube", String.valueOf(s.tTube));
                d.put("width", String.valueOf(s.tW));
                d.put("height", String.valueOf(s.tH));
                d.put("depth", String.valueOf(s.tD));
                d.put("hollow", String.valueOf(s.tHollow));
            }
            case EQUATION3 -> {
                d.put("equation", s.src3);
                d.put("xFrom", s.x3min); d.put("xTo", s.x3max);
                d.put("yFrom", s.y3min); d.put("yTo", s.y3max);
                d.put("zFrom", s.z3min); d.put("zTo", s.z3max);
                d.put("width", String.valueOf(s.q3W));
                d.put("depth", String.valueOf(s.q3D));
                d.put("height", String.valueOf(s.q3H));
                d.put("sameScale", String.valueOf(s.q3Lock));
                d.put("shape", s.q3Mode.name());
                d.put("thickness", num(s.q3T));
            }
            case BEZIER3 -> {
                d.put("width", String.valueOf(s.b3W));
                d.put("height", String.valueOf(s.b3H));
                d.put("depth", String.valueOf(s.b3D));
                d.put("points", points(s.pts3));
                d.put("thickness", num(s.b3T));
            }
            case SURFACE -> {
                d.put("width", String.valueOf(s.sW));
                d.put("height", String.valueOf(s.sH));
                d.put("depth", String.valueOf(s.sD));
                d.put("rows", String.valueOf(s.sRows));
                d.put("columns", String.valueOf(s.sCols));
                d.put("points", points(s.sPts));
                d.put("thickness", num(s.sT));
            }
        }
        return d;
    }

    /** Applies a preset to the settings, switching to its tab. Missing or unreadable values keep their current setting. */
    public static void apply(Map<String, String> d, ShapeSettings.Gen gen, ShapeSettings s) {
        s.gen = gen;
        switch (gen) {
            case ELLIPSE -> {
                s.eW = integer(d, "width", 1, 400, s.eW);
                s.eH = integer(d, "height", 1, 400, s.eH);
                s.eMode = enumOf(ShapeSettings.EllipseMode.class, d.get("shape"), s.eMode);
                s.eT = decimal(d, "thickness", 0.0625, 50, s.eT);
            }
            case EQUATION -> {
                s.src = d.getOrDefault("equation", s.src);
                s.xmin = d.getOrDefault("xFrom", s.xmin); s.xmax = d.getOrDefault("xTo", s.xmax);
                s.ymin = d.getOrDefault("yFrom", s.ymin); s.ymax = d.getOrDefault("yTo", s.ymax);
                s.qW = integer(d, "width", 1, 400, s.qW);
                s.qH = integer(d, "height", 1, 400, s.qH);
                if (d.containsKey("sameScale")) s.qLock = Boolean.parseBoolean(d.get("sameScale"));
                s.qMode = enumOf(ShapeSettings.EqMode.class, d.get("shape"), s.qMode);
                s.qLW = decimal(d, "lineWidth", 0.0625, 50, s.qLW);
            }
            case BEZIER -> {
                s.bW = integer(d, "width", 1, 400, s.bW);
                s.bH = integer(d, "height", 1, 400, s.bH);
                List<double[]> pts = points(d.get("points"), 2);
                if (pts.size() >= 2 && pts.size() <= 10) { s.pts.clear(); s.pts.addAll(pts); }
                s.bMode = enumOf(ShapeSettings.BzMode.class, d.get("shape"), s.bMode);
                s.bLW = decimal(d, "lineWidth", 0.0625, 50, s.bLW);
            }
            case ELLIPSOID -> {
                s.e3W = integer(d, "width", 1, Shape3.MAX_SIZE, s.e3W);
                s.e3H = integer(d, "height", 1, Shape3.MAX_SIZE, s.e3H);
                s.e3D = integer(d, "depth", 1, Shape3.MAX_SIZE, s.e3D);
                s.e3Mode = enumOf(ShapeSettings.EllipseMode.class, d.get("shape"), s.e3Mode);
                s.e3T = decimal(d, "thickness", 0.0625, 50, s.e3T);
            }
            case TORUS -> {
                s.tRing = integer(d, "ring", 1, Shape3.MAX_SIZE, s.tRing);
                s.tTube = integer(d, "tube", 1, Shape3.MAX_SIZE, s.tTube);
                s.tW = integer(d, "width", 1, Shape3.MAX_SIZE, s.tW);
                s.tH = integer(d, "height", 1, Shape3.MAX_SIZE, s.tH);
                s.tD = integer(d, "depth", 1, Shape3.MAX_SIZE, s.tD);
                if (d.containsKey("hollow")) s.tHollow = Boolean.parseBoolean(d.get("hollow"));
            }
            case EQUATION3 -> {
                s.src3 = d.getOrDefault("equation", s.src3);
                s.x3min = d.getOrDefault("xFrom", s.x3min); s.x3max = d.getOrDefault("xTo", s.x3max);
                s.y3min = d.getOrDefault("yFrom", s.y3min); s.y3max = d.getOrDefault("yTo", s.y3max);
                s.z3min = d.getOrDefault("zFrom", s.z3min); s.z3max = d.getOrDefault("zTo", s.z3max);
                s.q3W = integer(d, "width", 1, Shape3.MAX_SIZE, s.q3W);
                s.q3D = integer(d, "depth", 1, Shape3.MAX_SIZE, s.q3D);
                s.q3H = integer(d, "height", 1, Shape3.MAX_SIZE, s.q3H);
                if (d.containsKey("sameScale")) s.q3Lock = Boolean.parseBoolean(d.get("sameScale"));
                s.q3Mode = enumOf(ShapeSettings.Eq3Mode.class, d.get("shape"), s.q3Mode);
                s.q3T = decimal(d, "thickness", 0.0625, 50, s.q3T);
            }
            case BEZIER3 -> {
                s.b3W = integer(d, "width", 1, Shape3.MAX_SIZE, s.b3W);
                s.b3H = integer(d, "height", 1, Shape3.MAX_SIZE, s.b3H);
                s.b3D = integer(d, "depth", 1, Shape3.MAX_SIZE, s.b3D);
                List<double[]> pts = points(d.get("points"), 3);
                if (pts.size() >= 2 && pts.size() <= Bezier3.MAX_POINTS) { s.pts3.clear(); s.pts3.addAll(pts); }
                s.b3T = decimal(d, "thickness", 0.0625, 50, s.b3T);
            }
            case SURFACE -> {
                s.sW = integer(d, "width", 1, Shape3.MAX_SIZE, s.sW);
                s.sH = integer(d, "height", 1, Shape3.MAX_SIZE, s.sH);
                s.sD = integer(d, "depth", 1, Shape3.MAX_SIZE, s.sD);
                // The grid only makes sense whole: its size and every point, or none of it.
                int rows = integer(d, "rows", Bezier3.MIN_GRID, Bezier3.MAX_GRID, 0), cols = integer(d, "columns", Bezier3.MIN_GRID, Bezier3.MAX_GRID, 0);
                List<double[]> pts = points(d.get("points"), 3);
                if (rows > 0 && cols > 0 && pts.size() == rows * cols) { s.sRows = rows; s.sCols = cols; s.sPts.clear(); s.sPts.addAll(pts); }
                s.sT = decimal(d, "thickness", 0.0625, 50, s.sT);
            }
        }
    }

    /** Control points as text: "x,y;x,y" for a 2D curve, "x,y,z;x,y,z" in 3D. */
    private static String points(List<double[]> pts) {
        List<String> out = new ArrayList<>();
        for (double[] p : pts) {
            StringBuilder b = new StringBuilder(num(p[0]));
            for (int k = 1; k < p.length; k++) b.append(',').append(num(p[k]));
            out.add(b.toString());
        }
        return String.join(";", out);
    }

    /** Reads points back, skipping any that don't have exactly {@code dims} finite numbers. */
    private static List<double[]> points(String text, int dims) {
        List<double[]> pts = new ArrayList<>();
        for (String p : (text == null ? "" : text).split(";")) {
            String[] parts = p.split(",");
            if (parts.length != dims) continue;
            double[] v = new double[dims];
            boolean ok = true;
            try {
                for (int k = 0; k < dims; k++) { v[k] = Double.parseDouble(parts[k].trim()); ok &= Double.isFinite(v[k]); }
            } catch (NumberFormatException _) { ok = false; }
            if (ok) pts.add(v);
        }
        return pts;
    }

    /**
     * The block choices as text: for each piece type, its block's id ("slab": "minecraft:oak_slab")
     * and, except for full blocks, whether it's used ("slabUsed": "true").
     */
    public static Map<String, String> captureBlocks(ShapeSettings s, Function<Pieces.Family, String> blockId) {
        Map<String, String> d = new LinkedHashMap<>();
        for (Pieces.Family f : Pieces.Family.values()) {
            if (f == Pieces.Family.AIR) continue;
            d.put(key(f), blockId.apply(f));
            if (f != Pieces.Family.FULL) d.put(key(f) + "Used", String.valueOf(s.allows(f)));
        }
        return d;
    }

    /**
     * Applies stored block choices: sets whether each piece type is used, and hands each stored block id to setBlock.
     * Missing values keep their current setting, except shelves, chains and end rods in presets older than them.
     */
    public static void applyBlocks(Map<String, String> d, ShapeSettings s, BiConsumer<Pieces.Family, String> setBlock) {
        // A preset saved before shelves existed lists every other piece type, and its build had no shelves.
        if (d.containsKey("wallUsed") && !d.containsKey("shelfUsed")) s.shelf = false;
        // The same goes for chains and end rods, which came later still.
        if (d.containsKey("wallUsed") && !d.containsKey("chainUsed")) s.chain = false;
        if (d.containsKey("wallUsed") && !d.containsKey("rodUsed")) s.rod = false;
        for (Pieces.Family f : Pieces.Family.values()) {
            if (f == Pieces.Family.AIR) continue;
            String used = d.get(key(f) + "Used");
            if ("true".equals(used) || "false".equals(used)) s.allow(f, Boolean.parseBoolean(used));
            String id = d.get(key(f));
            if (id != null) setBlock.accept(f, id);
        }
    }

    private static String key(Pieces.Family f) { return f.name().toLowerCase(Locale.ROOT); }

    /** A name describing the shape, used to pre-fill the Save dialog. */
    public static String defaultName(ShapeSettings s, ShapeSettings.Gen gen) {
        return switch (gen) {
            case ELLIPSE -> "Ellipse " + s.eW + "×" + s.eH + ", " + switch (s.eMode) {
                case THIN -> "thin"; case FILLED -> "filled";
                case OUTWARDS -> "thick outwards " + num(s.eT); case INWARDS -> "thick inwards " + num(s.eT);
                case MIDDLE -> "thick middle " + num(s.eT);
            };
            case EQUATION -> {
                String eq = s.src.trim().replaceAll("\\s+", " ");
                if (eq.length() > 32) eq = eq.substring(0, 31) + "…";
                boolean ineq = eq.contains("<") || eq.contains(">") || eq.contains("≤") || eq.contains("≥");
                String shape = ineq ? "" : ", " + switch (s.qMode) {
                    case LINE -> "line " + num(s.qLW); case UNDER -> "fill under"; case OVER -> "fill over"; };
                yield eq + shape + ", " + s.qW + " wide";
            }
            case BEZIER -> {
                int n = s.pts.size();
                String kind = n == 2 ? "Straight line" : n == 3 ? "Quadratic Bézier" : n == 4 ? "Cubic Bézier" : n + "-point Bézier";
                yield kind + " " + s.bW + "×" + s.bH + ", " + (s.bMode == ShapeSettings.BzMode.LINE ? "line " + num(s.bLW) : "filled");
            }
            case ELLIPSOID -> "Ellipsoid " + s.e3W + "×" + s.e3H + "×" + s.e3D + ", " + switch (s.e3Mode) {
                case THIN -> "thin"; case FILLED -> "filled";
                case OUTWARDS -> "thick outwards " + num(s.e3T); case INWARDS -> "thick inwards " + num(s.e3T);
                case MIDDLE -> "thick middle " + num(s.e3T);
            };
            case TORUS -> "Torus " + s.tW + "×" + s.tH + "×" + s.tD + ", tube " + s.tTube + (s.tHollow ? ", hollow" : ", filled");
            case EQUATION3 -> {
                String eq = s.src3.trim().replaceAll("\\s+", " ");
                if (eq.length() > 32) eq = eq.substring(0, 31) + "…";
                boolean ineq = eq.contains("<") || eq.contains(">") || eq.contains("≤") || eq.contains("≥");
                String shape = ineq ? "" : ", " + switch (s.q3Mode) {
                    case SURFACE -> "surface " + num(s.q3T); case BELOW -> "fill below"; case ABOVE -> "fill above"; };
                yield eq + shape + ", " + s.q3W + " wide";
            }
            case BEZIER3 -> {
                int n = s.pts3.size();
                String kind = n == 2 ? "Straight line" : n == 3 ? "Quadratic 3D Bézier" : n == 4 ? "Cubic 3D Bézier" : n + "-point 3D Bézier";
                yield kind + " " + s.b3W + "×" + s.b3H + "×" + s.b3D + ", thickness " + num(s.b3T);
            }
            case SURFACE -> "Bézier surface " + s.sRows + "×" + s.sCols + " points, " + s.sW + "×" + s.sH + "×" + s.sD + ", thickness " + num(s.sT);
        };
    }

    /**
     * A safe file name (without ".json") for a preset: its tab, then its name in lower case with
     * anything but letters and digits turned into dashes, e.g. "equation-heart".
     */
    public static String fileStem(ShapeSettings.Gen gen, String name) {
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String slug = plain.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (slug.length() > 60) slug = slug.substring(0, 60).replaceAll("-$", "");
        return gen.name().toLowerCase(Locale.ROOT) + (slug.isEmpty() ? "" : "-" + slug);
    }

    /** The examples that used to sit behind the Equation tab's example button, as starting presets. */
    public static List<Example> examples() {
        List<Example> out = new ArrayList<>();
        String[][] ex = {
                {"Sine wave", "y = 2sin(x)", "-2pi", "2pi", "-3", "3", "LINE"},
                {"Parabolic arch", "y = 9 - x^2/4", "-6", "6", "0", "9", "UNDER"},
                {"Catenary arch", "y = 14 - 2cosh(x/2)", "-5.2", "5.2", "0", "12", "UNDER"},
                {"Gothic arch", "y = sqrt(64 - (|x| + 3)^2)", "-5", "5", "0", "8", "UNDER"},
                {"Circle", "x^2 + y^2 = 16", "-5", "5", "-5", "5", "LINE"},
                {"Heart", "(x^2 + y^2 - 1)^3 = x^2 y^3", "-1.5", "1.5", "-1.3", "1.5", "UNDER"},
                {"Tangent", "y = tan(x)", "-4", "4", "-4", "4", "LINE"}};
        ShapeSettings base = new ShapeSettings();
        for (String[] e : ex) {
            Map<String, String> d = capture(base, ShapeSettings.Gen.EQUATION);
            d.put("equation", e[1]); d.put("xFrom", e[2]); d.put("xTo", e[3]); d.put("yFrom", e[4]); d.put("yTo", e[5]); d.put("shape", e[6]);
            out.add(new Example(e[0], d));
        }
        return out;
    }

    // ---------- parsing helpers ----------
    static String num(double v) {
        String t = String.format(Locale.ROOT, "%.4f", v);
        t = t.replaceAll("0+$", "");
        return t.endsWith(".") ? t.substring(0, t.length() - 1) : t;
    }
    private static int integer(Map<String, String> d, String k, int lo, int hi, int def) {
        try { return Math.max(lo, Math.min(hi, Integer.parseInt(d.get(k).trim()))); } catch (Exception _) { return def; }
    }
    private static double decimal(Map<String, String> d, String k, double lo, double hi, double def) {
        try {
            double v = Double.parseDouble(d.get(k).trim());
            return Double.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : def;
        } catch (Exception _) { return def; }
    }
    private static <E extends Enum<E>> E enumOf(Class<E> c, String v, E def) {
        try { return Enum.valueOf(c, v); } catch (Exception _) { return def; }
    }
}
