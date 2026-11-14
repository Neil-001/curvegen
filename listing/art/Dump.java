import dev.curvegen.core.Mesh3;
import dev.curvegen.core.Pieces;
import dev.curvegen.core.Pieces3;
import dev.curvegen.core.Raster3;
import dev.curvegen.core.Shape3;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.Solver;
import dev.curvegen.core.Solver3;
import dev.curvegen.core.Stencil;
import dev.curvegen.core.Target;
import dev.curvegen.core.Built2D;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.Settle2D;
import dev.curvegen.core.edit.Box;
import dev.curvegen.core.edit.Orbit;
import dev.curvegen.core.edit.Orient;
import dev.curvegen.core.paint.Catalog;
import dev.curvegen.core.paint.Mix;
import dev.curvegen.core.paint.Paint;
import dev.curvegen.core.paint.Painted;
import dev.curvegen.core.paint.Painter2D;
import dev.curvegen.core.paint.Pattern;
import dev.curvegen.core.paint.Patterns;
import dev.curvegen.core.paint.Solve3Painter;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Solves shapes for render.py. Prints the piece table as one JSON line, then one JSON line per line of stdin.
 * Each input line sets ShapeSettings fields: "gen=EQUATION;src=y = 2sin(x);qW=48". Points are "pts=2,2 10,22".
 * "stencil=<file>" solves a picture instead of a generator's shape.
 *
 * A 3D shape is drawn here too, the way the menu's 3D preview draws it, and its pixels go to a file. The names
 * starting with "view" say how: viewOut (the file), viewW and viewH (pixels), viewYaw and viewPitch (degrees),
 * viewBg (the background, as RGB), viewColour (one colour for every block instead of the piece type colours), viewWires (the ideal shape's outline), viewCut (leave out the half nearest the
 * camera, to show the inside), viewTop (leave out the bottom half, as if the ground hid it) and viewHandles (the
 * in-world editor's box and its handles). viewColour, viewCut and viewHandles are unused: only render.py's unused
 * pictures ask for them, which the listing no longer shows. They stay for when one comes back.
 *
 * <p>A shape is painted as the mod paints it when the line names a catalog of the game's blocks ("catalog=<file>",
 * written by render.py from Blocks.java and the textures) and turns paint on. "paint.<key>=<value>" sets the paint as
 * a preset's text does ({@link Paint#apply}), "paintPattern" picks a bundled pattern and "paintMix" a mix of a bundled
 * pattern's blocks, both by their field names in {@link Patterns}. A painted shape is drawn in 3D in each block's colour,
 * as the menu's "Block colours" picture draws it, a 2D one built as {@link Settle2D} lays it out, flat with "floor"
 * and as deep as "depth". The reply then lists the painted blocks with how many of each. Painting is unused: only
 * render.py's unused paint() and gallery() ask for it, and the listing no longer shows their pictures.
 */
