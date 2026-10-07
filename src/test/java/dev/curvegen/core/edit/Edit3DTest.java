package dev.curvegen.core.edit;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.Shape3;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;
import org.junit.jupiter.api.Test;

class Edit3DTest {
    private static final boolean[] ALL = {true, true, true};

    private static ShapeSettings of(Gen gen) {
        ShapeSettings s = new ShapeSettings();
        s.gen = gen;
        return s;
    }

    @Test
    void anEllipsoidTakesTheBoxItIsGiven() {
        ShapeSettings s = of(Gen.ELLIPSOID);
        assertArrayEquals(new int[]{21, 21, 21}, Edit3D.size(s));
        Edit3D.resize(s, new int[]{30, 8, 1}, ALL);
        assertArrayEquals(new int[]{30, 8, 1}, new int[]{s.e3W, s.e3H, s.e3D});
        Edit3D.resize(s, new int[]{1000, 0, -5}, ALL);
        assertArrayEquals(new int[]{Shape3.MAX_SIZE, 1, 1}, Edit3D.size(s));
    }

    @Test
    void aThickEllipsoidsBoxLeavesOutTheRoomItsShellAdds() {
        ShapeSettings s = of(Gen.ELLIPSOID);
        s.e3Mode = ShapeSettings.EllipseMode.OUTWARDS; s.e3T = 3;
        assertArrayEquals(new int[]{21, 21, 21}, Edit3D.size(s));
        assertEquals(27, Shape3.of(s).nx());
    }

    @Test
    void stretchingATorusLeavesItsRingAndTubeAlone() {
        ShapeSettings s = of(Gen.TORUS);
        Edit3D.resize(s, new int[]{40, 12, 25}, ALL);
        assertArrayEquals(new int[]{40, 12, 25}, Edit3D.size(s));
        assertEquals(25, s.tRing);
        assertEquals(9, s.tTube);
        assertTrue(Edit3D.describe(s).contains("stretched from a 25 ring"), Edit3D.describe(s));
        Edit3D.resize(s, new int[]{999, 12, 25}, ALL);
        assertEquals(Shape3.MAX_SIZE, s.tW);
    }

    @Test
    void aRoundTorusFollowsItsRingAndTubeExactly() {
        ShapeSettings s = of(Gen.TORUS);
        Edit3D.setRing(s, 31);
        assertArrayEquals(new int[]{31, 9, 31}, Edit3D.size(s));
        Edit3D.setTube(s, 11);
        assertArrayEquals(new int[]{31, 11, 31}, Edit3D.size(s));
        assertFalse(Edit3D.describe(s).contains("stretched"));
    }

    @Test
    void aStretchedTorusKeepsItsStretchWhenTheRingOrTubeChanges() {
        ShapeSettings s = of(Gen.TORUS);
        Edit3D.resize(s, new int[]{50, 18, 25}, ALL);   // twice as wide and twice as tall as round
        Edit3D.setRing(s, 30);
        assertArrayEquals(new int[]{60, 18, 30}, Edit3D.size(s));
        Edit3D.setTube(s, 6);
        assertArrayEquals(new int[]{60, 12, 30}, Edit3D.size(s));
    }

    @Test
    void theTubeIsNeverThickerThanTheRing() {
        ShapeSettings s = of(Gen.TORUS);
        Edit3D.setTube(s, 80);
        assertEquals(25, s.tTube);
        Edit3D.setRing(s, 10);
        assertEquals(10, s.tTube);
        assertEquals(10, s.tH);
        Edit3D.setRing(s, 0);
        assertEquals(1, s.tRing);
        assertEquals(1, s.tTube);
    }
}
