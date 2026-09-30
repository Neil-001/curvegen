package dev.curvegen.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A preset stores one shape tab's own settings as text (so it survives format changes gracefully):
 * the ellipse's size and shape, the equation and its ranges, or the Bézier grid and points.
 * Block choices, pieces, orientation and depth aren't part of a preset.
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
                List<String> pts = new ArrayList<>();
                for (double[] p : s.pts) pts.add(num(p[0]) + "," + num(p[1]));
                d.put("points", String.join(";", pts));
                d.put("shape", s.bMode.name());
                d.put("lineWidth", num(s.bLW));
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
                List<double[]> pts = new ArrayList<>();
                for (String p : d.getOrDefault("points", "").split(";")) {
                    String[] xy = p.split(",");
                    if (xy.length != 2) continue;
                    try {
                        double x = Double.parseDouble(xy[0].trim()), y = Double.parseDouble(xy[1].trim());
                        if (Double.isFinite(x) && Double.isFinite(y)) pts.add(new double[]{x, y});
                    } catch (NumberFormatException ignored) { }
                }
                if (pts.size() >= 2 && pts.size() <= 10) { s.pts.clear(); s.pts.addAll(pts); }
                s.bMode = enumOf(ShapeSettings.BzMode.class, d.get("shape"), s.bMode);
                s.bLW = decimal(d, "lineWidth", 0.0625, 50, s.bLW);
            }
        }
    }

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
        try { return Math.max(lo, Math.min(hi, Integer.parseInt(d.get(k).trim()))); } catch (Exception e) { return def; }
    }
    private static double decimal(Map<String, String> d, String k, double lo, double hi, double def) {
        try {
            double v = Double.parseDouble(d.get(k).trim());
            return Double.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : def;
        } catch (Exception e) { return def; }
    }
    private static <E extends Enum<E>> E enumOf(Class<E> c, String v, E def) {
        try { return Enum.valueOf(c, v); } catch (Exception e) { return def; }
    }
}
