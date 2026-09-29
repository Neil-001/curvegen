package dev.curvegen.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.EmptyBlockView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Average colour of a block as you see it: its side face for upright shapes, its top face for floors.
 * Read from the textures of the active resource packs, with tints (grass, leaves…) applied per face.
 */
public final class ColorIndex {
    private ColorIndex() {}

    private static final Map<Block, Integer> SIDE = new HashMap<>(), TOP = new HashMap<>();
    /** Per texture: {red, green, blue, coverage}, or {-1} when it can't be read. */
    private static final Map<Identifier, int[]> TEXTURES = new HashMap<>();

    /** Colour for the current build orientation (top face when building a floor). */
    public static int of(Block block) { return of(block, CurveGenClient.SETTINGS.floor); }

    public static int of(Block block, boolean top) {
        return (top ? TOP : SIDE).computeIfAbsent(block, b -> compute(b, top));
    }

    public static void clear() { SIDE.clear(); TOP.clear(); TEXTURES.clear(); }

    private static int compute(Block block, boolean top) {
        BlockState state = block.getDefaultState();
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            BakedModel model = mc.getBlockRenderManager().getModel(state);
            Direction face = top ? Direction.UP : Direction.NORTH;
            List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, face, Random.create(42L)));
            if (quads.isEmpty())
                for (BakedQuad q : model.getQuads(state, null, Random.create(42L))) if (q.getFace() == face) quads.add(q);
            double r = 0, g = 0, b = 0, w = 0;
            for (BakedQuad q : quads) {
                int[] avg = average(q.getSprite());
                if (avg == null) continue;
                int rr = avg[0], gg = avg[1], bb = avg[2];
                if (q.hasColor()) {
                    int tint = tint(state, q.getColorIndex());
                    if (tint != -1) { rr = rr * ((tint >> 16) & 0xFF) / 255; gg = gg * ((tint >> 8) & 0xFF) / 255; bb = bb * (tint & 0xFF) / 255; }
                }
                r += rr * (double) avg[3]; g += gg * (double) avg[3]; b += bb * (double) avg[3]; w += avg[3];
            }
            if (w > 0) return ((int) (r / w) << 16) | ((int) (g / w) << 8) | (int) (b / w);

            // No quads on that face (unusual models): fall back to the particle texture.
            int[] p = average(model.getParticleSprite());
            if (p != null) {
                int rr = p[0], gg = p[1], bb = p[2];
                int tint = tint(state, 0);
                if (tint != -1 && saturation(rr, gg, bb) < 0.12) {
                    rr = rr * ((tint >> 16) & 0xFF) / 255; gg = gg * ((tint >> 8) & 0xFF) / 255; bb = bb * (tint & 0xFF) / 255;
                }
                return (rr << 16) | (gg << 8) | bb;
            }
        } catch (Exception ignored) {
            // fall through to the map colour
        }
        return state.getMapColor(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).color;
    }

    /** Some modded colour providers expect a world; a failure just means "no tint". */
    private static int tint(BlockState state, int index) {
        try { return MinecraftClient.getInstance().getBlockColors().getColor(state, null, null, index); }
        catch (Exception e) { return -1; }
    }

    private static int[] average(Sprite sprite) {
        int[] v = TEXTURES.computeIfAbsent(sprite.getContents().getId(), ColorIndex::readTexture);
        return v.length == 4 ? v : null;
    }

    private static int[] readTexture(Identifier sid) {
        Identifier tex = Identifier.of(sid.getNamespace(), "textures/" + sid.getPath() + ".png");
        Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(tex);
        if (res.isEmpty()) return new int[]{-1};
        try (InputStream in = res.get().getInputStream(); NativeImage img = NativeImage.read(in)) {
            long r = 0, g = 0, b = 0, w = 0;
            for (int y = 0; y < img.getHeight(); y++)
                for (int x = 0; x < img.getWidth(); x++) {
                    int abgr = img.getColor(x, y);
                    int a = abgr >>> 24;
                    if (a < 16) continue;
                    r += (long) (abgr & 0xFF) * a; g += (long) ((abgr >> 8) & 0xFF) * a; b += (long) ((abgr >> 16) & 0xFF) * a; w += a;
                }
            if (w == 0) return new int[]{-1};
            // Coverage (how much of the texture is opaque) weighs overlays such as the grass side's green fringe.
            return new int[]{(int) (r / w), (int) (g / w), (int) (b / w), (int) Math.max(1, w / 255)};
        } catch (Exception e) {
            return new int[]{-1};
        }
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
