package dev.curvegen.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.ModSettings;
import dev.curvegen.client.Placement;
import dev.curvegen.client.PresetStore;
import dev.curvegen.client.edit.EditShape;
import dev.curvegen.client.edit.Editor;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.PresetData;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;
import dev.curvegen.core.edit.Option;
import dev.curvegen.core.edit.Radial;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * The radial menu. The cursor is free while it's open, and the way it points from the centre picks a wedge.
 * Scrolling steps the wedge's value at once. Letting go of the menu's key chooses the wedge, and so does a click.
 * With "Press" chosen in the settings, a press opens the menu and it stays until a wedge is clicked or the key is
 * pressed again.
 *
 * <p>Without a hologram the menu starts one. With one, it changes the shape's options and blocks, and places,
 * exports or cancels it. Every change goes through {@link Editor#edit}, so Z takes it back.
 */
public class RadialScreen extends Screen {
    private static final ShapeSettings S = CurveGenClient.SETTINGS;

    private enum Page { ROOT, SHAPES_2D, SHAPES_3D, OPTIONS, BLOCKS }

    /** What choosing a wedge leaves of the menu: it stays, it stays only after a click, or it closes. */
    private enum Stay { ALWAYS, CLICK, NEVER }

    /**
     * One wedge. {@code off} is why it's greyed out, or null. {@code scroll} takes +1 or -1 and is null for a wedge
     * with nothing to step.
     */
    private record Wedge(String label, String value, String off, String tip, ItemStack icon, IntConsumer scroll, Stay stay, Runnable select) {}

    /** Names the value a run of scrolling changes, so the run is one undo step. */
    private record Group(Object menu, String label) {}

    /** The 3D shapes in the plan. A null kind is a shape that hasn't been built yet. */
    private record Kind(String label, Gen gen) {}
    private static final List<Kind> KINDS_2D = List.of(new Kind("Ellipse", Gen.ELLIPSE), new Kind("Equation", Gen.EQUATION), new Kind("Bézier curve", Gen.BEZIER)),
            KINDS_3D = List.of(new Kind("Ellipsoid", Gen.ELLIPSOID), new Kind("Torus", Gen.TORUS), new Kind("3D equation", null),
                    new Kind("3D Bézier", null), new Kind("Surface", null));

    private static final int RING_OUT = Radial.RING, RING_IN = 9;

    private Page page = Page.ROOT;
    /** True once the menu no longer closes when its key comes up: from the start with "Press", or after a submenu opened. */
    private boolean latched = ModSettings.radialToggle;
    /** Whether the key that opened the menu has come up since. */
    private boolean keyUp;
    private List<Wedge> wedges = List.of();

    public RadialScreen() { super(Component.literal("Curve Generator menu")); }

    // ---------- the menus ----------

    private List<Wedge> build() {
        List<Wedge> out = new ArrayList<>();
        // A hologram can end while a submenu is open, and its submenus go with it.
        if (!Editor.isActive() && (page == Page.OPTIONS || page == Page.BLOCKS)) page = Page.ROOT;
        switch (page) {
            case ROOT -> { for (String id : Radial.order(Editor.isActive() ? Radial.EDIT : Radial.START, ModSettings.radialOrder)) out.add(top(id)); }
            case SHAPES_2D -> { for (Kind k : KINDS_2D) out.add(kind(k)); }
            case SHAPES_3D -> { for (Kind k : KINDS_3D) out.add(kind(k)); }
            case OPTIONS -> { for (Option o : Editor.options()) out.add(option(o)); }
            case BLOCKS -> { for (Family f : BlockChoices.FAMILIES) out.add(blocks(f)); }
        }
        if (page != Page.ROOT) out.add(new Wedge("Back", null, null, null, null, null, Stay.ALWAYS, () -> page = Page.ROOT));
        return out;
    }

    private Wedge nav(String label, String off, Page to) { return new Wedge(label, null, off, null, null, null, Stay.ALWAYS, () -> page = to); }
    private Wedge leaf(String label, String off, Runnable select) { return new Wedge(label, null, off, null, null, null, Stay.NEVER, select); }

    private Wedge top(String id) {
        String name = Radial.name(id);
        return switch (id) {
            case "2d" -> nav(name, null, Page.SHAPES_2D);
            case "3d" -> nav(name, null, Page.SHAPES_3D);
            case "presets" -> leaf(name, null, () -> minecraft.gui.setScreen(new PresetsScreen(null, S.gen, RadialScreen::loadPreset)));
            case "last" -> leaf(name, Editor.hasLast() ? null : "There's no earlier shape to bring back yet.", Editor::restoreLast);
            case "options" -> nav(name, Editor.options().isEmpty() ? "This shape has no options of its own." : null, Page.OPTIONS);
            case "blocks" -> nav(name, null, Page.BLOCKS);
            case "export" -> leaf(name, Editor.notReady(), () -> Placement.say(Component.literal(Editor.export())));
            case "full" -> leaf(name, null, () -> minecraft.gui.setScreen(new CurveScreen()));
            case "place" -> leaf(name, !Placement.canPlace(minecraft) ? "You need operator permissions to place. Use Export instead." : Editor.notReady(), Editor::confirm);
            default -> new Wedge(name, null, null, null, null, null, Stay.ALWAYS, () -> { Editor.cancel(); page = Page.ROOT; });
        };
    }

    private Wedge kind(Kind k) {
        String off = !EditShape.supports(k.gen) ? "This shape can't be put in the world yet." : null;
        return leaf(k.label, off, () -> {
            S.gen = k.gen;
            if (!Placement.canPlace(minecraft)) Placement.say(Component.literal("You'll need operator permissions to place this."));
            Editor.start();
        });
    }

    private static void loadPreset(PresetStore.Preset p, boolean withBlocks) {
        Gen g = p.gen();
        if (g == null) return;
        PresetData.apply(p.data, g, S);
        if (withBlocks && p.blocks != null) BlockChoices.apply(p.blocks, S, BlockChoices.CHOICE);
        // A hologram that's out takes the change when the presets screen closes.
        if (!Editor.isActive() && EditShape.supports(S.gen)) Editor.start();
    }

    private Wedge option(Option o) {
        Group group = new Group(this, o.label());
        return switch (o) {
            case Option.Number n -> new Wedge(n.label(), InputScreen.fmt(n.get().getAsDouble()), n.off(), null, null,
                    d -> Editor.edit(group, () -> n.set().accept(n.fit(n.get().getAsDouble() + d * n.step()))), Stay.NEVER,
                    () -> minecraft.gui.setScreen(InputScreen.number(n.label(), InputScreen.fmt(n.get().getAsDouble()), n.whole(), n.min(), n.max(),
                            v -> Editor.edit(null, () -> n.set().accept(v)))));
            case Option.Cycler c -> {
                IntConsumer step = d -> Editor.edit(group, () -> c.set().accept(Math.floorMod(c.get().getAsInt() + d, c.names().size())));
                yield new Wedge(c.label(), c.names().get(c.get().getAsInt()), c.off(), null, null, step, Stay.CLICK, () -> step.accept(1));
            }
            case Option.Text t -> new Wedge(t.label(), t.get().get(), t.off(), null, null, null, Stay.NEVER,
                    () -> minecraft.gui.setScreen(InputScreen.text(t.label(), t.get().get(), t.check(), v -> Editor.edit(null, () -> t.set().accept(v)))));
            case Option.Action a -> new Wedge(a.label(), null, a.off(), null, null, null, Stay.CLICK, () -> Editor.edit(null, a.run()));
        };
    }

    private Wedge blocks(Family f) {
        Block block = BlockChoices.CHOICE.get(f);
        String name = BlockChoices.familyName(f), off = null;
        if (!S.is3d() && S.floor && (f == Family.SLAB || f == Family.STAIRS))
            off = "From above, " + name.toLowerCase(Locale.ROOT) + " look like full blocks, so flat builds don't use them.";
        boolean full = f == Family.FULL;
        return new Wedge(name, full ? "Always on" : S.allows(f) ? "ON" : "OFF", off, block.getName().getString(),
                new ItemStack(block), full ? null : d -> Editor.edit(new Group(this, name), () -> S.allow(f, !S.allows(f))), Stay.NEVER,
                () -> minecraft.gui.setScreen(new BlockPickerScreen(null, f, chosen -> {
                    BlockChoices.CHOICE.put(f, chosen);
                    S.fullConnects = BlockChoices.fullBlockConnects();
                })));
    }

    // ---------- input ----------

    private double stretch() {
        int[] r = Radial.radii(width, height);
        return Radial.stretch(r[0], r[1]);
    }

    private int hovered(double mx, double my) { return Radial.wedgeAt(mx - width / 2.0, my - height / 2.0, wedges.size(), RING_IN, stretch()); }

    /** Chooses a wedge. {@code released} says the menu's key coming up chose it, which closes the menu unless a submenu opened. */
    private void choose(int index, boolean released) {
        wedges = build();
        Wedge w = index >= 0 && index < wedges.size() ? wedges.get(index) : null;
        if (w == null || w.off != null) {
            if (released) onClose();
            return;
        }
        AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
        if (w.stay == Stay.NEVER || w.stay == Stay.CLICK && released) minecraft.gui.setScreen(null);
        else latched = true;
        w.select.run();
    }

    private void back() {
        if (page == Page.ROOT) onClose(); else page = Page.ROOT;
    }

    private void keyCameUp() {
        boolean chooses = !latched && !keyUp;
        keyUp = true;
        if (chooses) choose(hovered(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()), minecraft.mouseHandler.getScaledYPos(minecraft.getWindow())), true);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (CurveGenClient.RADIAL.matches(event)) { keyCameUp(); return true; }
        return super.keyReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (CurveGenClient.RADIAL.matches(event)) {
            if (latched && keyUp) onClose();   // not while it's still down from opening the menu, when it only repeats
            return true;
        }
        return super.keyPressed(event);
    }

    /** The key can come up before the menu is there to see it, on a quick tap. */
    @Override
    public void tick() {
        if (keyUp) return;
        InputConstants.Key key = InputConstants.getKey(CurveGenClient.RADIAL.saveString());
        if (key.getType() == InputConstants.Type.KEYBOARD && !InputConstants.isKeyDown(key.getValue())) keyCameUp();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int at = hovered(event.x(), event.y());
        if (CurveGenClient.RADIAL.matchesMouse(event)) {
            // A mouse button doesn't repeat, so a press means it came up, seen or not. With the menu on a mouse
            // button, that button chooses a wedge like a click and closes the menu from the centre.
            keyUp = true;
            if (at >= 0) choose(at, false); else onClose();
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && at >= 0) choose(at, false);
        else back();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (CurveGenClient.RADIAL.matchesMouse(event)) { keyCameUp(); return true; }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        wedges = build();
        int at = hovered(mx, my);
        if (at >= 0 && v != 0) {
            Wedge w = wedges.get(at);
            if (w.off == null && w.scroll != null) w.scroll.accept(v > 0 ? 1 : -1);
        }
        return true;
    }

    // ---------- drawing ----------

    /**
     * Which wedge each pixel of the ring belongs to, or -1, for a ring of n wedges that is {@code half} pixels from
     * its centre to its side. Row by row from the top left.
     */
    private static byte[] ring(int n, int half, double stretch) {
        byte[] out = new byte[2 * half * 2 * RING_OUT];
        for (int j = 0; j < 2 * RING_OUT; j++)
            for (int i = 0; i < 2 * half; i++) {
                double dx = i + 0.5 - half, dy = j + 0.5 - RING_OUT, u = dx / stretch, r = Math.hypot(u, dy);
                byte w = -1;
                if (r >= RING_IN && r <= RING_OUT) {
                    // About a pixel of gap between two wedges.
                    double part = Math.atan2(u, -dy) / (2 * Math.PI) * n, edge = 0.5 - Math.abs(part - Math.rint(part));
                    if (n == 1 || edge * 2 * Math.PI * r / n >= 0.6) w = (byte) Radial.wedgeAt(dx, dy, n, 0, stretch);
                }
                out[j * 2 * half + i] = w;
            }
        return out;
    }

    private byte[] ring;
    private int ringFor, ringHalf;

    private String fit(String text, int room) {
        return font.width(text) <= room ? text : font.plainSubstrByWidth(text, room - font.width("…")) + "…";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        wedges = build();
        int n = wedges.size(), cx = width / 2, cy = height / 2, hot = hovered(mx, my);
        int half = (int) Math.ceil(RING_OUT * stretch()), ringW = 2 * half;
        if (ring == null || ringFor != n || ringHalf != half) { ring = ring(n, half, stretch()); ringFor = n; ringHalf = half; }
        for (int j = 0; j < 2 * RING_OUT; j++)
            for (int i = 0; i < ringW; ) {
                byte w = ring[j * ringW + i];
                int end = i + 1;
                while (end < ringW && ring[j * ringW + end] == w) end++;
                if (w >= 0) ctx.fill(cx - half + i, cy - RING_OUT + j, cx - half + end, cy - RING_OUT + j + 1,
                        w != hot ? 0xB0202830 : wedges.get(w).off != null ? 0xC0808A96 : 0xF0FFFFFF);
                i = end;
            }

        int[] radii = Radial.radii(width, height);
        int[][] at = Radial.labels(n, cx, cy, radii[0], radii[1]);
        for (int k = 0; k < n; k++) {
            Wedge w = wedges.get(k);
            int x = at[k][0], y = at[k][1];
            boolean on = w.off == null;
            ctx.fill(x, y, x + Radial.LABEL_W, y + Radial.LABEL_H, k == hot && on ? 0xF02C4A78 : 0xE0101317);
            ctx.outline(x, y, Radial.LABEL_W, Radial.LABEL_H, k != hot ? 0xFF5A6472 : on ? 0xFFFFFFFF : 0xFF808A96);
            int left = x + 4, room = Radial.LABEL_W - 8;
            if (w.icon != null) { ctx.item(w.icon, x + 3, y + 3); left += 18; room -= 18; }
            String label = fit(w.label, room);
            if (w.value == null) ctx.text(font, label, w.icon != null ? left : x + (Radial.LABEL_W - font.width(label)) / 2, y + 7, on ? 0xFFFFFFFF : 0xFF808A96);
            else {
                String value = fit(w.value, room);
                ctx.text(font, label, w.icon != null ? left : x + (Radial.LABEL_W - font.width(label)) / 2, y + 2, on ? 0xFFFFFFFF : 0xFF808A96);
                ctx.text(font, value, w.icon != null ? left : x + (Radial.LABEL_W - font.width(value)) / 2, y + 12, on ? 0xFFFFE08A : 0xFF808A96);
            }
        }

        String key = CurveGenClient.RADIAL.getTranslatedKeyMessage().getString();
        String title = switch (page) {
            case ROOT -> Editor.isActive() ? Editor.describe() : "Start a shape";
            case SHAPES_2D -> "2D shapes"; case SHAPES_3D -> "3D shapes"; case OPTIONS -> "Shape options"; case BLOCKS -> "Blocks";
        };
        // Along the bottom: what the wedge pointed at is or why it's greyed out, or else how the menu works.
        String tip = hot < 0 ? null : wedges.get(hot).off != null ? wedges.get(hot).off : wedges.get(hot).tip;
        boolean scrolls = wedges.stream().anyMatch(w -> w.scroll != null);
        String hint = title + ": " + (latched ? "click to choose, " + (scrolls ? "scroll to change, " : "") + key + " closes"
                : "let go of " + key + " to choose" + (scrolls ? ", scroll to change" : ""));
        var lines = font.split(Component.literal(tip != null ? tip : hint), width - 12);
        int shown = Math.min(2, lines.size()), top = height - 2 - shown * 10, w = 0;
        for (int k = 0; k < shown; k++) w = Math.max(w, font.width(lines.get(k)));
        ctx.fill((width - w) / 2 - 3, top - 2, (width + w) / 2 + 3, height, 0xE0101317);
        for (int k = 0; k < shown; k++)
            ctx.text(font, lines.get(k), (width - font.width(lines.get(k))) / 2, top + k * 10, tip != null && wedges.get(hot).off != null ? 0xFFFFE08A : 0xFFDDE3EA);
    }

    /** The game stays in view, so a change shows on the hologram at once. */
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) { }

    @Override
    public boolean isPauseScreen() { return false; }
}
