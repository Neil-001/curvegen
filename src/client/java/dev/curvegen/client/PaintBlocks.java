package dev.curvegen.client;

import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.paint.Catalog;
import dev.curvegen.core.paint.MaterialNames;
import dev.curvegen.core.paint.Paint;
import dev.curvegen.core.paint.Painted;
import dev.curvegen.core.paint.Palette;
import dev.curvegen.core.paint.Pattern;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The game's side of painting: the {@link Catalog} a repaint works from, and turning a {@link Painted} shape's block
 * names back into blocks and states. Materials come from the game's own block families, and for blocks those leave
 * out, such as a mod's, from their ids ({@link MaterialNames}).
 */
public final class PaintBlocks {
    private PaintBlocks() {}

    private static final Map<Family, BlockFamily.Variant> VARIANT = new EnumMap<>(Map.of(Family.SLAB, BlockFamily.Variant.SLAB,
            Family.STAIRS, BlockFamily.Variant.STAIRS, Family.WALL, BlockFamily.Variant.WALL, Family.FENCE, BlockFamily.Variant.FENCE,
            Family.TRAPDOOR, BlockFamily.Variant.TRAPDOOR));

    /** Base block → its block per piece type, and any block in a material → the base. Built once: the registry doesn't change. */
    private static Map<String, Map<Family, String>> variants;
    private static Map<String, String> material;

    public static String id(Block b) { return BuiltInRegistries.BLOCK.getKey(b).toString(); }

    public static Block block(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null || !BuiltInRegistries.BLOCK.containsKey(key) ? null : BuiltInRegistries.BLOCK.getValue(key);
    }

    /** A material's block for each piece type, by the game's families or by name. For tests. */
    public static Map<Family, String> pieces(String base) {
        materials();
        return variants.getOrDefault(base, Map.of());
    }

    /** The base block of the material a block belongs to, or null. For tests. */
    public static String materialOf(String id) {
        materials();
        return material.get(id);
    }

    private static synchronized void materials() {
        if (variants != null) return;
        Map<String, Map<Family, String>> v = new HashMap<>();
        Map<String, String> m = new HashMap<>();
        BlockFamilies.getAllFamilies().forEach(fam -> {
            String base = id(fam.getBaseBlock());
            Map<Family, String> pieces = v.computeIfAbsent(base, _ -> new EnumMap<>(Family.class));
            for (var e : VARIANT.entrySet()) {
                Block b = fam.getVariants().get(e.getValue());
                if (b == null) continue;
                pieces.putIfAbsent(e.getKey(), id(b));
                m.putIfAbsent(id(b), base);
            }
        });
        Set<String> ids = new HashSet<>();
        for (Block b : BuiltInRegistries.BLOCK) ids.add(id(b));
        for (Block b : BlockChoices.candidates(Family.FULL)) {
            String base = id(b);
            Map<Family, String> pieces = v.computeIfAbsent(base, _ -> new EnumMap<>(Family.class));
            pieces.put(Family.FULL, base);
            m.putIfAbsent(base, base);
            for (Family f : BlockChoices.FAMILIES) {
                if (f == Family.FULL || pieces.containsKey(f)) continue;
                String guess = MaterialNames.variant(base, f, ids);
                Block g = guess == null ? null : block(guess);
                if (g == null || !BlockChoices.fits(g, f) || m.containsKey(guess)) continue;
                pieces.put(f, guess);
                m.put(guess, base);
            }
        }
        material = m; variants = v;
    }

    /**
     * What a block in the world becomes in a captured pattern: air an empty cell, a slab, stairs or another shaped
     * block its material's full block, and a block painting can't use, such as a torch, a chest or a falling block, an
     * unusable cell. A full block keeps the properties that differ from its default, such as a log's axis.
     * {@code full} is {@code BlockChoices.candidates(FULL)}, which the caller looks up once.
     */
    public static Pattern.Cell cell(BlockState s, Set<Block> full) {
        if (s.isAir()) return Pattern.Cell.EMPTY;
        String id = id(s.getBlock());
        if (full.contains(s.getBlock())) return Pattern.Cell.block(id + properties(s));
        String base = materialOf(id);
        Block b = base == null ? null : block(base);
        return b != null && full.contains(b) ? Pattern.Cell.block(base) : Pattern.Cell.UNUSABLE;
    }

