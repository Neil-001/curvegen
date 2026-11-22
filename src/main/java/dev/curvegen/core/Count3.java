package dev.curvegen.core;

import dev.curvegen.core.Pieces.Family;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Count3 {
    private Count3() {}

    public record Group(Family family, Message name, int[] states, int icon) {
        public int count(int[] counts) {
            int n = 0;
            for (int s : states) n += counts[s];
            return n;
        }
    }

    public static final List<Group> GROUPS;

    private static final String[] FACING = {"north", "east", "south", "west", "down", "up"};

    public static Message name(int s) {
        String key = switch (Pieces3.FAMILY[s]) {
            case FULL -> "full";
            case SLAB -> Pieces3.half(s) == 1 ? "slab.top" : "slab.bottom";
            case STAIRS -> "stairs." + (Pieces3.half(s) == 1 ? "inverted." : "upright.") + switch (Pieces3.stairShape(s)) {
                case Pieces3.STRAIGHT -> "straight";
                case Pieces3.INNER_LEFT, Pieces3.INNER_RIGHT -> "inner";
                default -> "outer";
            };
            case TRAPDOOR -> Pieces3.open(s) ? "trapdoor.open" : Pieces3.half(s) == 1 ? "trapdoor.top" : "trapdoor.bottom";
            case SHELF -> "shelf." + FACING[Pieces3.facing(s)];
            case CHAIN -> switch (Pieces3.axis(s)) { case 0 -> "chain.x"; case 1 -> "chain.y"; default -> "chain.z"; };
            case ROD -> "rod." + FACING[Pieces3.facing(s)];
            case FENCE, PANE, WALL -> {
                int n = 0;
                for (int d = 0; d < 4; d++) if (Pieces3.side(s, d) > 0) n++;
                boolean straight = n == 2 && (Pieces3.side(s, Pieces3.N) > 0) == (Pieces3.side(s, Pieces3.S) > 0);
                String sides = switch (n) { case 0 -> "post"; case 1 -> "one"; case 2 -> straight ? "straight" : "corner"; case 3 -> "three"; default -> "four"; };
                yield "connector." + sides + (Pieces3.isWall(s) && n > 0 && !Pieces3.up(s) ? ".no_post" : "");
            }
            default -> "air";
        };
        return Message.of("curvegen.count3." + key);
    }

    static int silhouette(int s) {
        boolean[] seen = new boolean[256];
        int n = 0;
        for (int[] b : Pieces3.BOXES[s])
            for (int y = b[1]; y < b[4]; y++)
                for (int x = b[0]; x < b[3]; x++)
                    if (!seen[y * 16 + x]) { seen[y * 16 + x] = true; n++; }
        return n;
    }

    static {
        Map<String, List<Integer>> byName = new LinkedHashMap<>();
        for (int s = 1; s < Pieces3.COUNT; s++)
            byName.computeIfAbsent(Pieces3.FAMILY[s].ordinal() + "/" + name(s), _ -> new ArrayList<>()).add(s);
        List<Group> out = new ArrayList<>();
        for (Family f : Family.values())
            for (List<Integer> states : byName.values()) {
                int first = states.get(0);
                if (Pieces3.FAMILY[first] != f) continue;
                int icon = first;
                for (int s : states) if (silhouette(s) < silhouette(icon)) icon = s;
                out.add(new Group(f, name(first), states.stream().mapToInt(Integer::intValue).toArray(), icon));
            }
        GROUPS = List.copyOf(out);
    }
}
