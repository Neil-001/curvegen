package dev.curvegen.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import static dev.curvegen.core.Pieces3.*;

/**
 * Picks the best block state for every cell of a 3D shape, by volume. Stairs take their corner shape, and fences,
 * panes and walls their connections, heights and post, from their neighbours by the game's own rules, so every
 * state in the result is one a block update would leave alone.
 *
 * The work is in three stages. Each cell is marked empty, full or mixed, and a mixed cell is sampled at 16³ points
 * for its overlap with every part in {@link Pieces3}. Each mixed cell then starts with its lowest-error token.
 * Refinement re-picks cells, counting the error of every cell the pick can change, until nothing improves.
 *
 * Refinement ends because each pick lowers the total error of the cells it's judged on, so everything a token can
 * change has to be in that list: {@link Run#gather} explains what is. A symmetric shape is sampled in one octant
 * and solved in the quarter x and z leave, with mirrored cells sharing a token and the error counted in that
 * quarter only.
 */
public final class Solver3 {
    private Solver3() {}

    /**
     * The solved grid, x east, y up and z south, with the state at (x, y, z) in grid[(y*nz + z)*nx + x]. The error
     * and the ideal shape's volume are in blocks. Refinement settled if it took at most MAX_SWEEPS passes.
     */
    public record Result(short[] grid, int nx, int ny, int nz, double err, double volume, int[] counts, Shape3 shape, int sweeps) {
        public int at(int x, int y, int z) { return grid[(y * nz + z) * nx + x]; }
    }

    private static final int EMPTY = -1, SOLID = -2, NO_DATA = -3;
    public static final int MAX_SWEEPS = 30;

    // For each part, the rows of a block's 16×16×16 voxels it touches and its bits in each. A row runs along x.
    private static final int[][] PART_ROWS = new int[PART_COUNT][], PART_ROWS_FLIPPED = new int[PART_COUNT][], PART_MASKS = new int[PART_COUNT][];
    private static final short[] SOLID_OVERLAP = new short[PART_COUNT], EMPTY_OVERLAP = new short[PART_COUNT];
    /** The twelve other cells within two steps on the same level, as {dx, dz}. */
    private static final int[][] NEAR = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    static {
        for (int p = 0; p < PART_COUNT; p++) {
            int n = 0;
            for (int[] b : PART_BOXES[p]) n += (b[4] - b[1]) * (b[5] - b[2]);
            int[] rows = new int[n], flipped = new int[n], masks = new int[n];
            n = 0;
            for (int[] b : PART_BOXES[p])
                for (int y = b[1]; y < b[4]; y++)
                    for (int z = b[2]; z < b[5]; z++) {
                        rows[n] = y * 16 + z; flipped[n] = (15 - y) * 16 + z;
                        masks[n++] = (1 << b[3]) - (1 << b[0]);
                    }
            PART_ROWS[p] = rows; PART_ROWS_FLIPPED[p] = flipped; PART_MASKS[p] = masks;
            SOLID_OVERLAP[p] = (short) PART_VOL[p];
        }
    }

    public static Result run(ShapeSettings s) { return solve(Shape3.of(s), s, () -> false); }

    /**
     * Solves the shape with the settings' piece types. It's meant for a worker thread: pass a copy of the settings,
     * and have {@code cancelled} return true once the result is no longer wanted, which makes this return null soon after.
     * Sampling spreads over the common pool, so the shape's field and {@code cancelled} are called from several threads.
     */
    public static Result solve(Shape3 shape, ShapeSettings s, BooleanSupplier cancelled) {
        return new Run(shape, s, cancelled).solve();
    }

    /** The cells carving clears: those whose centre is in the space the shape encloses. Indexed like the grid; null if there's none. */
    public static BitSet carve(Shape3 sh) {
        double[] band = sh.carve();
        if (band == null || sh.error() != null) return null;
        int nx = sh.nx(), ny = sh.ny(), nz = sh.nz();
        int i0 = sh.symX() ? nx / 2 : 0, j0 = sh.symY() ? ny / 2 : 0, k0 = sh.symZ() ? nz / 2 : 0;
        BitSet out = new BitSet(nx * ny * nz);
        for (int j = j0; j < ny; j++)
            for (int k = k0; k < nz; k++)
                for (int i = i0; i < nx; i++) {
                    double d = sh.field(i + .5, j + .5, k + .5);
                    if (!(d > band[0] && d < band[1])) continue;
                    for (int m = 0; m < 8; m++) {
                        if (((m & 1) != 0 && i0 == 0) || ((m & 2) != 0 && j0 == 0) || ((m & 4) != 0 && k0 == 0)) continue;
                        int x = (m & 1) != 0 ? nx - 1 - i : i, y = (m & 2) != 0 ? ny - 1 - j : j, z = (m & 4) != 0 ? nz - 1 - k : k;
                        out.set((y * nz + z) * nx + x);
                    }
                }
        return out;
    }

