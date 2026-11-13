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
        tag(Blocks.COBBLESTONE_WALL, List.of(BlockTags.WALLS));
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

    static List<ShapeSettings> shapes() {
        List<ShapeSettings> out = new ArrayList<>();
        for (String set : SETS)
            for (int k = 0; k < 8; k++) {
                ShapeSettings base = new ShapeSettings();
                if (!set.equals("all")) {
                    base.slab = set.contains("slab"); base.stair = set.contains("stair"); base.trap = base.shelf = false;
                    base.fence = set.contains("fence"); base.pane = set.contains("pane"); base.wall = set.contains("wall");
                }
                base.floor = (k & 1) != 0;
                base.depth = (k & 2) != 0 ? 3 : 1;
                base.fullConnects = (k & 4) == 0;
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
    static Map<BlockPos, BlockState> world(Solver.Result r, int depth, Painted painted) {
        Map<BlockPos, BlockState> w = new HashMap<>();
        boolean floor = r.floor();
        Direction forward = floor ? Direction.NORTH : Direction.SOUTH;
        for (int j = 0; j < r.ny(); j++)
            for (int i = 0; i < r.nx(); i++)
                for (int k = 0; k < depth; k++) {
                    BlockState st = PaintBlocks.state2D(r, i, j, k, depth, Direction.EAST, forward, painted, null);
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

    private static void choose(ShapeSettings c) {
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

    /**
     * Settling with nothing taken out gives what the shape gives unsettled, except where that is a state the game
     * would change: walls in the middle of a deep shape below a wall with a post, which the game gives a post too.
     */
    @Test
    void settlingWithoutHolesChangesOnlyWhatTheGameWould() {
        int same = 0;
        for (ShapeSettings c : shapes()) {
            choose(c);
            Solver.Result r = Solver.run(c);
            Map<BlockPos, BlockState> plain = world(r, c.depth, null), settled = world(r, c.depth, open(r, c.depth, new BitSet(), c.fullConnects));
            assertEquals(plain.keySet(), settled.keySet());
            check(c, settled, "no holes");
            LevelReader level = level(plain);
            for (var e : plain.entrySet()) {
                BlockState was = e.getValue(), now = settled.get(e.getKey());
                if (was.equals(now)) { same++; continue; }
                assertEquals(now, updated(level, e.getKey()), "unsettled " + was + " at " + e.getKey());
            }
        }
        assertTrue(same > 50000, same + " blocks");
    }
}
