package dev.curvegen.game;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.PaintBlocks;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.paint.Pattern;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
        assertEquals("minecraft:oak_shelf", PaintBlocks.pieces("minecraft:oak_planks").get(Family.SHELF));
        assertEquals("minecraft:glass_pane", PaintBlocks.pieces("minecraft:glass").get(Family.PANE));
    }

    @Test
    void fullBlocksTurnWithThePattern() {
        BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        assertEquals(Direction.Axis.Z, PaintBlocks.turn(log, 1, Direction.UP).getValue(BlockStateProperties.AXIS));
        assertEquals(Direction.Axis.X, PaintBlocks.turn(log, 2, Direction.UP).getValue(BlockStateProperties.AXIS));
        BlockState up = PaintBlocks.turn(log, 1, Direction.NORTH);
        assertEquals(Direction.Axis.Y, up.getValue(BlockStateProperties.AXIS));
        assertEquals(Direction.Axis.X, PaintBlocks.turn(up, 1, Direction.NORTH).getValue(BlockStateProperties.AXIS));
        BlockState observer = Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
        assertEquals(Direction.DOWN, PaintBlocks.turn(observer, 2, Direction.NORTH).getValue(BlockStateProperties.FACING));
        assertEquals(Direction.DOWN, PaintBlocks.turn(observer.setValue(BlockStateProperties.FACING, Direction.EAST), 1, Direction.NORTH)
                .getValue(BlockStateProperties.FACING));
    }

    @Test
    void aDrawingIsSeenFromRightCrossUp() {
        assertEquals(Direction.SOUTH, PaintBlocks.viewer(Direction.EAST, Direction.UP));
        assertEquals(Direction.UP, PaintBlocks.viewer(Direction.EAST, Direction.NORTH));
        BlockState observer = Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST);
        assertEquals(Direction.UP, PaintBlocks.turn(observer, 1, PaintBlocks.viewer(Direction.EAST, Direction.UP)).getValue(BlockStateProperties.FACING));
    }

    @Test
    void capturedBlocksCountAsTheirMaterial() {
        Set<Block> full = new HashSet<>(BlockChoices.candidates(Family.FULL));
        assertEquals(Pattern.Cell.EMPTY, PaintBlocks.cell(Blocks.AIR.defaultBlockState(), full));
        assertEquals(Pattern.Cell.block("minecraft:stone_bricks"), PaintBlocks.cell(Blocks.STONE_BRICKS.defaultBlockState(), full));
        assertEquals(Pattern.Cell.block("minecraft:stone_bricks"), PaintBlocks.cell(Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), full));
        assertEquals(Pattern.Cell.block("minecraft:oak_planks"), PaintBlocks.cell(Blocks.OAK_SLAB.defaultBlockState(), full));
        assertEquals(Pattern.Cell.block("minecraft:oak_log[axis=x]"),
                PaintBlocks.cell(Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), full));
        assertEquals(Pattern.Cell.block("minecraft:oak_log"), PaintBlocks.cell(Blocks.OAK_LOG.defaultBlockState(), full));
        for (Block b : new Block[]{Blocks.TORCH, Blocks.CHEST, Blocks.SAND, Blocks.POPPY, Blocks.WATER})
            assertEquals(Pattern.Cell.UNUSABLE, PaintBlocks.cell(b.defaultBlockState(), full), b.toString());
    }
}
