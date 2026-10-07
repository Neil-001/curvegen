package dev.curvegen.client;

import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.Pieces;
import dev.curvegen.core.Pieces3;
import dev.curvegen.core.PresetData;
import dev.curvegen.core.ShapeSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;

/** The block chosen for each piece family, and how a piece becomes a real BlockState. */
public final class BlockChoices {
    private BlockChoices() {}

    public static final Family[] FAMILIES = {Family.FULL, Family.SLAB, Family.STAIRS, Family.TRAPDOOR, Family.SHELF, Family.FENCE, Family.PANE, Family.WALL,
            Family.CHAIN, Family.ROD};
    public static final Map<Family, Block> CHOICE = new EnumMap<>(Family.class);
    /** Colour last picked with the colour picker, or -1. */
    public static int pickedColor = -1;

    static {
        CHOICE.put(Family.FULL, Blocks.STONE_BRICKS);
        CHOICE.put(Family.SLAB, Blocks.STONE_BRICK_SLAB);
        CHOICE.put(Family.STAIRS, Blocks.STONE_BRICK_STAIRS);
        CHOICE.put(Family.TRAPDOOR, Blocks.SPRUCE_TRAPDOOR);
        CHOICE.put(Family.SHELF, Blocks.SPRUCE_SHELF);
        CHOICE.put(Family.FENCE, Blocks.SPRUCE_FENCE);
        CHOICE.put(Family.PANE, Blocks.GLASS_PANE);
        CHOICE.put(Family.WALL, Blocks.STONE_BRICK_WALL);
        CHOICE.put(Family.CHAIN, Blocks.IRON_CHAIN);
        CHOICE.put(Family.ROD, Blocks.END_ROD);
    }

    public static String familyName(Family f) {
        return switch (f) {
            case FULL -> "Full blocks"; case SLAB -> "Slabs"; case STAIRS -> "Stairs";
            case TRAPDOOR -> "Trapdoors"; case SHELF -> "Shelves"; case FENCE -> "Fences"; case PANE -> "Panes"; case WALL -> "Walls";
            case CHAIN -> "Chains"; case ROD -> "End rods"; default -> "";
        };
    }

    private static final Map<Family, List<Block>> CANDIDATES = new EnumMap<>(Family.class);

    /** Every registered block that can play this role, sorted by name. */
    public static List<Block> candidates(Family f) {
        return CANDIDATES.computeIfAbsent(f, fam -> {
            List<Block> out = new ArrayList<>();
            for (Block b : BuiltInRegistries.BLOCK) if (fits(b, fam)) out.add(b);
            out.sort(Comparator.comparing(b -> b.getName().getString()));
            return out;
        });
    }

    public static boolean fits(Block b, Family f) {
        if (b.asItem() == Items.AIR) return false;
        return switch (f) {
            case FULL -> b.defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    && !(b instanceof FallingBlock)            // sand, gravel, concrete powder would fall
                    && !(b instanceof GameMasterBlock)           // command, structure and jigsaw blocks
                    && b != Blocks.BARRIER && b != Blocks.BEDROCK && !(b instanceof SlabBlock);
            case SLAB -> b instanceof SlabBlock;
            case STAIRS -> b instanceof StairBlock;
            case TRAPDOOR -> b instanceof TrapDoorBlock;
            case SHELF -> b instanceof ShelfBlock;
            case FENCE -> b instanceof FenceBlock;
            case PANE -> b instanceof IronBarsBlock;
            case WALL -> b instanceof WallBlock;
            case CHAIN -> b instanceof ChainBlock;
            case ROD -> b instanceof EndRodBlock;
            default -> false;
        };
    }

    /** Picks, for every family, the block whose average texture colour is closest to rgb. */
    public static void autoSelect(int rgb) {
        pickedColor = rgb;
        for (Family f : FAMILIES) {
            Block best = closest(f, rgb);
            if (best != null) CHOICE.put(f, best);   // closeness uses the side or top face, to match the build
        }
    }

