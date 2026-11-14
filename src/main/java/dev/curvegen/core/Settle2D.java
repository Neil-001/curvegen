package dev.curvegen.core;

import java.util.Arrays;
import java.util.BitSet;
import java.util.function.BooleanSupplier;

/**
 * A solved 2D shape with some of its blocks taken out after the solve, such as a pattern's holes, and every block
 * left given the state the game gives it then: a fence arm that reached into an emptied block drops and a wall
 * follows the block above it. Nothing is solved again, so every block keeps its piece type.
 *
 * <p>A 2D shape is the same drawing in every layer until blocks go, so the blocks are laid out as 3D states, as
 * {@link Pieces3} has them, and settled by {@link Solver3#settle}, which applies the game's own rules. The layout
 * is in fixed directions: the drawing's x runs east. Upright, its y runs up and the layers run south. Flat, its y
 * runs north and the layers stack up. {@link #joints} says what a fence, pane or wall joins in the shape's own terms,
 * so a client can turn it any way.
 *
 * <p>Blocks are numbered {@code (k * ny + j) * nx + i}, k being the layer, as a {@code Painted}'s slots are.
 */
public final class Settle2D {
    /** {@link #joints}: two bits a side for left, right, front and back, then the post. */
    public static final int LEFT = 0, RIGHT = 2, FRONT = 4, BACK = 6, POST = 1 << 8;

    private final Solver.Result r;
    /** The settled 3D state of each block, numbered like the blocks. */
    private final short[] states;

    private Settle2D(Solver.Result r, short[] states) { this.r = r; this.states = states; }

    /**
     * Empties the blocks in {@code gone}, numbered as the blocks are over {@code depth} layers, and settles the rest.
     * Returns null once {@code cancelled} reports true.
     *
     * <p>With few holes, the shape is settled whole first, without holes, from at most five layers, and then only
     * the blocks near the holes are worked out again ({@link Solver3#settleNear}). With many, every block is. Upright, a block's state follows the blocks up to two
     * layers in front and behind, so every layer two away from both faces settles as the middle layer of five does.
     * Flat, a layer follows the one above it only through walls, which settle to the same states a few layers down;
     * if they haven't by the bottom of five, the whole depth is settled.
     */
    public static Settle2D of(Solver.Result r, int depth, BitSet gone, boolean fullConnects, BooleanSupplier cancelled) {
        int nx = r.nx(), ny = r.ny(), d = Math.max(1, depth), n = nx * ny;
        boolean floor = r.floor();
        int sz = floor ? ny : d;
        BitSet cells = new BitSet(d * n);
        for (int b = gone.nextSetBit(0); b >= 0 && b < d * n; b = gone.nextSetBit(b + 1))
            cells.set(cell(b % nx, b % n / nx, b / n, nx, ny, sz, floor));
        Solver3.Result settled;
        if (Solver3.nearIsQuicker(cells.cardinality(), (long) d * n)) {
            Solver3.Result whole = whole(r, d, fullConnects, cancelled);
            settled = whole == null ? null : Solver3.settleNear(whole, cells, fullConnects, cancelled);
        } else settled = Solver3.settleAll(layout(r, d), cells, fullConnects, cancelled);
        if (settled == null) return null;
        short[] states = new short[d * n];
        for (int k = 0; k < d; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) states[(k * ny + j) * nx + i] = settled.grid()[cell(i, j, k, nx, ny, sz, floor)];
        return new Settle2D(r, states);
    }

    /** The layers of a shape before anything is taken out: a fence, pane or wall is its token. */
    static Solver3.Result layout(Solver.Result r, int d) {
        int nx = r.nx(), ny = r.ny();
        boolean floor = r.floor();
        // Upright: x = i, y = j, z = k. Flat: x = i, y = k, z = ny - 1 - j.
        int sy = floor ? d : ny, sz = floor ? ny : d;
        short[] grid = new short[nx * sy * sz];
        int[] counts = new int[Pieces3.COUNT];
        counts[Pieces3.AIR] = grid.length;
        for (int k = 0; k < d; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int p = r.at(i, j);
                    if (p == Pieces.EMPTY) continue;
                    int s = state(p, floor);
                    grid[cell(i, j, k, nx, ny, sz, floor)] = (short) s;
                    counts[s]++;
                    counts[Pieces3.AIR]--;
                }
        return new Solver3.Result(grid, nx, sy, sz, 0, 0, counts, null, 0);
    }

