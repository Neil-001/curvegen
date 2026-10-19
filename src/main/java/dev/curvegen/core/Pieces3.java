package dev.curvegen.core;

import dev.curvegen.core.Pieces.Family;

import java.util.ArrayList;
import java.util.List;

/**
 * Every block state the volumetric solver can place, as its 3D shape in 16ths of a block. Coordinates are the
 * world's: x east, y up, z south. A state index decodes to every property the block state needs, with
 * {@link #props} giving them in the game's own names, so nothing is left for the client to guess.
 *
 * Directions are N, E, S, W (clockwise from above), then DOWN and UP. The states:
 * <ul>
 * <li>AIR, FULL, SLAB_B, SLAB_T.</li>
 * <li>Stairs: STAIRS + half*20 + facing*5 + shape, with half 0 bottom or 1 top, and shape 0 straight, 1 inner left,
 *     2 inner right, 3 outer left, 4 outer right.</li>
 * <li>Trapdoors: TRAP_B and TRAP_T closed, TRAP_OPEN + facing open.</li>
 * <li>Shelves: SHELF + facing. Chains: CHAIN + axis (0 x, 1 y, 2 z). End rods: ROD + direction.</li>
 * <li>Fences and panes: FENCE or PANE + connection bits (north 1, east 2, south 4, west 8).</li>
 * <li>Walls: WALL + (((north*3 + east)*3 + south)*3 + west)*2 + up, each side 0 none, 1 low or 2 tall.</li>
 * </ul>
 * There are more than 127 states, so 3D grids are {@code short[]}.
 *
 * The solver works with tokens: a straight stair stands for whichever corner shape its neighbours give it, and
 * FENCE, PANE and WALL_POST for whichever connections theirs give. Every other state is its own token.
 *
 * Shapes are Minecraft's voxel shapes, except where the model is thinner than its outline: a fence's arms are its
 * two bars, a chain is 2 wide and an end rod is a plate and a thin rod, as in {@link Pieces}.
 */
public final class Pieces3 {
    private Pieces3() {}