    private static final class Run {
        final Shape3 sh;
        final ShapeSettings s;
        final BooleanSupplier cancelled;
        final int nx, ny, nz, px, pz, i0, j0, k0;
        final boolean sx, sy, sz, fullConnects;
        /** Step to each neighbour in the padded arrays, by direction. */
        final int[] off;
        final int up, down;
        /** Per padded cell: EMPTY, SOLID, NO_DATA (mixed, but outside the quarter that's scored) or its slot in overlap and count. */
        final int[] ref;
        final short[] tok;
        short[][] overlap = new short[256][];
        int[] count = new int[256];
        int slots;

        final double lo, hi;
        final boolean[] wanted = new boolean[PART_COUNT];

        // refinement scratch
        int[] aff = new int[64];
        int affN;

        Run(Shape3 sh, ShapeSettings s, BooleanSupplier cancelled) {
            this.sh = sh; this.s = s; this.cancelled = cancelled;
            nx = sh.nx(); ny = sh.ny(); nz = sh.nz();
            px = nx + 2; pz = nz + 2;
            sx = sh.symX(); sy = sh.symY(); sz = sh.symZ();
            i0 = sx ? nx / 2 : 0; j0 = sy ? ny / 2 : 0; k0 = sz ? nz / 2 : 0;
            fullConnects = s.fullConnects;
            up = px * pz; down = -up;
            off = new int[]{-px, 1, px, -1, down, up};
            int n = px * pz * (ny + 2);
            ref = new int[n];
            Arrays.fill(ref, EMPTY);
            tok = new short[n];
            lo = sh.lo(); hi = sh.hi();
            for (int p = 0; p < PART_COUNT; p++) wanted[p] = PART_FAMILY[p] == Pieces.Family.FULL || s.allows(PART_FAMILY[p]);
        }

        int at(int i, int j, int k) { return ((j + 1) * pz + k + 1) * px + i + 1; }

        Result solve() {
            short[] grid = new short[nx * ny * nz];
            int[] counts = new int[COUNT];
            if (sh.error() != null) { counts[AIR] = grid.length; return new Result(grid, nx, ny, nz, 0, 0, counts, sh, 0); }
            if (!sample()) return null;
            int sweeps = refine(start());
            if (sweeps < 0) return null;

            short[] st = new short[tok.length];
            double err = 0, volume = 0;
            for (int j = 0; j < ny; j++)
                for (int k = 0; k < nz; k++)
                    for (int i = 0; i < nx; i++) {
                        int p = at(i, j, k);
                        st[p] = (short) resolve(p);
                        if ((sx && i < i0) || (sz && k < k0)) continue;
                        int weight = (sx && 2 * i + 1 != nx ? 2 : 1) * (sz && 2 * k + 1 != nz ? 2 : 1);
                        err += weight * error(p, st[p]);
                        volume += weight * (ref[p] >= 0 ? count[ref[p]] : ref[p] == SOLID ? 4096 : 0);
                    }
            for (int j = 0; j < ny; j++)
                for (int k = 0; k < nz; k++)
                    for (int i = 0; i < nx; i++) {
                        int p = at(i, j, k), v = st[p];
                        if (v == FULL && sh.hollow() && hidden(st, p)) v = AIR;
                        grid[(j * nz + k) * nx + i] = (short) v;
                        counts[v]++;
                    }
            if (cancelled.getAsBoolean()) return null;
            return new Result(grid, nx, ny, nz, err / 4096, volume / 4096, counts, sh, sweeps);
        }

