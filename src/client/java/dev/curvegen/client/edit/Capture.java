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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Capturing a pattern from the world: the player right-clicks two corners of a box, names it, and it is saved to the
 * pattern library. The Cancel key backs out a step at a time: the second corner, the first, then out of capture. The
 * box is outlined from the first click, with the pattern's size beside the crosshair, and a line at the top of the
 * screen says what the next click does. The pattern lies as the player sees the box when they click the second corner
 * ({@link Flatten}), and a box too big for a pattern that way is refused then.
 *
 * <p>Each loader passes it mouse buttons before the editor, and draws its box and its lines beside the editor's. It
 * only takes right clicks, so a hologram that is out keeps its handles.
 */
public final class Capture {
    private Capture() {}

    /** How far away a corner can be marked. */
    private static final int REACH = 64;
    private static final int LINE = 0xFFFFFFFF, TOO_BIG = 0xFFFF5566;

    private enum Step { OFF, FIRST, SECOND, NAMING }
    private static Step step = Step.OFF;
    private static BlockPos first, second;
    /** What to do with the pattern once it's named and saved. */
    private static Consumer<Pattern> done;
    /** Buttons whose press was taken, so their release is taken too. */
    private static int swallowed;

    public static boolean isActive() { return step != Step.OFF; }

    /** Starts waiting for the first corner. {@code onSaved} gets the pattern once it's been named and saved. */
    public static void start(Consumer<Pattern> onSaved) {
        step = Step.FIRST; first = null; second = null; done = onSaved;
    }

    /** The Cancel key: back out one step. */
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

    /** Its release then goes to whatever screen is open, or to a game that never saw the press, so nothing is owed. */
    private static void stop() { step = Step.OFF; first = null; second = null; done = null; swallowed = 0; }

    /** Call every client tick. Naming that was cancelled goes back to waiting for the second corner. */
    public static void tick(Minecraft mc) {
        if (step == Step.OFF) return;
        if (mc.player == null || mc.level == null) { stop(); return; }
        if (step == Step.NAMING && mc.screen == null) { step = Step.SECOND; second = null; }
    }

    /** The block the player is looking at, within reach, or null. */
    private static BlockPos looking(Minecraft mc) {
        if (mc.player == null) return null;
        HitResult hit = mc.player.pick(REACH, 1f, false);
        return hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK ? b.getBlockPos() : null;
    }

