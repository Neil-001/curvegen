package dev.curvegen.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Picks the best piece for every cell, aware that fences, panes and walls change shape with their surroundings.
 * Upright shapes are drawn from the side; flat shapes (floors) from above.
 */
public final class Solver {
    private Solver() {}

    public record Result(byte[] grid, int nx, int ny, double err, double area, int[] counts, Target target, boolean floor) {
        public int at(int i, int j) { return grid[j * nx + i]; }
    }

    public static Result solve(Target t, ShapeSettings s) {
        final int nx = t.nx, ny = t.ny, N = nx * ny;
        final boolean floor = s.floor, deep = s.depth > 1, fullConnects = s.fullConnects;
        List<Integer> ids = new ArrayList<>(List.of(Pieces.EMPTY, Pieces.FULL));
        if (floor) {
            // From above, slabs, stairs and closed trapdoors all look like full blocks, so only these differ.
            if (s.trap) ids.addAll(List.of(Pieces.TD_L, Pieces.TD_R, Pieces.F_TD_U, Pieces.F_TD_D));
            if (s.shelf) ids.addAll(List.of(Pieces.F_SH_L, Pieces.F_SH_R, Pieces.F_SH_U, Pieces.F_SH_D));
            if (s.fence) ids.add(Pieces.F_FENCE);
            if (s.pane) ids.add(Pieces.F_PANE);
            if (s.wall) ids.add(Pieces.F_WALL);
        } else {
            if (s.slab) ids.addAll(List.of(Pieces.SLAB_B, Pieces.SLAB_T));
            if (s.stair) ids.addAll(List.of(Pieces.ST_UR, Pieces.ST_UL, Pieces.ST_DR, Pieces.ST_DL));
            if (s.trap) ids.addAll(List.of(Pieces.TD_B, Pieces.TD_T, Pieces.TD_L, Pieces.TD_R));
            // From the side, a shelf facing the viewer or away looks like a full block, so only these differ.
            if (s.shelf) ids.addAll(List.of(Pieces.SH_L, Pieces.SH_R));
            if (s.fence) ids.add(Pieces.FENCE);
            if (s.pane) ids.add(Pieces.PANE);
            if (s.wall) ids.add(Pieces.WALL);
        }
        if (s.chain) ids.addAll(List.of(Pieces.CHAIN_H, Pieces.CHAIN_V));
        if (s.rod) ids.addAll(List.of(Pieces.ROD_U, Pieces.ROD_D, Pieces.ROD_L, Pieces.ROD_R));
        int[] cAll = ids.stream().mapToInt(Integer::intValue).toArray();
        int[] cX = ids.stream().mapToInt(Integer::intValue).filter(p -> Pieces.MX[p] == p).toArray();
        int[] cY = ids.stream().mapToInt(Integer::intValue).filter(p -> Pieces.MY[p] == p).toArray();
        int[] cXY = java.util.Arrays.stream(cX).filter(p -> Pieces.MY[p] == p).toArray();

        final byte[] grid = new byte[N];
        final boolean sx = t.symX, sy = t.symY;

        final class Ctx {
            /** Does connector type v attach to neighbour nb through nb's given face (0 bottom, 1 top, 2 left, 3 right)? */
            boolean connects(int v, int nb, int face) {
                if (Pieces.isConnector(nb)) return Pieces.joins(Pieces.FAMILY[v], Pieces.FAMILY[nb]);
                if (nb == Pieces.FULL && !fullConnects) return false;
                return Pieces.STURDY[nb][face];
            }
            boolean left(int idx) { return idx % nx > 0 && connects(grid[idx], grid[idx - 1], 3); }
            boolean right(int idx) { return idx % nx < nx - 1 && connects(grid[idx], grid[idx + 1], 2); }
            boolean up(int idx) { return idx + nx < N && connects(grid[idx], grid[idx + nx], 0); }
            boolean down(int idx) { return idx - nx >= 0 && connects(grid[idx], grid[idx - nx], 1); }
            /** Side view: bottom row of whatever is in cell idx, as a 16-bit mask: what a wall below it "feels". */
            int bottom(int idx) {
                if (idx >= N) return 0;
                int v = grid[idx];
                if (!Pieces.isType(v)) return Pieces.BOTTOM[v];
                if (v == Pieces.FENCE) return 0x03C0;
                if (v == Pieces.PANE) return 0x0180 | (left(idx) ? 0x007F : 0) | (right(idx) ? 0xFE00 : 0);
                return 0x0FF0 | (left(idx) ? 0x00FF : 0) | (right(idx) ? 0xFF00 : 0);
            }
            int resolve(int idx) {
                int v = grid[idx];
                if (!Pieces.isType(v)) return v;
                boolean L = left(idx), R = right(idx);
                if (v >= Pieces.F_FENCE)   // floor: four directions
                    return v + (L ? Pieces.LEFT : 0) + (R ? Pieces.RIGHT : 0) + (up(idx) ? Pieces.TOP : 0) + (down(idx) ? Pieces.BOTTOM_SIDE : 0);
                if (v != Pieces.WALL) return v + (L ? 1 : 0) + (R ? 2 : 0);
                // A wall side is tall when the block above covers it. A straight wall keeps its post only if the block
                // above covers its centre, but with depth > 1 the front and back walls each connect on one side only,
                // so they always have a post, and that's the post you see from the side.
                int cov = bottom(idx + nx);
                int l = L ? ((cov & 0x01FF) == 0x01FF ? 2 : 1) : 0, r = R ? ((cov & 0xFF80) == 0xFF80 ? 2 : 1) : 0;
                boolean covered = (cov & 0x0180) == 0x0180;
                return Pieces.wall(l, r, covered, deep || Pieces.wallPostRule(l, r, covered));
            }
            int cellErr(int idx) {
                int st = resolve(idx), k = t.kind[idx];
                return k == 0 ? Pieces.PIXELS[st] : k == 1 ? 256 - Pieces.PIXELS[st] : t.errTab[idx][st];
            }
            /**
             * What refinement minimises: the pixel error, plus in hollow shapes a penalty that outweighs any error for
             * a connector that looks exactly like a full block, such as a wall with two tall sides mirrored from one
             * with two low sides. The block above such a wall would have to stay to keep it tall.
             */
            int cost(int idx) {
                int st = resolve(idx);
                return cellErr(idx) + (t.hollow && Pieces.isConnector(st) && Pieces.PIXELS[st] == 256 ? 1 << 16 : 0);
            }
        }
        Ctx c = new Ctx();

        record Orbit(int idx, int[][] cells, int[] cands, int[] aff) {}
        List<Orbit> orbs = new ArrayList<>();

        for (int j = 0; j < ny; j++) {
            if (sy && 2 * j + 1 < ny) continue;
            for (int i = 0; i < nx; i++) {
                if (sx && 2 * i + 1 < nx) continue;
                int idx = j * nx + i, i2 = nx - 1 - i, j2 = ny - 1 - j;
                List<int[]> cl = new ArrayList<>();
                cl.add(new int[]{i, j, 0});
                if (sx && i2 != i) cl.add(new int[]{i2, j, 1});
                if (sy && j2 != j) cl.add(new int[]{i, j2, 2});
                if (sx && sy && i2 != i && j2 != j) cl.add(new int[]{i2, j2, 3});
                int[][] cells = cl.toArray(new int[0][]);
                if (t.kind[idx] != 2) { setOrbit(grid, nx, cells, t.kind[idx]); continue; }
                boolean selfX = sx && 2 * i + 1 == nx, selfY = sy && 2 * j + 1 == ny;
                int[] cands = selfX && selfY ? cXY : selfX ? cX : selfY ? cY : cAll;

                // First guess: connectors attach wherever a neighbour will hold something.
                boolean nl = i > 0 && t.kind[idx - 1] != 0, nr = i < nx - 1 && t.kind[idx + 1] != 0;
                boolean nu = j < ny - 1 && t.kind[idx + nx] != 0, nd = j > 0 && t.kind[idx - nx] != 0;
                int side = (nl ? 1 : 0) + (nr ? 2 : 0);
                int four = (nl ? Pieces.LEFT : 0) + (nr ? Pieces.RIGHT : 0) + (nu ? Pieces.TOP : 0) + (nd ? Pieces.BOTTOM_SIDE : 0);
                int above = j + 1 < ny ? t.kind[idx + nx] : 0;
                int wl = nl ? (above == 1 ? 2 : 1) : 0, wr = nr ? (above == 1 ? 2 : 1) : 0;
                int wallGuess = Pieces.wall(wl, wr, above != 0, deep || Pieces.wallPostRule(wl, wr, above != 0));
                int best = 0, bestE = Integer.MAX_VALUE;
                for (int cand : cands) {
                    int st = cand == Pieces.WALL ? wallGuess : !Pieces.isType(cand) ? cand : cand >= Pieces.F_FENCE ? cand + four : cand + side;
                    int e = t.errTab[idx][st];
                    if (e < bestE || (e == bestE && beatsAir(cand, best))) { bestE = e; best = cand; }
                }
                setOrbit(grid, nx, cells, best);

                // Cells whose shape can change when this one does: side neighbours (connections) always;
                // upright, also the row below (wall heights); flat, the cells above and below (connections).
                Set<Integer> aff = new LinkedHashSet<>();
                for (int[] cc : cells) {
                    int ci = cc[0], cj = cc[1];
                    if (floor) {
                        int[][] ds = {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                        for (int[] d : ds) { int q = ci + d[0], r = cj + d[1]; if (q >= 0 && q < nx && r >= 0 && r < ny) aff.add(r * nx + q); }
                    } else {
                        for (int dj = -1; dj <= 0; dj++)
                            for (int di = -1; di <= 1; di++) { int q = ci + di, r = cj + dj; if (q >= 0 && q < nx && r >= 0) aff.add(r * nx + q); }
                    }
                }
                orbs.add(new Orbit(idx, cells, cands, aff.stream().mapToInt(Integer::intValue).toArray()));
            }
        }

        // Refine: re-pick each orbit given its neighbours' current pieces, until nothing improves.
        if (s.fence || s.pane || s.wall) {
            for (int it = 0; it < 30; it++) {
                boolean changed = false;
                for (Orbit o : orbs) {
                    int cur = grid[o.idx], bestE = 0, best = cur;
                    for (int q : o.aff) bestE += c.cost(q);
                    for (int cand : o.cands) {
                        if (cand == cur) continue;
                        setOrbit(grid, nx, o.cells, cand);
                        int e = 0;
                        for (int q : o.aff) e += c.cost(q);
                        if (e < bestE || (e == bestE && beatsAir(cand, best))) { bestE = e; best = cand; }
                    }
                    setOrbit(grid, nx, o.cells, best);
                    if (best != cur) changed = true;
                }
                if (!changed) break;
            }
        }
        double err = 0;
        for (int q = 0; q < N; q++) err += c.cellErr(q);
        err /= 256;
        byte[] out = new byte[N];
        for (int q = 0; q < N; q++) out[q] = (byte) c.resolve(q);

        if (t.hollow) {
            byte[] solid = out.clone();
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    if (solid[j * nx + i] != Pieces.FULL) continue;
                    if (Pieces.isConnector(at(solid, nx, ny, i - 1, j)) || Pieces.isConnector(at(solid, nx, ny, i + 1, j))) continue;
                    int below = at(solid, nx, ny, i, j - 1), above = at(solid, nx, ny, i, j + 1);
                    if (floor ? Pieces.isConnector(below) || Pieces.isConnector(above)          // attached from the front or back
                              : Pieces.FAMILY[below] == Pieces.Family.WALL) continue;           // a wall below takes its height from this block
                    // Hidden when every neighbour fills the shared edge, whether or not it's a full face.
                    boolean exposed = !Pieces.EDGE[above][0] || !Pieces.EDGE[below][1]
                            || !Pieces.EDGE[at(solid, nx, ny, i - 1, j)][3] || !Pieces.EDGE[at(solid, nx, ny, i + 1, j)][2];
                    if (!exposed) out[j * nx + i] = Pieces.EMPTY;
                }
        }
        int[] counts = new int[Pieces.COUNT];
        for (byte b : out) counts[b & 0xFF]++;
        return new Result(out, nx, ny, err, t.area, counts, t, floor);
    }

    /**
     * A chain or end rod wins a tie with air. A line half a block wide that crosses one always covers exactly half
     * of it, so the two tie often, and a rod's plate would otherwise decide whether the curve gets a piece there.
     * Air never wins a tie back, so refinement still can't cycle.
     */
    private static boolean beatsAir(int cand, int best) { return best == Pieces.EMPTY && Pieces.isLine(cand); }

    private static int at(byte[] g, int nx, int ny, int i, int j) {
        return (i < 0 || j < 0 || i >= nx || j >= ny) ? 0 : g[j * nx + i] & 0xFF;
    }

    private static void setOrbit(byte[] grid, int nx, int[][] cells, int p) {
        for (int[] c : cells)
            grid[c[1] * nx + c[0]] = (byte) switch (c[2]) {
                case 0 -> p;
                case 1 -> Pieces.MX[p];
                case 2 -> Pieces.MY[p];
                default -> Pieces.MX[Pieces.MY[p]];
            };
    }

    /** Convenience: build and solve in one go. */
    public static Result run(ShapeSettings s) { return solve(Target.build(s), s); }
}
