package dev.curvegen.core;

/**
 * Packs palette indices the way Litematica's LitematicaBitArray does: values are laid end to end
 * and may straddle two longs (unlike vanilla chunk sections, which pad each long).
 */
public final class LitematicBits {
    private LitematicBits() {}

    public static int bitsFor(int paletteSize) {
        return Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));
    }

    public static long[] pack(int[] values, int bits) {
        long[] arr = new long[(int) (((long) values.length * bits + 63) / 64)];
        long mask = (1L << bits) - 1;
        for (int index = 0; index < values.length; index++) {
            long v = values[index] & mask;
            long startOffset = (long) index * bits;
            int start = (int) (startOffset >> 6), end = (int) (((long) (index + 1) * bits - 1) >> 6);
            int bit = (int) (startOffset & 63);
            arr[start] = arr[start] & ~(mask << bit) | (v << bit);
            if (start != end) {
                int shift = 64 - bit, rest = bits - shift;
                arr[end] = arr[end] >>> rest << rest | (v >> shift);
            }
        }
        return arr;
    }

    public static int get(long[] arr, int bits, int index) {
        long mask = (1L << bits) - 1, startOffset = (long) index * bits;
        int start = (int) (startOffset >> 6), end = (int) (((long) (index + 1) * bits - 1) >> 6);
        int bit = (int) (startOffset & 63);
        if (start == end) return (int) (arr[start] >>> bit & mask);
        int shift = 64 - bit;
        return (int) ((arr[start] >>> bit | arr[end] << shift) & mask);
    }
}
