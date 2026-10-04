import dev.curvegen.core.Pieces;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.Solver;
import dev.curvegen.core.Stencil;
import dev.curvegen.core.Target;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;

/**
 * Solves shapes for render.py. Prints the piece table as one JSON line, then one JSON line per line of stdin.
 * Each input line sets ShapeSettings fields: "gen=EQUATION;src=y = 2sin(x);qW=48". Points are "pts=2,2 10,22".
 * "stencil=<file>" solves a picture instead of a generator's shape.
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
            for (String kv : line.split(";")) {
                if (kv.isBlank()) continue;
                String k = kv.substring(0, kv.indexOf('=')).trim(), v = kv.substring(kv.indexOf('=') + 1).trim();
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
                Field f = ShapeSettings.class.getField(k);
                Class<?> t = f.getType();
                if (t == int.class) f.setInt(s, Integer.parseInt(v));
                else if (t == double.class) f.setDouble(s, Double.parseDouble(v));
                else if (t == boolean.class) f.setBoolean(s, Boolean.parseBoolean(v));
                else if (t.isEnum()) f.set(s, Enum.valueOf(t.asSubclass(Enum.class), v));
                else f.set(s, v);
            }
            Solver.Result r = stencil != null ? Solver.solve(stencil, s) : Solver.run(s);
            StringJoiner grid = new StringJoiner(",", "[", "]");
            for (byte b : r.grid()) grid.add(Integer.toString(b & 0xFF));
            System.out.println("{\"nx\":" + r.nx() + ",\"ny\":" + r.ny() + ",\"err\":" + r.err() + ",\"area\":" + r.area()
                    + ",\"grid\":" + grid + ",\"overlay\":" + lines(r.target().overlay) + ",\"dashed\":" + lines(r.target().dashed)
                    + ",\"axisX\":" + r.target().axisX + ",\"axisY\":" + r.target().axisY
                    + ",\"error\":" + (r.target().error == null ? "null" : "\"" + r.target().error.replace("\"", "'") + "\"") + "}");
        }
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
