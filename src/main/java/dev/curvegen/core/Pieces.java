package dev.curvegen.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Every piece state the solver can place, as a 16×16 silhouette in the drawing (x right, y up).
 *
 * Upright shapes are seen from the side: all states below F_TD_U, plus SH_L and SH_R. Fence and pane states are base, +1 connected
 * left, +2 right, +3 both. Side walls are WALL + ((left*3 + right)*2 + covered)*2 + post, with left/right
 * 0 none, 1 low, 2 tall, and covered meaning the block above covers the wall's centre.
 *
 * Flat shapes (floors) are seen from above, with the drawing's "up" pointing away from the player. Only states that
 * look different from a full block from above are used: EMPTY, FULL, TD_L, TD_R, F_TD_U, F_TD_D, the F_SH shelves,
 * and floor fences, panes and walls, which connect in four directions: base + (left 1 | right 2 | top 4 | bottom 8).
 *
 * Chains and end rods lying in the drawing look the same in both orientations, so both share the states from CHAIN_H.
 * End-on they are only a dot, which isn't used.
 */
public final class Pieces {
    private Pieces() {}

    public static final int EMPTY = 0, FULL = 1, SLAB_B = 2, SLAB_T = 3,
            ST_UR = 4, ST_UL = 5, ST_DR = 6, ST_DL = 7,
            TD_B = 8, TD_T = 9, TD_L = 10, TD_R = 11,
            FENCE = 12, FENCE_L = 13, FENCE_R = 14, FENCE_LR = 15,
            PANE = 16, PANE_L = 17, PANE_R = 18, PANE_LR = 19,
            WALL = 20,
            F_TD_U = 56, F_TD_D = 57,
            F_FENCE = 58, F_PANE = 74, F_WALL = 90,
            SH_L = 106, SH_R = 107, F_SH_L = 108, F_SH_R = 109, F_SH_U = 110, F_SH_D = 111,
            CHAIN_H = 112, CHAIN_V = 113,
            ROD_U = 114, ROD_D = 115, ROD_L = 116, ROD_R = 117;
    public static final int COUNT = 118;

    public enum Family { AIR, FULL, SLAB, STAIRS, TRAPDOOR, SHELF, FENCE, PANE, WALL, CHAIN, ROD }

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
    /** Bottom row of the silhouette as a 16-bit mask (bit x set when pixel x of row 0 is filled). */
    public static final int[] BOTTOM = new int[COUNT];
    /** Edges the silhouette fills completely: [state][0=bottom,1=top,2=left,3=right]. */
    public static final boolean[][] EDGE = new boolean[COUNT][4];
    /** Full faces a fence, pane or wall can attach to, indexed like EDGE. Connectors offer none. */
    public static final boolean[][] STURDY = new boolean[COUNT][4];

    private static void def(int s, Family f, String name, int color, int mx, int my, int[]... rects) {
        FAMILY[s] = f; NAME[s] = name; COLOR[s] = color; MX[s] = mx; MY[s] = my; RECTS[s] = rects;
    }

    // ---------- side walls ----------
    public static int wall(int left, int right, boolean covered, boolean post) {
        return WALL + ((left * 3 + right) * 2 + (covered ? 1 : 0)) * 2 + (post ? 1 : 0);
    }
    public static int wallLeft(int s) { return (s - WALL) / 12; }
    public static int wallRight(int s) { return (s - WALL) / 4 % 3; }
    public static boolean wallCovered(int s) { return ((s - WALL) / 2 & 1) == 1; }
    public static boolean wallPost(int s) { return ((s - WALL) & 1) == 1; }
    /** The game's post rule for a wall with nothing in front or behind. */
    public static boolean wallPostRule(int left, int right, boolean covered) {
        if (left == 0 || right == 0) return true;
        return covered && !(left == 2 && right == 2);
    }

    /**
     * Does this piece, above a wall more than one block deep, cover the wall's front and back sides as well as its
     * centre? Chains and end rods are as thin from the side as from the front, so they reach the centre only.
     */
    public static boolean spansDepth(int s) { return !isLine(s); }
    public static boolean isLine(int s) { return FAMILY[s] == Family.CHAIN || FAMILY[s] == Family.ROD; }

