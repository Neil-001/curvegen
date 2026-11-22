package dev.curvegen.client;

import dev.curvegen.client.chroma.Chroma;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.Pieces;
import dev.curvegen.core.Pieces3;
import dev.curvegen.core.Settle2D;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.Solver;
import dev.curvegen.core.paint.Catalog;
import dev.curvegen.core.paint.MaterialNames;
import dev.curvegen.core.paint.Paint;
import dev.curvegen.core.paint.Painted;
import dev.curvegen.core.paint.Palette;
import dev.curvegen.core.paint.Pattern;
import dev.curvegen.core.paint.Solve3Painter;
import java.util.ArrayList;
import java.util.Arrays;
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

public final class PaintBlocks {
    private PaintBlocks() {}

    private static final Map<Family, BlockFamily.Variant> VARIANT = new EnumMap<>(Map.of(Family.SLAB, BlockFamily.Variant.SLAB,
            Family.STAIRS, BlockFamily.Variant.STAIRS, Family.WALL, BlockFamily.Variant.WALL, Family.FENCE, BlockFamily.Variant.FENCE,
            Family.TRAPDOOR, BlockFamily.Variant.TRAPDOOR));

    private static Map<String, Map<Family, String>> variants;
    private static Map<String, String> material;

    public static String id(Block b) { return BuiltInRegistries.BLOCK.getKey(b).toString(); }

    public static Block block(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null || !BuiltInRegistries.BLOCK.containsKey(key) ? null : BuiltInRegistries.BLOCK.getValue(key);
    }

    public static Map<Family, String> pieces(String base) {
        materials();
        return variants.getOrDefault(base, Map.of());
    }

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

    public static Pattern.Cell cell(BlockState s, Set<Block> full) {
        if (s.isAir()) return Pattern.Cell.EMPTY;
        String id = id(s.getBlock());
        if (full.contains(s.getBlock())) return Pattern.Cell.block(id + properties(s));
        String base = materialOf(id);
        Block b = base == null ? null : block(base);
        return b != null && full.contains(b) ? Pattern.Cell.block(base) : Pattern.Cell.UNUSABLE;
    }

    public static boolean placeable(String id) {
        Block b = block(id);
        if (b == null) return false;
        List<Block> full = BlockChoices.candidates(Family.FULL);
        if (full.contains(b)) return true;
        String base = materialOf(id);
        Block m = base == null ? null : block(base);
        return m != null && full.contains(m);
    }

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

    public static Catalog catalog(ShapeSettings s) { return catalog(s, BlockChoices.CHOICE); }

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
        return new Catalog(allowed, colours, material, variants, noConnect, wooden, choice, Chroma.matcher(top, allowed, Catalog.lab(colours)));
    }

    public static Map<Family, List<String>> allowedIds() {
        Map<Family, List<String>> allowed = new EnumMap<>(Family.class);
        for (Family f : BlockChoices.FAMILIES) {
            List<String> ids = new ArrayList<>();
            for (Block b : BlockChoices.candidates(f)) ids.add(id(b));
            allowed.put(f, ids);
        }
        return allowed;
    }

    public static boolean fullConnects(ShapeSettings s) { return fullConnects(s, BlockChoices.CHOICE); }

    public static boolean fullConnects(ShapeSettings s, Map<Family, Block> choice) {
        if (s.paint.on()) {
            Boolean most = Paint.connects(s.paint.palette(), id -> {
                Block b = block(id);
                return b == null || BlockChoices.connects(b);
            });
            if (most != null) return most;
        }
        return BlockChoices.connects(choice.get(Family.FULL));
    }

    public record Resolved(Block[] blocks, BlockState[] full) {
        public Block block(int entry) { return entry < 0 ? null : blocks[entry]; }
    }

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

    public static BlockState state2D(Solver.Result r, int i, int j, int k, int depth, Direction right, Direction forward, Painted painted, Resolved paint) {
        int p = r.at(i, j);
        if (p == Pieces.EMPTY) return null;
        int entry = painted == null ? -1 : painted.at((painted.layers() == 1 ? 0 : k) * r.nx() * r.ny() + j * r.nx() + i);
        if (entry == Painted.OPEN) return null;
        if (entry >= 0 && p == Pieces.FULL && paint.full()[entry] != null) return paint.full()[entry];
        Block block = paint == null ? null : paint.block(entry);
        Settle2D settled = painted == null ? null : painted.settled();
        if (settled != null && Pieces.isConnector(p)) return BlockChoices.connector(p, settled.joints(i, j, k), right, forward, block);
        int above = j + 1 < r.ny() ? r.at(i, j + 1) : Pieces.EMPTY;
        boolean post = !r.floor() && Pieces.FAMILY[p] == Pieces.Family.WALL && r.placedWallPost(i, j, k, depth);
        return BlockChoices.stateFor(p, above, post, right, forward, k, depth, r.floor(), block);
    }

    public static final class States3 {
        private final Resolved paint;
        private final BlockState[] plain;
        private final BlockState[][] states;

        public States3(Resolved paint) {
            this.paint = paint;
            plain = BlockChoices.statesFor3();
            states = new BlockState[paint == null ? 0 : paint.blocks().length][];
        }

        public BlockState get(int piece, int entry) {
            if (entry < 0 || paint == null || paint.blocks()[entry] == null) return plain[piece];
            BlockState[] row = states[entry];
            if (row == null) row = states[entry] = new BlockState[Pieces3.COUNT];
            BlockState s = row[piece];
            if (s == null)
                s = row[piece] = piece == Pieces3.FULL && paint.full()[entry] != null ? paint.full()[entry] : BlockChoices.stateFor3(piece, paint.blocks()[entry]);
            return s;
        }
    }

    public static Direction viewer3(Solve3Painter.Done done) {
        return done.right() < 0 ? Direction.UP : viewer(Direction.from3DDataValue(done.right()), Direction.from3DDataValue(done.up()));
    }

    public static int[] palette3(Painted p, int[] byFamily, Catalog catalog) {
        int families = byFamily.length;
        int[] out = Arrays.copyOf(byFamily, families + p.blocks().size());
        for (int e = 0; e < p.blocks().size(); e++) {
            String id = p.blocks().get(e);
            int b = id.indexOf('[');
            Integer rgb = catalog.colours().get(b < 0 ? id : id.substring(0, b));
            int fam = 0;
            for (long[] c : p.counts()) { if (c[e + 1] > 0) break; fam++; }
            out[families + e] = rgb != null && rgb >= 0 ? rgb : byFamily[Math.min(fam, families - 1)];
        }
        return out;
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
                s = turnFacing(s, p, quarters, viewer);
            }
        }
        return s;
    }

    private static <T extends Comparable<T>> BlockState turnFacing(BlockState s, Property<T> property, int quarters, Direction viewer) {
        Direction facing = Direction.class.cast(s.getValue(property));
        T turned = property.getValueClass().cast(turn(facing, quarters, viewer));
        return property.getPossibleValues().contains(turned) ? s.setValue(property, turned) : s;
    }

    public static Direction viewer(Direction right, Direction up) {
        int x = right.getStepY() * up.getStepZ() - right.getStepZ() * up.getStepY();
        int y = right.getStepZ() * up.getStepX() - right.getStepX() * up.getStepZ();
        int z = right.getStepX() * up.getStepY() - right.getStepY() * up.getStepX();
        return Direction.getNearest(x, y, z, Direction.UP);
    }

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
