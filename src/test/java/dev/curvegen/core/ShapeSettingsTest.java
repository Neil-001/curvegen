package dev.curvegen.core;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The in-world editor's undo relies on {@code copy}, {@code set} and {@code same} covering every setting. */
class ShapeSettingsTest {
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void change(Field f, ShapeSettings s) throws Exception {
        Class<?> t = f.getType();
        if (t == int.class) f.setInt(s, f.getInt(s) + 1);
        else if (t == double.class) f.setDouble(s, f.getDouble(s) + 0.5);
        else if (t == boolean.class) f.setBoolean(s, !f.getBoolean(s));
        else if (t == String.class) f.set(s, f.get(s) + "+1");
        else if (t.isEnum()) {
            Object[] all = t.getEnumConstants();
            f.set(s, all[(((Enum) f.get(s)).ordinal() + 1) % all.length]);
        } else if (List.class.isAssignableFrom(t)) ((List<double[]>) f.get(s)).get(0)[1] += 0.25;
        else fail("ShapeSettings." + f.getName() + " has a type this test doesn't know: teach it, and copy(), set() and same()");
    }

    @Test
    void everyFieldIsCopiedAndCompared() throws Exception {
        for (Field f : ShapeSettings.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            ShapeSettings a = new ShapeSettings(), b = new ShapeSettings();
            change(f, a);
            assertFalse(a.same(b), "same() ignores " + f.getName());
            assertTrue(a.same(a.copy()), "copy() loses " + f.getName());
            b.set(a);
            assertTrue(a.same(b), "set() loses " + f.getName());
        }
    }

    @Test
    void aCopyIsIndependent() {
        ShapeSettings a = new ShapeSettings(), b = a.copy();
        b.pts.get(0)[0] = 99;
        b.pts.remove(1);
        assertEquals(2, a.pts.get(0)[0], 0);
        assertEquals(4, a.pts.size());
        a.set(b);
        assertEquals(3, a.pts.size());
        assertNotSame(a.pts.get(0), b.pts.get(0));
    }
}
