package dev.curvegen.core;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PresetDataTest {
    private static ShapeSettings unusual() {
        ShapeSettings s = new ShapeSettings();
        s.eW = 57; s.eH = 23; s.eMode = ShapeSettings.EllipseMode.MIDDLE; s.eT = 1.75;
        s.src = "y = 9 - x^2/4"; s.xmin = "-6"; s.xmax = "2pi"; s.ymin = "0"; s.ymax = "9";
        s.qW = 31; s.qH = 17; s.qLock = false; s.qMode = ShapeSettings.EqMode.OVER; s.qLW = 2.5;
        s.bW = 64; s.bH = 30; s.pts.clear();
        s.pts.add(new double[]{1.5, 2}); s.pts.add(new double[]{20, 29.25}); s.pts.add(new double[]{63, 0.5});
        s.bMode = ShapeSettings.BzMode.FILLED; s.bLW = 0.75;
        return s;
    }

    @Test
    void roundTripGivesTheSameShape() {
        ShapeSettings s = unusual();
        for (ShapeSettings.Gen g : ShapeSettings.Gen.values()) {
            Map<String, String> d = PresetData.capture(s, g);
            ShapeSettings t = new ShapeSettings();
            PresetData.apply(d, g, t);
            assertEquals(g, t.gen);
            assertEquals(d, PresetData.capture(t, g), g + " data");
            ShapeSettings sg = s.copy();
            sg.gen = g;
            assertArrayEquals(Solver.run(sg).grid(), Solver.run(t).grid(), g + " shape");
        }
    }

    @Test
    void defaultNames() {
        ShapeSettings s = unusual();
        assertEquals("Ellipse 57×23, thick middle 1.75", PresetData.defaultName(s, ShapeSettings.Gen.ELLIPSE));
        assertEquals("y = 9 - x^2/4, fill over, 31 wide", PresetData.defaultName(s, ShapeSettings.Gen.EQUATION));
        assertEquals("Quadratic Bézier 64×30, filled", PresetData.defaultName(s, ShapeSettings.Gen.BEZIER));
        s.src = "y < 4 - x^2";
        assertEquals("y < 4 - x^2, 31 wide", PresetData.defaultName(s, ShapeSettings.Gen.EQUATION), "inequalities have no shape");
    }

    @Test
    void badValuesKeepCurrentSettings() {
        ShapeSettings t = new ShapeSettings();
        Map<String, String> junk = new HashMap<>(Map.of("width", "abc", "height", "-5", "shape", "SQUIGGLE", "points", "1,2;oops;3"));
        PresetData.apply(junk, ShapeSettings.Gen.BEZIER, t);
        assertEquals(40, t.bW);
        assertEquals(1, t.bH);                                 // a number, clamped to the minimum
        assertEquals(ShapeSettings.BzMode.LINE, t.bMode);
        assertEquals(4, t.pts.size(), "fewer than two valid points: keep the old ones");
    }

    @Test
    void blockChoicesRoundTrip() {
        ShapeSettings s = new ShapeSettings();
        s.slab = false; s.pane = false;
        Map<String, String> d = PresetData.captureBlocks(s, f -> "test:" + f.name().toLowerCase());
        assertEquals("test:stairs", d.get("stairs"));
        assertEquals("false", d.get("slabUsed"));
        assertFalse(d.containsKey("fullUsed"), "full blocks are always used");
        assertFalse(d.containsKey("air"));

        ShapeSettings t = new ShapeSettings();
        Map<Pieces.Family, String> blocks = new EnumMap<>(Pieces.Family.class);
        PresetData.applyBlocks(d, t, blocks::put);
        for (Pieces.Family f : Pieces.Family.values()) {
            if (f == Pieces.Family.AIR) continue;
            assertEquals(s.allows(f), t.allows(f), f + " used");
            assertEquals("test:" + f.name().toLowerCase(), blocks.get(f), f + " block");
        }
    }

    @Test
    void missingOrBadBlockValuesKeepCurrentChoices() {
        ShapeSettings t = new ShapeSettings();
        t.wall = false;
        Map<Pieces.Family, String> blocks = new EnumMap<>(Pieces.Family.class);
        PresetData.applyBlocks(Map.of("fenceUsed", "nope", "slabUsed", "false", "pane", "test:pane"), t, blocks::put);
        assertTrue(t.fence);
        assertFalse(t.slab);
        assertFalse(t.wall, "not in the preset");
        assertEquals(Map.of(Pieces.Family.PANE, "test:pane"), blocks);
    }

    @Test
    void examplesMatchTheOldExampleButton() {
        assertEquals(7, PresetData.examples().size());
        String[] names = {"Sine wave", "Parabolic arch", "Catenary arch", "Gothic arch", "Circle", "Heart", "Tangent"};
        for (int k = 0; k < names.length; k++) {
            PresetData.Example e = PresetData.examples().get(k);
            assertEquals(names[k], e.name());
            ShapeSettings u = new ShapeSettings();
            PresetData.apply(e.data(), ShapeSettings.Gen.EQUATION, u);
            assertNull(Solver.run(u).target().error, e.name());
        }
    }

    @Test
    void fileStemsAreSafeFileNames() {
        assertEquals("equation-heart", PresetData.fileStem(ShapeSettings.Gen.EQUATION, "Heart"));
        assertEquals("equation-y-2sin-x", PresetData.fileStem(ShapeSettings.Gen.EQUATION, "y = 2sin(x)"));
        assertEquals("bezier-cubic-bezier-40-20-line-1", PresetData.fileStem(ShapeSettings.Gen.BEZIER, "Cubic Bézier 40×20, line 1"));
        assertEquals("ellipse-a-b-c", PresetData.fileStem(ShapeSettings.Gen.ELLIPSE, "../a\\b:c*?"));
        assertEquals("ellipse", PresetData.fileStem(ShapeSettings.Gen.ELLIPSE, "???"));
        String longName = PresetData.fileStem(ShapeSettings.Gen.ELLIPSE, "x".repeat(59) + " yz");
        assertEquals("ellipse-" + "x".repeat(59), longName);
    }
}