public class Dump {
    public static void main(String[] args) throws Exception {
        StringJoiner pieces = new StringJoiner(",", "[", "]");
        for (int s = 0; s < Pieces.COUNT; s++) {
            StringJoiner rects = new StringJoiner(",", "[", "]");
            if (Pieces.RECTS[s] != null) for (int[] r : Pieces.RECTS[s]) rects.add("[" + r[0] + "," + r[1] + "," + r[2] + "," + r[3] + "]");
            pieces.add(Pieces.FAMILY[s] == null ? "null" : "{\"family\":\"" + Pieces.FAMILY[s] + "\",\"name\":\"" + Pieces.NAME[s]
                    + "\",\"color\":" + Pieces.COLOR[s] + ",\"rects\":" + rects + "}");
        }
        System.out.println(pieces);

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        for (String line; (line = in.readLine()) != null; ) {
            ShapeSettings s = new ShapeSettings();
            Target stencil = null;
            Map<String, String> view = new HashMap<>(), paint = new LinkedHashMap<>();
            Catalog catalog = null;
            for (String kv : line.split(";")) {
                if (kv.isBlank()) continue;
                String k = kv.substring(0, kv.indexOf('=')).trim(), v = kv.substring(kv.indexOf('=') + 1).trim();
                if (k.startsWith("view")) { view.put(k, v); continue; }
                if (k.startsWith("paint.")) { paint.put(k.substring(6), v); continue; }
                if (k.equals("paintPattern")) { s.paint.pattern = (Pattern) Patterns.class.getField(v).get(null); continue; }
                if (k.equals("paintMix")) { s.paint.mix = Mix.of((Pattern) Patterns.class.getField(v).get(null)); continue; }
                if (k.equals("catalog")) { view.put("catalog", v); continue; }
                if (k.equals("stencil")) {   // a file of 0s and 1s, one line per pixel row, 16 pixels a cell
                    List<String> rows = Files.readAllLines(Path.of(v));
                    int w = rows.get(0).length(), h = rows.size();
                    boolean[] px = new boolean[w * h];
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = rows.get(y).charAt(x) == '1';
                    stencil = Stencil.of(px, w / 16, h / 16);
                    continue;
                }
                if (k.equals("pts")) {
                    s.pts.clear();
                    for (String p : v.split(" ")) s.pts.add(new double[]{Double.parseDouble(p.split(",")[0]), Double.parseDouble(p.split(",")[1])});
                    continue;
                }
                if (k.equals("pts3") || k.equals("sPts")) {
                    List<double[]> to = k.equals("pts3") ? s.pts3 : s.sPts;
                    to.clear();
                    for (String p : v.split(" ")) {
                        String[] c = p.split(",");
                        to.add(new double[]{Double.parseDouble(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[2])});
                    }
                    continue;
                }
                Field f = ShapeSettings.class.getField(k);
                Class<?> t = f.getType();
                if (t == int.class) f.setInt(s, Integer.parseInt(v));
                else if (t == double.class) f.setDouble(s, Double.parseDouble(v));
                else if (t == boolean.class) f.setBoolean(s, Boolean.parseBoolean(v));
                else if (t.isEnum()) f.set(s, Enum.valueOf(t.asSubclass(Enum.class), v));
                else f.set(s, v);
            }
            s.paint.apply(paint);
            if (view.containsKey("catalog")) {
                catalog = catalog(view.get("catalog"), s.is3d() ? s.topColours : s.floor);
                // What PaintBlocks.fullConnects decides: what most of the painted blocks do, or the Blocks tab's full block.
                Set<String> no = catalog.noConnect();
                Boolean most = s.paint.on() ? Paint.connects(s.paint.palette(), id -> !no.contains(id)) : null;
                s.fullConnects = most != null ? most : !no.contains(catalog.choice().get(Family.FULL));
            }
            if (s.is3d()) { System.out.println(draw3(s, view, catalog)); continue; }
            if (catalog != null && s.paint.on()) { System.out.println(draw2(s, view, catalog)); continue; }
            Solver.Result r = stencil != null ? Solver.solve(stencil, s) : Solver.run(s);
            StringJoiner grid = new StringJoiner(",", "[", "]");
            for (byte b : r.grid()) grid.add(Integer.toString(b & 0xFF));
            System.out.println("{\"nx\":" + r.nx() + ",\"ny\":" + r.ny() + ",\"err\":" + r.err() + ",\"area\":" + r.area()
                    + ",\"grid\":" + grid + ",\"overlay\":" + lines(r.target().overlay) + ",\"dashed\":" + lines(r.target().dashed)
                    + ",\"axisX\":" + r.target().axisX + ",\"axisY\":" + r.target().axisY
                    + ",\"error\":" + (r.target().error == null ? "null" : "\"" + r.target().error.toString().replace("\"", "'") + "\"") + "}");
        }
    }

    /**
     * PreviewTexture's curve colour, and the editor's colours for a face, edge and corner handle. The box's and the
     * handles' colours are unused, like viewHandles.
     */
    private static final int CURVE = 0xFF4D73, BOX = 0xFFFFFF, FACE = 0xF2F2F2, EDGE = 0x7FD4FF, CORNER = 0xFFA646;

    /** The catalogs read so far, by file and face. */
    private static final Map<String, Catalog> CATALOGS = new HashMap<>();

