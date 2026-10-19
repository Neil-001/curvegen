package dev.curvegen.client.screen;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import static dev.curvegen.client.Lang.tr;

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
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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

    /** The shapes the menu starts. A kind with a null {@code gen} would be one the editor can't edit yet, shown greyed out. */
    private record Kind(String label, Gen gen) {}
    private static final List<Kind> KINDS_2D = List.of(new Kind("curvegen.radial.kind.ellipse", Gen.ELLIPSE), new Kind("curvegen.radial.kind.equation", Gen.EQUATION), new Kind("curvegen.radial.kind.bezier", Gen.BEZIER)),
            KINDS_3D = List.of(new Kind("curvegen.radial.kind.ellipsoid", Gen.ELLIPSOID), new Kind("curvegen.radial.kind.torus", Gen.TORUS), new Kind("curvegen.radial.kind.equation3", Gen.EQUATION3),
                    new Kind("curvegen.radial.kind.bezier3", Gen.BEZIER3), new Kind("curvegen.radial.kind.surface", Gen.SURFACE));

    /** Whether the menu can put this kind of shape in the world. The full menu's Place follows it. */
    static boolean offers(Gen gen) {
        for (List<Kind> kinds : List.of(KINDS_2D, KINDS_3D))
            for (Kind k : kinds) if (k.gen == gen) return EditShape.supports(gen);
        return false;
    }

    private Page page = Page.ROOT;
    /** True once the menu no longer closes when its key comes up: from the start with "Press", or after a submenu opened. */
    private boolean latched = ModSettings.radialToggle;
    /** Whether the key that opened the menu has come up since. */
    private boolean keyUp;
    private List<Wedge> wedges = List.of();

    public RadialScreen() { super(Component.literal(tr("curvegen.radial.title"))); }

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
        if (page != Page.ROOT) out.add(new Wedge(tr("curvegen.radial.back"), null, null, null, null, null, Stay.ALWAYS, () -> page = Page.ROOT));
        return out;
    }

    private Wedge nav(String label, String off, Page to) { return new Wedge(label, null, off, null, null, null, Stay.ALWAYS, () -> page = to); }
    private Wedge leaf(String label, String off, Runnable select) { return new Wedge(label, null, off, null, null, null, Stay.NEVER, select); }

    private Wedge top(String id) {
        String name = tr(Radial.name(id));
        return switch (id) {
            case "2d" -> nav(name, null, Page.SHAPES_2D);
            case "3d" -> nav(name, null, Page.SHAPES_3D);
            case "presets" -> leaf(name, null, () -> minecraft.setScreen(new PresetsScreen(null, S.gen, RadialScreen::loadPreset)));
            case "last" -> leaf(name, Editor.hasLast() ? null : tr("curvegen.radial.no_last"), Editor::restoreLast);
            case "options" -> nav(name, Editor.options().isEmpty() ? tr("curvegen.radial.no_options") : null, Page.OPTIONS);
            case "blocks" -> nav(name, null, Page.BLOCKS);
            case "export" -> leaf(name, Editor.notReady(), () -> Placement.say(Component.literal(Editor.export())));
            case "full" -> leaf(name, null, () -> minecraft.setScreen(new CurveScreen()));
            case "place" -> leaf(name, !Placement.canPlace(minecraft) ? tr("curvegen.editor.permission") : Editor.notReady(), Editor::confirm);
            default -> new Wedge(name, null, null, null, null, null, Stay.ALWAYS, () -> { Editor.cancel(); page = Page.ROOT; });
        };
    }

    private Wedge kind(Kind k) {
        String off = !EditShape.supports(k.gen) ? tr("curvegen.radial.unsupported") : null;
        return leaf(tr(k.label), off, () -> {
            S.gen = k.gen;
            if (!Placement.canPlace(minecraft)) Placement.say(Component.literal(tr("curvegen.editor.permission_hint")));
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
            case Option.Number n -> new Wedge(tr(n.label()), InputScreen.fmt(n.get().getAsDouble()), n.off() == null ? null : tr(n.off()), null, null,
                    d -> Editor.edit(group, () -> n.set().accept(n.fit(n.get().getAsDouble() + d * n.step()))), Stay.NEVER,
                    () -> minecraft.setScreen(InputScreen.number(tr(n.label()), InputScreen.fmt(n.get().getAsDouble()), n.whole(), n.min(), n.max(),
                            v -> Editor.edit(null, () -> n.set().accept(v)))));
            case Option.Cycler c -> {
                IntConsumer step = d -> Editor.edit(group, () -> c.set().accept(Math.floorMod(c.get().getAsInt() + d, c.names().size())));
                yield new Wedge(tr(c.label()), tr(c.names().get(c.get().getAsInt())), c.off() == null ? null : tr(c.off()), null, null, step, Stay.CLICK, () -> step.accept(1));
            }
            case Option.Text t -> new Wedge(tr(t.label()), t.get().get(), t.off() == null ? null : tr(t.off()), null, null, null, Stay.NEVER,
                    () -> minecraft.setScreen(InputScreen.text(tr(t.label()), t.get().get(), v -> dev.curvegen.client.Lang.text(t.check().apply(v)), v -> Editor.edit(null, () -> t.set().accept(v)))));
            case Option.Action a -> new Wedge(tr(a.label()), null, a.off() == null ? null : tr(a.off()), null, null, null, Stay.CLICK, () -> Editor.edit(null, a.run()));
        };
    }

    private Wedge blocks(Family f) {
        Block block = BlockChoices.CHOICE.get(f);
        String name = BlockChoices.familyName(f), off = null;
        if (block == null)
            return new Wedge(name, tr("curvegen.option.off"), tr(BlockChoices.MISSING_REASON), null, null, null, Stay.NEVER, () -> { });
        if (!S.is3d() && S.floor && (f == Family.SLAB || f == Family.STAIRS))
            off = tr(f == Family.SLAB ? "curvegen.radial.flat_slab" : "curvegen.radial.flat_stairs");
        boolean full = f == Family.FULL;
        return new Wedge(name, full ? tr("curvegen.radial.always_on") : S.allows(f) ? tr("curvegen.option.on") : tr("curvegen.option.off"), off, block.getName().getString(),
                new ItemStack(block), full ? null : b -> Editor.edit(new Group(this, name), () -> S.allow(f, !S.allows(f))), Stay.NEVER,
                () -> minecraft.setScreen(new BlockPickerScreen(null, f, chosen -> {
                    BlockChoices.CHOICE.put(f, chosen);
                    S.fullConnects = BlockChoices.fullBlockConnects();
                })));
    }

    // ---------- input ----------

    private double stretch() {
        int[] r = Radial.radii(width, height);
        return Radial.stretch(r[0], r[1]);
    }

    /** The wedge under the cursor, measured from {@link Radial#centre}. */
    private int hovered(double mx, double my) {
        boolean round = ModSettings.radialRound;
        return Radial.wedgeAt(mx - Radial.centre(width, round), my - Radial.centre(height, round), wedges.size(), Radial.RING_IN, stretch(), round);
    }

    /** Chooses a wedge. {@code released} says the menu's key coming up chose it, which closes the menu unless a submenu opened. */
    private void choose(int index, boolean released) {
        wedges = build();
        Wedge w = index >= 0 && index < wedges.size() ? wedges.get(index) : null;
        if (w == null || w.off != null) {
            if (released) onClose();
            return;
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        if (w.stay == Stay.NEVER || w.stay == Stay.CLICK && released) minecraft.setScreen(null);
        else latched = true;
        w.select.run();
    }

    private void back() {
        if (page == Page.ROOT) onClose(); else page = Page.ROOT;
    }

    private void keyCameUp() {
        boolean chooses = !latched && !keyUp;
        keyUp = true;
        if (chooses) choose(hovered(minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth(),
                minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight()), true);
    }

    @Override
    public boolean keyReleased(int key, int scancode, int modifiers) {
        if (CurveGenClient.RADIAL.matches(key, scancode)) { keyCameUp(); return true; }
        return super.keyReleased(key, scancode, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (CurveGenClient.RADIAL.matches(key, scancode)) {
            if (latched && keyUp) onClose();   // not while it's still down from opening the menu, when it only repeats
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    /** The key can come up before the menu is there to see it, on a quick tap. */
    @Override
    public void tick() {
        if (keyUp) return;
        InputConstants.Key key = InputConstants.getKey(CurveGenClient.RADIAL.saveString());
        if (key.getType() == InputConstants.Type.KEYSYM && !InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key.getValue())) keyCameUp();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int at = hovered(mx, my);
        if (CurveGenClient.RADIAL.matchesMouse(button)) {
            // A mouse button doesn't repeat, so a press means it came up, seen or not. With the menu on a mouse
            // button, that button chooses a wedge like a click and closes the menu from the centre.
            keyUp = true;
            if (at >= 0) choose(at, false); else onClose();
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && at >= 0) choose(at, false);
        else back();
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (CurveGenClient.RADIAL.matchesMouse(button)) { keyCameUp(); return true; }
        return super.mouseReleased(mx, my, button);
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

    /** The ring's pixels from {@link Radial#ring}, and the wedge count, stretch and shape they're for. */
    private byte[] ring;
    private int ringFor;
    private double ringStretch;
    private boolean ringRound;

    private String fit(String text, int room) {
        return font.width(text) <= room ? text : font.plainSubstrByWidth(text, room - font.width("…")) + "…";
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        wedges = build();
        int n = wedges.size(), cx = width / 2, cy = height / 2, hot = hovered(mx, my);
        boolean round = ModSettings.radialRound;
        int half = Radial.ringHalf(stretch(), round), ringW = 2 * half;
        if (ring == null || ringFor != n || ringStretch != stretch() || ringRound != round) {
            ring = Radial.ring(n, stretch(), round); ringFor = n; ringStretch = stretch(); ringRound = round;
        }
        for (int j = 0; j < 2 * Radial.RING; j++)
            for (int i = 0; i < ringW; ) {
                byte w = ring[j * ringW + i];
                int end = i + 1;
                while (end < ringW && ring[j * ringW + end] == w) end++;
                if (w >= 0) ctx.fill(cx - half + i, cy - Radial.RING + j, cx - half + end, cy - Radial.RING + j + 1,
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
            ctx.renderOutline(x, y, Radial.LABEL_W, Radial.LABEL_H, k != hot ? 0xFF5A6472 : on ? 0xFFFFFFFF : 0xFF808A96);
            int left = x + 4, room = Radial.LABEL_W - 8;
            if (w.icon != null) { ctx.renderItem(w.icon, x + 3, y + 3); left += 18; room -= 18; }
            String label = fit(w.label, room);
            if (w.value == null) ctx.drawString(font, label, w.icon != null ? left : x + (Radial.LABEL_W - font.width(label)) / 2, y + 7, on ? 0xFFFFFFFF : 0xFF808A96);
            else {
                String value = fit(w.value, room);
                ctx.drawString(font, label, w.icon != null ? left : x + (Radial.LABEL_W - font.width(label)) / 2, y + 2, on ? 0xFFFFFFFF : 0xFF808A96);
                ctx.drawString(font, value, w.icon != null ? left : x + (Radial.LABEL_W - font.width(value)) / 2, y + 12, on ? 0xFFFFE08A : 0xFF808A96);
            }
        }

        String key = CurveGenClient.RADIAL.getTranslatedKeyMessage().getString();
        String title = switch (page) {
            case ROOT -> Editor.isActive() ? Editor.describe() : tr("curvegen.radial.start");
            case SHAPES_2D -> tr("curvegen.radial.2d"); case SHAPES_3D -> tr("curvegen.radial.3d"); case OPTIONS -> tr("curvegen.radial.options"); case BLOCKS -> tr("curvegen.radial.blocks");
        };
        // Along the bottom: what the wedge pointed at is or why it's greyed out, or else how the menu works.
        String tip = hot < 0 ? null : wedges.get(hot).off != null ? wedges.get(hot).off : wedges.get(hot).tip;
        boolean scrolls = wedges.stream().anyMatch(w -> w.scroll != null);
        String hint = tr("curvegen.radial.hint." + (latched ? "click" : "release") + (scrolls ? ".scroll" : ""), title, key);
        var lines = font.split(Component.literal(tip != null ? tip : hint), width - 12);
        int shown = Math.min(2, lines.size()), top = height - 2 - shown * 10, w = 0;
        for (int k = 0; k < shown; k++) w = Math.max(w, font.width(lines.get(k)));
        ctx.fill((width - w) / 2 - 3, top - 2, (width + w) / 2 + 3, height, 0xE0101317);
        for (int k = 0; k < shown; k++)
            ctx.drawString(font, lines.get(k), (width - font.width(lines.get(k))) / 2, top + k * 10, tip != null && wedges.get(hot).off != null ? 0xFFFFE08A : 0xFFDDE3EA);
    }

    /** The game stays in view, so a change shows on the hologram at once. */
    @Override
    public void renderBackground(GuiGraphics ctx, int mx, int my, float delta) { }

    @Override
    public boolean isPauseScreen() { return false; }
}