    public static final int N = 0, E = 1, S = 2, W = 3, DOWN = 4, UP = 5;
    /** Step along x and z for the four horizontal directions. */
    public static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};

    public static final int AIR = 0, FULL = 1, SLAB_B = 2, SLAB_T = 3, STAIRS = 4,
            TRAP_B = 44, TRAP_T = 45, TRAP_OPEN = 46, SHELF = 50, CHAIN = 54, ROD = 57,
            FENCE = 63, PANE = 79, WALL = 95, WALL_POST = WALL + 1;
    public static final int COUNT = 257;
    public static final int STRAIGHT = 0, INNER_LEFT = 1, INNER_RIGHT = 2, OUTER_LEFT = 3, OUTER_RIGHT = 4;

    public static final Family[] FAMILY = new Family[COUNT];
    /** Boxes {x0, y0, z0, x1, y1, z1} per state, in 16ths of a block. They may overlap. */
    public static final int[][][] BOXES = new int[COUNT][][];
    /** Volume in voxels (4096 to a block). */
    public static final int[] VOL = new int[COUNT];
    public static final int[] MX = new int[COUNT], MY = new int[COUNT], MZ = new int[COUNT];
    /** Faces the shape fills completely, by direction. Connectors attach to these, and they hide a neighbour. */
    public static final boolean[][] FACE = new boolean[COUNT][6];
    /**
     * What a wall below this state feels, from the bottom of its collision shape: COVER_POST when it covers the
     * wall's centre, and 1 << direction when it covers that side's test strip, which makes the side tall.
     */
    public static final int[] COVER = new int[COUNT];
    public static final int COVER_POST = 16;

    // ---------- encoding ----------
    public static int stair(int half, int facing, int shape) { return STAIRS + half * 20 + facing * 5 + shape; }
    public static boolean isStairs(int s) { return s >= STAIRS && s < TRAP_B; }
    public static int wall(int n, int e, int s, int w, boolean up) { return WALL + (((n * 3 + e) * 3 + s) * 3 + w) * 2 + (up ? 1 : 0); }
    public static boolean isConnector(int s) { return s >= FENCE; }
    public static boolean isWall(int s) { return s >= WALL; }
    /** Does this token's state depend on its neighbours? */
    public static boolean dependent(int s) { return s >= FENCE || isStairs(s); }
    public static boolean isLine(int s) { return s >= CHAIN && s < FENCE; }

    /** Bottom (0) or top (1), for slabs, stairs and closed trapdoors. Open trapdoors use bottom. */
    public static int half(int s) {
        if (isStairs(s)) return (s - STAIRS) / 20;
        return s == SLAB_T || s == TRAP_T ? 1 : 0;
    }
    /** The facing of stairs, open trapdoors, shelves and end rods. Closed trapdoors use north. */
    public static int facing(int s) {
        if (isStairs(s)) return (s - STAIRS) / 5 % 4;
        if (s >= TRAP_OPEN && s < SHELF) return s - TRAP_OPEN;
        if (s >= SHELF && s < CHAIN) return s - SHELF;
        if (s >= ROD && s < FENCE) return s - ROD;
        return N;
    }
    public static int stairShape(int s) { return (s - STAIRS) % 5; }
    public static boolean open(int s) { return s >= TRAP_OPEN && s < SHELF; }
    /** A chain's axis: 0 x, 1 y, 2 z. */
    public static int axis(int s) { return s - CHAIN; }
    /** A connector's side in a horizontal direction: 0 none, 1 connected (a wall's low side), 2 a wall's tall side. */
    public static int side(int s, int dir) {
        if (s >= WALL) {
            int v = (s - WALL) / 2;
            for (int d = W; d > dir; d--) v /= 3;
            return v % 3;
        }
        return s >= FENCE ? ((s - (s >= PANE ? PANE : FENCE)) >> dir) & 1 : 0;
    }
    /** Does a wall have its post? */
    public static boolean up(int s) { return s >= WALL && ((s - WALL) & 1) == 1; }

    private static final String[] DIR = {"north", "east", "south", "west", "down", "up"};
    private static final String[] SHAPE = {"straight", "inner_left", "inner_right", "outer_left", "outer_right"};
    private static final String[] HEIGHT = {"none", "low", "tall"};

    /** The state's block state properties as Minecraft writes them, such as "facing=north,half=top,shape=straight". */
    public static String props(int s) {
        String half = half(s) == 1 ? "top" : "bottom";
        return switch (FAMILY[s]) {
            case SLAB -> "type=" + half;
            case STAIRS -> "facing=" + DIR[facing(s)] + ",half=" + half + ",shape=" + SHAPE[stairShape(s)];
            case TRAPDOOR -> "facing=" + DIR[facing(s)] + ",half=" + half + ",open=" + open(s);
            case SHELF, ROD -> "facing=" + DIR[facing(s)];
            case CHAIN -> "axis=" + "xyz".charAt(axis(s));
            case FENCE, PANE -> "north=" + (side(s, N) > 0) + ",east=" + (side(s, E) > 0) + ",south=" + (side(s, S) > 0) + ",west=" + (side(s, W) > 0);
            case WALL -> "north=" + HEIGHT[side(s, N)] + ",east=" + HEIGHT[side(s, E)] + ",south=" + HEIGHT[side(s, S)]
                    + ",west=" + HEIGHT[side(s, W)] + ",up=" + up(s);
            default -> "";
        };
    }

    public static String name(int s) {
        String p = props(s);
        return FAMILY[s] + (p.isEmpty() ? "" : "[" + p + "]");
    }

    // ---------- parts ----------
    // The solver scores a state by how much of the target it overlaps. Every state is a union of disjoint parts,
    // so a cell only stores its overlap with each part.

    /** Parts making up each state. They never overlap one another within a state. */
    public static final int[][] PARTS = new int[COUNT][];
    public static final int OCTANT = 0, FENCE_POST = 27, FENCE_ARM = 28, PANE_POST = 32, PANE_ARM = 33, WALL_PART = 37, PART_COUNT = 57;
    public static final int[][][] PART_BOXES = new int[PART_COUNT][][];
    public static final int[] PART_VOL = new int[PART_COUNT];
    /** Which piece family needs each part, so a solve skips the parts of families that are switched off. */
    public static final Family[] PART_FAMILY = new Family[PART_COUNT];

    private static int[] box(int x0, int y0, int z0, int x1, int y1, int z1) { return new int[]{x0, y0, z0, x1, y1, z1}; }

    /** A box given along a horizontal direction: from..to is measured inwards from that direction's face, l0..l1 across. */
    private static int[] toward(int dir, int from, int to, int l0, int l1, int y0, int y1) {
        return switch (dir) {
            case N -> box(l0, y0, from, l1, y1, to);
            case S -> box(l0, y0, 16 - to, l1, y1, 16 - from);
            case W -> box(from, y0, l0, to, y1, l1);
            default -> box(16 - to, y0, l0, 16 - from, y1, l1);
        };
    }

    private static void def(int s, Family f, int[] parts, int[]... boxes) { FAMILY[s] = f; PARTS[s] = parts; BOXES[s] = boxes; }
    private static void part(int p, Family f, int[]... boxes) { PART_FAMILY[p] = f; PART_BOXES[p] = boxes; }

    private static int mirrorDir(int d, int axis) {
        return switch (axis) {
            case 0 -> d == E ? W : d == W ? E : d;
            case 1 -> d == UP ? DOWN : d == DOWN ? UP : d;
            default -> d == N ? S : d == S ? N : d;
        };
    }

    private static int mirror(int s, int axis) {
        switch (FAMILY[s]) {
            case SLAB: return axis == 1 ? s ^ 1 : s;
            case STAIRS: {
                if (axis == 1) return stair(1 - half(s), facing(s), stairShape(s));
                int sh = stairShape(s);
                return stair(half(s), mirrorDir(facing(s), axis), sh == 0 ? 0 : ((sh - 1) ^ 1) + 1);
            }
            case TRAPDOOR: return open(s) ? TRAP_OPEN + mirrorDir(facing(s), axis) : axis == 1 ? (s == TRAP_B ? TRAP_T : TRAP_B) : s;
            case SHELF: return SHELF + mirrorDir(facing(s), axis);
            case ROD: return ROD + mirrorDir(facing(s), axis);
            case FENCE: case PANE: {
                int base = s >= PANE ? PANE : FENCE, bits = 0;
                for (int d = 0; d < 4; d++) if (side(s, d) > 0) bits |= 1 << mirrorDir(d, axis);
                return base + bits;
            }
            case WALL: {
                int[] h = new int[4];
                for (int d = 0; d < 4; d++) h[mirrorDir(d, axis)] = side(s, d);
                return wall(h[N], h[E], h[S], h[W], up(s));
            }
            default: return s;
        }
    }

    /** The state's shape as 4096 voxels, index (y*16 + z)*16 + x. */
    public static boolean[] voxels(int s) { return voxels(BOXES[s]); }

    public static boolean[] voxels(int[][] boxes) {
        boolean[] v = new boolean[4096];
        for (int[] b : boxes)
            for (int y = b[1]; y < b[4]; y++)
                for (int z = b[2]; z < b[5]; z++)
                    for (int x = b[0]; x < b[3]; x++) v[(y * 16 + z) * 16 + x] = true;
        return v;
    }

    static {
        // ---- parts ----
        for (int o = 0; o < 8; o++) {
            int x = (o & 1) * 8, z = (o >> 1 & 1) * 8, y = (o >> 2) * 8;
            part(OCTANT + o, Family.FULL, box(x, y, z, x + 8, y + 8, z + 8));
        }
        final int trap = 8, shelf = 14, chain = 18, rod = 21;
        part(trap, Family.TRAPDOOR, box(0, 0, 0, 16, 3, 16));
        part(trap + 1, Family.TRAPDOOR, box(0, 13, 0, 16, 16, 16));
        for (int d = 0; d < 4; d++) {
            int back = (d + 2) % 4;   // the panel is on the side opposite the facing
            part(trap + 2 + d, Family.TRAPDOOR, toward(back, 0, 3, 0, 16, 0, 16));
            part(shelf + d, Family.SHELF, toward(back, 0, 3, 0, 16, 0, 16), toward(back, 3, 5, 0, 16, 0, 4), toward(back, 3, 5, 0, 16, 12, 16));
            part(FENCE_ARM + d, Family.FENCE, toward(d, 0, 6, 7, 9, 6, 9), toward(d, 0, 6, 7, 9, 12, 15));
            part(PANE_ARM + d, Family.PANE, toward(d, 0, 7, 7, 9, 0, 16));
        }
        part(chain, Family.CHAIN, box(0, 7, 7, 16, 9, 9));
        part(chain + 1, Family.CHAIN, box(7, 0, 7, 9, 16, 9));
        part(chain + 2, Family.CHAIN, box(7, 7, 0, 9, 9, 16));
        // An end rod's plate is on the face it points away from.
        for (int d = 0; d < 4; d++) part(rod + d, Family.ROD, toward((d + 2) % 4, 0, 1, 6, 10, 6, 10), toward((d + 2) % 4, 1, 16, 7, 9, 7, 9));
        part(rod + DOWN, Family.ROD, box(6, 15, 6, 10, 16, 10), box(7, 0, 7, 9, 15, 9));
        part(rod + UP, Family.ROD, box(6, 0, 6, 10, 1, 10), box(7, 1, 7, 9, 16, 9));
        part(FENCE_POST, Family.FENCE, box(6, 0, 6, 10, 16, 10));
        part(PANE_POST, Family.PANE, box(7, 0, 7, 9, 16, 9));
        // A wall's plan splits into ten regions: the centre, where sides overlap; the rim of the post beside each
        // side; the post's four corners; and each side beyond the post. Each has a part up to the low height
        // and another above it.
        for (int top = 0; top < 2; top++) {
            int y0 = top * 14, y1 = 14 + top * 2, p = WALL_PART + top;
            part(p, Family.WALL, box(5, y0, 5, 11, y1, 11));
            for (int d = 0; d < 4; d++) {
                part(p + (1 + d) * 2, Family.WALL, toward(d, 4, 5, 5, 11, y0, y1));
                part(p + (6 + d) * 2, Family.WALL, toward(d, 0, 4, 5, 11, y0, y1));
            }
            part(p + 10, Family.WALL, box(4, y0, 4, 5, y1, 5), box(11, y0, 4, 12, y1, 5), box(4, y0, 11, 5, y1, 12), box(11, y0, 11, 12, y1, 12));
        }
        for (int p = 0; p < PART_COUNT; p++) for (int[] b : PART_BOXES[p]) PART_VOL[p] += (b[3] - b[0]) * (b[4] - b[1]) * (b[5] - b[2]);

        // ---- states ----
        def(AIR, Family.AIR, new int[0]);
        def(FULL, Family.FULL, new int[]{0, 1, 2, 3, 4, 5, 6, 7}, box(0, 0, 0, 16, 16, 16));
        def(SLAB_B, Family.SLAB, new int[]{0, 1, 2, 3}, box(0, 0, 0, 16, 8, 16));
        def(SLAB_T, Family.SLAB, new int[]{4, 5, 6, 7}, box(0, 8, 0, 16, 16, 16));
        for (int half = 0; half < 2; half++)
            for (int f = 0; f < 4; f++)
                for (int sh = 0; sh < 5; sh++) {
                    List<Integer> parts = new ArrayList<>();
                    List<int[]> boxes = new ArrayList<>();
                    int slab = half * 4, step = 4 - slab;   // octant offsets of the slab's layer and the step's
                    for (int q = 0; q < 4; q++) parts.add(slab + q);
                    boxes.add(box(0, half * 8, 0, 16, half * 8 + 8, 16));
                    int left = (f + 3) % 4;
                    for (int q = 0; q < 4; q++) {
                        int sx = (q & 1) * 2 - 1, sz = (q >> 1) * 2 - 1;
                        boolean front = sx * DX[f] + sz * DZ[f] > 0, onLeft = sx * DX[left] + sz * DZ[left] > 0;
                        boolean in = switch (sh) {
                            case INNER_LEFT -> front || onLeft;
                            case INNER_RIGHT -> front || !onLeft;
                            case OUTER_LEFT -> front && onLeft;
                            case OUTER_RIGHT -> front && !onLeft;
                            default -> front;
                        };
                        if (!in) continue;
                        parts.add(step + q);
                        int x = (q & 1) * 8, z = (q >> 1) * 8, y = 8 - half * 8;
                        boxes.add(box(x, y, z, x + 8, y + 8, z + 8));
                    }
                    def(stair(half, f, sh), Family.STAIRS, parts.stream().mapToInt(Integer::intValue).toArray(), boxes.toArray(new int[0][]));
                }
        def(TRAP_B, Family.TRAPDOOR, new int[]{trap}, PART_BOXES[trap]);
        def(TRAP_T, Family.TRAPDOOR, new int[]{trap + 1}, PART_BOXES[trap + 1]);
        for (int d = 0; d < 4; d++) {
            def(TRAP_OPEN + d, Family.TRAPDOOR, new int[]{trap + 2 + d}, PART_BOXES[trap + 2 + d]);
            def(SHELF + d, Family.SHELF, new int[]{shelf + d}, PART_BOXES[shelf + d]);
        }
        for (int a = 0; a < 3; a++) def(CHAIN + a, Family.CHAIN, new int[]{chain + a}, PART_BOXES[chain + a]);
        for (int d = 0; d < 6; d++) def(ROD + d, Family.ROD, new int[]{rod + d}, PART_BOXES[rod + d]);
        for (int bits = 0; bits < 16; bits++) {
            List<Integer> fp = new ArrayList<>(List.of(FENCE_POST)), pp = new ArrayList<>(List.of(PANE_POST));
            List<int[]> fb = new ArrayList<>(List.of(PART_BOXES[FENCE_POST])), pb = new ArrayList<>(List.of(PART_BOXES[PANE_POST]));
            for (int d = 0; d < 4; d++)
                if ((bits >> d & 1) != 0) {
                    fp.add(FENCE_ARM + d); fb.addAll(List.of(PART_BOXES[FENCE_ARM + d]));
                    pp.add(PANE_ARM + d); pb.addAll(List.of(PART_BOXES[PANE_ARM + d]));
                }
            def(FENCE + bits, Family.FENCE, fp.stream().mapToInt(Integer::intValue).toArray(), fb.toArray(new int[0][]));
            def(PANE + bits, Family.PANE, pp.stream().mapToInt(Integer::intValue).toArray(), pb.toArray(new int[0][]));
        }
        for (int s = WALL; s < COUNT; s++) {
            boolean post = ((s - WALL) & 1) == 1;
            int[] h = new int[4];
            for (int d = 0, v = (s - WALL) / 2; d < 4; d++, v /= 3) h[W - d] = v % 3;
            List<int[]> boxes = new ArrayList<>();
            if (post) boxes.add(box(4, 0, 4, 12, 16, 12));
            int[] reach = new int[10];   // height each region is filled to: 0, 14 or 16
            if (post) for (int r = 0; r < 6; r++) reach[r] = 16;
            for (int d = 0; d < 4; d++) {
                if (h[d] == 0) continue;
                int y = h[d] == 2 ? 16 : 14;
                boxes.add(toward(d, 0, 11, 5, 11, 0, y));
                for (int r : new int[]{0, 1 + d, 6 + d}) reach[r] = Math.max(reach[r], y);
            }
            List<Integer> parts = new ArrayList<>();
            for (int r = 0; r < 10; r++) {
                if (reach[r] > 0) parts.add(WALL_PART + r * 2);
                if (reach[r] == 16) parts.add(WALL_PART + r * 2 + 1);
            }
            def(s, Family.WALL, parts.stream().mapToInt(Integer::intValue).toArray(), boxes.toArray(new int[0][]));
        }

        for (int s = 0; s < COUNT; s++) {
            MX[s] = mirror(s, 0); MY[s] = mirror(s, 1); MZ[s] = mirror(s, 2);
            for (int p : PARTS[s]) VOL[s] += PART_VOL[p];
            boolean[] v = voxels(s);
            if (!isConnector(s))
                for (int d = 0; d < 6; d++) {
                    boolean full = true;
                    for (int a = 0; a < 16 && full; a++)
                        for (int b = 0; b < 16; b++) {
                            int x = d == W ? 0 : d == E ? 15 : a;
                            int y = d == DOWN ? 0 : d == UP ? 15 : b;
                            int z = d == N ? 0 : d == S ? 15 : d >= DOWN ? b : a;
                            if (!v[(y * 16 + z) * 16 + x]) { full = false; break; }
                        }
                    FACE[s][d] = full;
                }
            // A fence's collision shape has solid arms as wide as its post, unlike the two bars that are drawn.
            boolean[] floor = v;
            if (FAMILY[s] == Family.FENCE) {
                List<int[]> c = new ArrayList<>(List.of(box(6, 0, 6, 10, 16, 10)));
                for (int d = 0; d < 4; d++) if (side(s, d) > 0) c.add(toward(d, 0, 10, 6, 10, 0, 16));
                floor = voxels(c.toArray(new int[0][]));
            }
            int cover = covers(floor, box(7, 0, 7, 9, 1, 9)) ? COVER_POST : 0;
            for (int d = 0; d < 4; d++) if (covers(floor, toward(d, 0, 9, 7, 9, 0, 1))) cover |= 1 << d;
            COVER[s] = cover;
        }
    }

    private static boolean covers(boolean[] v, int[] b) {
        for (int z = b[2]; z < b[5]; z++)
            for (int x = b[0]; x < b[3]; x++) if (!v[z * 16 + x]) return false;
        return true;
    }

    /** Do these two families join when side by side? (Fences only join fences; panes and walls join each other.) */
    public static boolean joins(Family a, Family b) { return Pieces.joins(a, b); }
}