    /**
     * The catalog in render.py's file, matched by the top face or the side, with the matcher "Match a colour…" uses
     * when Chroma Studio isn't installed. Lines: "allowed FAMILY id…", "piece base FAMILY id", "noconnect id",
     * "wooden id", "choice FAMILY id", and "side id rrggbb" or "top id rrggbb" for each block's colour.
     */
    private static Catalog catalog(String file, boolean top) throws Exception {
        String key = file + top;
        if (CATALOGS.containsKey(key)) return CATALOGS.get(key);
        Map<Family, List<String>> allowed = new EnumMap<>(Family.class);
        Map<String, Integer> colours = new HashMap<>();
        Map<String, String> material = new HashMap<>();
        Map<String, Map<Family, String>> variants = new HashMap<>();
        Set<String> noConnect = new HashSet<>(), wooden = new HashSet<>();
        Map<Family, String> choice = new EnumMap<>(Family.class);
        for (String l : Files.readAllLines(Path.of(file))) {
            String[] w = l.trim().split(" ");
            switch (w[0]) {
                case "allowed" -> allowed.put(Family.valueOf(w[1]), List.of(w).subList(2, w.length));
                case "piece" -> {
                    variants.computeIfAbsent(w[1], key -> new EnumMap<>(Family.class)).put(Family.valueOf(w[2]), w[3]);
                    material.putIfAbsent(w[3], w[1]);
                }
                case "noconnect" -> noConnect.add(w[1]);
                case "wooden" -> wooden.add(w[1]);
                case "choice" -> choice.put(Family.valueOf(w[1]), w[2]);
                case "side", "top" -> { if (w[0].equals(top ? "top" : "side")) colours.put(w[1], Integer.parseInt(w[2], 16)); }
                default -> {}
            }
        }
        Catalog c = new Catalog(allowed, colours, material, variants, noConnect, wooden, choice, Catalog.lab(colours));
        CATALOGS.put(key, c);
        return c;
    }

    /** A colour for each piece type, from the Blocks tab's choice, then one for each painted entry, as the picture colours them. */
    private static int[] paletteOf(Catalog c, Painted p) {
        int families = Family.values().length;
        int[] out = new int[families + p.blocks().size()];
        for (Family f : Family.values()) out[f.ordinal()] = c.colours().getOrDefault(c.choice().get(f), 0x808080);
        for (int e = 0; e < p.blocks().size(); e++) {
            String id = p.blocks().get(e), block = id.contains("[") ? id.substring(0, id.indexOf('[')) : id;
            out[families + e] = c.colours().getOrDefault(block, 0x808080);
        }
        return out;
    }

    /**
     * Paints a 2D shape and draws it in 3D, laid out as the game would build it: upright with its layers running away
     * from the camera, or with viewFlat lying flat. viewDepth sets the depth.
     */
    private static String draw2(ShapeSettings s, Map<String, String> view, Catalog catalog) throws Exception {
        Solver.Result r = Solver.run(s);
        if (r.target().error != null) return "{\"error\":\"" + r.target().error.toString().replace("\"", "'") + "\"}";
        int depth = Math.max(1, s.depth);
        Painted p = Painter2D.paint(r, depth, s.paint, s.fullConnects, catalog);
        Settle2D settled = p.settled() != null ? p.settled() : Settle2D.of(r, depth, new BitSet(), s.fullConnects, () -> false);
        Solver3.Result built = Built2D.of(r, settled, depth);
        int nx = r.nx(), ny = r.ny();
        int[] perBlock = new int[built.grid().length];
        int k3 = 0, families = Family.values().length;
        short[] grid = built.grid();
        for (int y = 0, c = 0; y < built.ny(); y++)
            for (int z = 0; z < built.nz(); z++)
                for (int x = 0; x < built.nx(); x++, c++) {
                    if (grid[c] == Pieces3.AIR) continue;
                    int[] ijk = Built2D.drawing(x, y, z, nx, ny, r.floor());
                    int slot = p.layers() == 1 ? ijk[1] * nx + ijk[0] : (ijk[2] * ny + ijk[1]) * nx + ijk[0];
                    int e = p.at(slot);
                    perBlock[k3++] = e >= 0 ? families + e : Pieces3.FAMILY[grid[c]].ordinal();
                }
        return withPaint(render(built, view, paletteOf(catalog, p), perBlock, List.of(), 0, null), p);
    }

