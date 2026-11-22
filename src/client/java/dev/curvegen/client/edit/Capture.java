package dev.curvegen.client.edit;

import static dev.curvegen.client.Lang.tr;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.Lang;
import dev.curvegen.client.PaintBlocks;
import dev.curvegen.client.PatternStore;
import dev.curvegen.client.Placement;
import dev.curvegen.client.screen.NameDialogScreen;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.paint.Flatten;
import dev.curvegen.core.paint.Pattern;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

public final class Capture {
    private Capture() {}

    private static final int REACH = 64;
    private static final int LINE = 0xFFFFFFFF, TOO_BIG = 0xFFFF5566;

    private enum Step { OFF, FIRST, SECOND, NAMING }
    private static Step step = Step.OFF;
    private static BlockPos first, second;
    private static Consumer<Pattern> done;
    private static int swallowed;

    public static boolean isActive() { return step != Step.OFF; }

    public static void start(Consumer<Pattern> onSaved) {
        step = Step.FIRST; first = null; second = null; done = onSaved;
    }

    public static void back() {
        switch (step) {
            case SECOND, NAMING -> { step = Step.FIRST; first = null; second = null; }
            case FIRST -> {
                stop();
                Placement.say(Lang.component("curvegen.capture.stopped"));
            }
            case OFF -> { }
        }
    }

    private static void stop() { step = Step.OFF; first = null; second = null; done = null; swallowed = 0; }

    public static void tick(Minecraft mc) {
        if (step == Step.OFF) return;
        if (mc.player == null || mc.level == null) { stop(); return; }
        if (step == Step.NAMING && mc.gui.screen() == null) { step = Step.SECOND; second = null; }
    }

    private static BlockPos looking(Minecraft mc) {
        if (mc.player == null) return null;
        HitResult hit = mc.player.pick(REACH, 1f, false);
        return hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK ? b.getBlockPos() : null;
    }

    public static boolean mouseButton(int button, boolean pressed) {
        int bit = 1 << button;
        if (!pressed) {
            boolean took = (swallowed & bit) != 0;
            swallowed &= ~bit;
            return took;
        }
        if ((step != Step.FIRST && step != Step.SECOND) || button != InputConstants.MOUSE_BUTTON_RIGHT) return false;
        swallowed |= bit;
        Minecraft mc = Minecraft.getInstance();
        BlockPos at = looking(mc);
        if (at == null) { Placement.say(Lang.component("curvegen.capture.too_far", REACH)); return true; }
        if (step == Step.FIRST) {
            first = at;
            step = Step.SECOND;
            return true;
        }
        BlockPos lo = min(first, at);
        int[] size = size(first, at);
        Flatten.Frame f = frame(mc, lo, size);
        int w = f.width(size), h = f.height(size);
        if (w > Pattern.MAX || h > Pattern.MAX) {
            Placement.say(Lang.component("curvegen.capture.too_big", w, h, Pattern.MAX).withStyle(ChatFormatting.RED));
            return true;
        }
        Pattern p = read(mc, lo, size, f);
        if (p == null) { Placement.say(Lang.component("curvegen.capture.nothing")); return true; }
        second = at;
        step = Step.NAMING;
        swallowed &= ~bit;
        name(mc, p);
        return true;
    }