        /**
         * Is this full block out of sight? It is when every neighbour fills the face they share. No connector fills
         * a face, so a block that a connector attaches to, or that sets the height of a wall below, always stays.
         */
        boolean hidden(short[] st, int p) {
            for (int d = 0; d < 4; d++) if (!FACE[st[p + off[d]]][d ^ 2]) return false;
            return FACE[st[p + up]][DOWN] && FACE[st[p + down]][UP];
        }

        // ---------- sampling ----------

        /** Marks every cell empty, solid or mixed. Layers are sampled side by side, then stored in order so the result never depends on timing. */
        boolean sample() {
            Layer[] layers = new Layer[ny];
            IntStream.range(j0, ny).parallel().forEach(j -> { if (!cancelled.getAsBoolean()) layers[j] = new Layer(j); });
            for (int j = j0; j < ny; j++) {
                Layer l = layers[j];
                if (l == null || l.stopped) return false;
                if (slots + 2 * l.n > count.length) {
                    int size = Math.max(slots + 2 * l.n, count.length * 2);
                    count = Arrays.copyOf(count, size); overlap = Arrays.copyOf(overlap, size);
                }
                for (int q = 0; q < l.n; q++) {
                    int p = l.cells[q];
                    ref[p] = store(l.overlaps.get(2 * q), l.counts[q]);
                    if (sy && 2 * j + 1 != ny) ref[p + (ny - 1 - 2 * j) * up] = store(l.overlaps.get(2 * q + 1), l.counts[q]);
                }
            }
            // Cells outside the scored quarter only need to know what kind they are.
            if (sx || sz)
                for (int j = 0; j < ny; j++)
                    for (int k = 0; k < nz; k++)
                        for (int i = 0; i < nx; i++) {
                            if (!(sx && i < i0) && !(sz && k < k0)) continue;
                            int r = ref[at(sx && i < i0 ? nx - 1 - i : i, j, sz && k < k0 ? nz - 1 - k : k)];
                            ref[at(i, j, k)] = r >= 0 ? NO_DATA : r;
                        }
            return true;
        }

        int store(short[] ov, int cnt) { overlap[slots] = ov; count[slots] = cnt; return slots++; }

        /** One level of the grid: its solid cells go straight into {@link #ref}, and its mixed cells are kept here until every level is done. */
        final class Layer {
            int[] cells = new int[64], counts = new int[64];
            int n;
            boolean stopped;
            /** For each mixed cell, its overlap with every part, then the same for the cell's mirror image in y. */
            final List<short[]> overlaps = new ArrayList<>();
            final double[] lattice = new double[125], plane = new double[25], line = new double[5];
            final int[] rows = new int[256];

            Layer(int j) {
                int jm = ny - 1 - j;
                for (int k = k0; k < nz; k++) {
                    if (cancelled.getAsBoolean()) { stopped = true; return; }
                    for (int i = i0; i < nx; ) {
                        int run = sh.uniform(i, j, k);
                        if (run < 0) { i -= run; continue; }
                        if (run > 0) {
                            for (int e = Math.min(nx, i + run); i < e; i++) solid(i, j, jm, k);
                            continue;
                        }
                        int c = sampleCell(i, j, k);
                        if (c == 4096) solid(i, j, jm, k);
                        else if (c > 0) {
                            if (n == cells.length) { cells = Arrays.copyOf(cells, n * 2); counts = Arrays.copyOf(counts, n * 2); }
                            cells[n] = at(i, j, k); counts[n++] = c;
                            overlaps.add(overlaps(PART_ROWS));
                            overlaps.add(sy && jm != j ? overlaps(PART_ROWS_FLIPPED) : null);
                        }
                        i++;
                    }
                }
            }

            void solid(int i, int j, int jm, int k) {
                ref[at(i, j, k)] = SOLID;
                if (sy) ref[at(i, jm, k)] = SOLID;
            }

