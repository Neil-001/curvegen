package dev.curvegen.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.paint.Lab;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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

    /** Colour for the current build: the top face for a floor, or for a 3D shape set to match by its top. */
    public static int of(Block block) {
        ShapeSettings s = CurveGenClient.SETTINGS;
        return of(block, s.is3d() ? s.topColours : s.floor);
    }

    public static int of(Block block, boolean top) {
        return (top ? TOP : SIDE).computeIfAbsent(block, b -> compute(b, top));
    }

    public static void clear() { SIDE.clear(); TOP.clear(); TEXTURES.clear(); }

    private static int compute(Block block, boolean top) {
        BlockState state = block.defaultBlockState();
        Minecraft mc = Minecraft.getInstance();
        try {
            BlockStateModel model = mc.getModelManager().getBlockModelShaper().getBlockModel(state);
            Direction face = top ? Direction.UP : Direction.NORTH;
            List<BlockModelPart> parts = new ArrayList<>();
            CurveGenClient.platform.modelParts(model, state, RandomSource.create(42L), parts);
            List<BakedQuad> quads = new ArrayList<>();
            for (BlockModelPart part : parts) quads.addAll(part.getQuads(face));
            if (quads.isEmpty())
                for (BlockModelPart part : parts)
                    for (BakedQuad q : part.getQuads(null)) if (q.direction() == face) quads.add(q);
            double r = 0, g = 0, b = 0, w = 0;
            for (BakedQuad q : quads) {
                int[] avg = average(q.sprite());
                if (avg == null) continue;
                int rr = avg[0], gg = avg[1], bb = avg[2];
                if (q.isTinted()) {
                    int tint = tint(state, q.tintIndex());
                    if (tint != -1) { rr = rr * ((tint >> 16) & 0xFF) / 255; gg = gg * ((tint >> 8) & 0xFF) / 255; bb = bb * (tint & 0xFF) / 255; }
                }
                r += rr * (double) avg[3]; g += gg * (double) avg[3]; b += bb * (double) avg[3]; w += avg[3];
            }
            if (w > 0) return ((int) (r / w) << 16) | ((int) (g / w) << 8) | (int) (b / w);

            // No quads on that face (unusual models): fall back to the particle texture.
            int[] p = average(CurveGenClient.platform.particleIcon(model, state));
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

    /** The tint a block has outside a world, or -1 for none. A modded tint that fails just means "no tint". */
    private static int tint(BlockState state, int index) {
        try {
            return Minecraft.getInstance().getBlockColors().getColor(state, null, null, index);
        } catch (Exception ignored) { return -1; }
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
        } catch (Exception ignored) {
            return new int[]{-1};
        }
    }

    private static double saturation(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return max == 0 ? 0 : (max - min) / (double) max;
    }

    // ---------- CIELAB ----------
    public static double[] lab(int rgb) { return Lab.of(rgb); }

    public static double distance(int rgbA, int rgbB) { return Lab.distance(rgbA, rgbB); }
}
