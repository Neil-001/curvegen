package dev.curvegen.game;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.PaintBlocks;
import dev.curvegen.core.paint.Ladder;
import dev.curvegen.core.paint.Painted;
import dev.curvegen.core.Pieces;
import dev.curvegen.core.Settle2D;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.Solver;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks 2D shapes with holes cut through them against the game itself, as {@link GameRules3Test} does for 3D
 * shapes: once a pattern's holes are out, no fence, pane, wall or stair left changes when the game updates it. The
 * states come from the client's own mapping, {@code PaintBlocks.state2D}, so this covers what gets placed.
 */
class GameRules2Test {
    @BeforeAll
    static void loadBlocks() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (Block wall : new Block[]{Blocks.COBBLESTONE_WALL, Blocks.ANDESITE_WALL, Blocks.BRICK_WALL}) tag(wall, List.of(BlockTags.WALLS));
        tag(Blocks.OAK_FENCE, List.of(BlockTags.FENCES, BlockTags.WOODEN_FENCES));
        tag(Blocks.OAK_LEAVES, List.of(BlockTags.LEAVES));
        BlockChoices.CHOICE.putAll(Map.of(Pieces.Family.SLAB, Blocks.STONE_SLAB, Pieces.Family.STAIRS, Blocks.STONE_STAIRS,
                Pieces.Family.TRAPDOOR, Blocks.OAK_TRAPDOOR, Pieces.Family.SHELF, Blocks.OAK_SHELF, Pieces.Family.FENCE, Blocks.OAK_FENCE,
                Pieces.Family.PANE, Blocks.GLASS_PANE, Pieces.Family.WALL, Blocks.COBBLESTONE_WALL, Pieces.Family.CHAIN, Blocks.IRON_CHAIN,
                Pieces.Family.ROD, Blocks.END_ROD));
    }

    @SuppressWarnings("deprecation")
    private static void tag(Block b, List<TagKey<Block>> tags) throws ReflectiveOperationException {
        Method bind = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
        bind.setAccessible(true);
        bind.invoke(b.builtInRegistryHolder(), tags);
    }

    /** Piece sets: everything, fences beside full blocks, panes and walls, walls with slabs and stairs. */
    private static final String[] SETS = {"all", "fence", "pane wall", "wall slab stair"};

    static List<ShapeSettings> shapes() { return shapes(SETS, new int[]{1, 3}, false); }

    /** Every set at every depth, upright and flat, beside full blocks that connect and ones that don't. */
    static List<ShapeSettings> shapes(String[] sets, int[] depths, boolean chains) {
        List<ShapeSettings> out = new ArrayList<>();
        for (String set : sets)
            for (int k = 0; k < 2 * depths.length * 2; k++) {   // flat or not fastest, then depth, then fullConnects
                ShapeSettings base = new ShapeSettings();
                if (!set.equals("all")) {
                    base.slab = set.contains("slab"); base.stair = set.contains("stair"); base.trap = base.shelf = false;
                    base.fence = set.contains("fence"); base.pane = set.contains("pane"); base.wall = set.contains("wall");
                }
                base.chain = base.rod = chains && set.equals("all");
                base.floor = (k & 1) != 0;
                base.depth = depths[k / 2 % depths.length];
                base.fullConnects = k < 2 * depths.length;
                for (ShapeSettings.EllipseMode m : ShapeSettings.EllipseMode.values())
                    for (int w : new int[]{7, 19, 31})
                        for (int h : new int[]{5, 13}) {
                            ShapeSettings c = base.copy();
                            c.eMode = m; c.eW = w; c.eH = h; c.eT = 1.5;
                            out.add(c);
                        }
                for (String eq : new String[]{"y=2sin(x)", "y = 9 - x^2/4", "x^2+y^2=16"})
                    for (ShapeSettings.EqMode qm : ShapeSettings.EqMode.values()) {
                        ShapeSettings c = base.copy();
                        c.gen = ShapeSettings.Gen.EQUATION; c.src = eq; c.qMode = qm; c.qLW = 1.3;
                        out.add(c);
                    }
            }
        return out;
    }

    /**
     * Holes in about one block in four: through every layer alike when {@code perLayer} is false, as a pattern on a
     * shape's face makes them, or block by block, as one on its edge does.
     */
    static BitSet holes(Solver.Result r, int depth, boolean perLayer, long seed) {
        Random rnd = new Random(seed);
        int n = r.nx() * r.ny();
        BitSet gone = new BitSet(depth * n);
        for (int c = 0; c < n; c++) {
            boolean column = rnd.nextInt(4) == 0;
            for (int k = 0; k < depth; k++) if (perLayer ? rnd.nextInt(4) == 0 : column) gone.set(k * n + c);
        }
        return gone;
    }

    /** A painted shape that paints nothing and leaves {@code gone} open, settled as the painter settles it. */
    static Painted open(Solver.Result r, int depth, BitSet gone, boolean fullConnects) {
        int n = r.nx() * r.ny();
        int[] slots = new int[depth * n];
        Arrays.fill(slots, -1);
        long open = 0;
        for (int b = gone.nextSetBit(0); b >= 0; b = gone.nextSetBit(b + 1)) {
            slots[b] = Painted.OPEN;
            if (r.at(b % n % r.nx(), b % n / r.nx()) != Pieces.EMPTY) open++;
        }
        int f = Pieces.Family.values().length, w = Ladder.Why.values().length;
        return new Painted(slots, depth, List.of(), new int[0], new long[f][w], new long[f][1], open, Settle2D.of(r, depth, gone, fullConnects, () -> false));
    }

    /** The blocks as the client places them, with the drawing's x east: upright, layers run south; flat, its y runs north. */
    static Map<BlockPos, BlockState> world(Solver.Result r, int depth, Painted painted) { return world(r, depth, painted, null); }

    /** The same painted with {@code paint}'s blocks, which may be null for the Blocks tab's. */
    static Map<BlockPos, BlockState> world(Solver.Result r, int depth, Painted painted, PaintBlocks.Resolved paint) {
        Map<BlockPos, BlockState> w = new HashMap<>();
        boolean floor = r.floor();
        Direction forward = floor ? Direction.NORTH : Direction.SOUTH;
        for (int j = 0; j < r.ny(); j++)
            for (int i = 0; i < r.nx(); i++)
                for (int k = 0; k < depth; k++) {
                    BlockState st = PaintBlocks.state2D(r, i, j, k, depth, Direction.EAST, forward, painted, paint);
                    if (st != null) w.put(floor ? new BlockPos(i, k, -j) : new BlockPos(i, j, k), st);
                }
        return w;
    }

    static LevelReader level(Map<BlockPos, BlockState> w) {
        InvocationHandler h = (proxy, method, args) -> {
            if (method.getName().equals("getBlockState") && args.length == 1 && args[0] instanceof BlockPos p)
                return w.getOrDefault(p, Blocks.AIR.defaultBlockState());
            if (method.getName().equals("getFluidState")) return Fluids.EMPTY.defaultFluidState();
            if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
            throw new UnsupportedOperationException(method.toString());
        };
        return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class}, h);
    }

    private static final ScheduledTickAccess NO_TICKS = (ScheduledTickAccess) Proxy.newProxyInstance(ScheduledTickAccess.class.getClassLoader(),
            new Class<?>[]{ScheduledTickAccess.class}, (_, method, _) -> { throw new UnsupportedOperationException(method.toString()); });

    /** The state after the game has told the block about each of its six neighbours. */
    static BlockState updated(LevelReader level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        for (Direction d : Direction.values())
            st = st.updateShape(level, NO_TICKS, pos, d, pos.relative(d), level.getBlockState(pos.relative(d)), RandomSource.create(0));
        return st;
    }

    /** Blocks whose state depends on their neighbours. Leaves would ask for a tick, and other blocks never change. */
    static boolean dependent(BlockState s) {
        Block b = s.getBlock();
        return b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof StairBlock;
    }

    /** Checks every block that depends on its neighbours. Returns how many fences, panes and walls touch a hole. */
    private static int check(ShapeSettings c, Map<BlockPos, BlockState> w, String what) {
        LevelReader level = level(w);
        int beside = 0;
        for (var e : w.entrySet()) {
            if (!dependent(e.getValue())) continue;
            BlockPos p = e.getKey();
            assertEquals(e.getValue(), updated(level, p), what + " " + c.gen + " " + (c.floor ? "flat" : "upright") + " depth " + c.depth + " at " + p);
            for (Direction d : Direction.values()) if (!w.containsKey(p.relative(d))) { beside++; break; }
        }
        return beside;
    }

    static void choose(ShapeSettings c) {
        BlockChoices.CHOICE.put(Pieces.Family.FULL, c.fullConnects ? Blocks.STONE : Blocks.OAK_LEAVES);   // nothing attaches to leaves
    }

    @Test
    void theGameKeepsEveryStateBesideAHole() {
        int beside = 0, shapes = 0;
        for (ShapeSettings c : shapes()) {
            choose(c);
            Solver.Result r = Solver.run(c);
            for (boolean perLayer : c.depth > 1 ? new boolean[]{false, true} : new boolean[]{false}) {
                Painted p = open(r, c.depth, holes(r, c.depth, perLayer, shapes++), c.fullConnects);
                beside += check(c, world(r, c.depth, p), perLayer ? "holes by layer" : "holes through");
            }
        }
        assertTrue(beside > 20000, beside + " blocks beside holes");
    }

    /** Piece sets for shapes without holes: those above, walls alone, and walls with fences and panes. */
    private static final String[] PLAIN_SETS = {"all", "fence", "pane wall", "wall slab stair", "wall", "wall pane fence"};

    /**
     * Shapes without holes, unpainted, one, two, three and five deep, upright and flat: the game keeps every state. A
     * wall takes a post from the wall above it, so in a shape three or more deep a wall in the middle layers below a
     * wall with a post has one too.
     */
    @Test
    void theGameKeepsEveryStateOfAShapeWithoutHoles() {
        int checked = 0;
        for (ShapeSettings c : shapes(PLAIN_SETS, new int[]{1, 2, 3, 5}, true)) {
            choose(c);
            Map<BlockPos, BlockState> w = world(Solver.run(c), c.depth, null);
            check(c, w, "no holes");
            checked += w.size();
        }
        assertTrue(checked > 900000, checked + " blocks");
    }

    /**
     * One layer deep, a wall below a wall with a post has one too: in this narrow ellipse of walls beside leaves, a
     * wall with both sides tall sits below one with a post, and the drawing and the placed blocks both show its post.
     */
    @Test
    void aWallBelowAWallWithAPostHasOneAtDepthOne() {
        ShapeSettings c = new ShapeSettings();
        c.slab = c.stair = c.trap = c.shelf = c.fence = c.pane = false;
        c.fullConnects = false; c.eMode = ShapeSettings.EllipseMode.MIDDLE; c.eW = 3; c.eH = 19; c.eT = 1;
        choose(c);
        Solver.Result r = Solver.run(c);
        int found = 0;
        for (int j = 0; j + 1 < r.ny(); j++)
            for (int i = 0; i < r.nx(); i++) {
                int st = r.at(i, j), above = r.at(i, j + 1);
                if (Pieces.FAMILY[st] != Pieces.Family.WALL || Pieces.FAMILY[above] != Pieces.Family.WALL || !Pieces.wallPost(above)) continue;
                assertTrue(Pieces.wallPost(st), "drawing at " + i + "," + j);
                if (Pieces.wallLeft(st) == 2 && Pieces.wallRight(st) == 2) found++;
            }
        assertTrue(found > 0, "no wall with both sides tall below a wall with a post");
        check(c, world(r, 1, null), "depth 1");
    }

    /** Flat walls joined every way, one to four layers deep: a crossing has no post, as a straight wall has none. */
    @Test
    void flatWallsJoinedEveryWay() {
        for (int bits = 0; bits < 16; bits++) {
            // The wall in the middle of a 3×3 drawing, with a wall on each side it joins.
            byte[] grid = new byte[9];
            int[][] at = {{0, 1}, {2, 1}, {1, 2}, {1, 0}};
            grid[4] = (byte) (Pieces.F_WALL + bits);
            for (int d = 0; d < 4; d++) if ((bits & 1 << d) != 0) grid[at[d][1] * 3 + at[d][0]] = (byte) (Pieces.F_WALL + (1 << (d ^ 1)));
            Solver.Result r = new Solver.Result(grid, 3, 3, 0, 0, new int[Pieces.COUNT], null, true);
            ShapeSettings c = new ShapeSettings();
            c.floor = true;
            choose(c);
            for (int depth = 1; depth <= 4; depth++) {
                c.depth = depth;
                check(c, world(r, depth, null), "joined " + bits);
            }
        }
    }

    /**
     * The blocks a painted shape may use besides the Blocks tab's, by piece type: ones that join as those do. Full
     * blocks that fences, panes and walls join, or, without {@code fullConnects}, ones they don't.
     */
    private static Map<Pieces.Family, Block[]> paints(boolean fullConnects) {
        return Map.of(
                Pieces.Family.FULL, fullConnects ? new Block[]{Blocks.STONE_BRICKS, Blocks.GRANITE} : new Block[]{Blocks.OAK_LEAVES, Blocks.MELON},
                Pieces.Family.SLAB, new Block[]{Blocks.OAK_SLAB}, Pieces.Family.STAIRS, new Block[]{Blocks.BRICK_STAIRS},
                Pieces.Family.PANE, new Block[]{Blocks.IRON_BARS}, Pieces.Family.WALL, new Block[]{Blocks.ANDESITE_WALL, Blocks.BRICK_WALL});
    }

    /**
     * The same painted, block by block, with blocks of each piece type that join as the Blocks tab's do. Paint never
     * changes a state but the block, so the game keeps every one.
     */
    @Test
    void theGameKeepsEveryStateOfAPaintedShape() {
        int checked = 0, seed = 0;
        for (ShapeSettings c : shapes(new String[]{"all", "pane wall", "wall"}, new int[]{1, 3, 5}, false)) {
            Map<Pieces.Family, Block[]> paints = paints(c.fullConnects);
            List<Block> blocks = new ArrayList<>();
            for (Block[] bs : paints.values()) blocks.addAll(List.of(bs));
            PaintBlocks.Resolved paint = new PaintBlocks.Resolved(blocks.toArray(new Block[0]), new BlockState[blocks.size()]);
            choose(c);
            Solver.Result r = Solver.run(c);
            int n = r.nx() * r.ny();
            int[] slots = new int[c.depth * n];
            Random rnd = new Random(seed++);
            for (int b = 0; b < slots.length; b++) {
                Block[] bs = paints.get(Pieces.FAMILY[r.at(b % n % r.nx(), b % n / r.nx())]);
                slots[b] = bs == null || rnd.nextInt(3) == 0 ? -1 : blocks.indexOf(bs[rnd.nextInt(bs.length)]);
            }
            int f = Pieces.Family.values().length, w = Ladder.Why.values().length;
            Painted p = new Painted(slots, c.depth, List.of(), new int[0], new long[f][w], new long[f][1]);
            Map<BlockPos, BlockState> world = world(r, c.depth, p, paint);
            check(c, world, "painted");
            checked += world.size();
        }
        assertTrue(checked > 200000, checked + " blocks");
    }

    /** Settling with nothing taken out gives exactly what the shape gives unsettled. */
    @Test
    void settlingWithoutHolesChangesNothing() {
        int same = 0;
        for (ShapeSettings c : shapes()) {
            choose(c);
            Solver.Result r = Solver.run(c);
            Map<BlockPos, BlockState> plain = world(r, c.depth, null), settled = world(r, c.depth, open(r, c.depth, new BitSet(), c.fullConnects));
            assertEquals(plain, settled, c.gen + " " + (c.floor ? "flat" : "upright") + " depth " + c.depth);
            same += plain.size();
        }
        assertTrue(same > 50000, same + " blocks");
    }
}
