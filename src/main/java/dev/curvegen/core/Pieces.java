package dev.curvegen.core;

/**
 * Every piece state the solver can place, seen from the side (x right, y up, 16×16 pixel units).
 * Fence and pane states are consecutive: base, +1 connected left, +2 connected right, +3 both.
 */
public final class Pieces {
    private Pieces() {}

    public static final int EMPTY = 0, FULL = 1, SLAB_B = 2, SLAB_T = 3,
            ST_UR = 4, ST_UL = 5, ST_DR = 6, ST_DL = 7,
            TD_B = 8, TD_T = 9, TD_L = 10, TD_R = 11,
            FENCE = 12, FENCE_L = 13, FENCE_R = 14, FENCE_LR = 15,
            PANE = 16, PANE_L = 17, PANE_R = 18, PANE_LR = 19;
    public static final int COUNT = 20;

    public enum Family { AIR, FULL, SLAB, STAIRS, TRAPDOOR, FENCE, PANE }

    public static final Family[] FAMILY = {
            Family.AIR, Family.FULL, Family.SLAB, Family.SLAB,
            Family.STAIRS, Family.STAIRS, Family.STAIRS, Family.STAIRS,
            Family.TRAPDOOR, Family.TRAPDOOR, Family.TRAPDOOR, Family.TRAPDOOR,
            Family.FENCE, Family.FENCE, Family.FENCE, Family.FENCE,
            Family.PANE, Family.PANE, Family.PANE, Family.PANE};

    public static final String[] NAME = {
            "Air", "Full block", "Slab, bottom", "Slab, top",
            "Stairs, upright, facing right", "Stairs, upright, facing left",
            "Stairs, upside-down, facing right", "Stairs, upside-down, facing left",
            "Trapdoor, closed, bottom", "Trapdoor, closed, top",
            "Trapdoor, open, on left side", "Trapdoor, open, on right side",
            "Fence, post only", "Fence, connected left", "Fence, connected right", "Fence, connected both sides",
            "Glass pane, post only", "Glass pane, connected left", "Glass pane, connected right", "Glass pane, connected both sides"};

    /** Colour per state for the "piece type" colouring (0xRRGGBB). */
    public static final int[] COLOR = {
            0x000000, 0x8a96a8, 0xe3a93b, 0xb8641d,
            0x3a9e5f, 0x8ccf6f, 0x2a72b8, 0x6fbbe9,
            0xa74fc9, 0xe08ad3, 0xe0485f, 0xf28f4f,
            0x8d7d22, 0xc4b13a, 0x5f5616, 0xe4d96e,
            0x16908f, 0x4fcac0, 0x0d5f5e, 0xa3e4de};

    /** Rectangles {x0, y0, x1, y1} per state, in 16ths of a block. */
    public static final int[][][] RECTS = {
            {},
            {{0, 0, 16, 16}},
            {{0, 0, 16, 8}}, {{0, 8, 16, 16}},
            {{0, 0, 16, 8}, {8, 8, 16, 16}}, {{0, 0, 16, 8}, {0, 8, 8, 16}},
            {{0, 8, 16, 16}, {8, 0, 16, 8}}, {{0, 8, 16, 16}, {0, 0, 8, 8}},
            {{0, 0, 16, 3}}, {{0, 13, 16, 16}}, {{0, 0, 3, 16}}, {{13, 0, 16, 16}},
            {{6, 0, 10, 16}},
            {{6, 0, 10, 16}, {0, 6, 6, 9}, {0, 12, 6, 15}},
            {{6, 0, 10, 16}, {10, 6, 16, 9}, {10, 12, 16, 15}},
            {{6, 0, 10, 16}, {0, 6, 6, 9}, {0, 12, 6, 15}, {10, 6, 16, 9}, {10, 12, 16, 15}},
            {{7, 0, 9, 16}}, {{0, 0, 9, 16}}, {{7, 0, 16, 16}}, {{0, 0, 16, 16}}};

    public static final int[] MX = {0, 1, 2, 3, 5, 4, 7, 6, 8, 9, 11, 10, 12, 14, 13, 15, 16, 18, 17, 19};
    public static final int[] MY = {0, 1, 3, 2, 6, 7, 4, 5, 9, 8, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};

    /** 256-entry masks, index = y*16 + x. */
    public static final byte[][] MASK = new byte[COUNT][256];
    public static final int[] PIXELS = new int[COUNT];
    /** Full faces a fence or pane can attach to: [state][0=bottom,1=top,2=left,3=right]. */
    public static final boolean[][] STURDY = new boolean[COUNT][4];

    static {
        for (int s = 0; s < COUNT; s++) {
            for (int[] r : RECTS[s])
                for (int y = r[1]; y < r[3]; y++)
                    for (int x = r[0]; x < r[2]; x++) MASK[s][y * 16 + x] = 1;
            int n = 0;
            for (byte b : MASK[s]) n += b;
            PIXELS[s] = n;
            boolean b = true, t = true, l = true, rr = true;
            for (int k = 0; k < 16; k++) {
                if (MASK[s][k] == 0) b = false;
                if (MASK[s][240 + k] == 0) t = false;
                if (MASK[s][k * 16] == 0) l = false;
                if (MASK[s][k * 16 + 15] == 0) rr = false;
            }
            boolean connector = FAMILY[s] == Family.FENCE || FAMILY[s] == Family.PANE;
            STURDY[s] = connector ? new boolean[4] : new boolean[]{b, t, l, rr};
        }
    }

    public static boolean isConnector(int s) { return FAMILY[s] == Family.FENCE || FAMILY[s] == Family.PANE; }
    /** Is this the type token used while solving (base fence/pane state)? */
    public static boolean isType(int s) { return s == FENCE || s == PANE; }
    public static boolean connectsLeft(int s) { return isConnector(s) && ((s - (FAMILY[s] == Family.FENCE ? FENCE : PANE)) & 1) != 0; }
    public static boolean connectsRight(int s) { return isConnector(s) && ((s - (FAMILY[s] == Family.FENCE ? FENCE : PANE)) & 2) != 0; }
}
