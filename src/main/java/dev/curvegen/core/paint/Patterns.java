package dev.curvegen.core.paint;

import dev.curvegen.core.paint.Pattern.Cell;
import java.util.List;

/** The patterns the mod comes with. Their names are translation keys. */
public final class Patterns {
    private Patterns() {}

    private static Cell b(String id) { return Cell.block("minecraft:" + id); }

    /** Pattern rows from letters, top row first, each letter standing for one of {@code cells}' entries in {@code key}. */
    private static Pattern grid(String name, String key, Cell[] cells, String... rows) {
        Cell[][] out = new Cell[rows.length][];
        for (int r = 0; r < rows.length; r++) {
            out[r] = new Cell[rows[r].length()];
            for (int x = 0; x < rows[r].length(); x++) out[r][x] = cells[key.indexOf(rows[r].charAt(x))];
        }
        return Pattern.of(name, out);
    }

    /** Two blocks in turn, one cell each along the shape. */
    public static final Pattern STRIPES = grid("curvegen.pattern.stripes", "DL",
            new Cell[]{b("polished_blackstone_bricks"), b("smooth_quartz")}, "DL");

    public static final Pattern CHECKER = grid("curvegen.pattern.checker", "BW",
            new Cell[]{b("black_concrete"), b("white_concrete")}, "BW", "WB");

    /** Bricks, with a few granite and terracotta ones among them. */
    public static final Pattern BRICK_ROAD = grid("curvegen.pattern.brick_road", "BGT",
            new Cell[]{b("bricks"), b("polished_granite"), b("terracotta")},
            "BBGB",
            "BBBB",
            "GBTB",
            "BBBB");

    /** Stone bricks with moss and cracks through them. */
    public static final Pattern MOSSY_MIX = grid("curvegen.pattern.mossy_mix", "SMCO",
            new Cell[]{b("stone_bricks"), b("mossy_stone_bricks"), b("cracked_stone_bricks"), b("mossy_cobblestone")},
            "SMSC",
            "MSOS",
            "SCSM",
            "OSMS");

    /**
     * A road 9 blocks across and 8 long: white edge lines, a grey surface and a yellow centre line dashed 4 on and 4
     * off. It suits a flat Bézier line about 9 wide with Fit set to Repeat along.
     */
    public static final Pattern TWO_LANE_ROAD = grid("curvegen.pattern.two_lane_road", "WGY",
            new Cell[]{b("white_concrete"), b("gray_concrete"), b("yellow_concrete")},
            "WWWWWWWW",
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "YYYYGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "WWWWWWWW");

    /**
     * Spruce bars one block wide round openings two blocks across. The openings are empty cells, so with "Empty spots"
     * set to Open they're holes: a screen, a trellis or a dome you can see through.
     */
    public static final Pattern LATTICE = grid("curvegen.pattern.lattice", "S-",
            new Cell[]{b("spruce_planks"), Cell.EMPTY},
            "SSS",
            "S--",
            "S--");

    public static final List<Pattern> BUNDLED = List.of(STRIPES, CHECKER, BRICK_ROAD, MOSSY_MIX, TWO_LANE_ROAD, LATTICE);
}