    private static BlockPos min(BlockPos a, BlockPos b) { return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())); }

    private static int[] size(BlockPos a, BlockPos b) {
        return new int[]{Math.abs(a.getX() - b.getX()) + 1, Math.abs(a.getY() - b.getY()) + 1, Math.abs(a.getZ() - b.getZ()) + 1};
    }

    private static Flatten.Frame frame(Minecraft mc, BlockPos lo, int[] size) {
        var cam = mc.gameRenderer.mainCamera();
        Vec3 eye = cam.position();
        Vector3fc look = cam.forwardVector(), up = cam.upVector();
        return Flatten.frame(size, new double[]{eye.x - lo.getX(), eye.y - lo.getY(), eye.z - lo.getZ()},
                new double[]{look.x(), look.y(), look.z()}, new double[]{up.x(), up.y(), up.z()});
    }

    private static Pattern read(Minecraft mc, BlockPos lo, int[] size, Flatten.Frame f) {
        Set<Block> full = new HashSet<>(BlockChoices.candidates(Family.FULL));
        Pattern p = Flatten.flatten("", size, f, (x, y, z) -> PaintBlocks.cell(mc.level.getBlockState(lo.offset(x, y, z)), full));
        for (int k = 0; k < p.paletteSize(); k++) if (!p.cell(k).empty()) return p;
        return null;
    }

    private static void name(Minecraft mc, Pattern p) {
        String suggested = PatternStore.unique(tr("curvegen.capture.default_name"));
        mc.gui.setScreen(new NameDialogScreen(null, tr("curvegen.capture.name_title"), suggested, name -> {
            if (name.isBlank()) return new NameDialogScreen.Check(false, tr("curvegen.screen.save"), tr("curvegen.capture.type_a_name"));
            if (PatternStore.find(name) != null) return new NameDialogScreen.Check(true, tr("curvegen.capture.replace"), tr("curvegen.capture.name_taken"));
            return new NameDialogScreen.Check(true, tr("curvegen.screen.save"), null);
        }, name -> {
            Pattern saved = PatternStore.save(p.renamed(name.trim())).pattern;
            Consumer<Pattern> then = done;
            stop();
            Placement.say(Lang.component("curvegen.capture.saved", name.trim()));
            if (then != null) then.accept(saved);
        }));
    }


    public static void render(SubmitNodeCollector out, PoseStack ms, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        if (first == null || mc.player == null) return;
        BlockPos other = second != null ? second : looking(mc);
        if (other == null) other = first;
        BlockPos lo = min(first, other);
        int[] size = size(first, other);
        Flatten.Frame f = frame(mc, lo, size);
        int colour = f.width(size) > Pattern.MAX || f.height(size) > Pattern.MAX ? TOO_BIG : LINE;
        Vector3fc forward = mc.gameRenderer.mainCamera().forwardVector();
        double cx = cam.x - lo.getX(), cy = cam.y - lo.getY(), cz = cam.z - lo.getZ();
        ms.pushPose();
        ms.translate(-cx, -cy, -cz);
        Editor.Lines lines = new Editor.Lines();
        double e = 0.005, x0 = -e, y0 = -e, z0 = -e, x1 = size[0] + e, y1 = size[1] + e, z1 = size[2] + e;
        for (int a = 0; a <= 1; a++)
            for (int b = 0; b <= 1; b++) {
                double ya = a == 0 ? y0 : y1, yb = b == 0 ? y0 : y1, za = a == 0 ? z0 : z1, zb = b == 0 ? z0 : z1, xa = a == 0 ? x0 : x1;
                lines.add(x0, ya, zb, x1, ya, zb, colour);
                lines.add(xa, y0, zb, xa, y1, zb, colour);
                lines.add(xa, yb, z0, xa, yb, z1, colour);
            }
        float width = mc.getWindow().getAppropriateLineWidth();
        double spread = width / 2 * Math.tan(Math.toRadians(mc.gameRenderer.mainCamera().getFov()) / 2) * 2 / mc.getWindow().getHeight();
        lines.submit(out, ms, new double[]{cx, cy, cz}, new double[]{forward.x(), forward.y(), forward.z()}, spread);
        ms.popPose();
    }

    public static void renderHud(GuiGraphicsExtractor dc) {
        Minecraft mc = Minecraft.getInstance();
        if ((step != Step.FIRST && step != Step.SECOND) || mc.player == null || mc.gui.screen() != null) return;
        var font = mc.font;
        String cancel = Editor.key(CurveGenClient.CANCEL);
        Component hint = Lang.component(step == Step.FIRST ? "curvegen.capture.hint_first" : "curvegen.capture.hint_second", REACH, cancel);
        int y = 6;
        for (var line : font.split(hint, dc.guiWidth() - 12)) {
            dc.fill(4, y - 2, 8 + font.width(line), y + 10, 0x90000000);
            dc.text(font, line, 6, y, 0xFFFFFFFF);
            y += 13;
        }
        if (step != Step.SECOND) return;
        BlockPos other = looking(mc);
        if (other == null) return;
        int[] size = size(first, other);
        Flatten.Frame f = frame(mc, min(first, other), size);
        int w = f.width(size), h = f.height(size), d = size[f.depth()];
        boolean big = w > Pattern.MAX || h > Pattern.MAX;
        String text = d > 1 ? tr("curvegen.capture.size_deep", w, h, d) : tr("curvegen.capture.size", w, h);
        int tx = dc.guiWidth() / 2 + 8, ty = dc.guiHeight() / 2 + 6;
        dc.fill(tx - 2, ty - 2, tx + font.width(text) + 2, ty + 10, 0x90000000);
        dc.text(font, text, tx, ty, big ? TOO_BIG : 0xFFFFFFFF);
        if (big) {
            String why = tr("curvegen.capture.size_limit", Pattern.MAX);
            dc.fill(tx - 2, ty + 11, tx + font.width(why) + 2, ty + 23, 0x90000000);
            dc.text(font, why, tx, ty + 13, TOO_BIG);
        }
    }
}
