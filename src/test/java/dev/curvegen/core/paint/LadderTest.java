package dev.curvegen.core.paint;

import static dev.curvegen.core.paint.TestWorld.m;
import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.paint.Ladder.Why;
import dev.curvegen.core.paint.Pattern.Cell;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LadderTest {
    private static final Set<Family> ALL = EnumSet.allOf(Family.class);

    private static Pattern one(Cell c) { return Pattern.of("t", new Cell[][]{{c}}); }

    private static String pick(Ladder l, Family f) {
        int k = l.pick(0, f);
        return k < 0 ? null : l.blocks().get(k);
    }

    @Test
    void fullBlocksKeepTheirStateAndOtherPiecesFollowTheMaterial() {
        Ladder l = new Ladder(TestWorld.catalog(), one(Cell.block(m("oak_log[axis=x]"))), false, true, ALL);
        assertEquals(m("oak_log[axis=x]"), pick(l, Family.FULL));
        assertEquals(Why.OWN, l.why(0, Family.FULL));

        l = new Ladder(TestWorld.catalog(), one(Cell.block(m("stone_bricks"))), false, true, ALL);
        assertEquals(m("stone_brick_stairs"), pick(l, Family.STAIRS));
        assertEquals(m("stone_brick_slab"), pick(l, Family.SLAB));
        assertEquals(m("stone_brick_wall"), pick(l, Family.WALL));
        assertEquals(Why.OWN, l.why(0, Family.STAIRS));
    }

    @Test
    void aShapedBlockCountsAsItsMaterial() {
        Ladder l = new Ladder(TestWorld.catalog(), one(Cell.block(m("oak_stairs[facing=east]"))), false, true, ALL);
        assertEquals(m("oak_planks"), pick(l, Family.FULL));
        assertEquals(m("oak_slab"), pick(l, Family.SLAB));
    }

    @Test
    void aMissingPieceTakesTheClosestColour() {
        Ladder l = new Ladder(TestWorld.catalog(), one(Cell.block(m("red_wool"))), false, true, ALL);
        assertEquals(m("red_wool"), pick(l, Family.FULL));
        assertEquals(m("red_sandstone_stairs"), pick(l, Family.STAIRS));
        assertEquals(Why.CLOSEST, l.why(0, Family.STAIRS));
    }

    @Test
    void fallingBlocksAreNeverUsed() {
        Ladder l = new Ladder(TestWorld.catalog(), one(Cell.block(m("sand"))), false, true, ALL);
        String full = pick(l, Family.FULL);
        assertNotEquals(m("sand"), full);
        assertEquals(Why.CLOSEST, l.why(0, Family.FULL));
    }

    @Test
    void emptyCellsAndPiecesWithNoCandidateFallBackToTheBlocksTab() {
        Ladder l = new Ladder(TestWorld.catalog(), one(Cell.EMPTY), false, true, ALL);
        assertEquals(-1, l.pick(0, Family.FULL));
        assertEquals(Why.EMPTY, l.why(0, Family.FULL));
        l = new Ladder(TestWorld.catalog(), one(Cell.block(m("stone_bricks"))), false, true, ALL);
        assertEquals(-1, l.pick(0, Family.CHAIN));
        assertEquals(Why.BLOCKS_TAB, l.why(0, Family.CHAIN));
    }

    @Test
    void colourCellsAreMatchedPerPieceType() {
        Ladder l = new Ladder(TestWorld.catalog(), one(Cell.colour(0xa2834f)), false, true, ALL);
        assertEquals(m("oak_planks"), pick(l, Family.FULL));
        assertEquals(m("oak_stairs"), pick(l, Family.STAIRS));
        assertEquals(Why.CLOSEST, l.why(0, Family.FULL));
    }

    @Test
    void fencesStayInTheBlocksTabFencesGroup() {
        // The Blocks tab has a wooden fence, so nether bricks' own fence would stand apart from the rest.
        Ladder l = new Ladder(TestWorld.catalog(), one(Cell.block(m("nether_bricks"))), false, true, ALL);
        String fence = pick(l, Family.FENCE);
        assertTrue(fence.equals(m("oak_fence")) || fence.equals(m("spruce_fence")), fence);
        l = new Ladder(TestWorld.catalog(java.util.Map.of(Family.FENCE, m("nether_brick_fence"))), one(Cell.block(m("oak_planks"))), false, true, ALL);
        assertEquals(m("nether_brick_fence"), pick(l, Family.FENCE));
    }

    @Test
    void fullBlocksThatWouldJoinDifferentlyBecomeGaps() {
        // The solve had fences attach to full blocks, and leaves don't take them.
        Ladder l = new Ladder(TestWorld.catalog(), one(Cell.block(m("oak_leaves"))), false, true, ALL);
        assertEquals(-1, l.pick(0, Family.FULL));
        assertEquals(Why.GAP, l.why(0, Family.FULL));
        // With no fences, panes or walls in the shape, it doesn't matter.
        l = new Ladder(TestWorld.catalog(), one(Cell.block(m("oak_leaves"))), false, true, EnumSet.of(Family.FULL));
        assertEquals(m("oak_leaves"), pick(l, Family.FULL));
        // The other way round: a shape solved for leaves can't take stone bricks, which the Blocks tab has too.
        l = new Ladder(TestWorld.catalog(), one(Cell.block(m("stone_bricks"))), false, false, ALL);
        assertEquals(m("oak_leaves"), pick(l, Family.FULL));
    }

    @Test
    void oneMaterialSwapsMaterialsWithoutTheShapesPieces() {
        Set<Family> used = EnumSet.of(Family.FULL, Family.SLAB, Family.STAIRS);
        Ladder l = new Ladder(TestWorld.catalog(), one(Cell.block(m("white_wool"))), true, true, used);
        // Wool has no slabs or stairs; quartz is the closest material that has both.
        assertEquals(m("quartz_block"), pick(l, Family.FULL));
        assertEquals(m("quartz_stairs"), pick(l, Family.STAIRS));
        assertEquals(m("quartz_slab"), pick(l, Family.SLAB));
        // Without it, full blocks stay wool.
        l = new Ladder(TestWorld.catalog(), one(Cell.block(m("white_wool"))), false, true, used);
        assertEquals(m("white_wool"), pick(l, Family.FULL));
        // A shape with only full blocks needs no swap.
        l = new Ladder(TestWorld.catalog(), one(Cell.block(m("white_wool"))), true, true, EnumSet.of(Family.FULL));
        assertEquals(m("white_wool"), pick(l, Family.FULL));
    }

    @Test
    void theMajorityOfAPatternsBlocksDecidesWhetherFencesAttach() {
        Pattern p = Pattern.of("t", new Cell[][]{{Cell.block(m("oak_leaves")), Cell.block(m("oak_leaves")), Cell.block(m("stone_bricks"))}});
        assertEquals(Boolean.FALSE, Paint.connects(p, id -> !id.equals(m("oak_leaves"))));
        assertNull(Paint.connects(one(Cell.colour(0x123456)), unused -> true));
    }

    @Test
    void anEmptyCellNeverJoinsFencesDifferently() {
        // Solved for leaves, which fences don't take; the Blocks tab's stone bricks would, so an empty cell can't use them.
        Ladder l = new Ladder(TestWorld.catalog(), one(Cell.EMPTY), false, false, ALL);
        assertEquals(m("oak_leaves"), pick(l, Family.FULL));
    }

    @Test
    void aFenceOutsideTheWoodenGroupJoinsOnlyItsOwnKind() {
        Catalog c = TestWorld.catalog(java.util.Map.of(Family.FENCE, m("nether_brick_fence")));
        assertEquals(java.util.List.of(m("nether_brick_fence")), c.fences());
    }
}
