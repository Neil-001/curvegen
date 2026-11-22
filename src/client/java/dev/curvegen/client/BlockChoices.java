package dev.curvegen.client;

import static dev.curvegen.client.Lang.tr;

import dev.curvegen.client.chroma.Chroma;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.Pieces;
import dev.curvegen.core.Pieces3;
import dev.curvegen.core.Settle2D;
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

public final class BlockChoices {
    private BlockChoices() {}

    public static final Family[] FAMILIES = {Family.FULL, Family.SLAB, Family.STAIRS, Family.TRAPDOOR, Family.SHELF, Family.FENCE, Family.PANE, Family.WALL,
            Family.CHAIN, Family.ROD};
    public static final Map<Family, Block> CHOICE = new EnumMap<>(Family.class);
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
            case FULL -> tr("curvegen.family.full"); case SLAB -> tr("curvegen.family.slab"); case STAIRS -> tr("curvegen.family.stairs");
            case TRAPDOOR -> tr("curvegen.family.trapdoor"); case SHELF -> tr("curvegen.family.shelf"); case FENCE -> tr("curvegen.family.fence"); case PANE -> tr("curvegen.family.pane"); case WALL -> tr("curvegen.family.wall");
            case CHAIN -> tr("curvegen.family.chain"); case ROD -> tr("curvegen.family.rod"); default -> "";
        };
    }

    private static final Map<Family, List<Block>> CANDIDATES = new EnumMap<>(Family.class);

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
                    && !(b instanceof FallingBlock)
                    && !(b instanceof GameMasterBlock)
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

    public static void autoSelect(int rgb) {
        pickedColor = rgb;
        for (Family f : FAMILIES) {
            Block best = closest(f, rgb);
            if (best != null) CHOICE.put(f, best);
        }
    }

    public static Block closest(Family f, int rgb) {
        if (Chroma.available()) {
            ShapeSettings s = CurveGenClient.SETTINGS;
            Map<Family, List<String>> allowed = PaintBlocks.allowedIds();
            String id = Chroma.matcher(s.is3d() ? s.topColours : s.floor, allowed, (_, _) -> null).closest(rgb, allowed.get(f));
            Block b = id == null ? null : PaintBlocks.block(id);
            if (b != null) return b;
        }
        Block best = null; double bd = Double.MAX_VALUE;
        for (Block b : candidates(f)) {
            double d = ColorIndex.distance(ColorIndex.of(b), rgb);
            if (d < bd) { bd = d; best = b; }
        }
        return best;
    }

    public static boolean fullBlockConnects() { return connects(CHOICE.get(Family.FULL)); }

    public static boolean fullConnects(ShapeSettings s) { return PaintBlocks.fullConnects(s); }

    public static boolean connects(Block full) { return !Block.isExceptionForConnection(full.defaultBlockState()); }

    public static Map<String, String> capture(ShapeSettings s) {
        return PresetData.captureBlocks(s, f -> BuiltInRegistries.BLOCK.getKey(CHOICE.get(f)).toString());
    }

    public static void apply(Map<String, String> blocks, ShapeSettings s, Map<Family, Block> choice) {
        applyChoices(blocks, s, choice);
        s.fullConnects = connects(choice.get(Family.FULL));
    }

    public static Map<String, String> captureChoices(ShapeSettings s, Map<Family, Block> choice) {
        return PresetData.captureBlocks(s, f -> BuiltInRegistries.BLOCK.getKey(choice.get(f)).toString());
    }

    public static void applyChoices(Map<String, String> d, ShapeSettings s, Map<Family, Block> choice) {
        PresetData.applyBlocks(d, s, (f, id) -> choose(choice, f, id));
    }

    private static void choose(Map<Family, Block> choice, Family f, String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) return;
        Block b = BuiltInRegistries.BLOCK.getValue(key);
        if (fits(b, f)) choice.put(f, b);
    }

    public static Block blockFor(int piece) { return CHOICE.get(Pieces.FAMILY[piece]); }


    public static BlockState stateFor(int piece, int above, boolean post, Direction right, Direction forward, int k, int depth, boolean floor, Block block) {
        if (block == null) block = blockFor(piece);
        if (floor) return floorState(block, piece, right, forward, k, depth);
        BlockState s = block.defaultBlockState();
        Direction left = right.getOpposite();
        switch (piece) {
            case Pieces.CHAIN_H, Pieces.CHAIN_V, Pieces.ROD_U, Pieces.ROD_D, Pieces.ROD_L, Pieces.ROD_R -> s = lineState(s, piece, right, Direction.UP);
            case Pieces.SLAB_B -> s = with(s, SlabBlock.TYPE, SlabType.BOTTOM);
            case Pieces.SLAB_T -> s = with(s, SlabBlock.TYPE, SlabType.TOP);
            case Pieces.ST_UR -> s = with(with(s, StairBlock.FACING, right), StairBlock.HALF, Half.BOTTOM);
            case Pieces.ST_UL -> s = with(with(s, StairBlock.FACING, left), StairBlock.HALF, Half.BOTTOM);
            case Pieces.ST_DR -> s = with(with(s, StairBlock.FACING, right), StairBlock.HALF, Half.TOP);
            case Pieces.ST_DL -> s = with(with(s, StairBlock.FACING, left), StairBlock.HALF, Half.TOP);
            case Pieces.TD_B -> s = with(with(s, TrapDoorBlock.OPEN, false), TrapDoorBlock.HALF, Half.BOTTOM);
            case Pieces.TD_T -> s = with(with(s, TrapDoorBlock.OPEN, false), TrapDoorBlock.HALF, Half.TOP);
            case Pieces.TD_L -> s = with(with(s, TrapDoorBlock.OPEN, true), TrapDoorBlock.FACING, right);
            case Pieces.TD_R -> s = with(with(s, TrapDoorBlock.OPEN, true), TrapDoorBlock.FACING, left);
            case Pieces.SH_L -> s = with(s, ShelfBlock.FACING, right);
            case Pieces.SH_R -> s = with(s, ShelfBlock.FACING, left);
            default -> {
                if (Pieces.FAMILY[piece] == Family.WALL) {
                    s = wallState(s, piece, above, post, right, forward, k, depth);
                } else if (Pieces.isConnector(piece)) {
                    s = with(s, side(left), Pieces.connectsLeft(piece));
                    s = with(s, side(right), Pieces.connectsRight(piece));
                    s = with(s, side(forward), k < depth - 1);
                    s = with(s, side(forward.getOpposite()), k > 0);
                }
            }
        }
        s = with(s, BlockStateProperties.WATERLOGGED, false);
        if (s.hasProperty(BlockStateProperties.PERSISTENT)) s = s.setValue(BlockStateProperties.PERSISTENT, true);
        return s;
    }

    public static BlockState connector(int piece, int joints, Direction right, Direction forward, Block block) {
        if (block == null) block = blockFor(piece);
        BlockState s = block.defaultBlockState();
        Direction[] dirs = {right.getOpposite(), right, forward, forward.getOpposite()};
        int[] sides = {Settle2D.LEFT, Settle2D.RIGHT, Settle2D.FRONT, Settle2D.BACK};
        boolean wall = Pieces.FAMILY[piece] == Family.WALL;
        for (int d = 0; d < 4; d++) {
            int v = Settle2D.side(joints, sides[d]);
            s = wall ? with(s, wallSide(dirs[d]), shape(v)) : with(s, side(dirs[d]), v > 0);
        }
        if (wall) s = with(s, BlockStateProperties.UP, (joints & Settle2D.POST) != 0);
        return finish(s);
    }

    private static BlockState floorState(Block block, int piece, Direction right, Direction forward, int k, int depth) {
        BlockState s = block.defaultBlockState();
        Direction left = right.getOpposite(), back = forward.getOpposite();
        switch (piece) {
            case Pieces.CHAIN_H, Pieces.CHAIN_V, Pieces.ROD_U, Pieces.ROD_D, Pieces.ROD_L, Pieces.ROD_R -> s = lineState(s, piece, right, forward);
            case Pieces.TD_L -> s = with(with(s, TrapDoorBlock.OPEN, true), TrapDoorBlock.FACING, right);
            case Pieces.TD_R -> s = with(with(s, TrapDoorBlock.OPEN, true), TrapDoorBlock.FACING, left);
            case Pieces.F_TD_U -> s = with(with(s, TrapDoorBlock.OPEN, true), TrapDoorBlock.FACING, back);
            case Pieces.F_TD_D -> s = with(with(s, TrapDoorBlock.OPEN, true), TrapDoorBlock.FACING, forward);
            case Pieces.F_SH_L -> s = with(s, ShelfBlock.FACING, right);
            case Pieces.F_SH_R -> s = with(s, ShelfBlock.FACING, left);
            case Pieces.F_SH_U -> s = with(s, ShelfBlock.FACING, back);
            case Pieces.F_SH_D -> s = with(s, ShelfBlock.FACING, forward);
            default -> {
                if (Pieces.isFloorConnector(piece)) {
                    int bits = Pieces.floorBits(piece);
                    Direction[] dirs = {left, right, forward, back};
                    if (Pieces.FAMILY[piece] == Family.WALL) {
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

    private static BlockState wallState(BlockState s, int piece, int above, boolean post, Direction right, Direction forward, int k, int depth) {
        WallSide depthShape = Pieces.wallCovered(piece) && Pieces.spansDepth(above) ? WallSide.TALL : WallSide.LOW;
        s = with(s, wallSide(right.getOpposite()), shape(Pieces.wallLeft(piece)));
        s = with(s, wallSide(right), shape(Pieces.wallRight(piece)));
        s = with(s, wallSide(forward), k < depth - 1 ? depthShape : WallSide.NONE);
        s = with(s, wallSide(forward.getOpposite()), k > 0 ? depthShape : WallSide.NONE);
        return with(s, BlockStateProperties.UP, post);
    }


    public static BlockState stateFor3(int piece) { return stateFor3(piece, null); }

    public static BlockState stateFor3(int piece, Block block) {
        if (piece == Pieces3.AIR) return Blocks.AIR.defaultBlockState();
        Block b = block != null ? block : CHOICE.get(Pieces3.FAMILY[piece]);
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

    public static BlockState[] statesFor3() {
        BlockState[] out = new BlockState[Pieces3.COUNT];
        for (int p = 0; p < Pieces3.COUNT; p++) out[p] = stateFor3(p);
        return out;
    }

    public static BlockState finish(BlockState s) {
        s = with(s, BlockStateProperties.WATERLOGGED, false);
        return s.hasProperty(BlockStateProperties.PERSISTENT) ? s.setValue(BlockStateProperties.PERSISTENT, true) : s;
    }

    private static <T extends Comparable<T>> BlockState withNamed(BlockState s, Property<T> p, String value) {
        return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
    }

    private static WallSide shape(int v) { return v == 0 ? WallSide.NONE : v == 1 ? WallSide.LOW : WallSide.TALL; }

    private static EnumProperty<WallSide> wallSide(Direction d) {
        return switch (d) {
            case NORTH -> BlockStateProperties.NORTH_WALL; case SOUTH -> BlockStateProperties.SOUTH_WALL;
            case EAST -> BlockStateProperties.EAST_WALL; case WEST -> BlockStateProperties.WEST_WALL;
            default -> throw new IllegalArgumentException(tr("curvegen.diagnostic.not_horizontal", d));
        };
    }

    private static BooleanProperty side(Direction d) {
        return switch (d) {
            case NORTH -> BlockStateProperties.NORTH; case SOUTH -> BlockStateProperties.SOUTH;
            case EAST -> BlockStateProperties.EAST; case WEST -> BlockStateProperties.WEST;
            default -> throw new IllegalArgumentException(tr("curvegen.diagnostic.not_horizontal", d));
        };
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, T v) {
        return s.hasProperty(p) ? s.setValue(p, v) : s;
    }
}
