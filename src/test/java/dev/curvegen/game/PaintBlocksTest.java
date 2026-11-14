package dev.curvegen.game;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.PaintBlocks;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.paint.Pattern;
import dev.curvegen.core.paint.Patterns;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
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

    @Test
    void aDrawingIsSeenFromRightCrossUp() {
        // The export's upright drawing runs x east and y up, so it is seen from the south; its flat one from above.
        assertEquals(Direction.SOUTH, PaintBlocks.viewer(Direction.EAST, Direction.UP));
        assertEquals(Direction.UP, PaintBlocks.viewer(Direction.EAST, Direction.NORTH));
        // Turned a quarter with the pattern, an observer facing the drawing's right faces its up.
        BlockState observer = Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST);
        assertEquals(Direction.UP, PaintBlocks.turn(observer, 1, PaintBlocks.viewer(Direction.EAST, Direction.UP)).getValue(BlockStateProperties.FACING));
    }

    @Test
    void capturedBlocksCountAsTheirMaterial() {
        Set<Block> full = new HashSet<>(BlockChoices.candidates(Family.FULL));
        assertEquals(Pattern.Cell.EMPTY, PaintBlocks.cell(Blocks.AIR.defaultBlockState(), full));
        assertEquals(Pattern.Cell.block("minecraft:stone_bricks"), PaintBlocks.cell(Blocks.STONE_BRICKS.defaultBlockState(), full));
        // Shaped blocks give their material's full block, whichever way they face.
        assertEquals(Pattern.Cell.block("minecraft:stone_bricks"), PaintBlocks.cell(Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), full));
        assertEquals(Pattern.Cell.block("minecraft:oak_planks"), PaintBlocks.cell(Blocks.OAK_SLAB.defaultBlockState(), full));
        // A full block keeps what differs from its default, and nothing else.
        assertEquals(Pattern.Cell.block("minecraft:oak_log[axis=x]"),
                PaintBlocks.cell(Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), full));
        assertEquals(Pattern.Cell.block("minecraft:oak_log"), PaintBlocks.cell(Blocks.OAK_LOG.defaultBlockState(), full));
        // Torches, chests and falling blocks fill rather than leave holes.
        for (Block b : new Block[]{Blocks.TORCH, Blocks.CHEST, Blocks.SAND, Blocks.POPPY, Blocks.WATER})
            assertEquals(Pattern.Cell.UNUSABLE, PaintBlocks.cell(b.defaultBlockState(), full), b.toString());
    }

    @Test
    void bundledPatternsUseBlocksThisVersionHas() {
        for (Pattern p : Patterns.BUNDLED)
            for (int k = 0; k < p.paletteSize(); k++) {
                String state = p.cell(k).state();
                if (state == null) continue;
                String id = state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
                assertTrue(BuiltInRegistries.BLOCK.containsKey(Identifier.parse(id)), p.name + " uses " + id);
            }
    }

    @Test
    void concreteAndWoolHaveNoShapedPiecesBeforeTwentySixThree() {
        // Minecraft 26.3 adds their slabs and stairs. Here a road's slabs and stairs take the closest colour instead.
        for (String m : new String[]{"minecraft:white_concrete", "minecraft:gray_concrete", "minecraft:yellow_concrete", "minecraft:white_wool"}) {
            assertNull(PaintBlocks.pieces(m).get(Family.STAIRS), m);
            assertNull(PaintBlocks.pieces(m).get(Family.SLAB), m);
        }
    }
}