    /** "[axis=x]" for the properties that differ from the block's default state, or "". */
    private static String properties(BlockState s) {
        BlockState d = s.getBlock().defaultBlockState();
        StringBuilder out = new StringBuilder();
        for (Property<?> p : s.getProperties()) {
            if (s.getValue(p).equals(d.getValue(p))) continue;
            out.append(out.isEmpty() ? "[" : ",").append(p.getName()).append('=').append(valueName(s, p));
        }
        return out.isEmpty() ? "" : out.append(']').toString();
    }

    private static <T extends Comparable<T>> String valueName(BlockState s, Property<T> p) { return p.getName(s.getValue(p)); }

    /**
     * Everything a repaint needs, taken now, on the client thread: block colours on the face the shape is matched by,
     * for every block the Blocks tab could offer and every block the source paints with.
     */
    public static Catalog catalog(ShapeSettings s) { return catalog(s, BlockChoices.CHOICE); }

    /** The same, with {@code base} for the Blocks tab's choices, which the paint falls back to. */
    public static Catalog catalog(ShapeSettings s, Map<Family, Block> base) {
        materials();
        boolean top = s.is3d() ? s.topColours : s.floor;
        Map<Family, List<String>> allowed = new EnumMap<>(Family.class);
        Map<String, Integer> colours = new HashMap<>();
        Set<String> noConnect = new HashSet<>(), wooden = new HashSet<>();
        for (Family f : BlockChoices.FAMILIES) {
            List<String> ids = new ArrayList<>();
            for (Block b : BlockChoices.candidates(f)) {
                String id = id(b);
                ids.add(id);
                colours.put(id, ColorIndex.of(b, top));
                if (f == Family.FULL && !BlockChoices.connects(b)) noConnect.add(id);
                if (f == Family.FENCE && b.defaultBlockState().is(BlockTags.WOODEN_FENCES)) wooden.add(id);
            }
            allowed.put(f, ids);
        }
        Palette p = s.paint.palette();
        for (int k = 0; k < p.paletteSize(); k++) {
            String id = p.cell(k).block();
            Block b = id == null ? null : block(id);
            if (b != null) colours.putIfAbsent(id, ColorIndex.of(b, top));
        }
        Map<Family, String> choice = new EnumMap<>(Family.class);
        for (var e : base.entrySet()) choice.put(e.getKey(), id(e.getValue()));
        return new Catalog(allowed, colours, material, variants, noConnect, wooden, choice, Catalog.lab(colours));
    }

    /**
     * Whether fences, panes and walls attach to the shape's full blocks: what most of the pattern's or mix's blocks do
     * while it's painted, or what the Blocks tab's full block does.
     */
    public static boolean fullConnects(ShapeSettings s) { return fullConnects(s, BlockChoices.CHOICE); }

    /** The same, with {@code choice} for the Blocks tab's choices. */
    public static boolean fullConnects(ShapeSettings s, Map<Family, Block> choice) {
        // Only 2D shapes are painted so far. The 3D painting drops the is3d test.
        if (s.paint.on() && !s.is3d()) {
            Boolean most = Paint.connects(s.paint.palette(), id -> {
                Block b = block(id);
                return b == null || BlockChoices.connects(b);
            });
            if (most != null) return most;
        }
        return BlockChoices.connects(choice.get(Family.FULL));
    }

    /** A painted shape's entries as blocks, with each full block's state, turned with the pattern. */
    public record Resolved(Block[] blocks, BlockState[] full) {
        /** The block for a slot's entry, or null for the Blocks tab's choice. */
        public Block block(int entry) { return entry < 0 ? null : blocks[entry]; }
    }

