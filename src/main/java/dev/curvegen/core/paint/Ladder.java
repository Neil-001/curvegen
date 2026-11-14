package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.paint.Pattern.Cell;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks the block for one cell of a pattern or mix and one piece type, in order:
 * <ol>
 * <li>A full block uses the cell's own block as it is, properties and all.
 * <li>Any other piece uses that material's block for its piece type: stone bricks give stone brick stairs.
 * <li>When the material has no such piece, the piece type's block with the closest colour.
 * <li>Otherwise the Blocks tab's choice.
 * </ol>
 * The rules that keep painting from changing the shape apply on top. Full blocks that fences, panes and walls attach
 * to differently from the rest of the shape become gaps: the Blocks tab's full block, or the closest one that attaches
 * the right way. Fences come only from the group the Blocks tab's fence joins. Only blocks the Blocks tab could offer
 * are used, so falling blocks never are.
 *
 * <p>With "One material per colour", a cell whose material lacks the slabs or stairs the shape uses swaps to the
 * closest material that has them, for its full blocks too, and its other pieces follow that material.
 *
 * <p>Each answer is worked out once per pattern cell and piece type, so painting costs little per block.
 */
public final class Ladder {
    /**
     * Why a block was chosen, for the status line: its own material, the closest colour, the Blocks tab's choice
     * because nothing else fit, a gap where it would have joined fences differently, or an empty pattern cell.
     */
    public enum Why { OWN, CLOSEST, BLOCKS_TAB, GAP, EMPTY }

    private static final Family[] FAMILIES = Family.values();
    private static final int UNSET = Integer.MIN_VALUE;

    private final Catalog c;
    private final Palette p;
    private final boolean one, shapeConnects, connectors;
    private final List<Family> needed = new ArrayList<>();
    private final Map<Family, Set<String>> allowed = new EnumMap<>(Family.class);
    private final Map<Family, List<String>> candidates = new EnumMap<>(Family.class);
    /** The blocks chosen so far; a pick is an index into this, or -1 for the Blocks tab's choice. */
    final List<String> blocks = new ArrayList<>();
    private final Map<String, Integer> index = new HashMap<>();
    private final int[][] pick;
    private final Why[][] why;
    private final String[] base;
    private List<String> swapTo;

    /**
     * @param used           piece types the shape has at least one of
     * @param shapeConnects  whether the solve had fences, panes and walls attach to full blocks
     */
    public Ladder(Catalog c, Palette p, boolean oneMaterial, boolean shapeConnects, Set<Family> used) {
        this.c = c; this.p = p; this.one = oneMaterial; this.shapeConnects = shapeConnects;
        connectors = used.contains(Family.FENCE) || used.contains(Family.PANE) || used.contains(Family.WALL);
        for (Family f : new Family[]{Family.SLAB, Family.STAIRS}) if (used.contains(f)) needed.add(f);
        for (var e : c.allowed().entrySet()) allowed.put(e.getKey(), new HashSet<>(e.getValue()));
        for (Family f : FAMILIES) candidates.put(f, f == Family.FENCE ? c.fences() : c.allowed().getOrDefault(f, List.of()));
        allowed.put(Family.FENCE, new HashSet<>(candidates.get(Family.FENCE)));
        pick = new int[p.paletteSize()][FAMILIES.length];
        why = new Why[p.paletteSize()][FAMILIES.length];
        for (int[] row : pick) java.util.Arrays.fill(row, UNSET);
        base = new String[p.paletteSize()];
    }

    /** The block for pattern cell {@code cell} (a palette index) as piece type {@code f}: an index into {@link #blocks}, or -1. */
    public int pick(int cell, Family f) {
        int k = f.ordinal();
        if (pick[cell][k] == UNSET) compute(cell, f);
        return pick[cell][k];
    }

    public Why why(int cell, Family f) {
        pick(cell, f);
        return why[cell][f.ordinal()];
    }

    public List<String> blocks() { return blocks; }

    private boolean allows(Family f, String id) { return id != null && allowed.getOrDefault(f, Set.of()).contains(id); }

    private int colour(Cell cell) { return cell.rgb() >= 0 ? cell.rgb() : c.colours().getOrDefault(cell.block(), -1); }

    private void compute(int cell, Family f) {
        Cell x = p.cell(cell);
        String chosen = null;
        Why reason = x.empty() ? Why.EMPTY : Why.BLOCKS_TAB;
        int rgb = -1;
        if (!x.empty()) {
            rgb = colour(x);
            String mat = material(cell, rgb);
            boolean swapped = mat != null && x.block() != null && !mat.equals(c.material().getOrDefault(x.block(), x.block()));
            if (f == Family.FULL) {
                if (!swapped && allows(f, x.block())) chosen = x.state();
                else if (allows(f, mat)) chosen = mat;
            } else if (mat != null) {
                String v = c.variant(mat, f);
                if (allows(f, v)) chosen = v;
            }
            if (chosen != null) reason = Why.OWN;
            else if (rgb >= 0 && (chosen = c.matcher().closest(rgb, candidates.get(f))) != null) reason = Why.CLOSEST;
        }
        if (f == Family.FULL && connectors) {
            // Fences, panes and walls beside a full block must join it the way the solve assumed.
            if (chosen != null && attaches(chosen) != shapeConnects) { chosen = null; reason = Why.GAP; }
            String tab = c.choice().get(Family.FULL);
            if (chosen == null && tab != null && attaches(tab) != shapeConnects) {
                // The Blocks tab's block would join wrongly too: the closest one that joins the right way.
                int near = rgb >= 0 ? rgb : c.colours().getOrDefault(tab, -1);
                if (near >= 0) chosen = c.matcher().closest(near, agreeing());
            }
        }
        pick[cell][f.ordinal()] = chosen == null ? -1 : index.computeIfAbsent(chosen, b -> { blocks.add(b); return blocks.size() - 1; });
        why[cell][f.ordinal()] = reason;
    }

    private boolean attaches(String full) { return !c.noConnect().contains(blockOf(full)); }

    private static String blockOf(String state) {
        int b = state.indexOf('[');
        return b < 0 ? state : state.substring(0, b);
    }

    private List<String> agreeing;
    private List<String> agreeing() {
        if (agreeing == null) {
            agreeing = new ArrayList<>();
            for (String id : candidates.get(Family.FULL)) if (attaches(id) == shapeConnects) agreeing.add(id);
        }
        return agreeing;
    }

    /** The cell's material's base block, swapped for "One material per colour" when it lacks a piece the shape uses. */
    private String material(int cell, int rgb) {
        if (base[cell] != null) return base[cell].isEmpty() ? null : base[cell];
        String b = p.cell(cell).block();
        String mat = b == null ? null : c.material().getOrDefault(b, b);
        if (one && (mat == null || !complete(mat)) && rgb >= 0) {
            String to = c.matcher().closest(rgb, swapTargets());
            if (to != null) mat = to;
        }
        base[cell] = mat == null ? "" : mat;
        return mat;
    }

    /** Whether a material has a full block and every one of the slab and stairs the shape uses. */
    private boolean complete(String mat) {
        if (!allows(Family.FULL, c.variant(mat, Family.FULL))) return false;
        for (Family f : needed) if (!allows(f, c.variant(mat, f))) return false;
        return true;
    }

    private List<String> swapTargets() {
        if (swapTo == null) {
            swapTo = new ArrayList<>();
            for (String mat : c.variants().keySet()) if (complete(mat)) swapTo.add(c.variant(mat, Family.FULL));
        }
        return swapTo;
    }
}
