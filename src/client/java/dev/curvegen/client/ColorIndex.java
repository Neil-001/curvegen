package dev.curvegen.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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

    // NeoForge deprecates the model methods that take no world, in favour of its own that need a world and a position.
    // These colours are worked out without a world, and Fabric only has the plain methods.
    @SuppressWarnings("deprecation")
    private static int compute(Block block, boolean top) {
        BlockState state = block.defaultBlockState();
        Minecraft mc = Minecraft.getInstance();
        try {
            BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
            Direction face = top ? Direction.UP : Direction.NORTH;
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(RandomSource.create(42L), parts);
            List<BakedQuad> quads = new ArrayList<>();
            for (BlockStateModelPart part : parts) quads.addAll(part.getQuads(face));
            if (quads.isEmpty())
                for (BlockStateModelPart part : parts)
                    for (BakedQuad q : part.getQuads(null)) if (q.direction() == face) quads.add(q);
            double r = 0, g = 0, b = 0, w = 0;
            for (BakedQuad q : quads) {
                BakedQuad.MaterialInfo material = q.materialInfo();
                int[] avg = average(material.sprite());
                if (avg == null) continue;
                int rr = avg[0], gg = avg[1], bb = avg[2];
                if (material.isTinted()) {
                    int tint = tint(state, material.tintIndex());
                    if (tint != -1) { rr = rr * ((tint >> 16) & 0xFF) / 255; gg = gg * ((tint >> 8) & 0xFF) / 255; bb = bb * (tint & 0xFF) / 255; }
                }
                r += rr * (double) avg[3]; g += gg * (double) avg[3]; b += bb * (double) avg[3]; w += avg[3];
            }
            if (w > 0) return ((int) (r / w) << 16) | ((int) (g / w) << 8) | (int) (b / w);

            // No quads on that face (unusual models): fall back to the particle texture.
            int[] p = average(model.particleMaterial().sprite());
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
        return state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
    }

    /** The tint a block has outside a world. A modded tint source that fails just means "no tint". */
    private static int tint(BlockState state, int index) {
        try {
            BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, index);
            return source == null ? -1 : source.color(state);
        } catch (Exception e) { return -1; }
    }

    private static int[] average(TextureAtlasSprite sprite) {
        int[] v = TEXTURES.computeIfAbsent(sprite.contents().name(), ColorIndex::readTexture);
        return v.length == 4 ? v : null;
    }

    private static int[] readTexture(Identifier sid) {
        Identifier tex = Identifier.fromNamespaceAndPath(sid.getNamespace(), "textures/" + sid.getPath() + ".png");
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(tex);
        if (res.isEmpty()) return new int[]{-1};
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            long r = 0, g = 0, b = 0, w = 0;
            for (int y = 0; y < img.getHeight(); y++)
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getPixel(x, y);
                    int a = argb >>> 24;
                    if (a < 16) continue;
                    r += (long) ((argb >> 16) & 0xFF) * a; g += (long) ((argb >> 8) & 0xFF) * a; b += (long) (argb & 0xFF) * a; w += a;
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