    /**
     * Looks up a painted shape's entries. {@code viewer} is the side the pattern is seen from, which it turns
     * anticlockwise about: up for a shape lying flat, towards the player for an upright one. An entry naming a block that doesn't exist takes the Blocks tab's.
     */
    public static Resolved resolve(Painted p, Direction viewer) {
        int n = p.blocks().size();
        Block[] blocks = new Block[n];
        BlockState[] full = new BlockState[n];
        for (int k = 0; k < n; k++) {
            String s = p.blocks().get(k);
            int b = s.indexOf('[');
            Block block = block(b < 0 ? s : s.substring(0, b));
            if (block == null) continue;
            blocks[k] = block;
            full[k] = BlockChoices.finish(turn(state(block, b < 0 ? "" : s.substring(b + 1, s.length() - 1)), p.quarters()[k], viewer));
        }
        return new Resolved(blocks, full);
    }

    private static BlockState state(Block block, String props) {
        BlockState s = block.defaultBlockState();
        if (props.isEmpty()) return s;
        for (String kv : props.split(",")) {
            int eq = kv.indexOf('=');
            if (eq < 0) continue;
            Property<?> p = block.getStateDefinition().getProperty(kv.substring(0, eq));
            if (p != null) s = withNamed(s, p, kv.substring(eq + 1));
        }
        return s;
    }

    private static <T extends Comparable<T>> BlockState withNamed(BlockState s, Property<T> p, String value) {
        return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
    }

    /**
     * Turns a full block by quarter turns, anticlockwise as seen from {@code viewer}. About the vertical that is the
     * game's own rotation. About a level axis, a block's axis and the way it faces turn too, where the block allows it.
     */
    @SuppressWarnings({"deprecation", "unchecked"})   // the game marks rotation for its own use, but it's the one way to turn a state
    public static BlockState turn(BlockState s, int quarters, Direction viewer) {
        quarters = Math.floorMod(quarters, 4);
        if (quarters == 0) return s;
        if (viewer == Direction.UP)
            return s.rotate(quarters == 1 ? Rotation.COUNTERCLOCKWISE_90 : quarters == 2 ? Rotation.CLOCKWISE_180 : Rotation.CLOCKWISE_90);
        for (Property<?> p : s.getProperties()) {
            if (p == BlockStateProperties.AXIS) {
                Direction.Axis a = s.getValue(BlockStateProperties.AXIS);
                Direction d = turn(Direction.fromAxisAndDirection(a, Direction.AxisDirection.POSITIVE), quarters, viewer);
                s = s.setValue(BlockStateProperties.AXIS, d.getAxis());
            } else if (p.getValueClass() == Direction.class) {
                Property<Direction> dp = (Property<Direction>) p;
                Direction d = turn(s.getValue(dp), quarters, viewer);
                if (dp.getPossibleValues().contains(d)) s = s.setValue(dp, d);
            }
        }
        return s;
    }

    /**
     * The side a drawing is seen from, given where its x and y point in the world: the one its turns are anticlockwise
     * from. That is {@code right × up}: up for a flat shape, towards the viewer of an upright one.
     */
    public static Direction viewer(Direction right, Direction up) {
        int x = right.getStepY() * up.getStepZ() - right.getStepZ() * up.getStepY();
        int y = right.getStepZ() * up.getStepX() - right.getStepX() * up.getStepZ();
        int z = right.getStepX() * up.getStepY() - right.getStepY() * up.getStepX();
        return Direction.getNearest(x, y, z, Direction.UP);
    }

    /** A direction turned by quarter turns anticlockwise as seen from {@code viewer}: each is {@code viewer × d}. */
    static Direction turn(Direction d, int quarters, Direction viewer) {
        for (int k = 0; k < quarters; k++) {
            if (d.getAxis() == viewer.getAxis()) return d;
            int x = viewer.getStepY() * d.getStepZ() - viewer.getStepZ() * d.getStepY();
            int y = viewer.getStepZ() * d.getStepX() - viewer.getStepX() * d.getStepZ();
            int z = viewer.getStepX() * d.getStepY() - viewer.getStepY() * d.getStepX();
            d = Direction.getNearest(x, y, z, d);
        }
        return d;
    }
}
