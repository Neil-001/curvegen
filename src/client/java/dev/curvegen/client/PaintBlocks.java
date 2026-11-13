package dev.curvegen.client;

import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.paint.Catalog;
import dev.curvegen.core.paint.MaterialNames;
import dev.curvegen.core.paint.Paint;
import dev.curvegen.core.paint.Painted;
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
     * Everything a repaint needs, taken now, on the client thread: block colours on the face the shape is matched by,
     * for every block the Blocks tab could offer and every block in the pattern.
     */
    public static Catalog catalog(ShapeSettings s) {
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
        Pattern p = s.paint.pattern;
        for (int k = 0; k < p.paletteSize(); k++) {
            String id = p.cell(k).block();
            Block b = id == null ? null : block(id);
            if (b != null) colours.putIfAbsent(id, ColorIndex.of(b, top));
        }
        Map<Family, String> choice = new EnumMap<>(Family.class);
        for (var e : BlockChoices.CHOICE.entrySet()) choice.put(e.getKey(), id(e.getValue()));
        return new Catalog(allowed, colours, material, variants, noConnect, wooden, choice, Catalog.lab(colours));
    }

    /**
     * Whether fences, panes and walls attach to the shape's full blocks: what most of the pattern's blocks do while
     * it's painted, or what the Blocks tab's full block does.
     */
    public static boolean fullConnects(ShapeSettings s) { return fullConnects(s, BlockChoices.CHOICE); }

    /** The same, with {@code choice} for the Blocks tab's choices. */
    public static boolean fullConnects(ShapeSettings s, Map<Family, Block> choice) {
        // Only 2D shapes are painted so far. The 3D painting drops the is3d test.
        if (s.paint.on() && !s.is3d()) {
            Boolean most = Paint.connects(s.paint.pattern, id -> {
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
     * Looks up a painted shape's entries. {@code normal} is the axis the pattern is turned about: up for a shape
     * lying flat, the depth for an upright one. An entry naming a block that doesn't exist takes the Blocks tab's.
     */
    public static Resolved resolve(Painted p, Direction.Axis normal) {
        int n = p.blocks().size();
        Block[] blocks = new Block[n];
        BlockState[] full = new BlockState[n];
        for (int k = 0; k < n; k++) {
            String s = p.blocks().get(k);
            int b = s.indexOf('[');
            Block block = block(b < 0 ? s : s.substring(0, b));
            if (block == null) continue;
            blocks[k] = block;
            full[k] = BlockChoices.finish(turn(state(block, b < 0 ? "" : s.substring(b + 1, s.length() - 1)), p.quarters(), normal));
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
     * Turns a full block by quarter turns about an axis. About the vertical that is the game's own rotation. About a
     * level axis only a block's axis can follow: a quarter turn swaps the vertical and the other level axis.
     */
    @SuppressWarnings("deprecation")   // the game marks rotation for its own use, but it's the one way to turn a state
    static BlockState turn(BlockState s, int quarters, Direction.Axis normal) {
        if (quarters == 0) return s;
        if (normal == Direction.Axis.Y)
            return s.rotate(quarters == 1 ? Rotation.COUNTERCLOCKWISE_90 : quarters == 2 ? Rotation.CLOCKWISE_180 : Rotation.CLOCKWISE_90);
        if (quarters % 2 == 0 || !s.hasProperty(BlockStateProperties.AXIS)) return s;
        Direction.Axis a = s.getValue(BlockStateProperties.AXIS), level = normal == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        return a == Direction.Axis.Y ? s.setValue(BlockStateProperties.AXIS, level) : a == level ? s.setValue(BlockStateProperties.AXIS, Direction.Axis.Y) : s;
    }
}
