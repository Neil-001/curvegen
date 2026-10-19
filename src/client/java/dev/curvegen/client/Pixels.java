package dev.curvegen.client;

import com.mojang.blaze3d.platform.NativeImage;

/** 1.21.1's {@link NativeImage} keeps pixels as ABGR. These take and return ARGB, as the rest of the mod uses. */
public final class Pixels {
    private Pixels() {}

    public static int get(NativeImage img, int x, int y) { return swapRedBlue(img.getPixelRGBA(x, y)); }

    public static void set(NativeImage img, int x, int y, int argb) { img.setPixelRGBA(x, y, swapRedBlue(argb)); }

    public static void fill(NativeImage img, int argb) { img.fillRect(0, 0, img.getWidth(), img.getHeight(), swapRedBlue(argb)); }

    static int swapRedBlue(int c) { return (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16); }
}