    // ---------- floor connectors ----------
    public static final int LEFT = 1, RIGHT = 2, TOP = 4, BOTTOM_SIDE = 8;
    public static boolean isFloorConnector(int s) { return s >= F_FENCE && s < F_WALL + 16; }
    public static int floorBits(int s) { return (s - F_FENCE) & 15; }
    /** A floor wall is straight (and so has no post) when it connects on exactly two opposite sides. */
    public static boolean floorWallPost(int bits) { return bits != (LEFT | RIGHT) && bits != (TOP | BOTTOM_SIDE); }

    private static int swapBits(int b, int x, int y) {
        boolean bx = (b & x) != 0, by = (b & y) != 0;
        b &= ~(x | y);
        return b | (bx ? y : 0) | (by ? x : 0);
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
                for (int c = 0; c < 2; c++)
                    for (int p = 0; p < 2; p++) {
                        int s = wall(l, r, c == 1, p == 1);
                        List<int[]> rects = new ArrayList<>();
                        if (p == 1) rects.add(new int[]{4, 0, 12, 16});
                        if (l > 0) rects.add(new int[]{0, 0, 8, l == 2 ? 16 : 14});
                        if (r > 0) rects.add(new int[]{8, 0, 16, r == 2 ? 16 : 14});
                        String name = l == 0 && r == 0 ? "Wall, post only"
                                : "Wall, left " + side[l] + ", right " + side[r] + (p == 1 ? ", with post" : ", no post");
                        int base = 0x6a6f8c + 0x0c0c0c * (l + r) + (l == 2 || r == 2 ? 0x000018 : 0) + (p == 1 ? 0x080000 : 0);
                        def(s, Family.WALL, name, base, wall(r, l, c == 1, p == 1), s, rects.toArray(new int[0][]));
                    }

        // ---- floors (top view) ----
        def(F_TD_U, Family.TRAPDOOR, "Trapdoor, open, on top side", 0xb85bd6, F_TD_U, F_TD_D, new int[]{0, 13, 16, 16});
        def(F_TD_D, Family.TRAPDOOR, "Trapdoor, open, on bottom side", 0x7d3aa0, F_TD_D, F_TD_U, new int[]{0, 0, 16, 3});
        String[] dirs = {"left", "right", "top", "bottom"};
        for (int bits = 0; bits < 16; bits++) {
            List<String> ds = new ArrayList<>();
            for (int d = 0; d < 4; d++) if ((bits & (1 << d)) != 0) ds.add(dirs[d]);
            String conn = ds.isEmpty() ? "post only" : "connected " + String.join(", ", ds);
            int mx = swapBits(bits, LEFT, RIGHT), my = swapBits(bits, TOP, BOTTOM_SIDE);
            int n = Integer.bitCount(bits);
            def(F_FENCE + bits, Family.FENCE, "Fence (from above), " + conn, 0x8d7d22 + 0x0e0c04 * n,
                    F_FENCE + mx, F_FENCE + my, arms(new int[]{6, 6, 10, 10}, bits, 7, 9));
            def(F_PANE + bits, Family.PANE, "Glass pane (from above), " + conn, 0x16908f + 0x0e0c0c * n,
                    F_PANE + mx, F_PANE + my, arms(new int[]{7, 7, 9, 9}, bits, 7, 9));
            boolean hasPost = floorWallPost(bits);
            def(F_WALL + bits, Family.WALL, "Wall (from above), " + conn + (hasPost ? "" : ", no post"), 0x6a6f8c + 0x0c0c0c * n,
                    F_WALL + mx, F_WALL + my, arms(hasPost ? new int[]{4, 4, 12, 12} : null, bits, 5, 11));
        }

        // A shelf is a panel 3 thick with a lip 2 deep along its top and bottom. From above it's a strip 5 deep.
        def(SH_L, Family.SHELF, "Shelf, on left side", 0xc98f5a, SH_R, SH_L, new int[]{0, 0, 3, 16}, new int[]{3, 0, 5, 4}, new int[]{3, 12, 5, 16});
        def(SH_R, Family.SHELF, "Shelf, on right side", 0x8a5a33, SH_L, SH_R, new int[]{13, 0, 16, 16}, new int[]{11, 0, 13, 4}, new int[]{11, 12, 13, 16});
        def(F_SH_L, Family.SHELF, "Shelf, on left side", 0xc98f5a, F_SH_R, F_SH_L, new int[]{0, 0, 5, 16});
        def(F_SH_R, Family.SHELF, "Shelf, on right side", 0x8a5a33, F_SH_L, F_SH_R, new int[]{11, 0, 16, 16});
        def(F_SH_U, Family.SHELF, "Shelf, on top side", 0xe0b98a, F_SH_U, F_SH_D, new int[]{0, 11, 16, 16});
        def(F_SH_D, Family.SHELF, "Shelf, on bottom side", 0x6b4226, F_SH_D, F_SH_U, new int[]{0, 0, 16, 5});

        // ---- chains and end rods (either view) ----
        // A chain's two crossed strips are 3 wide but turned 45°, so it looks 2 wide. A rod points away from its plate.
        def(CHAIN_H, Family.CHAIN, "Chain, left to right", 0xc75b8e, CHAIN_H, CHAIN_H, new int[]{0, 7, 16, 9});
        def(CHAIN_V, Family.CHAIN, "Chain, top to bottom", 0x8f3a63, CHAIN_V, CHAIN_V, new int[]{7, 0, 9, 16});
        def(ROD_U, Family.ROD, "End rod, pointing up", 0xf5ecd0, ROD_U, ROD_D, new int[]{6, 0, 10, 1}, new int[]{7, 1, 9, 16});
        def(ROD_D, Family.ROD, "End rod, pointing down", 0xd9cfae, ROD_D, ROD_U, new int[]{6, 15, 10, 16}, new int[]{7, 0, 9, 15});
        def(ROD_L, Family.ROD, "End rod, pointing left", 0xbfb48f, ROD_R, ROD_L, new int[]{15, 6, 16, 10}, new int[]{0, 7, 15, 9});
        def(ROD_R, Family.ROD, "End rod, pointing right", 0xa39873, ROD_L, ROD_R, new int[]{0, 6, 1, 10}, new int[]{1, 7, 16, 9});

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
            EDGE[s] = new boolean[]{b, t, l, rr};
            STURDY[s] = isConnector(s) ? new boolean[4] : EDGE[s];
        }
    }

    /** A post (or none) plus arms of width a0..a1 reaching from the centre to each connected edge. */
    private static int[][] arms(int[] post, int bits, int a0, int a1) {
        List<int[]> r = new ArrayList<>();
        if (post != null) r.add(post);
        if ((bits & LEFT) != 0) r.add(new int[]{0, a0, 8, a1});
        if ((bits & RIGHT) != 0) r.add(new int[]{8, a0, 16, a1});
        if ((bits & TOP) != 0) r.add(new int[]{a0, 8, a1, 16});
        if ((bits & BOTTOM_SIDE) != 0) r.add(new int[]{a0, 0, a1, 8});
        return r.toArray(new int[0][]);
    }

    public static boolean isConnector(int s) {
        Family f = FAMILY[s];
        return f == Family.FENCE || f == Family.PANE || f == Family.WALL;
    }
    /** Is this a type token used while solving (the base fence/pane/wall state of either orientation)? */
    public static boolean isType(int s) { return s == FENCE || s == PANE || s == WALL || s == F_FENCE || s == F_PANE || s == F_WALL; }
    public static boolean connectsLeft(int s) {
        if (isFloorConnector(s)) return (floorBits(s) & LEFT) != 0;
        return switch (FAMILY[s]) {
            case FENCE -> ((s - FENCE) & 1) != 0;
            case PANE -> ((s - PANE) & 1) != 0;
            case WALL -> wallLeft(s) > 0;
            default -> false;
        };
    }
    public static boolean connectsRight(int s) {
        if (isFloorConnector(s)) return (floorBits(s) & RIGHT) != 0;
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
