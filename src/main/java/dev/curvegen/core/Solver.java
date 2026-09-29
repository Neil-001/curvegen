package dev.curvegen.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Picks the best piece for every cell, aware that fences and panes change shape with their neighbours. */
public final class Solver {
    private Solver() {}

    public record Result(byte[] grid, int nx, int ny, double err, double area, int[] counts, Target target) {
        public int at(int i, int j) { return grid[j * nx + i]; }
    }

    public static Result solve(Target t, ShapeSettings s) {
        final int nx = t.nx, ny = t.ny, N = nx * ny;
        List<Integer> ids = new ArrayList<>(List.of(Pieces.EMPTY, Pieces.FULL));
        if (s.slab) ids.addAll(List.of(Pieces.SLAB_B, Pieces.SLAB_T));
        if (s.stair) ids.addAll(List.of(Pieces.ST_UR, Pieces.ST_UL, Pieces.ST_DR, Pieces.ST_DL));
        if (s.trap) ids.addAll(List.of(Pieces.TD_B, Pieces.TD_T, Pieces.TD_L, Pieces.TD_R));
        if (s.fence) ids.add(Pieces.FENCE);
        if (s.pane) ids.add(Pieces.PANE);
        if (s.wall) ids.add(Pieces.WALL);
        int[] cAll = ids.stream().mapToInt(Integer::intValue).toArray();
        int[] cX = ids.stream().mapToInt(Integer::intValue).filter(p -> Pieces.MX[p] == p).toArray();
        int[] cY = ids.stream().mapToInt(Integer::intValue).filter(p -> Pieces.MY[p] == p).toArray();
        int[] cXY = java.util.Arrays.stream(cX).filter(p -> Pieces.MY[p] == p).toArray();

        final byte[] grid = new byte[N];
        final boolean sx = t.symX, sy = t.symY, fullConnects = s.fullConnects;

        final class Ctx {
            /** Does the connector type v attach to neighbour nb through nb's given face? */
            boolean connects(int v, int nb, int face) {
                if (Pieces.isConnector(nb)) return Pieces.joins(Pieces.FAMILY[v], Pieces.FAMILY[nb]);
                if (nb == Pieces.FULL && !fullConnects) return false;
                return Pieces.STURDY[nb][face];
            }
            boolean left(int idx) { int v = grid[idx]; return idx % nx > 0 && connects(v, grid[idx - 1], 3); }
            boolean right(int idx) { int v = grid[idx]; return idx % nx < nx - 1 && connects(v, grid[idx + 1], 2); }
            /** Bottom row of whatever is in cell idx, as a 16-bit mask: what a wall below it "feels". */
            int bottom(int idx) {
                if (idx >= N) return 0;
                int v = grid[idx];
                if (!Pieces.isType(v)) return Pieces.BOTTOM[v];
                if (v == Pieces.FENCE) return 0x03C0;                                   // post, x 6–9
                if (v == Pieces.PANE) return 0x0180 | (left(idx) ? 0x007F : 0) | (right(idx) ? 0xFE00 : 0);
                return 0x0FF0 | (left(idx) ? 0x00FF : 0) | (right(idx) ? 0xFF00 : 0); // wall: post, sides
            }
            int resolve(int idx) {
                int v = grid[idx];
                if (!Pieces.isType(v)) return v;
                boolean L = left(idx), R = right(idx);
                if (v != Pieces.WALL) return v + (L ? 1 : 0) + (R ? 2 : 0);
                // A wall side is tall when the block above covers it; a straight wall keeps its post
                // only if the block above covers its centre.
                int cov = bottom(idx + nx);
                return Pieces.wall(L ? ((cov & 0x01FF) == 0x01FF ? 2 : 1) : 0,
                        R ? ((cov & 0xFF80) == 0xFF80 ? 2 : 1) : 0,
                        (cov & 0x0180) == 0x0180);
            }
            int cellErr(int idx) {
                int st = resolve(idx), k = t.kind[idx];
                return k == 0 ? Pieces.PIXELS[st] : k == 1 ? 256 - Pieces.PIXELS[st] : t.errTab[idx][st];
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
                int optim = (i > 0 && t.kind[idx - 1] != 0 ? 1 : 0) + (i < nx - 1 && t.kind[idx + 1] != 0 ? 2 : 0);
                int above = j + 1 < ny ? t.kind[idx + nx] : 0;
                int wallGuess = Pieces.wall((optim & 1) != 0 ? (above == 1 ? 2 : 1) : 0, (optim & 2) != 0 ? (above == 1 ? 2 : 1) : 0, above != 0);
                int best = 0, bestE = Integer.MAX_VALUE;
                for (int cand : cands) {
                    int st = cand == Pieces.WALL ? wallGuess : Pieces.isType(cand) ? cand + optim : cand;
                    int e = t.errTab[idx][st];
                    if (e < bestE) { bestE = e; best = cand; }
                }
                setOrbit(grid, nx, cells, best);
                Set<Integer> aff = new LinkedHashSet<>();
                // A change here can reshape side neighbours (connections) and the row below (wall heights).
                for (int[] cc : cells)
                    for (int dj = -1; dj <= 0; dj++)
                        for (int di = -1; di <= 1; di++) {
                            int q = cc[0] + di, r = cc[1] + dj;
                            if (q >= 0 && q < nx && r >= 0) aff.add(r * nx + q);
                        }
                orbs.add(new Orbit(idx, cells, cands, aff.stream().mapToInt(Integer::intValue).toArray()));
            }
        }

        if (s.fence || s.pane || s.wall) {
            for (int it = 0; it < 30; it++) {
                boolean changed = false;
                for (Orbit o : orbs) {
                    int cur = grid[o.idx], bestE = 0, best = cur;
                    for (int q : o.aff) bestE += c.cellErr(q);
                    for (int cand : o.cands) {
                        if (cand == cur) continue;
                        setOrbit(grid, nx, o.cells, cand);
                        int e = 0;
                        for (int q : o.aff) e += c.cellErr(q);
                        if (e < bestE) { bestE = e; best = cand; }
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
                    if (Pieces.FAMILY[at(solid, nx, ny, i, j - 1)] == Pieces.Family.WALL) continue;   // a wall below takes its height from this block
                    boolean exposed = !Pieces.STURDY[at(solid, nx, ny, i, j + 1)][0] || !Pieces.STURDY[at(solid, nx, ny, i, j - 1)][1]
                            || !Pieces.STURDY[at(solid, nx, ny, i - 1, j)][3] || !Pieces.STURDY[at(solid, nx, ny, i + 1, j)][2];
                    if (!exposed) out[j * nx + i] = Pieces.EMPTY;
                }
        }
        int[] counts = new int[Pieces.COUNT];
        for (byte b : out) counts[b]++;
        return new Result(out, nx, ny, err, t.area, counts, t);
    }

    private static int at(byte[] g, int nx, int ny, int i, int j) {
        return (i < 0 || j < 0 || i >= nx || j >= ny) ? 0 : g[j * nx + i];
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