            /** Fills {@link #rows} with the cell's voxels that are inside the shape and returns how many are. */
            int sampleCell(int i, int j, int k) {
                int cnt = 0;
                if (!sh.smooth()) {
                    for (int r = 0; r < 256; r++) {
                        double y = j + ((r >> 4) + .5) / 16, z = k + ((r & 15) + .5) / 16;
                        int bits = 0;
                        for (int x = 0; x < 16; x++) {
                            double v = sh.field(i + (x + .5) / 16, y, z);
                            if (v >= lo && v <= hi) bits |= 1 << x;
                        }
                        rows[r] = bits; cnt += Integer.bitCount(bits);
                    }
                    return cnt;
                }
                // The field at 5×5×5 points a quarter of a block apart, interpolated to the voxel centres between them.
                int in = 0, above = 0, below = 0;
                for (int b = 0, q = 0; b < 5; b++)
                    for (int c = 0; c < 5; c++)
                        for (int a = 0; a < 5; a++) {
                            double v = sh.field(i + a * .25, j + b * .25, k + c * .25);
                            lattice[q++] = v;
                            if (v > hi) above++; else if (v < lo) below++; else if (v == v) in++;
                        }
                if (in == 125) return 4096;
                if (above == 125 || below == 125) return 0;
                for (int y = 0; y < 16; y++) {
                    int b = (y >> 2) * 25;
                    double ty = ((y & 3) + .5) / 4;
                    for (int q = 0; q < 25; q++) plane[q] = lattice[b + q] + (lattice[b + 25 + q] - lattice[b + q]) * ty;
                    for (int z = 0; z < 16; z++) {
                        int c = (z >> 2) * 5;
                        double tz = ((z & 3) + .5) / 4;
                        for (int a = 0; a < 5; a++) line[a] = plane[c + a] + (plane[c + 5 + a] - plane[c + a]) * tz;
                        int bits = 0;
                        for (int x = 0; x < 16; x++) {
                            int a = x >> 2;
                            double v = line[a] + (line[a + 1] - line[a]) * (((x & 3) + .5) / 4);
                            if (v >= lo && v <= hi) bits |= 1 << x;
                        }
                        rows[y * 16 + z] = bits; cnt += Integer.bitCount(bits);
                    }
                }
                return cnt;
            }

            /** The sampled cell's overlap with every part in use. */
            short[] overlaps(int[][] partRows) {
                short[] ov = new short[PART_COUNT];
                for (int p = 0; p < PART_COUNT; p++) {
                    if (!wanted[p]) continue;
                    int[] r = partRows[p], m = PART_MASKS[p];
                    int v = 0;
                    for (int q = 0; q < r.length; q++) v += Integer.bitCount(rows[r[q]] & m[q]);
                    ov[p] = (short) v;
                }
                return ov;
            }
        }

        // ---------- states ----------

        short[] overlapOf(int p) { int r = ref[p]; return r >= 0 ? overlap[r] : r == SOLID ? SOLID_OVERLAP : EMPTY_OVERLAP; }
        int countOf(int p) { int r = ref[p]; return r >= 0 ? count[r] : r == SOLID ? 4096 : 0; }

        /** Voxels where this state and the target in cell p differ. */
        int error(int p, int state) {
            short[] ov = overlapOf(p);
            int both = 0;
            for (int part : PARTS[state]) both += ov[part];
            return countOf(p) + VOL[state] - 2 * both;
        }

        /** The least error any state of this token can have in cell p, whatever its neighbours are. */
        int bound(int p, int t) {
            if (!dependent(t)) return error(p, t);
            if (t < FENCE) {
                int best = Integer.MAX_VALUE;
                for (int shape = 0; shape < 5; shape++) best = Math.min(best, error(p, t + shape));
                return best;
            }
            short[] ov = overlapOf(p);
            int e = countOf(p), first, last;
            if (t >= WALL) { first = WALL_PART; last = PART_COUNT; }
            else {
                int post = t >= PANE ? PANE_POST : FENCE_POST;
                e += PART_VOL[post] - 2 * ov[post];
                first = post + 1; last = post + 5;
            }
            for (int part = first; part < last; part++) e += Math.min(0, PART_VOL[part] - 2 * ov[part]);
            return e;
        }

        /** The corner shape the game gives the stair token t at p, from the stairs in front of and behind it. */
        int stairState(int p, int t) {
            int half = (t - STAIRS) / 20, f = (t - STAIRS) / 5 % 4, left = (f + 3) & 3;
            int n = tok[p + off[f]];
            if (isStairs(n) && (n - STAIRS) / 20 == half) {
                int nf = (n - STAIRS) / 5 % 4;
                if (((nf ^ f) & 1) != 0 && differs(p + off[nf ^ 2], t)) return t + (nf == left ? OUTER_LEFT : OUTER_RIGHT);
            }
            n = tok[p + off[f ^ 2]];
            if (isStairs(n) && (n - STAIRS) / 20 == half) {
                int nf = (n - STAIRS) / 5 % 4;
                if (((nf ^ f) & 1) != 0 && differs(p + off[nf], t)) return t + (nf == left ? INNER_LEFT : INNER_RIGHT);
            }
            return t;
        }

