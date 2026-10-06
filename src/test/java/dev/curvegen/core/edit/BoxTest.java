package dev.curvegen.core.edit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BoxTest {
    private static final double NAN = Double.NaN;
    private final Box box = new Box(10, 60, 20, 8, 4, 2);

    @Test
    void thereAreSixFacesTwelveEdgesAndEightCorners() {
        int[] kinds = new int[4];
        Set<String> seen = new HashSet<>();
        for (int[] h : Box.handles()) {
            kinds[Math.abs(h[0]) + Math.abs(h[1]) + Math.abs(h[2])]++;
            assertTrue(seen.add(h[0] + "," + h[1] + "," + h[2]));
        }
        assertArrayEquals(new int[]{0, 6, 12, 8}, kinds);
        assertArrayEquals(new double[]{18, 62, 21}, box.handle(new int[]{1, 0, 0}), 0);
        assertArrayEquals(new double[]{10, 64, 21}, box.handle(new int[]{-1, 1, 0}), 0);
    }

    @Test
    void aFaceMovesInWholeBlocksAndTheOppositeSideStays() {
        int[] east = {1, 0, 0};
        int[] size = box.dragSize(east, new double[]{20.4, NAN, NAN}, false);
        assertArrayEquals(new int[]{10, 4, 2}, size);
        assertEquals(new Box(10, 60, 20, 10, 4, 2), box.fit(east, false, size));

        int[] west = {-1, 0, 0};
        size = box.dragSize(west, new double[]{12.6, NAN, NAN}, false);
        assertArrayEquals(new int[]{5, 4, 2}, size);
        assertEquals(new Box(13, 60, 20, 5, 4, 2), box.fit(west, false, size), "the east side stays at 18");
    }

    @Test
    void aBoxNeverGetsThinnerThanOneBlock() {
        int[] size = box.dragSize(new int[]{1, 0, 0}, new double[]{-50, NAN, NAN}, false);
        assertEquals(1, size[0]);
        assertEquals(new Box(10, 60, 20, 1, 4, 2), box.fit(new int[]{1, 0, 0}, false, size));
    }

    @Test
    void aCornerMovesThreeSidesAtOnce() {
        int[] corner = {1, 1, -1};
        int[] size = box.dragSize(corner, new double[]{19, 66, 18}, false);
        assertArrayEquals(new int[]{9, 6, 4}, size);
        assertEquals(new Box(10, 60, 18, 9, 6, 4), box.fit(corner, false, size));
    }

    @Test
    void anEdgeLeavesTheAxisItRunsAlongAlone() {
        int[] edge = {1, 1, 0};
        assertArrayEquals(new int[]{11, 5, 2}, box.dragSize(edge, new double[]{21, 65, NAN}, false));
    }

    @Test
    void sneakingResizesBothSidesAboutTheCentre() {
        int[] east = {1, 0, 0};
        int[] size = box.dragSize(east, new double[]{20, NAN, NAN}, true);
        assertArrayEquals(new int[]{12, 4, 2}, size);
        assertEquals(new Box(8, 60, 20, 12, 4, 2), box.fit(east, true, size));
        // Dragged right through the middle, an even width stops at 2 and an odd one at 1, so the centre never shifts.
        assertEquals(2, box.dragSize(east, new double[]{0, NAN, NAN}, true)[0]);
        assertEquals(1, new Box(0, 0, 0, 7, 1, 1).dragSize(east, new double[]{-9, NAN, NAN}, true)[0]);
        assertEquals(new Box(3, 0, 0, 1, 1, 1), new Box(0, 0, 0, 7, 1, 1).fit(east, true, new int[]{1, 1, 1}));
    }

    @Test
    void anAxisThatChangedWithoutBeingDraggedKeepsItsCentreOrItsBottom() {
        // Dragging the east face of a locked equation also makes it taller and, flat, longer.
        Box b = box.fit(new int[]{1, 0, 0}, false, new int[]{12, 8, 6});
        assertEquals(new Box(10, 60, 18, 12, 8, 6), b);
    }

    @Test
    void refitKeepsTheCentreAndTheBottom() {
        assertEquals(new Box(13, 60, 17, 2, 4, 8), box.refit(new int[]{2, 4, 8}));
        assertEquals(box, box.refit(box.size()));
    }

    @Test
    void spawnSitsOnTheAnchorCentredSideways() {
        assertEquals(new Box(-4, 64, 0, 9, 5, 1), Box.spawn(0, 64, 0, new int[]{9, 5, 1}));
    }

    @Test
    void regrowFollowsTheShapesOwnCorner() {
        // Own x east: the grid grew 3 cells to the left, so the box's west side moves out by 3.
        Orient east = new Orient(Orient.EAST, Orient.UP, Orient.SOUTH);
        assertEquals(new Box(7, 60, 20, 11, 4, 2), box.regrown(east, new int[]{-3, 0, 0}, new int[]{11, 4, 2}));
        // Own x west: the grid's left is the box's east side, so that's the side that moves out.
        Orient west = new Orient(Orient.WEST, Orient.UP, Orient.NORTH);
        assertEquals(new Box(10, 60, 20, 11, 4, 2), box.regrown(west, new int[]{-3, 0, 0}, new int[]{11, 4, 2}));
        // Growing at the far end (no shift) is the other way round.
        assertEquals(new Box(7, 60, 20, 11, 4, 2), box.regrown(west, new int[]{0, 0, 0}, new int[]{11, 4, 2}));
    }
}