    public static Block closest(Family f, int rgb) {
        Block best = null; double bd = Double.MAX_VALUE;
        for (Block b : candidates(f)) {
            double d = ColorIndex.distance(ColorIndex.of(b), rgb);
            if (d < bd) { bd = d; best = b; }
        }
        return best;
    }

    /** Fences, panes and walls refuse to attach to some full blocks (leaves, pumpkins, melons, shulker boxes…). */
    public static boolean fullBlockConnects() { return connects(CHOICE.get(Family.FULL)); }

    public static boolean connects(Block full) { return !Block.isExceptionForConnection(full.defaultBlockState()); }

    /** The current block choices as preset text. */
    public static Map<String, String> capture(ShapeSettings s) {
        return PresetData.captureBlocks(s, f -> BuiltInRegistries.BLOCK.getKey(CHOICE.get(f)).toString());
    }

    /**
     * Applies a preset's block choices to s and choice. Blocks that don't exist (say, from a
     * mod that isn't installed) or don't fit their piece type keep the current choice.
     */
    public static void apply(Map<String, String> blocks, ShapeSettings s, Map<Family, Block> choice) {
        PresetData.applyBlocks(blocks, s, (f, id) -> {
            Identifier key = Identifier.tryParse(id);
            if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) return;
            Block b = BuiltInRegistries.BLOCK.getValue(key);
            if (fits(b, f)) choice.put(f, b);
        });
        s.fullConnects = connects(choice.get(Family.FULL));
    }

    public static Block blockFor(int piece) { return CHOICE.get(Pieces.FAMILY[piece]); }

    // ---------- piece → BlockState ----------

    /**
     * @param above the piece in the cell above
     * @param right world direction of the drawing's +x
     * @param forward upright: the direction the shape is extruded in (depth); flat: the direction of the drawing's +y
     * @param k layer, 0 … depth-1 (upright: front to back; flat: bottom to top)
     * @param floor whether the shape lies flat, drawn from above
     */
    public static BlockState stateFor(int piece, int above, Direction right, Direction forward, int k, int depth, boolean floor) {
        if (floor) return floorState(piece, right, forward, k, depth);
        BlockState s = blockFor(piece).defaultBlockState();
        Direction left = right.getOpposite();
        switch (piece) {
            case Pieces.CHAIN_H, Pieces.CHAIN_V, Pieces.ROD_U, Pieces.ROD_D, Pieces.ROD_L, Pieces.ROD_R -> s = lineState(s, piece, right, Direction.UP);
            case Pieces.SLAB_B -> s = with(s, SlabBlock.TYPE, SlabType.BOTTOM);
            case Pieces.SLAB_T -> s = with(s, SlabBlock.TYPE, SlabType.TOP);
            // A stair's FACING is the side its tall back is on.
            case Pieces.ST_UR -> s = with(with(s, StairBlock.FACING, right), StairBlock.HALF, Half.BOTTOM);
            case Pieces.ST_UL -> s = with(with(s, StairBlock.FACING, left), StairBlock.HALF, Half.BOTTOM);
            case Pieces.ST_DR -> s = with(with(s, StairBlock.FACING, right), StairBlock.HALF, Half.TOP);
            case Pieces.ST_DL -> s = with(with(s, StairBlock.FACING, left), StairBlock.HALF, Half.TOP);
            case Pieces.TD_B -> s = with(with(s, TrapDoorBlock.OPEN, false), TrapDoorBlock.HALF, Half.BOTTOM);
            case Pieces.TD_T -> s = with(with(s, TrapDoorBlock.OPEN, false), TrapDoorBlock.HALF, Half.TOP);
            // An open trapdoor lies against the side opposite its FACING.
            case Pieces.TD_L -> s = with(with(s, TrapDoorBlock.OPEN, true), TrapDoorBlock.FACING, right);
            case Pieces.TD_R -> s = with(with(s, TrapDoorBlock.OPEN, true), TrapDoorBlock.FACING, left);
            // A shelf's panel is on the side opposite its FACING, like an open trapdoor's.
            case Pieces.SH_L -> s = with(s, ShelfBlock.FACING, right);
            case Pieces.SH_R -> s = with(s, ShelfBlock.FACING, left);
            default -> {
                if (Pieces.FAMILY[piece] == Family.WALL) {
                    s = wallState(s, piece, above, right, forward, k, depth);
                } else if (Pieces.isConnector(piece)) {
                    s = with(s, side(left), Pieces.connectsLeft(piece));
                    s = with(s, side(right), Pieces.connectsRight(piece));
                    s = with(s, side(forward), k < depth - 1);
                    s = with(s, side(forward.getOpposite()), k > 0);
                }
            }
        }
        s = with(s, BlockStateProperties.WATERLOGGED, false);
        if (s.hasProperty(BlockStateProperties.PERSISTENT)) s = s.setValue(BlockStateProperties.PERSISTENT, true);   // stop leaves decaying
        return s;
    }

    /** A flat shape: the drawing is the view from above and layers stack upwards. */
    private static BlockState floorState(int piece, Direction right, Direction forward, int k, int depth) {
        BlockState s = blockFor(piece).defaultBlockState();
        Direction left = right.getOpposite(), back = forward.getOpposite();
        switch (piece) {
            case Pieces.CHAIN_H, Pieces.CHAIN_V, Pieces.ROD_U, Pieces.ROD_D, Pieces.ROD_L, Pieces.ROD_R -> s = lineState(s, piece, right, forward);
            // An open trapdoor lies against the side opposite its FACING.
            case Pieces.TD_L -> s = with(with(s, TrapDoorBlock.OPEN, true), TrapDoorBlock.FACING, right);
            case Pieces.TD_R -> s = with(with(s, TrapDoorBlock.OPEN, true), TrapDoorBlock.FACING, left);
            case Pieces.F_TD_U -> s = with(with(s, TrapDoorBlock.OPEN, true), TrapDoorBlock.FACING, back);
            case Pieces.F_TD_D -> s = with(with(s, TrapDoorBlock.OPEN, true), TrapDoorBlock.FACING, forward);
            // A shelf's panel is on the side opposite its FACING too.
            case Pieces.F_SH_L -> s = with(s, ShelfBlock.FACING, right);
            case Pieces.F_SH_R -> s = with(s, ShelfBlock.FACING, left);
            case Pieces.F_SH_U -> s = with(s, ShelfBlock.FACING, back);
            case Pieces.F_SH_D -> s = with(s, ShelfBlock.FACING, forward);
            default -> {
                if (Pieces.isFloorConnector(piece)) {
                    int bits = Pieces.floorBits(piece);
                    Direction[] dirs = {left, right, forward, back};
                    if (Pieces.FAMILY[piece] == Family.WALL) {
                        // Every layer but the top has the same wall above it, which makes its sides tall.
                        WallSide sh = k < depth - 1 ? WallSide.TALL : WallSide.LOW;
                        for (int d = 0; d < 4; d++) s = with(s, wallSide(dirs[d]), (bits & (1 << d)) != 0 ? sh : WallSide.NONE);
                        s = with(s, BlockStateProperties.UP, Pieces.floorWallPost(bits));
                    } else {
                        for (int d = 0; d < 4; d++) s = with(s, side(dirs[d]), (bits & (1 << d)) != 0);
                    }
                }
            }
        }
        s = with(s, BlockStateProperties.WATERLOGGED, false);
        if (s.hasProperty(BlockStateProperties.PERSISTENT)) s = s.setValue(BlockStateProperties.PERSISTENT, true);
        return s;
    }

    /** A chain along, or an end rod pointing along, the drawing's +x (right) or +y (up). A rod's FACING is the way it points. */
    private static BlockState lineState(BlockState s, int piece, Direction right, Direction up) {
        return switch (piece) {
            case Pieces.CHAIN_H -> with(s, BlockStateProperties.AXIS, right.getAxis());
            case Pieces.CHAIN_V -> with(s, BlockStateProperties.AXIS, up.getAxis());
            case Pieces.ROD_U -> with(s, BlockStateProperties.FACING, up);
            case Pieces.ROD_D -> with(s, BlockStateProperties.FACING, up.getOpposite());
            case Pieces.ROD_L -> with(s, BlockStateProperties.FACING, right.getOpposite());
            default -> with(s, BlockStateProperties.FACING, right);
        };
    }

    /**
     * Wall sides come from the solver (left/right: none, low or tall). Walls in the layers in front and
     * behind (depth) connect too, tall when the piece above covers them, and the post follows the game's rule for the whole set of four sides.
     */
    private static BlockState wallState(BlockState s, int piece, int above, Direction right, Direction forward, int k, int depth) {
        int l = Pieces.wallLeft(piece), r = Pieces.wallRight(piece);
        boolean covered = Pieces.wallCovered(piece), front = k < depth - 1, back = k > 0;
        WallSide depthShape = covered && Pieces.spansDepth(above) ? WallSide.TALL : WallSide.LOW;
        s = with(s, wallSide(right.getOpposite()), shape(l));
        s = with(s, wallSide(right), shape(r));
        s = with(s, wallSide(forward), front ? depthShape : WallSide.NONE);
        s = with(s, wallSide(forward.getOpposite()), back ? depthShape : WallSide.NONE);
        boolean post;
        if (!front && !back && l == 0 && r == 0) post = true;                       // on its own
        else if (front != back || (l == 0) != (r == 0)) post = true;                // a corner or an end
        else if ((front && back && depthShape == WallSide.TALL) || (l == 2 && r == 2)) post = false; // straight and tall
        else post = covered;                                                        // straight: post if something sits on it
        return with(s, BlockStateProperties.UP, post);
    }

    // ---------- 3D piece → BlockState ----------

    /**
     * The block state for one of the volumetric solver's states: the block chosen for its piece type, with every
     * property {@link Pieces3#props} names. The solver has already settled connections, corner shapes and wall
     * heights by the game's rules, so nothing here depends on the neighbours.
     */
    public static BlockState stateFor3(int piece) {
        if (piece == Pieces3.AIR) return Blocks.AIR.defaultBlockState();
        Block b = CHOICE.get(Pieces3.FAMILY[piece]);
        BlockState s = b.defaultBlockState();
        String props = Pieces3.props(piece);
        if (!props.isEmpty())
            for (String kv : props.split(",")) {
                int eq = kv.indexOf('=');
                Property<?> p = b.getStateDefinition().getProperty(kv.substring(0, eq));
                if (p != null) s = withNamed(s, p, kv.substring(eq + 1));
            }
        s = with(s, BlockStateProperties.WATERLOGGED, false);
        if (s.hasProperty(BlockStateProperties.PERSISTENT)) s = s.setValue(BlockStateProperties.PERSISTENT, true);
        return s;
    }

    /** Every 3D state's block state, indexed by state, for the current block choices. */
    public static BlockState[] statesFor3() {
        BlockState[] out = new BlockState[Pieces3.COUNT];
        for (int p = 0; p < Pieces3.COUNT; p++) out[p] = stateFor3(p);
        return out;
    }

    private static <T extends Comparable<T>> BlockState withNamed(BlockState s, Property<T> p, String value) {
        return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
    }

    private static WallSide shape(int v) { return v == 0 ? WallSide.NONE : v == 1 ? WallSide.LOW : WallSide.TALL; }

    private static EnumProperty<WallSide> wallSide(Direction d) {
        return switch (d) {
            case NORTH -> BlockStateProperties.NORTH_WALL; case SOUTH -> BlockStateProperties.SOUTH_WALL;
            case EAST -> BlockStateProperties.EAST_WALL; case WEST -> BlockStateProperties.WEST_WALL;
            default -> throw new IllegalArgumentException("not horizontal: " + d);
        };
    }

    private static BooleanProperty side(Direction d) {
        return switch (d) {
            case NORTH -> BlockStateProperties.NORTH; case SOUTH -> BlockStateProperties.SOUTH;
            case EAST -> BlockStateProperties.EAST; case WEST -> BlockStateProperties.WEST;
            default -> throw new IllegalArgumentException("not horizontal: " + d);
        };
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, T v) {
        return s.hasProperty(p) ? s.setValue(p, v) : s;
    }
}
