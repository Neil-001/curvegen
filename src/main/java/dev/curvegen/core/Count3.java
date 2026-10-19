package dev.curvegen.core;

import dev.curvegen.core.Pieces.Family;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rows of the Count tab for a 3D shape. {@link Pieces3} has 257 states, most of them the same piece turned
 * another way or a wall with another mix of sides, so states that a builder would call the same thing share a row.
 */
public final class Count3 {
    private Count3() {}

    /**
     * One row: a piece family, what to call these states within it, the states, and the one whose view from the
     * south says most about the piece, for the row's icon.
     */
    public record Group(Family family, Message name, int[] states, int icon) {
        public int count(int[] counts) {
            int n = 0;
            for (int s : states) n += counts[s];
            return n;
        }
    }

    /** Every row, by family in the order the states are numbered. */
    public static final List<Group> GROUPS;

    private static final String[] FACING = {"north", "east", "south", "west", "down", "up"};

    /** What a state is called within its family. */
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

    /** How much of a block's face the state covers seen from the south, in 256ths. */
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
                // The view that covers least shows the piece's profile rather than its back.
                int icon = first;
                for (int s : states) if (silhouette(s) < silhouette(icon)) icon = s;
                out.add(new Group(f, name(first), states.stream().mapToInt(Integer::intValue).toArray(), icon));
            }
        GROUPS = List.copyOf(out);
    }
}
