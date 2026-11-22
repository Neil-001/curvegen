package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces.Family;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MaterialNames {
    private MaterialNames() {}

    private static final Map<Family, String> SUFFIX = new EnumMap<>(Map.of(Family.SLAB, "_slab", Family.STAIRS, "_stairs",
            Family.WALL, "_wall", Family.FENCE, "_fence", Family.TRAPDOOR, "_trapdoor", Family.SHELF, "_shelf", Family.PANE, "_pane"));

    static List<String> stems(String path) {
        List<String> out = new ArrayList<>();
        for (String[] r : new String[][]{{"_planks", ""}, {"_bricks", "_brick"}, {"_tiles", "_tile"}, {"_block", ""}})
            if (path.endsWith(r[0])) out.add(path.substring(0, path.length() - r[0].length()) + r[1]);
        out.add(path);
        return out;
    }

    public static String variant(String id, Family f, Set<String> ids) {
        String suffix = SUFFIX.get(f);
        int colon = id.indexOf(':');
        if (suffix == null || colon < 0) return null;
        String ns = id.substring(0, colon + 1);
        for (String stem : stems(id.substring(colon + 1))) {
            String v = ns + stem + suffix;
            if (ids.contains(v)) return v;
        }
        return null;
    }
}