        /** A stair turns a corner only where the cell beside it isn't a stair facing the same way. Tokens are straight, so equal tokens mean exactly that. */
        boolean differs(int p, int t) { return tok[p] != t; }

        /** Does a connector (0 fence, 1 pane, 2 wall) attach to the cell at p, which lies in direction d from it? */
        boolean connects(int kind, int p, int d) {
            int n = tok[p];
            if (n >= FENCE) return kind == 0 ? n < PANE : n >= PANE;
            if (n == FULL) return fullConnects;
            return FACE[isStairs(n) ? stairState(p, n) : n][d ^ 2];
        }

        int resolve(int p) {
            int t = tok[p];
            if (!dependent(t)) return t;
            if (t < FENCE) return stairState(p, t);
            int kind = t >= WALL ? 2 : t >= PANE ? 1 : 0, bits = 0;
            for (int d = 0; d < 4; d++) if (connects(kind, p + off[d], d)) bits |= 1 << d;
            if (kind < 2) return t + bits;
            int above = resolve(p + up);
            return wallState(bits, COVER[above], isWall(above) && Pieces3.up(above));
        }

        // ---------- solving ----------

        /** Gives every mixed cell its lowest-error token and returns the cells to refine, as {x, y, z, candidate list}. */
        List<int[]> start() {
            List<Integer> ids = new ArrayList<>(List.of(AIR, FULL));
            if (s.slab) ids.addAll(List.of(SLAB_B, SLAB_T));
            if (s.stair) for (int half = 0; half < 2; half++) for (int f = 0; f < 4; f++) ids.add(stair(half, f, STRAIGHT));
            if (s.trap) for (int t = TRAP_B; t < SHELF; t++) ids.add(t);
            if (s.shelf) for (int t = SHELF; t < CHAIN; t++) ids.add(t);
            if (s.fence) ids.add(FENCE);
            if (s.pane) ids.add(PANE);
            if (s.wall) ids.add(WALL_POST);
            if (s.chain) for (int t = CHAIN; t < ROD; t++) ids.add(t);
            if (s.rod) for (int t = ROD; t < FENCE; t++) ids.add(t);
            // A cell on a mirror plane is its own image, so it may only hold tokens that are too.
            candidates = new int[8][];
            for (int m = 0; m < 8; m++) {
                final int mm = m;
                candidates[m] = ids.stream().mapToInt(Integer::intValue)
                        .filter(t -> ((mm & 1) == 0 || MX[t] == t) && ((mm & 2) == 0 || MY[t] == t) && ((mm & 4) == 0 || MZ[t] == t)).toArray();
            }
            List<int[]> orbits = new ArrayList<>();
            for (int j = j0; j < ny; j++)
                for (int k = k0; k < nz; k++)
                    for (int i = i0; i < nx; i++) {
                        int p = at(i, j, k);
                        if (ref[p] < 0) { place(i, j, k, ref[p] == SOLID ? FULL : AIR); continue; }
                        int list = (sx && 2 * i + 1 == nx ? 1 : 0) | (sy && 2 * j + 1 == ny ? 2 : 0) | (sz && 2 * k + 1 == nz ? 4 : 0);
                        // First guess: connectors attach wherever a neighbour will hold something.
                        int bits = 0;
                        for (int d = 0; d < 4; d++) if (ref[p + off[d]] != EMPTY) bits |= 1 << d;
                        int over = ref[p + up];
                        int wallGuess = wallState(bits, over == SOLID ? 31 : over == EMPTY ? 0 : COVER_POST, false);
                        int best = AIR, bestE = Integer.MAX_VALUE;
                        for (int cand : candidates[list]) {
                            int e = error(p, cand == WALL_POST ? wallGuess : cand == FENCE || cand == PANE ? cand + bits : cand);
                            if (e < bestE || (e == bestE && best == AIR && isLine(cand))) { bestE = e; best = cand; }
                        }
                        place(i, j, k, best);
                        orbits.add(new int[]{i, j, k, list});
                    }
            return orbits;
        }

        int[][] candidates;

