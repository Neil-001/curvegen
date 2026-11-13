package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces;
import dev.curvegen.core.Solver;
import dev.curvegen.core.Target;
import dev.curvegen.core.paint.Tiling.Strip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where each block of a solved 2D shape lies on the pattern's strips. A line or an outline wraps: {@code u} runs along
 * its centre line and {@code v} across its width, from its right-hand side as the line runs. A loop runs anticlockwise
 * from its bottom, or from its back when the shape lies flat, so {@code v} starts on its outside; a line with two
 * ends runs left to right, so {@code v} starts underneath. A filled shape is projected flat:
 * {@code u} is x and {@code v} is y across the box round its blocks.
 *
 * <p>With "Pattern on: Edge", {@code u} runs round the outline (the centre line, for a line) and {@code v} through
 * the depth, so each layer has its own {@code v}: {@link #v} then holds -1 and the caller uses the layer.
 * A block takes the spot on the curve nearest it, so blocks inside a thick line or a filled shape carry the
 * pattern straight through.
 */
public final class Wrap2D {
    /** Per grid cell {@code j * nx + i}: the strip it lies on (-1 for an empty cell), and its place on it. */
    public final int[] strip;
    public final float[] u, v;
    public final List<Strip> strips;
    /** Whether the shape is a line or an outline rather than a filled shape. */
    public final boolean line;

    private Wrap2D(int n, boolean line) {
        strip = new int[n]; u = new float[n]; v = new float[n];
        strips = new ArrayList<>();
        this.line = line;
        java.util.Arrays.fill(strip, -1);
    }

    /** Places every non-empty cell of {@code r}. {@code edge} is "Pattern on: Edge" over {@code depth} layers. */
    public static Wrap2D of(Solver.Result r, boolean edge, int depth) {
        Target t = r.target();
        int nx = r.nx(), ny = r.ny();
        boolean line = t.centre != null;
        Wrap2D w = new Wrap2D(nx * ny, line);
        if (!edge && !line) {
            int i0 = nx, j0 = ny, i1 = -1, j1 = -1;
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++)
                    if (r.at(i, j) != Pieces.EMPTY) { i0 = Math.min(i0, i); i1 = Math.max(i1, i); j0 = Math.min(j0, j); j1 = Math.max(j1, j); }
            if (i1 < 0) return w;
            w.strips.add(new Strip(i1 - i0 + 1, j1 - j0 + 1, false));
            for (int j = j0; j <= j1; j++)
                for (int i = i0; i <= i1; i++) {
                    int c = j * nx + i;
                    if (r.at(i, j) == Pieces.EMPTY) continue;
                    w.strip[c] = 0; w.u[c] = i - i0 + .5f; w.v[c] = j - j0 + .5f;
                }
            return w;
        }
        List<double[]> segs = new ArrayList<>(line ? t.centre : t.overlay);
        if (!line) {
            segs.addAll(t.dashed);
            segs.add(border(r));
        }
        List<Poly> polys = Poly.chain(segs, r.floor());
        // Cells name their chain in 11 bits. An equation with more pieces than that keeps its longest.
        if (polys.size() > 2047) {
            polys.sort((a, b) -> Double.compare(b.length, a.length));
            polys = new ArrayList<>(polys.subList(0, 2047));
        }
        double width = edge ? depth : line ? t.centreWidth : 1;
        for (Poly p : polys) w.strips.add(new Strip(p.length, width, p.closed));
        if (polys.isEmpty()) return w;
        boolean[] filled = new boolean[nx * ny];
        for (int c = 0; c < filled.length; c++) filled[c] = r.grid()[c] != Pieces.EMPTY;
        // Every block of a line is within half its width of the centre line, so only an outline needs the sweeps.
        int[] nearest = line ? Nearest.of(nx, ny, polys, (int) Math.ceil(t.centreWidth / 2) + 1, filled, filled) : Nearest.of(nx, ny, polys, 1, null, filled);
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                if (r.at(i, j) == Pieces.EMPTY || nearest[c] < 0) continue;
                int code = nearest[c];
                Poly p = polys.get(code >>> 20);
                int k = code & 0xFFFFF;
                double[] at = p.project(k, i + .5, j + .5);
                w.strip[c] = code >>> 20;
                w.u[c] = (float) at[0];
                w.v[c] = edge ? -1 : (float) (width / 2 + at[1]);
            }
        return w;
    }

    /**
     * The edges of the grid that blocks touch, as segments: where a filled shape is cut off by the edge of its box,
     * such as an equation's range, that is part of its outline.
     */
    private static double[] border(Solver.Result r) {
        int nx = r.nx(), ny = r.ny();
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < nx; i++) {
            if (r.at(i, 0) != Pieces.EMPTY) out.add(new double[]{i, 0, i + 1, 0});
            if (r.at(i, ny - 1) != Pieces.EMPTY) out.add(new double[]{i, ny, i + 1, ny});
        }
        for (int j = 0; j < ny; j++) {
            if (r.at(0, j) != Pieces.EMPTY) out.add(new double[]{0, j, 0, j + 1});
            if (r.at(nx - 1, j) != Pieces.EMPTY) out.add(new double[]{nx, j, nx, j + 1});
        }
        double[] flat = new double[out.size() * 4];
        for (int k = 0; k < out.size(); k++) System.arraycopy(out.get(k), 0, flat, k * 4, 4);
        return flat;
    }

    /** A chain of segments: points xs, ys, with the distance along it to each point. */
    static final class Poly {
        final double[] xs, ys, at;
        final double length;
        final boolean closed;

        Poly(double[] xs, double[] ys, boolean closed) {
            this.xs = xs; this.ys = ys; this.closed = closed;
            at = new double[xs.length];
            for (int k = 1; k < xs.length; k++) at[k] = at[k - 1] + Math.hypot(xs[k] - xs[k - 1], ys[k] - ys[k - 1]);
            length = at[xs.length - 1];
        }

        int segments() { return xs.length - 1; }

        /** Distance along, and signed distance to the left (positive) or right, for the point's projection on segment k. */
        double[] project(int k, double px, double py) {
            double ax = xs[k], ay = ys[k], dx = xs[k + 1] - ax, dy = ys[k + 1] - ay, len2 = dx * dx + dy * dy;
            double f = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
            double qx = ax + dx * f, qy = ay + dy * f, d = Math.hypot(px - qx, py - qy);
            double cross = dx * (py - ay) - dy * (px - ax);
            return new double[]{at[k] + f * Math.sqrt(len2), cross >= 0 ? d : -d};
        }

        double distance2(int k, double px, double py) {
            double ax = xs[k], ay = ys[k], dx = xs[k + 1] - ax, dy = ys[k + 1] - ay, len2 = dx * dx + dy * dy;
            double f = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
            double qx = ax + dx * f - px, qy = ay + dy * f - py;
            return qx * qx + qy * qy;
        }

        private record Key(long x, long y) {
            static Key of(double x, double y) { return new Key(Math.round(x * 1e6), Math.round(y * 1e6)); }
        }

        /** A chain's points while it's being put together, and whether it came in one piece in order. */
        private record Run(List<double[]> pts, boolean inOrder) {}

        /** Ends this close count as one point, which mends the tiny gaps an equation's curve has where it is exactly 0. */
        private static final double JOIN = 0.05;

        /**
         * Joins segments {x1,y1,x2,y2,...} that share ends into chains. A loop runs anticlockwise from its lowest point,
         * or from its furthest back one when the shape lies flat. A chain with two ends runs left to right, unless it
         * came in one piece in order, as a Bézier curve does, which keeps its own direction.
         */
        static List<Poly> chain(List<double[]> lists, boolean flat) {
            List<double[]> s = new ArrayList<>();
            for (double[] list : lists)
                for (int k = 0; k + 3 < list.length; k += 4)
                    if (list[k] != list[k + 2] || list[k + 1] != list[k + 3]) s.add(new double[]{list[k], list[k + 1], list[k + 2], list[k + 3]});
            Map<Key, List<Integer>> ends = new HashMap<>();
            for (int k = 0; k < s.size(); k++) {
                double[] g = s.get(k);
                ends.computeIfAbsent(Key.of(g[0], g[1]), _ -> new ArrayList<>()).add(k);
                ends.computeIfAbsent(Key.of(g[2], g[3]), _ -> new ArrayList<>()).add(k);
            }
            boolean[] used = new boolean[s.size()];
            List<Run> runs = new ArrayList<>();
            // Chains with an end first, from that end, then whatever is left, which is loops.
            for (int pass = 0; pass < 2; pass++)
                for (int k = 0; k < s.size(); k++) {
                    if (used[k]) continue;
                    double[] g = s.get(k);
                    boolean startA = ends.get(Key.of(g[0], g[1])).size() == 1, startB = ends.get(Key.of(g[2], g[3])).size() == 1;
                    if (pass == 0 && !startA && !startB) continue;
                    runs.add(walk(s, ends, used, k, pass == 1 || startA));
                }
            mend(runs);
            List<Poly> out = new ArrayList<>();
            for (Run r : runs) {
                Poly p = finish(r, flat);
                // Where an equation is exactly 0 on a sample, it leaves a speck of a segment beside the curve.
                if (p.length > 2 * JOIN) out.add(p);
            }
            return out;
        }

        private static Run walk(List<double[]> s, Map<Key, List<Integer>> ends, boolean[] used, int first, boolean forward) {
            List<double[]> pts = new ArrayList<>();
            double[] g = s.get(first);
            pts.add(forward ? new double[]{g[0], g[1]} : new double[]{g[2], g[3]});
            double[] tip = forward ? new double[]{g[2], g[3]} : new double[]{g[0], g[1]};
            pts.add(tip);
            used[first] = true;
            boolean inOrder = forward;
            int last = first;
            for (;;) {
                int next = -1;
                for (int k : ends.get(Key.of(tip[0], tip[1]))) if (!used[k]) { next = k; break; }
                if (next < 0) break;
                double[] n = s.get(next);
                boolean fw = Key.of(n[0], n[1]).equals(Key.of(tip[0], tip[1]));
                inOrder &= fw && next == last + 1;
                tip = fw ? new double[]{n[2], n[3]} : new double[]{n[0], n[1]};
                pts.add(tip);
                used[next] = true;
                last = next;
            }
            return new Run(pts, inOrder);
        }

        private static boolean near(double[] a, double[] b) { return Math.abs(a[0] - b[0]) <= JOIN && Math.abs(a[1] - b[1]) <= JOIN; }

        /** Joins chains whose ends almost meet. There are few chains, so this checks every pair. */
        private static void mend(List<Run> runs) {
            if (runs.size() > 500) return;
            boolean joined = true;
            while (joined) {
                joined = false;
                for (int a = 0; a < runs.size() && !joined; a++)
                    for (int b = a + 1; b < runs.size() && !joined; b++) {
                        List<double[]> p = runs.get(a).pts(), q = runs.get(b).pts();
                        if (near(p.getFirst(), p.getLast()) || near(q.getFirst(), q.getLast())) continue;   // loops
                        List<double[]> joinedPts = null;
                        if (near(p.getLast(), q.getFirst())) joinedPts = concat(p, q);
                        else if (near(q.getLast(), p.getFirst())) joinedPts = concat(q, p);
                        else if (near(p.getLast(), q.getLast())) joinedPts = concat(p, q.reversed());
                        else if (near(p.getFirst(), q.getFirst())) joinedPts = concat(p.reversed(), q);
                        if (joinedPts != null) {
                            runs.set(a, new Run(joinedPts, false));
                            runs.remove(b);
                            joined = true;
                        }
                    }
            }
        }

        private static List<double[]> concat(List<double[]> a, List<double[]> b) {
            List<double[]> out = new ArrayList<>(a);
            out.addAll(b.subList(1, b.size()));
            return out;
        }

        private static Poly finish(Run run, boolean flat) {
            List<double[]> pts = new ArrayList<>(run.pts());
            double[] tip = pts.getLast();
            boolean closed = pts.size() > 3 && near(pts.getFirst(), tip);
            if (closed) {
                pts.removeLast();
                double area = 0;
                for (int k = 0; k < pts.size(); k++) {
                    double[] p = pts.get(k), q = pts.get((k + 1) % pts.size());
                    area += p[0] * q[1] - q[0] * p[1];
                }
                if (area < 0) java.util.Collections.reverse(pts);
                int start = 0;
                for (int k = 1; k < pts.size(); k++) {
                    double y = pts.get(k)[1], best = pts.get(start)[1];
                    if (flat ? y > best : y < best) start = k;
                }
                java.util.Collections.rotate(pts, -start);
                pts.add(pts.getFirst());
            } else if (!run.inOrder() && pts.getFirst()[0] > tip[0]) {
                java.util.Collections.reverse(pts);
            }
            double[] xs = new double[pts.size()], ys = new double[pts.size()];
            for (int k = 0; k < xs.length; k++) { xs[k] = pts.get(k)[0]; ys[k] = pts.get(k)[1]; }
            return new Poly(xs, ys, closed);
        }
    }

    /**
     * The nearest segment to cells' centres, as (chain index << 20 | segment index), or -1. A cell within
     * {@code reach} cells of a segment checks every segment that near. With {@code only} given, just those cells are
     * worked out, which is enough when every one is that close to the curve. Otherwise the rest take the best of their
     * neighbours' answers, swept across the grid both ways, and then step along the chain to the nearest segment.
     * Only cells in {@code wanted} (all, when null) are sure to be exact.
     */
    static final class Nearest {
        static int[] of(int nx, int ny, List<Poly> polys, int reach, boolean[] only, boolean[] wanted) {
            int n = nx * ny;
            // Each cell's nearby segments, packed: cell c's are near[start[c]] to near[start[c + 1] - 1].
            int[] start = new int[n + 1];
            for (int pass = 0; pass < 2; pass++) {
                int[] fill = pass == 1 ? start.clone() : null;
                int[] near = pass == 1 ? new int[start[n]] : null;
                for (int p = 0; p < polys.size(); p++) {
                    Poly poly = polys.get(p);
                    for (int k = 0; k < poly.segments(); k++) {
                        int i0 = clamp((int) Math.floor(Math.min(poly.xs[k], poly.xs[k + 1])) - reach, nx), i1 = clamp((int) Math.floor(Math.max(poly.xs[k], poly.xs[k + 1])) + reach, nx);
                        int j0 = clamp((int) Math.floor(Math.min(poly.ys[k], poly.ys[k + 1])) - reach, ny), j1 = clamp((int) Math.floor(Math.max(poly.ys[k], poly.ys[k + 1])) + reach, ny);
                        for (int j = j0; j <= j1; j++)
                            for (int i = i0; i <= i1; i++) {
                                int c = j * nx + i;
                                if (only != null && !only[c]) continue;
                                if (pass == 0) start[c + 1]++;
                                else near[fill[c]++] = p << 20 | k;
                            }
                    }
                }
                if (pass == 0) { for (int c = 0; c < n; c++) start[c + 1] += start[c]; continue; }
                return finish(nx, ny, polys, start, near, only == null, wanted);
            }
            throw new IllegalStateException();
        }

        private static int[] finish(int nx, int ny, List<Poly> polys, int[] start, int[] near, boolean sweep, boolean[] wanted) {
            int n = nx * ny;
            int[] best = new int[n];
            double[] dist = new double[n];
            for (int c = 0; c < n; c++) {
                best[c] = -1; dist[c] = Double.MAX_VALUE;
                double x = c % nx + .5, y = c / nx + .5;
                for (int q = start[c]; q < start[c + 1]; q++) {
                    int code = near[q];
                    double d = polys.get(code >>> 20).distance2(code & 0xFFFFF, x, y);
                    if (d < dist[c]) { dist[c] = d; best[c] = code; }
                }
            }
            if (!sweep) return best;
            int[][] fwd = {{-1, 0}, {-1, -1}, {0, -1}, {1, -1}}, back = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}};
            for (int round = 0; round < 2; round++) {
                for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) relax(i, j, nx, ny, fwd, best, dist, polys);
                for (int j = ny - 1; j >= 0; j--) for (int i = nx - 1; i >= 0; i--) relax(i, j, nx, ny, back, best, dist, polys);
            }
            // A neighbour's answer can be a few segments off along the same chain: walk along it to the nearest.
            for (int c = 0; c < n; c++) if (best[c] >= 0 && (wanted == null || wanted[c])) best[c] = climb(polys, best[c], c % nx + .5, c / nx + .5, dist[c]);
            return best;
        }

        /** From segment {@code code}, steps along its chain while the next segment either way is nearer the point. */
        private static int climb(List<Poly> polys, int code, double x, double y, double d) {
            Poly p = polys.get(code >>> 20);
            int k = code & 0xFFFFF, n = p.segments();
            for (int steps = 0; steps < n; steps++) {
                int moved = -1;
                for (int step : new int[]{-1, 1}) {
                    int to = k + step;
                    if (p.closed) to = Math.floorMod(to, n);
                    else if (to < 0 || to >= n) continue;
                    double dd = p.distance2(to, x, y);
                    if (dd < d) { d = dd; moved = to; }
                }
                if (moved < 0) break;
                k = moved;
            }
            return (code >>> 20) << 20 | k;
        }

        private static void relax(int i, int j, int nx, int ny, int[][] dirs, int[] best, double[] dist, List<Poly> polys) {
            int c = j * nx + i;
            double x = i + .5, y = j + .5;
            for (int[] d : dirs) {
                int a = i + d[0], b = j + d[1];
                if (a < 0 || b < 0 || a >= nx || b >= ny) continue;
                int code = best[b * nx + a];
                if (code < 0 || code == best[c]) continue;
                double dd = polys.get(code >>> 20).distance2(code & 0xFFFFF, x, y);
                if (dd < dist[c]) { dist[c] = dd; best[c] = code; }
            }
        }

        private static int clamp(int k, int n) { return k < 0 ? 0 : k >= n ? n - 1 : k; }
    }
}
