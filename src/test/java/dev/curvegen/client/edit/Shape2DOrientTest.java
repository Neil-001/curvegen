package dev.curvegen.client.edit;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.edit.Orient;
import org.junit.jupiter.api.Test;

/** A drawing can take over a hologram from a 3D shape that was turned any way up. */
class Shape2DOrientTest {
    @Test
    void anyOrientationBecomesOneADrawingAllows() {
        Orient start = new Orient(Orient.EAST, Orient.UP, Orient.SOUTH);
        for (Orient base : new Orient[]{start, start.tip(Orient.NORTH), start.tip(Orient.NORTH).tip(Orient.NORTH),
                start.tip(Orient.SOUTH), start.tip(Orient.EAST), start.tip(Orient.WEST)})
            for (int k = 0; k < 4; k++, base = base.turn())
                for (boolean floor : new boolean[]{false, true}) {
                    ShapeSettings s = new ShapeSettings();
                    s.floor = floor;
                    Orient o = new Shape2D(s).orient(base);
                    assertEquals(Orient.UP, floor ? o.z() : o.y(), base + " floor " + floor);
                    assertEquals(o, new Shape2D(s).orient(o), "an allowed orientation stays as it is");
                }
    }

    @Test
    void aDrawingsOwnOrientationsAreKept() {
        ShapeSettings s = new ShapeSettings();
        Orient upright = Orient.facing(Orient.WEST);
        assertEquals(upright, new Shape2D(s).orient(upright));
        s.floor = true;
        assertEquals(new Orient(upright.x(), upright.z(), Orient.UP), new Shape2D(s).orient(upright));
    }
}
