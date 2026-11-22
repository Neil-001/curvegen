package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces.Family;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record Catalog(Map<Family, List<String>> allowed, Map<String, Integer> colours, Map<String, String> material,
                      Map<String, Map<Family, String>> variants, Set<String> noConnect, Set<String> woodenFences,
                      Map<Family, String> choice, Matcher matcher) {

    public interface Matcher {
        String closest(int rgb, List<String> allowed);
    }

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

    public String variant(String id, Family f) {
        String base = material.getOrDefault(id, id);
        String v = variants.getOrDefault(base, Map.of()).get(f);
        if (v == null && f == Family.FULL) v = base;
        return allows(f, v) ? v : null;
    }

    public List<String> fences() {
        String tab = choice.get(Family.FENCE);
        if (!woodenFences.contains(tab)) return tab == null ? List.of() : List.of(tab);
        List<String> out = new ArrayList<>();
        for (String id : allowed.getOrDefault(Family.FENCE, List.of())) if (woodenFences.contains(id)) out.add(id);
        return out;
    }
}
