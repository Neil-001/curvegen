package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces.Family;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What painting needs to know about the game's blocks, taken on the client thread and never changed after, so a
 * repaint can use it on a worker thread. Blocks are named by id ({@code minecraft:stone_bricks}).
 *
 * @param allowed     the blocks each piece type may use, the ones the Blocks tab's picker offers
 * @param colours     each block's colour 0xRRGGBB on the face colours are matched by: every allowed block's, and the
 *                    pattern's own blocks'
 * @param material    any block in a material (a slab, stairs, the base block) to that material's base block
 * @param variants    a material's base block to its block for each piece type it has, the full block included
 * @param noConnect   full blocks fences, panes and walls don't attach to
 * @param woodenFences fences that join each other; a fence outside this set joins only its own kind
 * @param choice      the Blocks tab's block for each piece type
 * @param matcher     finds the closest colour; a later version can hand in Chroma Studio's
 */
public record Catalog(Map<Family, List<String>> allowed, Map<String, Integer> colours, Map<String, String> material,
                      Map<String, Map<Family, String>> variants, Set<String> noConnect, Set<String> woodenFences,
                      Map<Family, String> choice, Matcher matcher) {

    /** Finds the block whose colour is closest to a colour. It must be safe to call from any thread. */
    public interface Matcher {
        /** The closest of {@code allowed} to {@code rgb}, or null when {@code allowed} is empty. */
        String closest(int rgb, List<String> allowed);
    }

    /** The matcher "Match a colour…" uses: distance in CIELAB between average colours. */
    public static Matcher lab(Map<String, Integer> colours) {
        Map<String, double[]> lab = new HashMap<>();
        for (var e : colours.entrySet()) lab.put(e.getKey(), Lab.of(e.getValue()));
        return (rgb, allowed) -> {
            double[] want = Lab.of(rgb);
            String best = null;
            double bd = Double.MAX_VALUE;
            for (String id : allowed) {
                double[] c = lab.get(id);
                if (c == null) continue;
                double d = Lab.distance(c, want);
                if (d < bd) { bd = d; best = id; }
            }
            return best;
        };
    }

    public boolean allows(Family f, String id) { return id != null && allowed.getOrDefault(f, List.of()).contains(id); }

    /** The material's block for a piece type, or null when it has none the piece type allows. */
    public String variant(String id, Family f) {
        String base = material.getOrDefault(id, id);
        String v = variants.getOrDefault(base, Map.of()).get(f);
        if (v == null && f == Family.FULL) v = base;
        return allows(f, v) ? v : null;
    }

    /** Fences the shape may use: the ones that join the Blocks tab's fence. Any wooden fence joins any other; any other fence only its own kind. */
    public List<String> fences() {
        String tab = choice.get(Family.FENCE);
        if (!woodenFences.contains(tab)) return tab == null ? List.of() : List.of(tab);
        List<String> out = new ArrayList<>();
        for (String id : allowed.getOrDefault(Family.FENCE, List.of())) if (woodenFences.contains(id)) out.add(id);
        return out;
    }
}
