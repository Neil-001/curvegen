package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces;
import dev.curvegen.core.Solver;
import dev.curvegen.core.Target;
import dev.curvegen.core.paint.Tiling.Strip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Wrap2D {
    public final int[] strip;
    public final float[] u, v;
    public final List<Strip> strips;
    public final boolean line;

    private Wrap2D(int n, boolean line) {
        strip = new int[n]; u = new float[n]; v = new float[n];
        strips = new ArrayList<>();
        this.line = line;
        java.util.Arrays.fill(strip, -1);
    }

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
            if (t.rim != null) segs.add(t.rim);
        }
        List<Poly> polys = Poly.chain(segs, r.floor());
        double width = edge ? depth : line ? t.centreWidth : 1;
        for (Poly p : polys) w.strips.add(new Strip(p.length, width, p.closed));
        if (polys.isEmpty()) return w;
        boolean[] filled = new boolean[nx * ny];
        for (int c = 0; c < filled.length; c++) filled[c] = r.grid()[c] != Pieces.EMPTY;
        Nearest segments = new Nearest(polys);
        int[] nearest = line ? segments.find(nx, ny, (int) Math.ceil(t.centreWidth / 2) + 1, filled, filled)
                : segments.find(nx, ny, 1, null, filled);
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                if (r.at(i, j) == Pieces.EMPTY || nearest[c] < 0) continue;
                int segment = nearest[c], strip = segments.stripOf(segment);
                double[] at = polys.get(strip).project(segments.segmentInStrip(segment, strip), i + .5, j + .5);
                w.strip[c] = strip;
                w.u[c] = (float) at[0];
                w.v[c] = edge ? -1 : (float) (width / 2 + at[1]);
            }
        return w;
    }

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

        private record Run(List<double[]> pts, boolean inOrder) {}

        private static final double JOIN = 0.05;

        static List<Poly> chain(List<double[]> lists, boolean flat) {
            List<double[]> s = new ArrayList<>();
            java.util.Set<List<Key>> seen = new java.util.HashSet<>();
            for (double[] list : lists)
                for (int k = 0; k + 3 < list.length; k += 4) {
                    Key a = Key.of(list[k], list[k + 1]), b = Key.of(list[k + 2], list[k + 3]);
                    if (a.equals(b) || !seen.add(List.of(a, b)) || !seen.add(List.of(b, a))) continue;
                    s.add(new double[]{list[k], list[k + 1], list[k + 2], list[k + 3]});
                }
            Map<Key, List<Integer>> ends = new HashMap<>();
            for (int k = 0; k < s.size(); k++) {
                double[] g = s.get(k);
                ends.computeIfAbsent(Key.of(g[0], g[1]), _ -> new ArrayList<>()).add(k);
                ends.computeIfAbsent(Key.of(g[2], g[3]), _ -> new ArrayList<>()).add(k);
            }
            boolean[] used = new boolean[s.size()];
            List<Run> runs = new ArrayList<>();
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
            for (Run r : runs) out.add(finish(r, flat));
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

        private static void mend(List<Run> runs) {
            Map<Key, Set<Integer>> ends = new HashMap<>();
            for (int k = 0; k < runs.size(); k++) {
                List<double[]> pts = runs.get(k).pts();
                if (near(pts.getFirst(), pts.getLast())) continue;
                ends.computeIfAbsent(bucket(pts.getFirst()), _ -> new LinkedHashSet<>()).add(k);
                ends.computeIfAbsent(bucket(pts.getLast()), _ -> new LinkedHashSet<>()).add(k);
            }
            for (int a = 0; a < runs.size(); a++) {
                Run run = runs.get(a);
                if (run == null || near(run.pts().getFirst(), run.pts().getLast())) continue;
                List<double[]> p = run.pts();
                removeEnds(ends, p, a);
                while (!near(p.getFirst(), p.getLast())) {
                    int b = firstJoin(ends, runs, p.getFirst(), runs.size());
                    b = firstJoin(ends, runs, p.getLast(), b);
                    if (b == runs.size()) break;
                    List<double[]> q = runs.get(b).pts();
                    removeEnds(ends, q, b);
                    runs.set(b, null);
                    if (near(p.getLast(), q.getFirst())) p = concat(p, q);
                    else if (near(q.getLast(), p.getFirst())) p = concat(q, p);
                    else if (near(p.getLast(), q.getLast())) p = concat(p, q.reversed());
                    else p = concat(p.reversed(), q);
                    runs.set(a, new Run(p, false));
                }
            }
            runs.removeIf(java.util.Objects::isNull);
        }

        private static Key bucket(double[] point) {
            return new Key((long) Math.floor(point[0] / (2 * JOIN)), (long) Math.floor(point[1] / (2 * JOIN)));
        }

        private static void removeEnds(Map<Key, Set<Integer>> ends, List<double[]> pts, int k) {
            ends.get(bucket(pts.getFirst())).remove(k);
            ends.get(bucket(pts.getLast())).remove(k);
        }

        private static int firstJoin(Map<Key, Set<Integer>> ends, List<Run> runs, double[] point, int best) {
            Key key = bucket(point);
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++)
                    for (int k : ends.getOrDefault(new Key(key.x() + dx, key.y() + dy), Set.of())) {
                        if (k >= best) break;
                        List<double[]> pts = runs.get(k).pts();
                        if (near(point, pts.getFirst()) || near(point, pts.getLast())) {
                            best = k;
                            break;
                        }
                    }
            return best;
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

    static final class Nearest {
        private final List<Poly> polys;
        private final int[] firstSegment, stripBySegment;

        Nearest(List<Poly> polys) {
            this.polys = polys;
            firstSegment = new int[polys.size() + 1];
            for (int p = 0; p < polys.size(); p++) firstSegment[p + 1] = firstSegment[p] + polys.get(p).segments();
            stripBySegment = new int[firstSegment[polys.size()]];
            for (int p = 0; p < polys.size(); p++) java.util.Arrays.fill(stripBySegment, firstSegment[p], firstSegment[p + 1], p);
        }

        int stripOf(int segment) { return stripBySegment[segment]; }

        int segmentInStrip(int segment, int strip) { return segment - firstSegment[strip]; }

        double distance2(int segment, double x, double y) {
            int strip = stripOf(segment);
            return polys.get(strip).distance2(segmentInStrip(segment, strip), x, y);
        }

        int[] find(int nx, int ny, int reach, boolean[] only, boolean[] wanted) {
            int n = nx * ny;
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
                                else near[fill[c]++] = firstSegment[p] + k;
                            }
                    }
                }
                if (pass == 0) { for (int c = 0; c < n; c++) start[c + 1] += start[c]; continue; }
                return finish(nx, ny, start, near, only == null, wanted);
            }
            throw new IllegalStateException();
        }

        private int[] finish(int nx, int ny, int[] start, int[] near, boolean sweep, boolean[] wanted) {
            int n = nx * ny;
            int[] best = new int[n];
            double[] dist = new double[n];
            for (int c = 0; c < n; c++) {
                best[c] = -1; dist[c] = Double.MAX_VALUE;
                double x = c % nx + .5, y = c / nx + .5;
                for (int q = start[c]; q < start[c + 1]; q++) {
                    int segment = near[q];
                    double d = distance2(segment, x, y);
                    if (d < dist[c]) { dist[c] = d; best[c] = segment; }
                }
            }
            if (!sweep) return best;
            int[][] fwd = {{-1, 0}, {-1, -1}, {0, -1}, {1, -1}}, back = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}};
            for (int round = 0; round < 2; round++) {
                for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) relax(i, j, nx, ny, fwd, best, dist);
                for (int j = ny - 1; j >= 0; j--) for (int i = nx - 1; i >= 0; i--) relax(i, j, nx, ny, back, best, dist);
            }
            exact(nx, ny, best, dist, wanted);
            return best;
        }

        private static final int COARSE = 8, EXACT = 16;

        private void exact(int nx, int ny, int[] best, double[] dist, boolean[] wanted) {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (Poly p : polys)
                for (int k = 0; k < p.xs.length; k++) {
                    minX = Math.min(minX, p.xs[k]); maxX = Math.max(maxX, p.xs[k]);
                    minY = Math.min(minY, p.ys[k]); maxY = Math.max(maxY, p.ys[k]);
                }
            double ox = Math.floor(minX), oy = Math.floor(minY);
            int bx = (int) ((maxX - ox) / COARSE) + 1, by = (int) ((maxY - oy) / COARSE) + 1;
            int[] start = new int[bx * by + 1], in = null, fill = null;
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 1) {
                    for (int k = 0; k < bx * by; k++) start[k + 1] += start[k];
                    in = new int[start[bx * by]];
                    fill = start.clone();
                }
                for (int p = 0; p < polys.size(); p++) {
                    Poly poly = polys.get(p);
                    for (int k = 0; k < poly.segments(); k++) {
                        int i0 = clamp((int) ((Math.min(poly.xs[k], poly.xs[k + 1]) - ox) / COARSE), bx), i1 = clamp((int) ((Math.max(poly.xs[k], poly.xs[k + 1]) - ox) / COARSE), bx);
                        int j0 = clamp((int) ((Math.min(poly.ys[k], poly.ys[k + 1]) - oy) / COARSE), by), j1 = clamp((int) ((Math.max(poly.ys[k], poly.ys[k + 1]) - oy) / COARSE), by);
                        for (int j = j0; j <= j1; j++)
                            for (int i = i0; i <= i1; i++) {
                                if (pass == 0) start[j * bx + i + 1]++;
                                else in[fill[j * bx + i]++] = firstSegment[p] + k;
                            }
                    }
                }
            }
            for (int c = 0; c < nx * ny; c++) {
                if (best[c] < 0 || wanted != null && !wanted[c]) continue;
                double limit = Math.min(dist[c], EXACT * EXACT);
                double x = c % nx + .5 - ox, y = c / nx + .5 - oy, rad = Math.sqrt(limit);
                int i0 = clamp((int) Math.floor((x - rad) / COARSE), bx), i1 = clamp((int) Math.floor((x + rad) / COARSE), bx);
                int j0 = clamp((int) Math.floor((y - rad) / COARSE), by), j1 = clamp((int) Math.floor((y + rad) / COARSE), by);
                for (int j = j0; j <= j1; j++)
                    for (int i = i0; i <= i1; i++) {
                        double gx = Math.max(0, Math.max(i * COARSE - x, x - (i + 1) * COARSE));
                        double gy = Math.max(0, Math.max(j * COARSE - y, y - (j + 1) * COARSE));
                        if (gx * gx + gy * gy >= Math.min(dist[c], limit)) continue;
                        for (int q = start[j * bx + i]; q < start[j * bx + i + 1]; q++) {
                            int segment = in[q];
                            double d = distance2(segment, x + ox, y + oy);
                            if (d < dist[c]) { dist[c] = d; best[c] = segment; }
                        }
                    }
            }
        }

        private void relax(int i, int j, int nx, int ny, int[][] dirs, int[] best, double[] dist) {
            int c = j * nx + i;
            double x = i + .5, y = j + .5;
            for (int[] d : dirs) {
                int a = i + d[0], b = j + d[1];
                if (a < 0 || b < 0 || a >= nx || b >= ny) continue;
                int segment = best[b * nx + a];
                if (segment < 0 || segment == best[c]) continue;
                double dd = distance2(segment, x, y);
                if (dd < dist[c]) { dist[c] = dd; best[c] = segment; }
            }
        }

        private static int clamp(int k, int n) { return k < 0 ? 0 : k >= n ? n - 1 : k; }
    }
}