    /** Solves a 3D shape and draws it as Preview3 does: the solver's blocks in their piece type colours, then the outline. */
    private static String draw3(ShapeSettings s, Map<String, String> view, Catalog catalog) throws Exception {
        Shape3 own = Shape3.of(s);
        Solver3.Result r = Solver3.solve(own, s, () -> false);
        dev.curvegen.core.Message error = r.shape().error();
        if (error != null) return "{\"error\":\"" + error.toString().replace("\"", "'") + "\"}";
        int[] palette = null, perBlock = null;
        Painted paintedOf = null;
        Solver3.Result solved = r;
        if (catalog != null && s.paint.on()) {
            // The editor's own painter, with the shape unturned: its x east, y up and z south.
            Solve3Painter.Done done = new Solve3Painter(r, own, new Orient(Orient.EAST, Orient.UP, Orient.SOUTH)).paint(s.paint, s.fullConnects, catalog);
            r = done.result();
            palette = paletteOf(catalog, done.painted());
            paintedOf = done.painted();
            perBlock = Solve3Painter.colourIndex(r, done.painted());
        }
        String out = render(r, view, palette, perBlock, r.shape().wireframe(), r.shape().pad(), solved);
        return paintedOf == null ? out : withPaint(out, paintedOf);
    }

    /** A reply with the painted blocks added: how many of each, the Blocks tab's choice as "", and how many were left open. */
    private static String withPaint(String reply, Painted p) {
        StringJoiner out = new StringJoiner(",", "{", "}");
        long[] n = new long[p.blocks().size() + 1];
        for (long[] f : p.counts()) for (int e = 0; e < f.length; e++) n[e] += f[e];
        out.add("\"\":" + n[0]);
        for (int e = 0; e < p.blocks().size(); e++) out.add("\"" + p.blocks().get(e) + "\":" + n[e + 1]);
        out.add("\"open\":" + p.open());
        return reply.substring(0, reply.length() - 1) + ",\"painted\":" + out + "}";
    }

