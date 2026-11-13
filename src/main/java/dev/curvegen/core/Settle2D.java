package dev.curvegen.core;

import java.util.BitSet;

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
    private final int depth;
    /** The settled 3D state of each block, numbered like the blocks. */
    private final short[] states;
    private final int gone;

    private Settle2D(Solver.Result r, int depth, short[] states, int gone) {
        this.r = r; this.depth = depth; this.states = states; this.gone = gone;
    }

    /**
     * Empties the blocks in {@code gone}, numbered as the blocks are over {@code depth} layers, and settles the rest.
     * Returns null once {@code cancelled} reports true.
     */
    public static Settle2D of(Solver.Result r, int depth, BitSet gone, boolean fullConnects, java.util.function.BooleanSupplier cancelled) {
        int nx = r.nx(), ny = r.ny(), d = Math.max(1, depth), n = nx * ny;
        boolean floor = r.floor();
        // Upright: x = i, y = j, z = k. Flat: x = i, y = k, z = ny - 1 - j.
        int sy = floor ? d : ny, sz = floor ? ny : d;
        short[] grid = new short[nx * sy * sz];
        int[] counts = new int[Pieces3.COUNT];
        int emptied = 0;
        for (int k = 0; k < d; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int p = r.at(i, j);
                    if (p == Pieces.EMPTY) continue;
                    if (gone.get((k * ny + j) * nx + i)) { emptied++; continue; }
                    int s = state(p, floor);
                    grid[cell(i, j, k, nx, ny, sz, floor)] = (short) s;
                    counts[s]++;
                }
        Solver3.Result settled = Solver3.settle(new Solver3.Result(grid, nx, sy, sz, 0, 0, counts, null, 0), new BitSet(), fullConnects, cancelled);
        if (settled == null) return null;
        short[] states = new short[d * n];
        for (int k = 0; k < d; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) states[(k * ny + j) * nx + i] = settled.grid()[cell(i, j, k, nx, ny, sz, floor)];
        return new Settle2D(r, d, states, emptied);
    }

    private static int cell(int i, int j, int k, int nx, int ny, int sz, boolean floor) {
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

    public int nx() { return r.nx(); }
    public int ny() { return r.ny(); }
    public int depth() { return depth; }
    public boolean floor() { return r.floor(); }
    /** How many blocks were emptied, in every layer. */
    public int gone() { return gone; }

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
