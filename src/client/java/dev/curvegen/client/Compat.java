package dev.curvegen.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Drawing and texture calls that differ between Minecraft versions, so the screens don't have to. */
public final class Compat {
    private Compat() {}

    /** A one-pixel border: the same four rectangles vanilla draws, under whatever name the version gives it. */
    public static void outline(GuiGraphics ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Draws a whole w×h texture with its top-left corner at (x, y), scaled. */
    public static void drawTexture(GuiGraphics ctx, Identifier id, int w, int h, float x, float y, float scale) {
        var m = ctx.pose();
        //? if >=1.21.6 {
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, w, h, w, h);
        m.popMatrix();
        //?} else {
        /*m.pushPose();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1);
        //? if >=1.21.2 {
        ctx.blit(net.minecraft.client.renderer.RenderType::guiTextured, id, 0, 0, 0f, 0f, w, h, w, h);
        //?} else
        //ctx.blit(id, 0, 0, 0f, 0f, w, h, w, h);
        m.popPose();
        *///?}
    }

    /**
     * Wrapped text without a shadow. Vanilla's drawWordWrap gained a shadow, and a new name in the jar, in 1.21.4,
     * so calling it would break the jar shared by 1.21.2 to 1.21.4.
     */
    public static void wordWrap(GuiGraphics ctx, Font font, Component text, int x, int y, int width, int color) {
        for (var line : font.split(text, width)) {
            ctx.drawString(font, line, x, y, color, false);
            y += font.lineHeight;
        }
    }

    public static void tooltip(GuiGraphics ctx, Font font, Component text, int x, int y) {
        //? if >=1.21.6 {
        ctx.setTooltipForNextFrame(font, text, x, y);
        //?} else
        //ctx.renderTooltip(font, text, x, y);
    }

    public static DynamicTexture texture(Identifier id, NativeImage image) {
        //? if >=1.21.5 {
        return new DynamicTexture(id::toString, image);
        //?} else
        /*return new DynamicTexture(image);*/
    }

    // NativeImage took ABGR colours until 1.21.2. These always take and return ARGB.

    public static int getPixel(NativeImage img, int x, int y) {
        //? if >=1.21.2 {
        return img.getPixel(x, y);
        //?} else
        /*return swapRedBlue(img.getPixelRGBA(x, y));*/
    }

    public static void setPixel(NativeImage img, int x, int y, int argb) {
        //? if >=1.21.2 {
        img.setPixel(x, y, argb);
        //?} else
        /*img.setPixelRGBA(x, y, swapRedBlue(argb));*/
    }

    public static void fill(NativeImage img, int argb) {
        //? if >=1.21.2 {
        img.fillRect(0, 0, img.getWidth(), img.getHeight(), argb);
        //?} else
        /*img.fillRect(0, 0, img.getWidth(), img.getHeight(), swapRedBlue(argb));*/
    }

    //? if <1.21.2 {
    /*private static int swapRedBlue(int c) {
        return (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
    }
    *///?}
}
