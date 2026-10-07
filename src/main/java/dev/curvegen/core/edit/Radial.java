package dev.curvegen.core.edit;

import java.util.ArrayList;
import java.util.List;

/**
 * The radial menu's maths: which wedge the cursor points at, where each wedge's label goes, and the order the player
 * chose for the wedges of the two top-level menus.
 */
public final class Radial {
    private Radial() {}

    /** The top-level wedges' ids in their default order: with no hologram, and with one. */
    public static final List<String> START = List.of("2d", "3d", "presets", "last"),
            EDIT = List.of("options", "blocks", "presets", "export", "full", "place", "cancel");

    /** A label box's size, and the most wedges a menu can have without two labels overlapping on a 427 by 240 screen. */
    public static final int LABEL_W = 80, LABEL_H = 22, MAX_WEDGES = 12;
    /** Half the ring's height, and the room kept under the labels for two lines of text. */
    public static final int RING = 26, FOOT = 24;

    public static String name(String id) {
        return switch (id) {
            case "2d" -> "2D shapes"; case "3d" -> "3D shapes"; case "presets" -> "Presets"; case "last" -> "Last shape";
            case "options" -> "Shape options"; case "blocks" -> "Blocks"; case "export" -> "Export"; case "full" -> "Full menu";
            case "place" -> "Place"; case "cancel" -> "Cancel";
            default -> id;
        };
    }

    /**
     * The wedge a cursor at (dx, dy) from the centre points at, with y running down the screen. Wedge 0 is centred on
     * straight up and the rest follow clockwise. -1 within {@code dead} of the centre, where no direction is clear.
     *
     * <p>The labels sit round an oval, wider than it is tall, so the menu is one too: {@code stretch} is how many times
     * wider, from {@link #stretch}. Wedges are even slices of the oval, which keeps every label inside its own wedge.
     */
    public static int wedgeAt(double dx, double dy, int n, double dead, double stretch) {
        double u = dx / stretch;
        if (n <= 0 || Math.hypot(u, dy) < dead) return -1;
        double turn = Math.atan2(u, -dy) / (2 * Math.PI);   // 0 straight up, a quarter to the right
        return Math.floorMod((int) Math.round(turn * n), n);
    }

    /** How many times wider than tall the menu is, for the radii its labels sit at. */
    public static double stretch(int rx, int ry) { return (rx + LABEL_W / 2.0) / (ry + LABEL_H / 2.0); }

    /**
     * Where the label of each of {@code n} wedges goes, as {x, y} of its top-left corner. Labels sit around an ellipse
     * of radii (rx, ry) about (cx, cy) and are pushed outwards from it, so a label at the side starts at the ellipse
     * and one at the top stands on it.
     */
    public static int[][] labels(int n, int cx, int cy, int rx, int ry) {
        int[][] out = new int[n][];
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * k / n, sin = Math.sin(a), cos = Math.cos(a);
            double x = cx + (rx + LABEL_W / 2.0) * sin, y = cy - (ry + LABEL_H / 2.0) * cos;
            out[k] = new int[]{(int) Math.round(x - LABEL_W / 2.0), (int) Math.round(y - LABEL_H / 2.0)};
        }
        return out;
    }

    /** The ellipse's radii for a screen: as wide and tall as leaves room for the labels and the text below them. */
    public static int[] radii(int width, int height) {
        return new int[]{Math.max(40, Math.min(150, width / 2 - LABEL_W - 4)), Math.max(30, Math.min(100, height / 2 - LABEL_H - FOOT))};
    }

    /**
     * A menu's wedges in the player's order. Ids in {@code saved} come first, in that order. The rest follow in their
     * default order, so a wedge added in a later version still shows up. Ids the menu doesn't have are ignored.
     */
    public static List<String> order(List<String> defaults, List<String> saved) {
        List<String> out = new ArrayList<>();
        for (String id : saved) if (defaults.contains(id) && !out.contains(id)) out.add(id);
        for (String id : defaults) if (!out.contains(id)) out.add(id);
        return out;
    }

    /**
     * One list to save for the two menus' orders, or an empty list when both are in their default order. The menus
     * share only "presets", so one list can always hold both orders: what comes before it in either menu, then it,
     * then the rest.
     */
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