    /** The shape {@code d} layers deep, settled with nothing taken out, or null if cancelled. */
    static Solver3.Result whole(Solver.Result r, int d, boolean fullConnects, BooleanSupplier cancelled) {
        int few = Math.min(d, FEW);
        Solver3.Result s = Solver3.settleAll(layout(r, few), new BitSet(), fullConnects, cancelled);
        if (s == null || few == d) return s;
        int nx = r.nx(), ny = r.ny(), level = nx * s.nz();
        short[] small = s.grid();
        boolean floor = r.floor();
        if (floor && !Arrays.equals(small, 0, level, small, level, 2 * level))
            return Solver3.settleAll(layout(r, d), new BitSet(), fullConnects, cancelled);
        short[] grid = new short[nx * (floor ? d * ny : ny * d)];
        if (floor) {
            // The layer with m layers above it is the small one's with as many, or its bottom one past that.
            for (int k = 0; k < d; k++) System.arraycopy(small, Math.max(0, k - (d - few)) * level, grid, k * level, level);
        } else {
            for (int j = 0; j < ny; j++)
                for (int k = 0; k < d; k++) {
                    int from = k < 2 ? k : k >= d - 2 ? k - (d - few) : 2;
                    System.arraycopy(small, (j * few + from) * nx, grid, (j * d + k) * nx, nx);
                }
        }
        int[] counts = new int[Pieces3.COUNT];
        for (short v : grid) counts[v]++;
        return new Solver3.Result(grid, nx, floor ? d : ny, floor ? ny : d, 0, 0, counts, null, 0);
    }

    /** Layers settled to work out a deeper shape: two for each face and one for all the layers between. */
    private static final int FEW = 5;

    static int cell(int i, int j, int k, int nx, int ny, int sz, boolean floor) {
        return floor ? (k * sz + ny - 1 - j) * nx + i : (j * sz + k) * nx + i;
    }

    /**
     * The 3D state a 2D piece is placed as, before its neighbours shape it: a fence, pane or wall is its token. These
     * match the client's {@code BlockChoices.stateFor} with the drawing's x east, and south (upright) or north (flat)
     * as the drawing's forward.
     */
    static int state(int p, boolean floor) {
        if (Pieces.isConnector(p)) {
            return switch (Pieces.FAMILY[p]) {
                case FENCE -> Pieces3.FENCE;
                case PANE -> Pieces3.PANE;
                default -> Pieces3.WALL_POST;
            };
        }
        int forward = floor ? Pieces3.N : Pieces3.S, back = floor ? Pieces3.S : Pieces3.N;
        return switch (p) {
            case Pieces.FULL -> Pieces3.FULL;
            case Pieces.SLAB_B -> Pieces3.SLAB_B;
            case Pieces.SLAB_T -> Pieces3.SLAB_T;
            // A stair faces the side its tall back is on.
            case Pieces.ST_UR -> Pieces3.stair(0, Pieces3.E, Pieces3.STRAIGHT);
            case Pieces.ST_UL -> Pieces3.stair(0, Pieces3.W, Pieces3.STRAIGHT);
            case Pieces.ST_DR -> Pieces3.stair(1, Pieces3.E, Pieces3.STRAIGHT);
            case Pieces.ST_DL -> Pieces3.stair(1, Pieces3.W, Pieces3.STRAIGHT);
            case Pieces.TD_B -> Pieces3.TRAP_B;
            case Pieces.TD_T -> Pieces3.TRAP_T;
            // An open trapdoor and a shelf lie against the side opposite their facing.
            case Pieces.TD_L -> Pieces3.TRAP_OPEN + Pieces3.E;
            case Pieces.TD_R -> Pieces3.TRAP_OPEN + Pieces3.W;
            case Pieces.F_TD_U -> Pieces3.TRAP_OPEN + back;
            case Pieces.F_TD_D -> Pieces3.TRAP_OPEN + forward;
            case Pieces.SH_L, Pieces.F_SH_L -> Pieces3.SHELF + Pieces3.E;
            case Pieces.SH_R, Pieces.F_SH_R -> Pieces3.SHELF + Pieces3.W;
            case Pieces.F_SH_U -> Pieces3.SHELF + back;
            case Pieces.F_SH_D -> Pieces3.SHELF + forward;
            case Pieces.CHAIN_H -> Pieces3.CHAIN;
            case Pieces.CHAIN_V -> Pieces3.CHAIN + (floor ? 2 : 1);
            // A rod faces the way it points.
            case Pieces.ROD_U -> Pieces3.ROD + (floor ? forward : Pieces3.UP);
            case Pieces.ROD_D -> Pieces3.ROD + (floor ? back : Pieces3.DOWN);
            case Pieces.ROD_L -> Pieces3.ROD + Pieces3.W;
            case Pieces.ROD_R -> Pieces3.ROD + Pieces3.E;
            default -> throw new IllegalArgumentException(String.valueOf(p));
        };
    }

