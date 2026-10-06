package dev.curvegen.game;

import dev.curvegen.core.Pieces;
import dev.curvegen.core.Pieces3;
import dev.curvegen.core.Shapes3Cases;
import dev.curvegen.core.Solver3;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the 3D pieces against the game itself rather than a copy of its rules: every state's properties build a
 * real block state with the same shape, and no block in a solved shape changes when the game updates it.
 * This is the one test that loads Minecraft's blocks. It needs no world or client.
 */
class GameRules3Test {
    private static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN, Direction.UP};

    @BeforeAll
    static void loadBlocks() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Tags come from data packs, which aren't loaded here. Walls and fences find each other by tag.
        tag(Blocks.COBBLESTONE_WALL, BlockTags.WALLS);
        tag(Blocks.OAK_FENCE, BlockTags.FENCES, BlockTags.WOODEN_FENCES);
        tag(Blocks.OAK_LEAVES, BlockTags.LEAVES);
    }

    @SafeVarargs
    @SuppressWarnings("deprecation")
    private static void tag(Block b, TagKey<Block>... tags) throws ReflectiveOperationException {
        Method bind = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
        bind.setAccessible(true);
        bind.invoke(b.builtInRegistryHolder(), List.of(tags));
    }

    private static Block block(Pieces.Family f, boolean fullConnects) {
        return switch (f) {
            case AIR -> Blocks.AIR;
            case FULL -> fullConnects ? Blocks.STONE : Blocks.OAK_LEAVES;   // nothing attaches to leaves
            case SLAB -> Blocks.STONE_SLAB;
            case STAIRS -> Blocks.STONE_STAIRS;
            case TRAPDOOR -> Blocks.OAK_TRAPDOOR;
            case SHELF -> Blocks.OAK_SHELF;
            case FENCE -> Blocks.OAK_FENCE;
            case PANE -> Blocks.GLASS_PANE;
            case WALL -> Blocks.COBBLESTONE_WALL;
            case CHAIN -> Blocks.IRON_CHAIN;
            case ROD -> Blocks.END_ROD;
        };
    }

    /** The block state for a piece state, built only from {@link Pieces3#props}, as the client will. */
    private static BlockState state(int piece, boolean fullConnects) {
        Block b = block(Pieces3.FAMILY[piece], fullConnects);
        BlockState st = b.defaultBlockState();
        String props = Pieces3.props(piece);
        if (props.isEmpty()) return st;
        for (String kv : props.split(",")) {
            String[] p = kv.split("=");
            Property<?> prop = b.getStateDefinition().getProperty(p[0]);
            assertNotNull(prop, b + " has no property " + p[0]);
            st = with(st, prop, p[1]);
        }
        return st;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState st, Property<T> prop, String value) {
        return st.setValue(prop, prop.getValue(value).orElseThrow(() -> new AssertionError(prop + " has no value " + value)));
    }

    private static boolean[] voxels(VoxelShape shape) {
        boolean[] v = new boolean[4096];
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        if ((x + .5) / 16 > x0 && (x + .5) / 16 < x1 && (y + .5) / 16 > y0 && (y + .5) / 16 < y1 && (z + .5) / 16 > z0 && (z + .5) / 16 < z1)
                            v[(y * 16 + z) * 16 + x] = true;
        });
        return v;
    }

    @Test
    void everyStateHasTheGamesShape() {
        for (int s = 0; s < Pieces3.COUNT; s++) {
            if (s == Pieces3.WALL) continue;   // a wall with no sides and no post: never produced
            BlockState st = state(s, true);
            boolean[] ours = Pieces3.voxels(s), game = voxels(st.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            Pieces.Family f = Pieces3.FAMILY[s];
            // A fence's bars, a chain and an end rod are drawn thinner than their outline, and scored as drawn.
            boolean thinner = f == Pieces.Family.FENCE || f == Pieces.Family.CHAIN || f == Pieces.Family.ROD;
            for (int v = 0; v < 4096; v++) {
                if (thinner) assertTrue(!ours[v] || game[v], Pieces3.name(s) + " reaches outside the game's shape");
                else assertEquals(game[v], ours[v], Pieces3.name(s) + " differs from " + st + " at voxel " + v);
            }
            if (!Pieces3.isConnector(s))
                for (int d = 0; d < 6; d++)
                    assertEquals(st.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, DIRS[d]), Pieces3.FACE[s][d], Pieces3.name(s) + " face " + DIRS[d]);
        }
    }

    private static LevelReader level(Solver3.Result r, boolean fullConnects) {
        InvocationHandler h = (proxy, method, args) -> {
            if (method.getName().equals("getBlockState") && args.length == 1 && args[0] instanceof BlockPos p) {
                boolean in = p.getX() >= 0 && p.getY() >= 0 && p.getZ() >= 0 && p.getX() < r.nx() && p.getY() < r.ny() && p.getZ() < r.nz();
                return in ? state(r.at(p.getX(), p.getY(), p.getZ()), fullConnects) : Blocks.AIR.defaultBlockState();
            }
            if (method.getName().equals("getFluidState")) return Fluids.EMPTY.defaultFluidState();
            if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
            throw new UnsupportedOperationException(method.toString());
        };
        return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class}, h);
    }

    private static final ScheduledTickAccess NO_TICKS = (ScheduledTickAccess) Proxy.newProxyInstance(ScheduledTickAccess.class.getClassLoader(),
            new Class<?>[]{ScheduledTickAccess.class}, (proxy, method, args) -> { throw new UnsupportedOperationException(method.toString()); });

    /** The state after the game has told the block about each of its six neighbours. */
    private static BlockState updated(LevelReader level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        for (Direction d : Direction.values())
            st = st.updateShape(level, NO_TICKS, pos, d, pos.relative(d), level.getBlockState(pos.relative(d)), RandomSource.create(0));
        return st;
    }

    @Test
    void theGameKeepsEverySolvedState() {
        int connectors = 0, corners = 0, walls = 0;
        for (Shapes3Cases.Case c : Shapes3Cases.all()) {
            Solver3.Result r = c.solve();
            LevelReader level = level(r, c.settings().fullConnects);
            for (int y = 0; y < r.ny(); y++)
                for (int z = 0; z < r.nz(); z++)
                    for (int x = 0; x < r.nx(); x++) {
                        int s = r.at(x, y, z);
                        if (!Pieces3.dependent(s)) continue;
                        BlockPos pos = new BlockPos(x, y, z);
                        assertEquals(level.getBlockState(pos), updated(level, pos), c.name() + " at " + x + "," + y + "," + z);
                        if (Pieces3.isConnector(s)) connectors++;
                        else if (Pieces3.stairShape(s) != Pieces3.STRAIGHT) corners++;
                        if (Pieces3.isWall(s)) walls++;
                    }
        }
        assertTrue(connectors > 5000 && walls > 2000 && corners > 2000, connectors + " connectors, " + walls + " walls, " + corners + " corner stairs");
    }

    @Test
    void theGameCorrectsAWrongState() {
        // The check above only means something if the game does change states it disagrees with.
        Solver3.Result r = new Solver3.Result(new short[]{(short) Pieces3.wall(1, 0, 1, 0, false), 0, (short) Pieces3.stair(0, Pieces3.N, Pieces3.INNER_LEFT)},
                3, 1, 1, 0, 0, new int[Pieces3.COUNT], null, 0);
        LevelReader level = level(r, true);
        assertEquals(state(Pieces3.WALL_POST, true), updated(level, new BlockPos(0, 0, 0)));
        assertEquals(state(Pieces3.stair(0, Pieces3.N, Pieces3.STRAIGHT), true), updated(level, new BlockPos(2, 0, 0)));
    }
}
