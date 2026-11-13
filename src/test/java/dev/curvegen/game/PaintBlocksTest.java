package dev.curvegen.game;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.client.PaintBlocks;
import dev.curvegen.core.Pieces.Family;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Painting's materials and turned blocks against the game's own blocks. Needs no world or client. */
class PaintBlocksTest {
    @BeforeAll
    static void loadBlocks() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void materialsComeFromTheGamesFamilies() {
        assertEquals("minecraft:stone_brick_stairs", PaintBlocks.pieces("minecraft:stone_bricks").get(Family.STAIRS));
        assertEquals("minecraft:stone_brick_wall", PaintBlocks.pieces("minecraft:stone_bricks").get(Family.WALL));
        assertEquals("minecraft:oak_fence", PaintBlocks.pieces("minecraft:oak_planks").get(Family.FENCE));
        assertEquals("minecraft:stone_bricks", PaintBlocks.materialOf("minecraft:stone_brick_slab"));
        assertNull(PaintBlocks.pieces("minecraft:glass").get(Family.STAIRS), "glass has no stairs");
    }

    @Test
    void blocksTheFamiliesLeaveOutAreFoundByName() {
        // The game's families have no shelves or panes; their names give them away.
        assertEquals("minecraft:oak_shelf", PaintBlocks.pieces("minecraft:oak_planks").get(Family.SHELF));
        assertEquals("minecraft:glass_pane", PaintBlocks.pieces("minecraft:glass").get(Family.PANE));
    }

    @Test
    void fullBlocksTurnWithThePattern() {
        BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        // Lying flat, a quarter turn about the vertical swaps the level axes.
        assertEquals(Direction.Axis.Z, PaintBlocks.turn(log, 1, Direction.UP).getValue(BlockStateProperties.AXIS));
        assertEquals(Direction.Axis.X, PaintBlocks.turn(log, 2, Direction.UP).getValue(BlockStateProperties.AXIS));
        // Upright, seen from the north, a quarter turn stands it up, and another lays it down again.
        BlockState up = PaintBlocks.turn(log, 1, Direction.NORTH);
        assertEquals(Direction.Axis.Y, up.getValue(BlockStateProperties.AXIS));
        assertEquals(Direction.Axis.X, PaintBlocks.turn(up, 1, Direction.NORTH).getValue(BlockStateProperties.AXIS));
        // A block that faces turns its face: half a turn upright points an observer the other way.
        BlockState observer = Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
        assertEquals(Direction.DOWN, PaintBlocks.turn(observer, 2, Direction.NORTH).getValue(BlockStateProperties.FACING));
        // Seen from the north, anticlockwise takes east (on the viewer's left) to down.
        assertEquals(Direction.DOWN, PaintBlocks.turn(observer.setValue(BlockStateProperties.FACING, Direction.EAST), 1, Direction.NORTH)
                .getValue(BlockStateProperties.FACING));
    }
}