    /**
     * A mouse button went down or up while no screen was open. Returns true when capture used it, and the game and
     * the editor should then not see it.
     */
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
        swallowed &= ~bit;   // the release goes to the dialog
        name(mc, p);
        return true;
    }

    private static BlockPos min(BlockPos a, BlockPos b) { return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())); }

    private static int[] size(BlockPos a, BlockPos b) {
        return new int[]{Math.abs(a.getX() - b.getX()) + 1, Math.abs(a.getY() - b.getY()) + 1, Math.abs(a.getZ() - b.getZ()) + 1};
    }

    /** How the pattern lies in the box as the player sees it now. */
    private static Flatten.Frame frame(Minecraft mc, BlockPos lo, int[] size) {
        var cam = mc.gameRenderer.getMainCamera();
        Vec3 eye = cam.position();
        Vector3fc look = cam.forwardVector(), up = cam.upVector();
        return Flatten.frame(size, new double[]{eye.x - lo.getX(), eye.y - lo.getY(), eye.z - lo.getZ()},
                new double[]{look.x(), look.y(), look.z()}, new double[]{up.x(), up.y(), up.z()});
    }

    /** The pattern of the box from {@code lo}, or null when it holds no block a pattern can use. */
    private static Pattern read(Minecraft mc, BlockPos lo, int[] size, Flatten.Frame f) {
        Set<Block> full = new HashSet<>(BlockChoices.candidates(Family.FULL));
        Pattern p = Flatten.flatten("", size, f, (x, y, z) -> PaintBlocks.cell(mc.level.getBlockState(lo.offset(x, y, z)), full));
        for (int k = 0; k < p.paletteSize(); k++) if (!p.cell(k).empty()) return p;
        return null;
    }

    /** Asks for a name, then saves the pattern and hands it on. Cancelling goes back to the second corner. */
    private static void name(Minecraft mc, Pattern p) {
        String suggested = PatternStore.unique(tr("curvegen.capture.default_name"));
        mc.setScreen(new NameDialogScreen(null, tr("curvegen.capture.name_title"), suggested, name -> {
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

    // ---------- drawing ----------

    /**
     * Outlines the box from the first corner to the second, or to the block looked at while the second is to come.
     * {@link Editor#render} calls this each frame, so the outline goes in the same queues as the hologram's lines.
     */
    static void render(PoseStack ms, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        if (first == null || mc.player == null) return;
        BlockPos other = second != null ? second : looking(mc);
        if (other == null) other = first;
        BlockPos lo = min(first, other);
        int[] size = size(first, other);
        Flatten.Frame f = frame(mc, lo, size);
        int colour = f.width(size) > Pattern.MAX || f.height(size) > Pattern.MAX ? TOO_BIG : LINE;
        Vector3fc forward = mc.gameRenderer.getMainCamera().forwardVector();
        double cx = cam.x - lo.getX(), cy = cam.y - lo.getY(), cz = cam.z - lo.getZ();
        ms.pushPose();
        ms.translate(-cx, -cy, -cz);
        Editor.Lines lines = new Editor.Lines();
        // A little outside the blocks, so the lines don't hide in their faces.
        double e = 0.005, x0 = -e, y0 = -e, z0 = -e, x1 = size[0] + e, y1 = size[1] + e, z1 = size[2] + e;
        for (int a = 0; a <= 1; a++)
            for (int b = 0; b <= 1; b++) {
                double ya = a == 0 ? y0 : y1, yb = b == 0 ? y0 : y1, za = a == 0 ? z0 : z1, zb = b == 0 ? z0 : z1, xa = a == 0 ? x0 : x1;
                lines.add(x0, ya, zb, x1, ya, zb, colour);
                lines.add(xa, y0, zb, xa, y1, zb, colour);
                lines.add(xa, yb, z0, xa, yb, z1, colour);
            }
        float width = mc.getWindow().getAppropriateLineWidth();
        double spread = width / 2 * Editor.halfViewHeight(mc) * 2 / mc.getWindow().getHeight();
        lines.submit(ms, new double[]{cx, cy, cz}, new double[]{forward.x(), forward.y(), forward.z()}, spread);
        ms.popPose();
    }

    /** The hint line at the top left, where the editor's lines go while it's out, and the box's size by the crosshair. */
    public static void renderHud(GuiGraphics dc) {
        Minecraft mc = Minecraft.getInstance();
        if ((step != Step.FIRST && step != Step.SECOND) || mc.player == null || mc.screen != null) return;
        var font = mc.font;
        String cancel = Editor.key(CurveGenClient.CANCEL);
        Component hint = Lang.component(step == Step.FIRST ? "curvegen.capture.hint_first" : "curvegen.capture.hint_second", REACH, cancel);
        int y = 6;
        for (var line : font.split(hint, dc.guiWidth() - 12)) {
            dc.fill(4, y - 2, 8 + font.width(line), y + 10, 0x90000000);
            dc.drawString(font, line, 6, y, 0xFFFFFFFF);
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
        dc.drawString(font, text, tx, ty, big ? TOO_BIG : 0xFFFFFFFF);
        if (big) {
            String why = tr("curvegen.capture.size_limit", Pattern.MAX);
            dc.fill(tx - 2, ty + 11, tx + font.width(why) + 2, ty + 23, 0x90000000);
            dc.drawString(font, why, tx, ty + 13, TOO_BIG);
        }
    }
}