    /** The settled 3D state of the block at column i, row j and layer k, as {@link Pieces3} has them. */
    int state(int i, int j, int k) { return states[(k * r.ny() + j) * r.nx() + i]; }

    /** Whether there's a block at column i, row j and layer k. */
    public boolean filled(int i, int j, int k) { return states[(k * r.ny() + j) * r.nx() + i] != Pieces3.AIR; }

    /**
     * What a fence, pane or wall at column i, row j and layer k joins: for each side ({@link #LEFT}, {@link #RIGHT},
     * {@link #FRONT}, {@link #BACK}, each a shift), 0 for none, 1 joined (low, for a wall) or 2 tall, and
     * {@link #POST} when a wall has its post. Front is the way the layers run upright and the drawing's y flat.
     * 0 for any other block.
     */
    public int joints(int i, int j, int k) {
        int s = states[(k * r.ny() + j) * r.nx() + i];
        if (!Pieces3.isConnector(s)) return 0;
        boolean floor = r.floor();
        int front = floor ? Pieces3.N : Pieces3.S, back = floor ? Pieces3.S : Pieces3.N;
        return Pieces3.side(s, Pieces3.W) << LEFT | Pieces3.side(s, Pieces3.E) << RIGHT | Pieces3.side(s, front) << FRONT
                | Pieces3.side(s, back) << BACK | (Pieces3.up(s) ? POST : 0);
    }

    public static int side(int joints, int side) { return joints >> side & 3; }

    /**
     * The 2D piece at column i, row j and layer k as the drawing shows it, with the connections it has now, or
     * {@link Pieces#EMPTY} once it's gone.
     */
    public int piece(int i, int j, int k) {
        int p = r.at(i, j);
        if (!filled(i, j, k)) return Pieces.EMPTY;
        if (!Pieces.isConnector(p)) return p;
        int jt = joints(i, j, k), l = side(jt, LEFT), rr = side(jt, RIGHT);
        if (r.floor()) {
            int bits = (l > 0 ? Pieces.LEFT : 0) | (rr > 0 ? Pieces.RIGHT : 0) | (side(jt, FRONT) > 0 ? Pieces.TOP : 0) | (side(jt, BACK) > 0 ? Pieces.BOTTOM_SIDE : 0);
            return switch (Pieces.FAMILY[p]) {
                case FENCE -> Pieces.F_FENCE + bits;
                case PANE -> Pieces.F_PANE + bits;
                default -> Pieces.F_WALL + bits;
            };
        }
        return switch (Pieces.FAMILY[p]) {
            case FENCE -> Pieces.FENCE + (l > 0 ? 1 : 0) + (rr > 0 ? 2 : 0);
            case PANE -> Pieces.PANE + (l > 0 ? 1 : 0) + (rr > 0 ? 2 : 0);
            default -> Pieces.wall(l, rr, covered(i, j + 1, k), (jt & POST) != 0);
        };
    }

    /** Whether the block in column i, row j and layer k of an upright shape covers the centre of a wall below it. */
    private boolean covered(int i, int j, int k) {
        return j < r.ny() && (Pieces3.COVER[states[(k * r.ny() + j) * r.nx() + i]] & Pieces3.COVER_POST) != 0;
    }

    /** The drawing as layer k shows it: the solve with this layer's pieces, for a picture of the shape. */
    public Solver.Result layer(int k) {
        int nx = r.nx(), ny = r.ny();
        byte[] grid = new byte[nx * ny];
        int[] counts = new int[Pieces.COUNT];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int p = piece(i, j, k);
                grid[j * nx + i] = (byte) p;
                counts[p]++;
            }
        return new Solver.Result(grid, nx, ny, r.err(), r.area(), counts, r.target(), r.floor());
    }
}
