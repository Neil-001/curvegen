package dev.curvegen.client;

import dev.curvegen.core.Pieces;
import dev.curvegen.core.Pieces.Family;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.OperatorBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WallShape;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The block chosen for each piece family, and how a piece becomes a real BlockState. */
public final class BlockChoices {
    private BlockChoices() {}

    public static final Family[] FAMILIES = {Family.FULL, Family.SLAB, Family.STAIRS, Family.TRAPDOOR, Family.FENCE, Family.PANE, Family.WALL};
    public static final Map<Family, Block> CHOICE = new EnumMap<>(Family.class);
    /** Colour last picked with the colour picker, or -1. */
    public static int pickedColor = -1;

    static {
        CHOICE.put(Family.FULL, Blocks.STONE_BRICKS);
        CHOICE.put(Family.SLAB, Blocks.STONE_BRICK_SLAB);
        CHOICE.put(Family.STAIRS, Blocks.STONE_BRICK_STAIRS);
        CHOICE.put(Family.TRAPDOOR, Blocks.SPRUCE_TRAPDOOR);
        CHOICE.put(Family.FENCE, Blocks.SPRUCE_FENCE);
        CHOICE.put(Family.PANE, Blocks.GLASS_PANE);
        CHOICE.put(Family.WALL, Blocks.STONE_BRICK_WALL);
    }

    public static String familyName(Family f) {
        return switch (f) {
            case FULL -> "Full blocks"; case SLAB -> "Slabs"; case STAIRS -> "Stairs";
            case TRAPDOOR -> "Trapdoors"; case FENCE -> "Fences"; case PANE -> "Panes"; case WALL -> "Walls"; default -> "";
        };
    }

    private static final Map<Family, List<Block>> CANDIDATES = new EnumMap<>(Family.class);

    /** Every registered block that can play this role, sorted by name. */
    public static List<Block> candidates(Family f) {
        return CANDIDATES.computeIfAbsent(f, fam -> {
            List<Block> out = new ArrayList<>();
            for (Block b : Registries.BLOCK) if (fits(b, fam)) out.add(b);
            out.sort(Comparator.comparing(b -> b.getName().getString()));
            return out;
        });
    }

