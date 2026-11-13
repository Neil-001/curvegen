package dev.curvegen.core.paint;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.paint.Pattern.Cell;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaintTest {
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void change(Field f, Paint p) throws Exception {
        Class<?> t = f.getType();
        if (t == int.class) f.setInt(p, f.getInt(p) + 1);
        else if (t == boolean.class) f.setBoolean(p, !f.getBoolean(p));
        else if (t == Pattern.class) f.set(p, Patterns.CHECKER);
        else if (t == Mix.class) f.set(p, Mix.of(Patterns.CHECKER));
        else if (t.isEnum()) {
            Object[] all = t.getEnumConstants();
            Object v = f.get(p);
            f.set(p, all[v == null ? 0 : (((Enum) v).ordinal() + 1) % all.length]);
        } else fail("Paint." + f.getName() + " has a type this test doesn't know: teach it, and copy(), set(), same(), capture() and apply()");
    }

    @Test
    void everySettingIsCopiedComparedAndSaved() throws Exception {
        for (Field f : Paint.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            Paint a = new Paint(), b = new Paint();
            change(f, a);
            assertFalse(a.same(b), "same() ignores " + f.getName());
            assertTrue(a.same(a.copy()), "copy() loses " + f.getName());
            b.set(a);
            assertTrue(a.same(b), "set() loses " + f.getName());
            Paint c = new Paint();
            c.apply(a.capture());
            assertTrue(a.same(c), "capture() and apply() lose " + f.getName());
        }
    }

    @Test
    void unreadableTextKeepsTheCurrentSettings() {
        Paint p = new Paint();
        p.size = 3; p.pattern = Patterns.MOSSY_MIX;
        Map<String, String> d = new HashMap<>(Map.of("size", "lots", "turn", "720", "source", "GRADIENT", "pattern.width", "2"));
        p.apply(d);
        assertEquals(3, p.size);
        assertEquals(359, p.turn);
        assertEquals(Paint.Source.OFF, p.source, "a source this version doesn't know turns paint off");
        assertSame(Patterns.MOSSY_MIX, p.pattern);
    }

    @Test
    void patternsRoundTripAsText() {
        Pattern p = Pattern.of("Mine", new Cell[][]{
                {Cell.block("minecraft:oak_log[axis=x]"), Cell.EMPTY, Cell.colour(0x12ab34)},
                {Cell.block("minecraft:stone"), Cell.block("minecraft:stone"), Cell.EMPTY}});
        assertEquals(p, Pattern.decode(p.encode()));
        for (Pattern b : Patterns.BUNDLED) assertEquals(b, Pattern.decode(b.encode()));
        assertEquals(2, p.height);
        assertEquals("minecraft:oak_log[axis=x]", p.cell(p.at(0, 1)).state());
        assertEquals("minecraft:oak_log", p.cell(p.at(0, 1)).block());
        assertEquals(2, p.count(p.at(0, 0)));
    }

    @Test
    void brokenPatternTextIsRefused() {
        Map<String, String> good = Patterns.CHECKER.encode();
        for (String[] bad : new String[][]{{"width", "3"}, {"rows", "0,1/1"}, {"palette", "minecraft:a minecraft:a"}, {"rows", "0,9/1,0"},
                {"palette", "Not An Id"}, {"width", "129"}}) {
            Map<String, String> d = new HashMap<>(good);
            d.put(bad[0], bad[1]);
            assertNull(Pattern.decode(d), bad[0] + " = " + bad[1]);
        }
    }

    @Test
    void defaultsDependOnTheShape() {
        Paint p = new Paint();
        assertEquals(Paint.Fit.REPEAT_ALONG, p.fitFor(true));
        assertEquals(Paint.Fit.REPEAT, p.fitFor(false));
        p.on = Paint.On.EDGE;
        assertEquals(Paint.On.FACE, p.onFor(1));
        assertEquals(Paint.On.EDGE, p.onFor(2));
    }

    @Test
    void materialNamesFollowTheGamesHabits() {
        java.util.Set<String> ids = java.util.Set.of("mod:foo_bricks", "mod:foo_brick_stairs", "mod:foo_brick_wall", "mod:bar_planks", "mod:bar_stairs",
                "mod:bar_slab", "mod:baz_glass", "mod:baz_glass_pane", "mod:qux_block", "mod:qux_slab");
        assertEquals("mod:foo_brick_stairs", MaterialNames.variant("mod:foo_bricks", dev.curvegen.core.Pieces.Family.STAIRS, ids));
        assertEquals("mod:foo_brick_wall", MaterialNames.variant("mod:foo_bricks", dev.curvegen.core.Pieces.Family.WALL, ids));
        assertNull(MaterialNames.variant("mod:foo_bricks", dev.curvegen.core.Pieces.Family.SLAB, ids));
        assertEquals("mod:bar_stairs", MaterialNames.variant("mod:bar_planks", dev.curvegen.core.Pieces.Family.STAIRS, ids));
        assertEquals("mod:baz_glass_pane", MaterialNames.variant("mod:baz_glass", dev.curvegen.core.Pieces.Family.PANE, ids));
        assertEquals("mod:qux_slab", MaterialNames.variant("mod:qux_block", dev.curvegen.core.Pieces.Family.SLAB, ids));
    }
}
