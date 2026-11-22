package dev.curvegen.core.edit;

import java.util.ArrayList;
import java.util.List;

public final class Radial {
    private Radial() {}

    public static final List<String> START = List.of("2d", "3d", "presets", "last", "capture"),
            EDIT = List.of("options", "blocks", "paint", "presets", "export", "full", "place", "cancel");

    public static final int LABEL_W = 80, LABEL_H = 22, MAX_WEDGES = 12;
    public static final int RING = 26, RING_IN = 9, FOOT = 24;

    public static String name(String id) {
        return switch (id) {
            case "2d" -> "curvegen.radial.2d"; case "3d" -> "curvegen.radial.3d"; case "presets" -> "curvegen.radial.presets"; case "last" -> "curvegen.radial.last"; case "capture" -> "curvegen.radial.capture";
            case "options" -> "curvegen.radial.options"; case "blocks" -> "curvegen.radial.blocks"; case "paint" -> "curvegen.paint.title"; case "export" -> "curvegen.radial.export"; case "full" -> "curvegen.radial.full";
            case "place" -> "curvegen.radial.place"; case "cancel" -> "curvegen.radial.cancel";
            default -> id;
        };
    }

    public static int wedgeAt(double dx, double dy, int n, double dead, double stretch, boolean round) {
        double u = dx / stretch;
        if (n <= 0 || Math.hypot(round ? dx : u, dy) < dead) return -1;
        double turn = Math.atan2(u, -dy) / (2 * Math.PI);
        return Math.floorMod((int) Math.round(turn * n), n);
    }

    public static double centre(int size, boolean round) { return round ? size / 2 : size / 2.0; }

    public static int ringHalf(double stretch, boolean round) { return round ? RING : (int) Math.ceil(RING * stretch); }

    public static byte[] ring(int n, double stretch, boolean round) {
        int half = ringHalf(stretch, round);
        byte[] out = new byte[2 * half * 2 * RING];
        for (int j = 0; j < 2 * RING; j++)
            for (int i = 0; i < 2 * half; i++) {
                double dx = i + 0.5 - half, dy = j + 0.5 - RING;
                int w = -1;
                if (round) {
                    w = Math.hypot(dx, dy) > RING ? -1 : wedgeAt(dx, dy, n, RING_IN, stretch, true);
                    for (int c = 0; c < 4 && w >= 0; c++)
                        if (wedgeAt(dx + (c & 1) - 0.5, dy + (c >> 1) - 0.5, n, 0, stretch, true) != w) w = -1;
                } else {
                    double u = dx / stretch, r = Math.hypot(u, dy);
                    if (r >= RING_IN && r <= RING) {
                        double part = Math.atan2(u, -dy) / (2 * Math.PI) * n, edge = 0.5 - Math.abs(part - Math.rint(part));
                        if (n == 1 || edge * 2 * Math.PI * r / n >= 0.6) w = wedgeAt(dx, dy, n, 0, stretch, false);
                    }
                }
                out[j * 2 * half + i] = (byte) w;
            }
        return out;
    }

    public static double stretch(int rx, int ry) { return (rx + LABEL_W / 2.0) / (ry + LABEL_H / 2.0); }

    public static int[][] labels(int n, int cx, int cy, int rx, int ry) {
        int[][] out = new int[n][];
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * k / n, sin = Math.sin(a), cos = Math.cos(a);
            double x = cx + (rx + LABEL_W / 2.0) * sin, y = cy - (ry + LABEL_H / 2.0) * cos;
            out[k] = new int[]{(int) Math.round(x - LABEL_W / 2.0), (int) Math.round(y - LABEL_H / 2.0)};
        }
        return out;
    }

    public static int[] radii(int width, int height) {
        return new int[]{Math.max(40, Math.min(150, width / 2 - LABEL_W - 4)), Math.max(30, Math.min(100, height / 2 - LABEL_H - FOOT))};
    }

    public static List<String> order(List<String> defaults, List<String> saved) {
        List<String> out = new ArrayList<>();
        for (String id : saved) if (defaults.contains(id) && !out.contains(id)) out.add(id);
        for (int k = 0; k < defaults.size(); k++) {
            String id = defaults.get(k);
            if (!out.contains(id)) out.add(k == 0 ? 0 : out.indexOf(defaults.get(k - 1)) + 1, id);
        }
        return out;
    }

    public static List<String> merge(List<String> start, List<String> edit) {
        if (start.equals(START) && edit.equals(EDIT)) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        int s = start.indexOf("presets"), e = edit.indexOf("presets");
        out.addAll(start.subList(0, s));
        out.addAll(edit.subList(0, e));
        out.add("presets");
        out.addAll(start.subList(s + 1, start.size()));
        out.addAll(edit.subList(e + 1, edit.size()));
        return out;
    }
}
