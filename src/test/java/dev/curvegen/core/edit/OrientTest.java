package dev.curvegen.core.edit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OrientTest {
    @Test
    void facingPutsXToThePlayersRight() {
        // Facing north, east is to the right.
        assertEquals(new Orient(Orient.EAST, Orient.UP, Orient.NORTH), Orient.facing(Orient.NORTH));
        assertEquals(new Orient(Orient.SOUTH, Orient.UP, Orient.EAST), Orient.facing(Orient.EAST));
    }

    @Test
    void fourTurnsComeBackRound() {
        Orient o = Orient.facing(Orient.NORTH);
        assertEquals(Orient.facing(Orient.EAST), o.turn());
        assertEquals(o, o.turn().turn().turn().turn());
        assertEquals(Orient.UP, o.turn().y(), "turning leaves the vertical axis alone");
    }

    @Test
    void tippingSendsTheTopAwayFromThePlayer() {
        Orient o = Orient.facing(Orient.NORTH).tip(Orient.NORTH);
        assertEquals(new Orient(Orient.EAST, Orient.NORTH, Orient.DOWN), o);
        Orient back = o;
        for (int k = 0; k < 3; k++) back = back.tip(Orient.NORTH);
        assertEquals(Orient.facing(Orient.NORTH), back);
    }

    @Test
    void sizesAndPointsGoThereAndBack() {
        Orient o = new Orient(Orient.WEST, Orient.SOUTH, Orient.UP);   // flat, own y south
        int[] own = {7, 3, 2};
        assertArrayEquals(new int[]{7, 2, 3}, o.worldSize(own));
        assertArrayEquals(own, o.ownSize(o.worldSize(own)));
        double[] p = {1, 0.5, 2};
        double[] w = o.toWorld(p, own);
        assertArrayEquals(new double[]{6, 2, 0.5}, w, 1e-12);   // own x runs west, so it counts from the east side
        assertArrayEquals(p, o.toOwn(w, own), 1e-12);
        assertEquals(1, o.ownAxis(2));
    }

    @Test
    void cellsFillTheWorldBox() {
        Orient o = new Orient(Orient.WEST, Orient.UP, Orient.NORTH);
        int[] own = {4, 3, 2};
        assertArrayEquals(new int[]{3, 0, 1}, o.cell(0, 0, 0, own));
        assertArrayEquals(new int[]{0, 2, 0}, o.cell(3, 2, 1, own));
        assertArrayEquals(new int[]{4, 0, 1}, o.cell(-1, 0, 0, own), "a cell outside the box stays outside it");
    }

    @Test
    void axesMustBePerpendicular() {
        assertThrows(IllegalArgumentException.class, () -> new Orient(Orient.EAST, Orient.WEST, Orient.UP));
    }
}
