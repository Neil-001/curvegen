package dev.curvegen.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Average colour of each block's texture, and perceptual (CIELAB) colour distance. */
public final class ColorIndex {
    private ColorIndex() {}

    private static final Map<Block, Integer> CACHE = new HashMap<>();

    /** 0xRRGGBB average colour of the block's particle texture. */
    public static int of(Block block) {
        return CACHE.computeIfAbsent(block, ColorIndex::compute);
    }

    public static void clear() { CACHE.clear(); }

    private static int compute(Block block) {
        BlockState state = block.getDefaultState();
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            Sprite sprite = mc.getBlockRenderManager().getModel(state).getParticleSprite();
            Identifier sid = sprite.getContents().getId();
            Identifier tex = Identifier.of(sid.getNamespace(), "textures/" + sid.getPath() + ".png");
            Optional<Resource> res = mc.getResourceManager().getResource(tex);
            if (res.isPresent()) {
                try (InputStream in = res.get().getInputStream(); NativeImage img = NativeImage.read(in)) {
                    long r = 0, g = 0, b = 0, w = 0;
                    for (int y = 0; y < img.getHeight(); y++)
                        for (int x = 0; x < img.getWidth(); x++) {
                            int abgr = img.getColor(x, y);
                            int a = abgr >>> 24;
                            if (a < 16) continue;
                            r += (long) (abgr & 0xFF) * a; g += (long) ((abgr >> 8) & 0xFF) * a; b += (long) ((abgr >> 16) & 0xFF) * a; w += a;
                        }
                    if (w > 0) {
                        int rr = (int) (r / w), gg = (int) (g / w), bb = (int) (b / w);
                        // Grey textures (leaves, vines…) get their colour from a tint; apply the default one.
                        int tint = mc.getBlockColors().getColor(state, null, null, 0);
                        if (tint != -1 && saturation(rr, gg, bb) < 0.12) {
                            rr = rr * ((tint >> 16) & 0xFF) / 255; gg = gg * ((tint >> 8) & 0xFF) / 255; bb = bb * (tint & 0xFF) / 255;
                        }
                        return (rr << 16) | (gg << 8) | bb;
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through to the map colour
        }
        return state.getMapColor(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).color;
    }

    private static double saturation(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return max == 0 ? 0 : (max - min) / (double) max;
    }

    // ---------- CIELAB ----------
    public static double[] lab(int rgb) {
        double r = lin(((rgb >> 16) & 0xFF) / 255.0), g = lin(((rgb >> 8) & 0xFF) / 255.0), b = lin((rgb & 0xFF) / 255.0);
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        double y = (0.2126 * r + 0.7152 * g + 0.0722 * b);
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }
    private static double lin(double c) { return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
    private static double f(double t) { return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116; }

    public static double distance(int rgbA, int rgbB) {
        double[] a = lab(rgbA), b = lab(rgbB);
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }
}