    /** Draws a solve, with the shape's outline and the editor's handles when asked, and writes its pixels to viewOut. */
    private static String render(Solver3.Result r, Map<String, String> view, int[] painted, int[] perBlock, List<double[]> wireframe, int pad,
                                 Solver3.Result solved) throws Exception {
        int nx = r.nx(), ny = r.ny(), nz = r.nz(), blocks = 0;
        for (short c : r.grid()) if (c != Pieces3.AIR) blocks++;
        int w = Integer.parseInt(view.get("viewW")), h = Integer.parseInt(view.get("viewH"));
        Orbit cam = new Orbit();
        if (view.containsKey("viewYaw")) cam.yaw = Math.toRadians(Double.parseDouble(view.get("viewYaw")));
        if (view.containsKey("viewPitch")) cam.pitch = Math.toRadians(Double.parseDouble(view.get("viewPitch")));
        cam.fit(nx, ny, nz, 0, 0, w, h, Double.parseDouble(view.getOrDefault("viewMargin", "12")));
        if (view.containsKey("viewScale")) cam.scale = Double.parseDouble(view.get("viewScale"));

        short[] grid = r.grid();
        boolean cut = Boolean.parseBoolean(view.get("viewCut")), top = Boolean.parseBoolean(view.get("viewTop"));
        // viewCut is unused, like the hollow ellipsoid cut open in render.py's shapes3d() that asked for it.
        if (cut || top) {
            // With viewCut, the camera's side of the upright plane through the middle that faces it most squarely.
            short[] whole = grid;
            grid = grid.clone();
            double[] b = cam.back();
            boolean alongX = Math.abs(b[0]) > Math.abs(b[2]);
            for (int y = 0, c = 0; y < ny; y++)
                for (int z = 0; z < nz; z++)
                    for (int x = 0; x < nx; x++, c++) {
                        double side = alongX ? (x + .5 - nx / 2.0) * b[0] : (z + .5 - nz / 2.0) * b[2];
                        if (cut && side > 0 || top && y < ny / 2) grid[c] = Pieces3.AIR;
                    }
            if (perBlock != null) {
                // Blocks are numbered among those left, so keep the colours of those.
                int[] kept = new int[perBlock.length];
                int k = 0, o = 0;
                for (int c = 0; c < whole.length; c++) {
                    if (whole[c] == Pieces3.AIR) continue;
                    if (grid[c] != Pieces3.AIR) kept[o++] = perBlock[k];
                    k++;
                }
                perBlock = kept;
            }
        }

        int[] palette = new int[Pieces.Family.values().length];
        for (Pieces.Family f : Pieces.Family.values())
            for (int p = 1; p < Pieces.COUNT; p++) if (Pieces.FAMILY[p] == f) { palette[f.ordinal()] = Pieces.COLOR[p]; break; }
        // Unused, like render.py's card3() and editor() that asked for it.
        if (view.containsKey("viewColour")) java.util.Arrays.fill(palette, Integer.parseInt(view.get("viewColour"), 16));
        if (painted != null) palette = painted;
        Raster3 raster = new Raster3(w, h);
        raster.clear(0xFF000000 | Integer.parseInt(view.getOrDefault("viewBg", "1B1F25"), 16));
        raster.draw(new Mesh3(grid, nx, ny, nz), cam, palette, perBlock);
        int thick = Integer.parseInt(view.getOrDefault("viewThick", "2"));
        if (Boolean.parseBoolean(view.getOrDefault("viewWires", "true")))
            for (double[] l : wireframe)
                for (int p = 0; p + 5 < l.length; p += 3) raster.line(cam, l[p], l[p + 1], l[p + 2], l[p + 3], l[p + 4], l[p + 5], CURVE, thick);
        // Unused, like render.py's editor() that asked for it.
        if (Boolean.parseBoolean(view.get("viewHandles"))) {
            // The box the player set, which leaves out the room an outwards shell adds.
            Box box = new Box(pad, pad, pad, nx - 2 * pad, ny - 2 * pad, nz - 2 * pad);
            for (int[] a : Box.handles())
                for (int k = 0; k < 3; k++) {
                    if (a[0] == 0 || a[1] == 0 || a[2] == 0 || a[k] > 0) continue;   // from each corner, the edges towards +x, +y and +z
                    int[] b = a.clone();
                    b[k] = 1;
                    double[] p = box.handle(a), q = box.handle(b);
                    raster.line(cam, p[0], p[1], p[2], q[0], q[1], q[2], BOX, thick);
                }
            int size = Integer.parseInt(view.getOrDefault("viewHandleSize", "11"));
            for (int[] sign : Box.handles()) {
                int n = Math.abs(sign[0]) + Math.abs(sign[1]) + Math.abs(sign[2]);
                // Handles show through whatever is in front of them, so they're drawn nearer than the shape.
                double[] p = box.handle(sign), back = cam.back();
                for (int k = 0; k < 3; k++) p[k] += back[k] * 4 * Shape3.MAX_SIZE;
                raster.line(cam, p[0], p[1], p[2], p[0], p[1], p[2], 0x15181D, size + 4);
                raster.line(cam, p[0], p[1], p[2], p[0], p[1], p[2], n == 1 ? FACE : n == 2 ? EDGE : CORNER, size);
            }
        }
        try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(Path.of(view.get("viewOut")))))) {
            for (int px : raster.argb) out.writeInt(px);
        }
        Solver3.Result first = solved == null ? r : solved;
        return "{\"error\":null,\"nx\":" + nx + ",\"ny\":" + ny + ",\"nz\":" + nz + ",\"blocks\":" + blocks + ",\"err\":" + first.err()
                + ",\"volume\":" + first.volume() + ",\"w\":" + w + ",\"h\":" + h + "}";
    }

    private static String lines(List<double[]> all) {
        StringJoiner out = new StringJoiner(",", "[", "]");
        for (double[] seg : all) {
            StringJoiner one = new StringJoiner(",", "[", "]");
            for (double d : seg) one.add(Double.toString(d));
            out.add(one.toString());
        }
        return out.toString();
    }
}