        /** A wall's state from its connections, what the block above covers, and whether that block is a wall with a post. */
        static int wallState(int bits, int cover, boolean postAbove) {
            int tall = bits & cover, n = height(bits, tall, N), e = height(bits, tall, E), s = height(bits, tall, S), w = height(bits, tall, W);
            boolean post = postAbove || bits == 0 || (n == 0) != (s == 0) || (e == 0) != (w == 0)
                    || (!(n == 2 && s == 2) && !(e == 2 && w == 2) && (cover & COVER_POST) != 0);
            return wall(n, e, s, w, post);
        }

        static int height(int bits, int tall, int d) { return (bits >> d & 1) + (tall >> d & 1); }

        /** Puts a token in a cell and its mirror image in every cell the shape's symmetry pairs it with. */
        void place(int i, int j, int k, int t) {
            int im = nx - 1 - i, jm = ny - 1 - j, km = nz - 1 - k;
            for (int m = 0; m < 8; m++) {
                boolean mx = (m & 1) != 0, my = (m & 2) != 0, mz = (m & 4) != 0;
                if ((mx && (!sx || im == i)) || (my && (!sy || jm == j)) || (mz && (!sz || km == k))) continue;
                int v = t;
                if (mx) v = MX[v];
                if (my) v = MY[v];
                if (mz) v = MZ[v];
                tok[at(mx ? im : i, my ? jm : j, mz ? km : k)] = (short) v;
            }
        }

        /** Returns how many passes it made, or -1 if cancelled. */
        int refine(List<int[]> orbits) {
            if (!(s.stair || s.fence || s.pane || s.wall)) return 0;
            for (int sweep = 0; sweep < MAX_SWEEPS; sweep++) {
                boolean changed = false;
                int n = 0;
                for (int[] o : orbits) {
                    if ((n++ & 1023) == 0 && cancelled.getAsBoolean()) return -1;
                    int i = o[0], j = o[1], k = o[2], c = at(i, j, k);
                    int twin = sy && 2 * j + 1 != ny ? at(i, ny - 1 - j, k) : -1;
                    affN = 0;
                    gather(i, k, c);
                    if (twin >= 0) gather(i, k, twin);
                    int cur = tok[c], best = cur, bestE = 0, others = 0;
                    for (int q = 0; q < affN; q++) {
                        int p = aff[q];
                        bestE += error(p, resolve(p));
                        if (p != c && p != twin) others += bound(p, tok[p]);
                    }
                    for (int cand : candidates[o[3]]) {
                        if (cand == cur) continue;
                        boolean tieWins = best == AIR && isLine(cand);
                        int least = others + bound(c, cand) + (twin >= 0 ? bound(twin, MY[cand]) : 0);
                        if (least > bestE || (least == bestE && !tieWins)) continue;
                        place(i, j, k, cand);
                        int e = 0;
                        for (int q = 0; q < affN; q++) e += error(aff[q], resolve(aff[q]));
                        if (e < bestE || (e == bestE && tieWins)) { bestE = e; best = cand; }
                    }
                    place(i, j, k, best);
                    if (best != cur) changed = true;
                }
                if (!changed) return sweep + 1;
            }
            return MAX_SWEEPS + 1;
        }

        /**
         * Adds to {@link #aff} the scored cells whose state can change when the token at c does. A stair's shape
         * and a connector's sides follow the four cells around it, and a connector attaches to a stair by the
         * stair's shape, so the reach is two steps on c's level. Below any of those, a wall takes its heights and
         * post from the state above it, and passes its post on to a wall below. Only stair and connector tokens
         * change with their surroundings, so the others are left out.
         */
        void gather(int i, int k, int c) {
            column(c);
            for (int[] d : NEAR) {
                int x = i + d[0], z = k + d[1];
                if (x < i0 || x >= nx || z < k0 || z >= nz) continue;
                int p = c + d[0] + d[1] * px;
                if (dependent(tok[p])) column(p);
            }
        }

        void column(int p) {
            do {
                boolean seen = false;
                for (int q = 0; q < affN && !seen; q++) seen = aff[q] == p;
                if (!seen) {
                    if (affN == aff.length) aff = Arrays.copyOf(aff, affN * 2);
                    aff[affN++] = p;
                }
                p += down;
            } while (tok[p] == WALL_POST);
        }
    }
}
