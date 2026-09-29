package dev.curvegen.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Every piece state the solver can place, seen from the side (x right, y up, 16×16 pixel units).
 * Fence and pane states are consecutive: base, +1 connected left, +2 connected right, +3 both.
 * Wall states are WALL + left*6 + right*2 + covered, where left/right are 0 none, 1 low, 2 tall,
 * and covered says the block above covers the wall's centre (which decides whether a straight wall keeps its post).
 */
public final class Pieces {
    private Pieces() {}

    public static final int EMPTY = 0, FULL = 1, SLAB_B = 2, SLAB_T = 3,
            ST_UR = 4, ST_UL = 5, ST_DR = 6, ST_DL = 7,
            TD_B = 8, TD_T = 9, TD_L = 10, TD_R = 11,
            FENCE = 12, FENCE_L = 13, FENCE_R = 14, FENCE_LR = 15,
            PANE = 16, PANE_L = 17, PANE_R = 18, PANE_LR = 19,
            WALL = 20;
    public static final int COUNT = 38;

    public enum Family { AIR, FULL, SLAB, STAIRS, TRAPDOOR, FENCE, PANE, WALL }

    public static final Family[] FAMILY = new Family[COUNT];
    public static final String[] NAME = new String[COUNT];
    /** Colour per state for the "piece type" colouring (0xRRGGBB). */
    public static final int[] COLOR = new int[COUNT];
    /** Rectangles {x0, y0, x1, y1} per state, in 16ths of a block. */
    public static final int[][][] RECTS = new int[COUNT][][];
    public static final int[] MX = new int[COUNT], MY = new int[COUNT];
    /** 256-entry masks, index = y*16 + x. */
    public static final byte[][] MASK = new byte[COUNT][256];
    public static final int[] PIXELS = new int[COUNT];
    /** Bottom row of the shape as a 16-bit mask (bit x set when pixel x of row 0 is filled). */
    public static final int[] BOTTOM = new int[COUNT];
    /** Full faces a fence, pane or wall can attach to: [state][0=bottom,1=top,2=left,3=right]. */
    public static final boolean[][] STURDY = new boolean[COUNT][4];

    private static void def(int s, Family f, String name, int color, int mx, int my, int[]... rects) {
        FAMILY[s] = f; NAME[s] = name; COLOR[s] = color; MX[s] = mx; MY[s] = my; RECTS[s] = rects;
    }

    public static int wall(int left, int right, boolean covered) { return WALL + left * 6 + right * 2 + (covered ? 1 : 0); }
    public static int wallLeft(int s) { return (s - WALL) / 6; }
    public static int wallRight(int s) { return (s - WALL) / 2 % 3; }
    public static boolean wallCovered(int s) { return ((s - WALL) & 1) == 1; }
    /** Post rule for a wall with nothing in front or behind: a straight wall drops its post unless something rests on it. */
    public static boolean wallPost(int s) {
        int l = wallLeft(s), r = wallRight(s);
        if (l == 0 || r == 0) return true;
        return wallCovered(s) && !(l == 2 && r == 2);
    }

