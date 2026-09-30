package dev.curvegen.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Litematica packs palette indices end to end, straddling longs (unlike vanilla chunk sections). */
class LitematicBitsTest {
    @Test
    void roundTrip() {
        Random rnd = new Random(1);
        for (int bits = 2; bits <= 13; bits++) {
            int[] v = new int[5000];
            for (int i = 0; i < v.length; i++) v[i] = rnd.nextInt(1 << bits);
            long[] packed = LitematicBits.pack(v, bits);
            assertEquals((v.length * (long) bits + 63) / 64, packed.length);
            for (int i = 0; i < v.length; i++) assertEquals(v[i], LitematicBits.get(packed, bits, i), "bits " + bits + " index " + i);
        }
    }

    @Test
    void bitsPerEntry() {
        assertEquals(2, LitematicBits.bitsFor(1));
        assertEquals(2, LitematicBits.bitsFor(4));
        assertEquals(3, LitematicBits.bitsFor(5));
        assertEquals(8, LitematicBits.bitsFor(256));
        assertEquals(9, LitematicBits.bitsFor(257));
    }
}