    public static boolean fits(Block b, Family f) {
        if (b.asItem() == Items.AIR) return false;
        return switch (f) {
            case FULL -> b.getDefaultState().isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)
                    && !(b instanceof FallingBlock)            // sand, gravel, concrete powder would fall
                    && !(b instanceof OperatorBlock)           // command, structure and jigsaw blocks
                    && b != Blocks.BARRIER && b != Blocks.BEDROCK && !(b instanceof SlabBlock);
            case SLAB -> b instanceof SlabBlock;
            case STAIRS -> b instanceof StairsBlock;
            case TRAPDOOR -> b instanceof TrapdoorBlock;
            case FENCE -> b instanceof FenceBlock;
            case PANE -> b instanceof PaneBlock;
            case WALL -> b instanceof WallBlock;
            default -> false;
        };
    }

    /** Picks, for every family, the block whose average texture colour is closest to rgb. */
    public static void autoSelect(int rgb) {
        pickedColor = rgb;
        for (Family f : FAMILIES) {
            Block best = closest(f, rgb);
            if (best != null) CHOICE.put(f, best);
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
    public static boolean fullBlockConnects() {
        return !Block.cannotConnect(CHOICE.get(Family.FULL).getDefaultState());
    }

    public static Block blockFor(int piece) { return CHOICE.get(Pieces.FAMILY[piece]); }

    // ---------- piece → BlockState ----------

    /**
     * @param right world direction of the drawing's +x
     * @param forward world direction the shape is extruded in (depth)
     * @param k depth layer, 0 … depth-1
     */
    public static BlockState stateFor(int piece, Direction right, Direction forward, int k, int depth) {
        BlockState s = blockFor(piece).getDefaultState();
        Direction left = right.getOpposite();
        switch (piece) {
            case Pieces.SLAB_B -> s = with(s, SlabBlock.TYPE, SlabType.BOTTOM);
            case Pieces.SLAB_T -> s = with(s, SlabBlock.TYPE, SlabType.TOP);
            // A stair's FACING is the side its tall back is on.
            case Pieces.ST_UR -> s = with(with(s, StairsBlock.FACING, right), StairsBlock.HALF, BlockHalf.BOTTOM);
            case Pieces.ST_UL -> s = with(with(s, StairsBlock.FACING, left), StairsBlock.HALF, BlockHalf.BOTTOM);
            case Pieces.ST_DR -> s = with(with(s, StairsBlock.FACING, right), StairsBlock.HALF, BlockHalf.TOP);
            case Pieces.ST_DL -> s = with(with(s, StairsBlock.FACING, left), StairsBlock.HALF, BlockHalf.TOP);
            case Pieces.TD_B -> s = with(with(s, TrapdoorBlock.OPEN, false), TrapdoorBlock.HALF, BlockHalf.BOTTOM);
            case Pieces.TD_T -> s = with(with(s, TrapdoorBlock.OPEN, false), TrapdoorBlock.HALF, BlockHalf.TOP);
            // An open trapdoor lies against the side opposite its FACING.
            case Pieces.TD_L -> s = with(with(s, TrapdoorBlock.OPEN, true), TrapdoorBlock.FACING, right);
            case Pieces.TD_R -> s = with(with(s, TrapdoorBlock.OPEN, true), TrapdoorBlock.FACING, left);
            default -> {
                if (Pieces.FAMILY[piece] == Family.WALL) {
                    s = wallState(s, piece, right, forward, k, depth);
                } else if (Pieces.isConnector(piece)) {
                    s = with(s, side(left), Pieces.connectsLeft(piece));
                    s = with(s, side(right), Pieces.connectsRight(piece));
                    s = with(s, side(forward), k < depth - 1);
                    s = with(s, side(forward.getOpposite()), k > 0);
                }
            }
        }
        s = with(s, Properties.WATERLOGGED, false);
        if (s.contains(Properties.PERSISTENT)) s = s.with(Properties.PERSISTENT, true);   // stop leaves decaying
        return s;
    }

    /**
     * Wall sides come from the solver (left/right: none, low or tall). Walls in the layers in front and
     * behind (depth) connect too, and the post follows the game's rule for the whole set of four sides.
     */
    private static BlockState wallState(BlockState s, int piece, Direction right, Direction forward, int k, int depth) {
        int l = Pieces.wallLeft(piece), r = Pieces.wallRight(piece);
        boolean covered = Pieces.wallCovered(piece), front = k < depth - 1, back = k > 0;
        WallShape depthShape = covered ? WallShape.TALL : WallShape.LOW;
        s = with(s, wallSide(right.getOpposite()), shape(l));
        s = with(s, wallSide(right), shape(r));
        s = with(s, wallSide(forward), front ? depthShape : WallShape.NONE);
        s = with(s, wallSide(forward.getOpposite()), back ? depthShape : WallShape.NONE);
        boolean post;
        if (!front && !back && l == 0 && r == 0) post = true;                       // on its own
        else if (front != back || (l == 0) != (r == 0)) post = true;                // a corner or an end
        else if ((front && back && depthShape == WallShape.TALL) || (l == 2 && r == 2)) post = false; // straight and tall
        else post = covered;                                                        // straight: post if something sits on it
        return with(s, Properties.UP, post);
    }

    private static WallShape shape(int v) { return v == 0 ? WallShape.NONE : v == 1 ? WallShape.LOW : WallShape.TALL; }

    private static EnumProperty<WallShape> wallSide(Direction d) {
        return switch (d) {
            case NORTH -> Properties.NORTH_WALL_SHAPE; case SOUTH -> Properties.SOUTH_WALL_SHAPE;
            case EAST -> Properties.EAST_WALL_SHAPE; case WEST -> Properties.WEST_WALL_SHAPE;
            default -> throw new IllegalArgumentException("not horizontal: " + d);
        };
    }

    private static BooleanProperty side(Direction d) {
        return switch (d) {
            case NORTH -> Properties.NORTH; case SOUTH -> Properties.SOUTH;
            case EAST -> Properties.EAST; case WEST -> Properties.WEST;
            default -> throw new IllegalArgumentException("not horizontal: " + d);
        };
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, T v) {
        return s.contains(p) ? s.with(p, v) : s;
    }
}