    static {
        def(EMPTY, Family.AIR, "Air", 0, EMPTY, EMPTY);
        def(FULL, Family.FULL, "Full block", 0x8a96a8, FULL, FULL, new int[]{0, 0, 16, 16});
        def(SLAB_B, Family.SLAB, "Slab, bottom", 0xe3a93b, SLAB_B, SLAB_T, new int[]{0, 0, 16, 8});
        def(SLAB_T, Family.SLAB, "Slab, top", 0xb8641d, SLAB_T, SLAB_B, new int[]{0, 8, 16, 16});
        def(ST_UR, Family.STAIRS, "Stairs, upright, facing right", 0x3a9e5f, ST_UL, ST_DR, new int[]{0, 0, 16, 8}, new int[]{8, 8, 16, 16});
        def(ST_UL, Family.STAIRS, "Stairs, upright, facing left", 0x8ccf6f, ST_UR, ST_DL, new int[]{0, 0, 16, 8}, new int[]{0, 8, 8, 16});
        def(ST_DR, Family.STAIRS, "Stairs, upside-down, facing right", 0x2a72b8, ST_DL, ST_UR, new int[]{0, 8, 16, 16}, new int[]{8, 0, 16, 8});
        def(ST_DL, Family.STAIRS, "Stairs, upside-down, facing left", 0x6fbbe9, ST_DR, ST_UL, new int[]{0, 8, 16, 16}, new int[]{0, 0, 8, 8});
        def(TD_B, Family.TRAPDOOR, "Trapdoor, closed, bottom", 0xa74fc9, TD_B, TD_T, new int[]{0, 0, 16, 3});
        def(TD_T, Family.TRAPDOOR, "Trapdoor, closed, top", 0xe08ad3, TD_T, TD_B, new int[]{0, 13, 16, 16});
        def(TD_L, Family.TRAPDOOR, "Trapdoor, open, on left side", 0xe0485f, TD_R, TD_L, new int[]{0, 0, 3, 16});
        def(TD_R, Family.TRAPDOOR, "Trapdoor, open, on right side", 0xf28f4f, TD_L, TD_R, new int[]{13, 0, 16, 16});
        int[] post = {6, 0, 10, 16}, lL = {0, 6, 6, 9}, lH = {0, 12, 6, 15}, rL = {10, 6, 16, 9}, rH = {10, 12, 16, 15};
        def(FENCE, Family.FENCE, "Fence, post only", 0x8d7d22, FENCE, FENCE, post);
        def(FENCE_L, Family.FENCE, "Fence, connected left", 0xc4b13a, FENCE_R, FENCE_L, post, lL, lH);
        def(FENCE_R, Family.FENCE, "Fence, connected right", 0x5f5616, FENCE_L, FENCE_R, post, rL, rH);
        def(FENCE_LR, Family.FENCE, "Fence, connected both sides", 0xe4d96e, FENCE_LR, FENCE_LR, post, lL, lH, rL, rH);
        def(PANE, Family.PANE, "Glass pane, post only", 0x16908f, PANE, PANE, new int[]{7, 0, 9, 16});
        def(PANE_L, Family.PANE, "Glass pane, connected left", 0x4fcac0, PANE_R, PANE_L, new int[]{0, 0, 9, 16});
        def(PANE_R, Family.PANE, "Glass pane, connected right", 0x0d5f5e, PANE_L, PANE_R, new int[]{7, 0, 16, 16});
        def(PANE_LR, Family.PANE, "Glass pane, connected both sides", 0xa3e4de, PANE_LR, PANE_LR, new int[]{0, 0, 16, 16});

        String[] side = {"none", "low", "tall"};
        for (int l = 0; l < 3; l++)
            for (int r = 0; r < 3; r++)
                for (int c = 0; c < 2; c++) {
                    int s = wall(l, r, c == 1);
                    List<int[]> rects = new ArrayList<>();
                    if (wallPost(s)) rects.add(new int[]{4, 0, 12, 16});
                    if (l > 0) rects.add(new int[]{0, 0, 8, l == 2 ? 16 : 14});
                    if (r > 0) rects.add(new int[]{8, 0, 16, r == 2 ? 16 : 14});
                    String name = l == 0 && r == 0 ? "Wall, post only"
                            : "Wall, left " + side[l] + ", right " + side[r] + (wallPost(s) ? ", with post" : ", no post");
                    // Shades of slate: brighter with more connections, bluer when tall.
                    int base = 0x6a6f8c + 0x0c0c0c * (l + r) + (l == 2 || r == 2 ? 0x000018 : 0) + (c == 1 ? 0x080000 : 0);
                    def(s, Family.WALL, name, base, wall(r, l, c == 1), s, rects.toArray(new int[0][]));
                }

        for (int s = 0; s < COUNT; s++) {
            for (int[] r : RECTS[s])
                for (int y = r[1]; y < r[3]; y++)
                    for (int x = r[0]; x < r[2]; x++) MASK[s][y * 16 + x] = 1;
            int n = 0;
            for (byte b : MASK[s]) n += b;
            PIXELS[s] = n;
            int bottom = 0;
            for (int x = 0; x < 16; x++) if (MASK[s][x] != 0) bottom |= 1 << x;
            BOTTOM[s] = bottom;
            boolean b = true, t = true, l = true, rr = true;
            for (int k = 0; k < 16; k++) {
                if (MASK[s][k] == 0) b = false;
                if (MASK[s][240 + k] == 0) t = false;
                if (MASK[s][k * 16] == 0) l = false;
                if (MASK[s][k * 16 + 15] == 0) rr = false;
            }
            STURDY[s] = isConnector(s) ? new boolean[4] : new boolean[]{b, t, l, rr};
        }
    }

    public static boolean isConnector(int s) {
        Family f = FAMILY[s];
        return f == Family.FENCE || f == Family.PANE || f == Family.WALL;
    }
    /** Is this the type token used while solving (the base fence/pane/wall state)? */
    public static boolean isType(int s) { return s == FENCE || s == PANE || s == WALL; }
    public static boolean connectsLeft(int s) {
        return switch (FAMILY[s]) {
            case FENCE -> ((s - FENCE) & 1) != 0;
            case PANE -> ((s - PANE) & 1) != 0;
            case WALL -> wallLeft(s) > 0;
            default -> false;
        };
    }
    public static boolean connectsRight(int s) {
        return switch (FAMILY[s]) {
            case FENCE -> ((s - FENCE) & 2) != 0;
            case PANE -> ((s - PANE) & 2) != 0;
            case WALL -> wallRight(s) > 0;
            default -> false;
        };
    }
    /** Do these two families join when side by side? (Fences only join fences; panes and walls join each other.) */
    public static boolean joins(Family a, Family b) {
        if (a == b) return true;
        return (a == Family.PANE && b == Family.WALL) || (a == Family.WALL && b == Family.PANE);
    }
}
