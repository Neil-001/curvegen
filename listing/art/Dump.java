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
import dev.curvegen.core.edit.Box;
import dev.curvegen.core.edit.Orbit;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Solves shapes for render.py. Prints the piece table as one JSON line, then one JSON line per line of stdin.
 * Each input line sets ShapeSettings fields: "gen=EQUATION;src=y = 2sin(x);qW=48". Points are "pts=2,2 10,22".
 * "stencil=<file>" solves a picture instead of a generator's shape.
 *
 * A 3D shape is drawn here too, the way the menu's 3D preview draws it, and its pixels go to a file. The names
 * starting with "view" say how: viewOut (the file), viewW and viewH (pixels), viewYaw and viewPitch (degrees),
 * viewBg (the background, as RGB), viewColour (one colour for every block instead of the piece type colours), viewWires (the ideal shape's outline), viewCut (leave out the half nearest the
 * camera, to show the inside) and viewHandles (the in-world editor's box and its handles).
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
            Map<String, String> view = new HashMap<>();
            for (String kv : line.split(";")) {
                if (kv.isBlank()) continue;
                String k = kv.substring(0, kv.indexOf('=')).trim(), v = kv.substring(kv.indexOf('=') + 1).trim();
                if (k.startsWith("view")) { view.put(k, v); continue; }
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
            if (s.is3d()) { System.out.println(draw3(s, view)); continue; }
            Solver.Result r = stencil != null ? Solver.solve(stencil, s) : Solver.run(s);
            StringJoiner grid = new StringJoiner(",", "[", "]");
            for (byte b : r.grid()) grid.add(Integer.toString(b & 0xFF));
            System.out.println("{\"nx\":" + r.nx() + ",\"ny\":" + r.ny() + ",\"err\":" + r.err() + ",\"area\":" + r.area()
                    + ",\"grid\":" + grid + ",\"overlay\":" + lines(r.target().overlay) + ",\"dashed\":" + lines(r.target().dashed)
                    + ",\"axisX\":" + r.target().axisX + ",\"axisY\":" + r.target().axisY
                    + ",\"error\":" + (r.target().error == null ? "null" : "\"" + r.target().error.replace("\"", "'") + "\"") + "}");
        }
    }

    /** PreviewTexture's curve colour, and the editor's colours for a face, edge and corner handle. */
    private static final int CURVE = 0xFF4D73, BOX = 0xFFFFFF, FACE = 0xF2F2F2, EDGE = 0x7FD4FF, CORNER = 0xFFA646;

    /** Solves a 3D shape and draws it as Preview3 does: the solver's blocks in their piece type colours, then the outline. */
    private static String draw3(ShapeSettings s, Map<String, String> view) throws Exception {
        Solver3.Result r = Solver3.run(s);
        String error = r.shape().error();
        if (error != null) return "{\"error\":\"" + error.replace("\"", "'") + "\"}";
        int nx = r.nx(), ny = r.ny(), nz = r.nz(), blocks = 0;
        for (short c : r.grid()) if (c != Pieces3.AIR) blocks++;
        int w = Integer.parseInt(view.get("viewW")), h = Integer.parseInt(view.get("viewH"));
        Orbit cam = new Orbit();
        if (view.containsKey("viewYaw")) cam.yaw = Math.toRadians(Double.parseDouble(view.get("viewYaw")));
        if (view.containsKey("viewPitch")) cam.pitch = Math.toRadians(Double.parseDouble(view.get("viewPitch")));
        cam.fit(nx, ny, nz, 0, 0, w, h, Double.parseDouble(view.getOrDefault("viewMargin", "12")));
        if (view.containsKey("viewScale")) cam.scale = Double.parseDouble(view.get("viewScale"));

        short[] grid = r.grid();
        if (Boolean.parseBoolean(view.get("viewCut"))) {
            // The camera's side of the upright plane through the middle that faces it most squarely.
            grid = grid.clone();
            double[] b = cam.back();
            boolean alongX = Math.abs(b[0]) > Math.abs(b[2]);
            for (int y = 0, c = 0; y < ny; y++)
                for (int z = 0; z < nz; z++)
                    for (int x = 0; x < nx; x++, c++) {
                        double side = alongX ? (x + .5 - nx / 2.0) * b[0] : (z + .5 - nz / 2.0) * b[2];
                        if (side > 0) grid[c] = Pieces3.AIR;
                    }
        }

        int[] palette = new int[Pieces.Family.values().length];
        for (Pieces.Family f : Pieces.Family.values())
            for (int p = 1; p < Pieces.COUNT; p++) if (Pieces.FAMILY[p] == f) { palette[f.ordinal()] = Pieces.COLOR[p]; break; }
        if (view.containsKey("viewColour")) java.util.Arrays.fill(palette, Integer.parseInt(view.get("viewColour"), 16));
        Raster3 raster = new Raster3(w, h);
        raster.clear(0xFF000000 | Integer.parseInt(view.getOrDefault("viewBg", "1B1F25"), 16));
        raster.draw(new Mesh3(grid, nx, ny, nz), cam, palette);
        int thick = Integer.parseInt(view.getOrDefault("viewThick", "2"));
        if (Boolean.parseBoolean(view.getOrDefault("viewWires", "true")))
            for (double[] l : r.shape().wireframe())
                for (int p = 0; p + 5 < l.length; p += 3) raster.line(cam, l[p], l[p + 1], l[p + 2], l[p + 3], l[p + 4], l[p + 5], CURVE, thick);
        if (Boolean.parseBoolean(view.get("viewHandles"))) {
            // The box the player set, which leaves out the room an outwards shell adds.
            int pad = r.shape().pad();
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
        return "{\"error\":null,\"nx\":" + nx + ",\"ny\":" + ny + ",\"nz\":" + nz + ",\"blocks\":" + blocks + ",\"err\":" + r.err()
                + ",\"volume\":" + r.volume() + ",\"w\":" + w + ",\"h\":" + h + "}";
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
