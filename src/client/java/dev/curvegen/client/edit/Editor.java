package dev.curvegen.client.edit;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.LitematicExporter;
import dev.curvegen.client.ModSettings;
import dev.curvegen.client.Placement;
import dev.curvegen.client.screen.RadialScreen;
import dev.curvegen.core.Pieces;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.edit.Box;
import dev.curvegen.core.edit.HandleMath;
import dev.curvegen.core.edit.Option;
import dev.curvegen.core.edit.Orient;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * The in-world editor: one hologram at a time, with handles to drag and keys to move, resize and turn it. It knows
 * nothing about any particular shape. An {@link EditShape} supplies the handles' meaning and the blocks.
 *
 * <p>The box and the ideal curve are drawn from the live settings every frame, so they follow a drag at once. The
 * blocks come from a solve on a worker thread and catch up when it finishes.
 */
public final class Editor {
    private Editor() {}

    private static final ShapeSettings S = CurveGenClient.SETTINGS;
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Curve Generator hologram solver");
        t.setDaemon(true);
        return t;
    });

    /** How far a handle can be dragged from the player, and how far away the look ray can find the ground. */
    private static final double REACH = 256;
    private static final int UNDO_LIMIT = 100;
    /** Above this many blocks the HUD warns that placing will take a long time. */
    private static final int VERY_LARGE = 1_000_000;

    /** Everything undo puts back. */
    private record Snapshot(ShapeSettings settings, Box box, Orient orient, int[] follow, int[] pivot) {}

    private static boolean active, locked;
    private static EditShape shape;
    private static Box box;
    private static Orient orient;
    /** While unlocked: the way the player faced last tick, and how far they nudged the shape from where they look. */
    private static int facing;
    private static int[] follow = new int[3];
    /** What the box turns about, from {@link Box#pivot}. Null until the next turn, after anything else changed the box's size. */
    private static int[] pivot;
    private static final ArrayDeque<Snapshot> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    /** What the last hologram was when it ended, for the radial menu's "Last shape". */
    private static Snapshot last;

    // ---------- solving ----------
    private static boolean dirty;
    private static Future<Object> job;
    /** Tells the running solve its result is no longer wanted. */
    private static AtomicBoolean jobCancelled;
    /**
     * When solves started being dropped because the shape kept changing, in milliseconds, or 0. After
     * {@link #STARVED_MS} of that the next one runs to its end, so a long drag of a slow shape still shows blocks.
     */
    private static long droppingSince;
    private static final long STARVED_MS = 400, SLOW_MS = 1000;
    /** When the running solve started, and how long the last one to finish took. */
    private static long jobStarted, lastSolveMillis;
    /** The settings of the running solve, and the settings and block choices the hologram was last built from. */
    private static ShapeSettings jobSettings, shown;
    private static Map<Pieces.Family, Block> shownChoices;
    /** The orientation the running solve was started with, and the box's corner then (moved along with later nudges). */
    private static Orient jobOrient;
    private static int[] jobOrigin;
    private static Object solved;
    private static Hologram hologram;
    /** The block limit the hologram was built with, so a change in the settings screen rebuilds it. */
    private static int blockLimit;
    private static int[] hologramOrigin;
    private static BlockPos viewOrigin;
    private static int viewAge;
    private static boolean viewOverwrite, viewCarve;

    // ---------- handles ----------
    /** A handle: a control point (by index) or one of the box's 26 (by the side it sits on along each axis). */
    private record Handle(int point, int[] sign) {
        boolean isPoint() { return sign == null; }
        boolean same(Handle o) { return o != null && point == o.point && Arrays.equals(sign, o.sign); }
    }
    private static Handle hover;

    /** A drag in progress. Every frame starts again from {@code grab}, so the drag is a function of where the player looks now. */
    private static final class Drag {
        Handle handle;
        Snapshot grab;
        int button;
        /** The plane the handle moves in: its normal axis and where it sits along it. -1 for a face handle, which moves along {@link #axis}. */
        int normal = -1, axis = -1;
        double coord;
        /** From where the look ray met the plane or axis to the handle, when grabbed, so the handle doesn't jump. */
        double[] offset = new double[3];
        /** The handle's position when grabbed, and where it was last sent. */
        double[] start, target;
        /** Whether scrolling moves the handle along the normal, and which way is away from the player. */
        boolean scrolls;
        int away = 1;
        double scroll;
        /** Whether the undo entry for this drag is already on the stack (a copied point's is). */
        boolean recorded;
    }
    private static Drag drag;
    /** Mouse buttons whose press the editor took, so it takes their release too. */
    private static int swallowed;

    private static boolean screenOpen;
    private static Snapshot beforeScreen;
    /** Set when an undo step was recorded or taken back, so a screen that is open doesn't count that change as its own. */
    private static boolean rebase;
    /** The last {@link #edit} that recorded a step: what it was for, and the step. */
    private static Object editGroup;
    private static Snapshot editStep;

    public static boolean isActive() { return active; }
    public static boolean isLocked() { return locked; }

    // ---------- starting and ending ----------

    /** Spawns the hologram for the current settings, locked at the block the player is looking at. */
    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        reset();
        facing = mc.player.getDirection().get3DDataValue();
        reshape();
        orient = shape.orient(Orient.facing(facing));
        BlockPos at = target(mc);
        box = Box.spawn(at.getX(), at.getY(), at.getZ(), worldSize());
        begin();
    }

    /** Brings back the previous hologram where it was, locked. False when there hasn't been one. */
    public static boolean restoreLast() {
        if (last == null || Minecraft.getInstance().player == null) return false;
        reset();
        S.set(last.settings);
        reshape();
        orient = last.orient; box = last.box;
        begin();
        return true;
    }

    public static boolean hasLast() { return last != null; }

    /**
     * Picks the handler for the settings as they are now. A solve of the other family of shape, 2D or 3D, means
     * nothing to the new handler, so it goes, along with the blocks it gave.
     */
    private static void reshape() {
        EditShape next = EditShape.of(S);
        if (shape == null || next.getClass() != shape.getClass()) {
            dropJob();
            solved = null; hologram = null; hologramOrigin = null; shown = null;
        }
        shape = next;
    }

    /** Takes up settings something else changed, such as the menu: the handler, an orientation they allow, the box's size. */
    private static void adopt() {
        reshape();
        orient = shape.orient(orient);
        Box fitted = box.refit(worldSize());
        if (!fitted.equals(box)) { box = fitted; pivot = null; }
        dirty = true;
    }

    /** The menu can switch between a 2D and a 3D shape while it's open, and the hologram is drawn behind it all the while. */
    private static void sync() {
        if (active && (shape instanceof Shape3D) != S.is3d()) adopt();
    }

    private static void dropJob() {
        if (job == null) return;
        jobCancelled.set(true);
        job.cancel(false);
        job = null;
    }

    private static void reset() {
        dropJob();
        solved = null; hologram = null; hologramOrigin = null; viewOrigin = null;
        undo.clear(); redo.clear(); editGroup = null; editStep = null;
        drag = null; hover = null; follow = new int[3]; pivot = null;
        screenOpen = Minecraft.getInstance().gui.screen() != null; beforeScreen = null;
    }

    private static void begin() {
        S.fullConnects = BlockChoices.fullBlockConnects();
        locked = true; active = true; dirty = true;
    }

    private static void end() {
        last = snapshot();
        active = false; drag = null; hover = null;
        dropJob();
    }

    public static void cancel() {
        if (!active) return;
        end();
        Placement.say(Component.literal("Shape cancelled."));
    }

    /** Places the hologram's blocks and ends it. */
    public static void confirm() {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.player == null || mc.level == null) return;
        if (!Placement.canPlace(mc)) {
            Placement.say(Component.literal("You need operator permissions to place. Use Export instead.").withStyle(ChatFormatting.RED));
            return;
        }
        if (drag != null) endDrag();
        if (hologram == null || dirty || job != null) { Placement.say(Component.literal("Still working on the shape, try again in a moment.")); return; }
        if (hologram.error != null) { Placement.say(Component.literal("Fix the shape first: " + hologram.error).withStyle(ChatFormatting.RED)); return; }
        if (hologram.size() == 0) { Placement.say(Component.literal("The shape is empty, so there's nothing to place.")); return; }
        List<BlockPos> pos = new ArrayList<>(hologram.size());
        List<BlockState> states = new ArrayList<>(hologram.size()), old = new ArrayList<>(hologram.size());
        hologram.collect(mc, origin(), S.overwrite, S.carve, pos, states, old);
        if (pos.isEmpty()) { Placement.say(Component.literal("Nothing to place: every spot is taken and Replace is off.")); return; }
        Placement.place(mc, pos, states, old);
        end();
    }

    /**
     * Writes the hologram's blocks to a Litematica schematic, as they stand in the world. Returns a message for the
     * player either way.
     */
    public static String export() {
        Minecraft mc = Minecraft.getInstance();
        if (!active) return "There's no shape in the world to export.";
        // With the menu open, the hologram may still be the shape or the blocks from before a change made there.
        if (catchingUp()) return "Still working on the shape, try again in a moment.";
        if (hologram.error != null) return "Fix the shape first: " + hologram.error;
        if (hologram.size() == 0) return "The shape is empty, so there's nothing to export.";
        try {
            String author = mc.player != null ? mc.player.getName().getString() : "Curve Generator";
            String name = LitematicExporter.defaultName(S.gen.name().toLowerCase(java.util.Locale.ROOT));
            return "Exported to schematics/" + LitematicExporter.export(hologram.blocks(), name, author).getFileName();
        } catch (Exception e) {
            return "Export failed: " + e.getMessage();
        }
    }

    /**
     * Whether the hologram's blocks are still behind the settings. A screen that changes them asks this before it
     * exports, and asking has the hologram take the changes up, which it otherwise does when the screen closes.
     */
    public static boolean catchingUp() {
        if (!active) return false;
        if (hologram != null && !dirty && job == null && stale()) adopt();
        return hologram == null || dirty || job != null;
    }

    /** Whether the hologram was built from other shape settings or block choices than the ones set now. */
    private static boolean stale() { return !solvedFor(true); }

    /**
     * Whether the hologram's blocks, or with {@code shownOnly} unset the ones a running solve is about to give, are
     * for the shape settings and block choices as they are now.
     */
    private static boolean solvedFor(boolean shownOnly) {
        boolean running = !shownOnly && job != null && !jobCancelled.get();
        ShapeSettings ref = running ? jobSettings : shown;
        // A running solve's blocks get the choices as they are when it finishes, so only the ones on show can be out of date.
        if (ref == null || !running && !BlockChoices.CHOICE.equals(shownChoices)) return false;
        ShapeSettings now = S.copy();
        now.overwrite = ref.overwrite;   // Replace doesn't change which blocks the shape has
        // Nor does Carve, but a shape that only works out what to clear while it's on needs a solve when it comes on.
        if (shownOnly || !shape.solveUsesCarve() || !S.carve) now.carve = ref.carve;
        return now.same(ref);
    }

    // ---------- state ----------

    private static Snapshot snapshot() { return new Snapshot(S.copy(), box, orient, follow.clone(), pivot == null ? null : pivot.clone()); }
    private static int[] worldSize() { return orient.worldSize(shape.size()); }
    private static BlockPos origin() { return new BlockPos(hologramOrigin[0], hologramOrigin[1], hologramOrigin[2]); }

    /** Call before an edit, so undo can take it back. */
    private static void record() { record(snapshot()); }

    private static void record(Snapshot before) {
        rebase = true;
        undo.push(before);
        if (undo.size() > UNDO_LIMIT) undo.removeLast();
        redo.clear();
    }

    private static void restore(Snapshot s) {
        rebase = true;
        S.set(s.settings);
        // Block choices aren't part of a snapshot, so this flag has to follow the blocks chosen now.
        S.fullConnects = BlockChoices.fullBlockConnects();
        reshape();
        box = s.box; orient = s.orient; follow = s.follow.clone();
        pivot = s.pivot == null ? null : s.pivot.clone();
        dirty = true;
    }

    /** Takes back the last edit to the hologram. */
    public static void undo() {
        StepHold.cancel();   // the number box would replace a step that undo has just moved
        if (drag != null) endDrag();
        if (undo.isEmpty()) { Placement.say(Component.literal("Nothing to undo on this shape.")); return; }
        redo.push(snapshot());
        restore(undo.pop());
    }

    public static void redo() {
        StepHold.cancel();
        if (drag != null) endDrag();
        if (redo.isEmpty()) { Placement.say(Component.literal("Nothing to redo.")); return; }
        undo.push(snapshot());
        restore(redo.pop());
    }

    /**
     * The newest undo step, for {@link #retract}. Hold-to-type uses the pair to swap the step a key's press made for
     * the amount typed, so the two are one undo step.
     */
    public static Object lastStep() { return undo.peek(); }

    /** Takes back the newest undo step without leaving a redo, if it is still {@code step}. False if it isn't. */
    public static boolean retract(Object step) {
        if (step == null || undo.peek() != step) return false;
        restore(undo.pop());
        return true;
    }

    /**
     * Makes a change to the settings from outside the editor, such as the radial menu's, as one undo step, and fits
     * the box to the result. {@code change} only has to change the settings. Changes in a row with the same non-null
     * {@code group}, such as the steps of scrolling one value, share an undo step.
     */
    public static void edit(Object group, Runnable change) {
        if (!active) { change.run(); return; }
        if (drag != null) endDrag();
        Snapshot before = snapshot();
        change.run();
        if (S.same(before.settings)) return;
        boolean merges = group != null && group.equals(editGroup) && undo.peek() == editStep;
        if (!merges) {
            record(before);
            editGroup = group; editStep = before;
        }
        rebase = true;
        reshape();
        settle();
        adopt();
    }

    /** After an edit that can leave control points outside the box: grows it to hold them, where they are in the world. */
    private static void settle() {
        int[] shift = shape.settle();
        if (shift == null) return;
        Box grown = box.regrown(orient, shift, shape.size());
        if (!grown.equals(box)) { box = grown; pivot = null; }
    }

    /** Moves the box and everything drawn relative to it. */
    private static void translate(int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) return;
        box = box.moved(dx, dy, dz);
        if (pivot != null) { pivot[0] += 2 * dx; pivot[1] += 2 * dz; }
        for (int[] o : new int[][]{hologramOrigin, jobOrigin})
            if (o != null) { o[0] += dx; o[1] += dy; o[2] += dz; }
    }

    /** After the orientation changed: fits the box to it and gets the blocks to follow. */
    private static void turned() {
        if (pivot == null) pivot = box.pivot();
        box = box.about(pivot, worldSize());
        if (!shape.solveUsesOrient() && solved != null && !dirty && job == null) rebuild(new int[]{box.x(), box.y(), box.z()});
        else dirty = true;
    }

    private static void rebuild(int[] at) {
        blockLimit = ModSettings.hologramBlockLimit;
        hologram = new Hologram(shape.build(solved, orient), blockLimit);
        shownChoices = new EnumMap<>(BlockChoices.CHOICE);
        hologramOrigin = at;
        viewOrigin = null;
    }

    // ---------- keys ----------

    private static int playerFacing() { return Minecraft.getInstance().player.getDirection().get3DDataValue(); }

    /** A quarter turn about the vertical axis. */
    public static void rotate() {
        if (drag != null) return;
        record();
        orient = orient.turn();
        turned();
    }

    /** A quarter turn about the horizontal axis in front of the player. A 2D shape switches between upright and flat. */
    public static void tip() {
        if (drag != null) return;
        record();
        orient = shape.tip(orient, playerFacing());
        box = box.refit(worldSize());
        pivot = null;
        dirty = true;
    }

    /** Moves the whole shape. Negative amounts go the other way. */
    public static void nudge(Direction d, int amount) {
        if (drag != null || amount == 0) return;
        record();
        int dx = d.getStepX() * amount, dy = d.getStepY() * amount, dz = d.getStepZ() * amount;
        translate(dx, dy, dz);
        if (!locked) { follow[0] += dx; follow[1] += dy; follow[2] += dz; }
    }

    /** Moves the shape along whichever of the six directions the player is looking closest to. */
    public static void nudgeWithView(int amount) { nudge(Minecraft.getInstance().player.getNearestViewDirection(), amount); }

    /**
     * Moves the side of the box the player is looking towards: out for a positive amount, in for a negative one. The
     * opposite side stays put. A Bézier curve's points stretch with the box.
     */
    public static void bump(int amount) {
        if (drag != null || amount == 0) return;
        Direction d = Minecraft.getInstance().player.getNearestViewDirection();
        int axis = d.getAxis().ordinal();
        int[] sign = new int[3], want = box.size();
        sign[axis] = d.getAxisDirection().getStep();
        want[axis] = Math.max(1, want[axis] + amount);
        Snapshot before = snapshot();
        int own = orient.ownAxis(axis);
        int[] shift = shape.bump(own, sign[axis] * Orient.sign(orient.dir(own)), amount);
        if (shift != null) { box = box.regrown(orient, shift, shape.size()); pivot = null; }
        else resize(box, sign, false, want);
        if (!S.same(before.settings)) { record(before); dirty = true; }
    }

    /** Asks the shape for a world size and fits the box to what it allows. {@code sign} marks the sides that moved. */
    private static void resize(Box from, int[] sign, boolean symmetric, int[] worldWant) {
        boolean[] dragged = new boolean[3];
        for (int a = 0; a < 3; a++) dragged[orient.ownAxis(a)] = sign[a] != 0;
        shape.resize(orient.ownSize(worldWant), dragged);
        box = from.fit(sign, symmetric, worldSize());
        pivot = null;
    }

    public static void toggleReplace() {
        if (drag != null) endDrag();   // a drag starts again from its grab every frame, which would drop the change
        record();
        S.overwrite = !S.overwrite;
        Placement.say(Component.literal("Replace: ").append(CommonComponents.optionStatus(S.overwrite)));
    }

    public static void toggleCarve() {
        if (drag != null) endDrag();
        record();
        S.carve = !S.carve;
        if (S.carve && shape.solveUsesCarve()) dirty = true;
        Placement.say(Component.literal("Carve: ").append(CommonComponents.optionStatus(S.carve)));
    }

    /** Unlocked, the shape follows the player's view and hides its handles. Locked, it stays put and shows them. */
    public static void toggleLock() {
        if (drag != null) endDrag();
        locked = !locked;
        hover = null;
        if (!locked) { facing = playerFacing(); follow = new int[3]; }
    }

    /** Adds a point where the player looks at the curve. */
    public static void addPoint() {
        if (!locked || drag != null) return;
        if (shape.points().isEmpty()) { Placement.say(Component.literal("Only curves made of points can take another.")); return; }
        boolean alt = sneaking();
        edit(null, () -> insertAtLook(alt));
    }

    /** Sneaking turns an edit of a surface's row into one of its column. */
    private static boolean sneaking() { return Minecraft.getInstance().options.keyShift.isDown(); }
    private static boolean grid() { return shape.pointColumns() > 0; }
    private static String line(boolean alt) { return alt ? "column" : "row"; }
    private static String full(boolean alt) { return grid() ? "The surface can't take another " + line(alt) + "." : "This curve can't take any more points."; }
    private static String least(boolean alt) { return grid() ? "The surface needs the " + line(alt) + "s it has left." : "The shape needs the points it has left."; }
    private static String lookHere() { return grid() ? "Look at the surface where you want the new points." : "Look at the curve where you want the new point."; }

    /** Where the player looks at the curve or surface, in the shape's own axes, or null when they aren't looking at it. */
    private static double[] lookOnCurve() {
        double[] from = toOwn(eye), to = toOwn(new double[]{eye[0] + look[0], eye[1] + look[1], eye[2] + look[2]});
        double[] near = shape.lookAt(from, new double[]{to[0] - from[0], to[1] - from[1], to[2] - from[2]});
        if (near == null) {
            double[] segs = worldCurve();
            double[] world = segs == null ? null : HandleMath.nearestOnSegments(eye, look, segs);
            if (world == null) return null;
            double[] own = toOwn(world);
            near = new double[]{own[0], own[1], own[2], world[3], world[4]};
        }
        return near[3] > HandleMath.scaled(ModSettings.pickRadius * 2, near[4]) ? null : near;
    }

    private static void insertAtLook(boolean alt) {
        double[] near = lookOnCurve();
        if (near == null) Placement.say(Component.literal(lookHere()));
        else if (shape.insertPoint(near, alt) < 0) Placement.say(Component.literal(full(alt)));
    }

    /** Removes the point the player looks at. */
    public static void removePoint() {
        if (!locked || drag != null) return;
        if (hover == null || !hover.isPoint()) { Placement.say(Component.literal("Look at a point to remove it.")); return; }
        removePoint(hover.point, sneaking());
    }

    /**
     * What the radial menu's "Shape options" shows: the shape's own options, then adding and removing a point for a
     * shape that has points. The menu frees the cursor, so both act on what the player was looking at when it opened.
     */
    public static List<Option> options() {
        if (!active) return List.of();
        List<Option> out = new ArrayList<>(shape.options());
        // A surface lists its rows and columns as numbers of its own.
        if (!grid() && !shape.points().isEmpty()) {
            String unlocked = locked ? null : "Lock the shape first.";
            out.add(new Option.Action("Add point", unlocked != null ? unlocked
                    : lookOnCurve() == null ? "Look at the curve where you want the new point, then open this menu." : null, () -> insertAtLook(false)));
            Handle aimed = hover;
            out.add(new Option.Action("Remove point", unlocked != null ? unlocked
                    : aimed == null || !aimed.isPoint() ? "Look at the point to remove, then open this menu." : null, () -> {
                        if (!shape.removePoint(aimed.point)) Placement.say(Component.literal("The shape needs the points it has left."));
                        hover = null;
                    }));
        }
        return out;
    }

    /** The HUD's first line: the shape and its size. */
    public static String describe() { return active ? shape.describe() : ""; }

    /** Null when the hologram's blocks are ready to place or export. Otherwise why they aren't. */
    public static String notReady() {
        if (!active) return "There's no shape out.";
        if (hologram == null || dirty || job != null) return "Still working on the shape, try again in a moment.";
        if (hologram.error != null) return "Fix the shape first: " + hologram.error;
        if (hologram.size() == 0) return "The shape is empty.";
        return null;
    }

    private static void removePoint(int index, boolean alt) {
        Snapshot before = snapshot();
        if (!shape.removePoint(index, alt)) { Placement.say(Component.literal(least(alt))); return; }
        settle();
        record(before);
        hover = null;
        dirty = true;
    }

    // ---------- mouse ----------

    /**
     * A mouse button went down or up while no screen was open. Returns true when the editor used it, and the game
     * should then not see it. It only uses a click when a handle is highlighted.
     */
    public static boolean mouseButton(int button, boolean pressed) {
        int bit = 1 << button;
        if (!pressed) {
            if (drag != null && drag.button == button) endDrag();
            boolean took = (swallowed & bit) != 0;
            swallowed &= ~bit;
            return took;
        }
        if (!active || !locked || Minecraft.getInstance().gui.screen() != null) return false;
        boolean took = false;
        if (drag != null) took = true;   // a stray click mid-drag shouldn't break a block
        else if (hover != null) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT) { beginDrag(hover, button, false); took = true; }
            else if (hover.isPoint() && button == InputConstants.MOUSE_BUTTON_RIGHT) { removePoint(hover.point, sneaking()); took = true; }
            else if (hover.isPoint() && button == InputConstants.MOUSE_BUTTON_MIDDLE) {
                Snapshot before = snapshot();
                boolean alt = sneaking();
                int copy = shape.duplicatePoint(hover.point, alt);
                if (copy < 0) Placement.say(Component.literal(full(alt)));
                else {
                    record(before);
                    dirty = true;
                    beginDrag(new Handle(copy, null), button, true);
                }
                took = true;
            }
        }
        if (took) swallowed |= bit;
        return took;
    }

    /** The wheel turned while no screen was open. Returns true when a drag used it to move its handle nearer or further. */
    public static boolean mouseScroll(double amount) {
        if (drag == null || !drag.scrolls) return false;
        drag.scroll += ModSettings.invertDragScroll ? -amount : amount;
        return true;
    }

    private static void beginDrag(Handle h, int button, boolean recorded) {
        Drag d = new Drag();
        d.handle = h; d.button = button; d.recorded = recorded;
        d.grab = snapshot();
        d.start = handlePosition(h);
        d.target = d.start.clone();
        if (h.isPoint()) {
            int plane = shape.pointPlane();
            d.normal = plane >= 0 ? Orient.worldAxis(orient.dir(plane)) : HandleMath.facingAxis(look);
            d.scrolls = plane < 0;
        } else {
            int moving = 0, fixed = -1, only = -1;
            for (int a = 0; a < 3; a++) if (h.sign[a] != 0) { moving++; only = a; } else fixed = a;
            if (moving == 1) d.axis = only;
            else if (moving == 2) d.normal = fixed;
            else { d.normal = HandleMath.facingAxis(look); d.scrolls = true; }
        }
        if (d.normal >= 0) {
            d.coord = d.start[d.normal];
            d.away = look[d.normal] >= 0 ? 1 : -1;
            double[] hit = HandleMath.onPlane(eye, look, d.normal, d.coord, REACH);
            if (hit != null) for (int a = 0; a < 3; a++) d.offset[a] = d.start[a] - hit[a];
        } else {
            double t = HandleMath.alongAxis(eye, look, d.start, d.axis);
            if (!Double.isNaN(t)) d.offset[d.axis] = d.start[d.axis] - t;
        }
        drag = d;
        hover = h;
    }

    private static void endDrag() {
        Drag d = drag;
        drag = null;
        if (!d.recorded && (!S.same(d.grab.settings) || !box.equals(d.grab.box))) record(d.grab);
    }

    /** Puts the handle where the player looks now, starting again from the state the drag began in. */
    private static void updateDrag(Minecraft mc) {
        Drag d = drag;
        if (d.normal >= 0) {
            // Whole steps of the wheel move the plane itself, a block at a time.
            double coord = d.coord + (d.scrolls ? Math.round(d.scroll) * d.away : 0);
            double[] hit = HandleMath.onPlane(eye, look, d.normal, coord, REACH);
            if (hit != null) for (int a = 0; a < 3; a++) d.target[a] = hit[a] + d.offset[a];
            d.target[d.normal] = coord;
        } else {
            double t = HandleMath.alongAxis(eye, look, d.start, d.axis);
            if (!Double.isNaN(t) && Math.abs(t - eye[d.axis]) <= REACH) d.target[d.axis] = t + d.offset[d.axis];
        }
        ShapeSettings before = S.copy();
        Box boxBefore = box;
        S.set(d.grab.settings);
        box = d.grab.box;
        if (d.handle.isPoint()) {
            int[] ownSize = shape.size();
            double[] rel = {d.target[0] - box.x(), d.target[1] - box.y(), d.target[2] - box.z()};
            int[] shift = shape.movePoint(d.handle.point, orient.toOwn(rel, ownSize));
            if (shift != null) box = box.regrown(orient, shift, shape.size());
            pivot = null;
        } else {
            double[] target = new double[3];
            for (int a = 0; a < 3; a++) target[a] = d.handle.sign[a] != 0 && (d.axis < 0 || d.axis == a) ? d.target[a] : Double.NaN;
            boolean symmetric = mc.options.keyShift.isDown();
            resize(box, d.handle.sign, symmetric, box.dragSize(d.handle.sign, target, symmetric));
        }
        if (!S.same(before) || !box.equals(boxBefore)) dirty = true;
    }

    // ---------- geometry ----------

    /** The camera and the way it looks, refreshed every frame. */
    private static double[] eye = new double[3], look = {0, 0, 1};

    private static double[] handlePosition(Handle h) {
        if (!h.isPoint()) return box.handle(h.sign);
        double[] w = orient.toWorld(shape.points().get(h.point), shape.size());
        return new double[]{box.x() + w[0], box.y() + w[1], box.z() + w[2]};
    }

    private static double[] toOwn(double[] world) {
        return orient.toOwn(new double[]{world[0] - box.x(), world[1] - box.y(), world[2] - box.z()}, shape.size());
    }

    /** The ideal curve in world coordinates, or null when the shape has none to show yet. */
    private static double[] worldCurve() {
        double[] own = shape.curve(solved);
        if (own == null) return null;
        int[] size = shape.size();
        double[] out = new double[own.length];
        double[] p = new double[3];
        for (int k = 0; k + 2 < own.length; k += 3) {
            p[0] = own[k]; p[1] = own[k + 1]; p[2] = own[k + 2];
            double[] w = orient.toWorld(p, size);
            out[k] = box.x() + w[0]; out[k + 1] = box.y() + w[1]; out[k + 2] = box.z() + w[2];
        }
        return out;
    }

    /** Layers of box handles closer together than this many handle widths thin out to one. */
    private static final double THIN = 5;

    private static List<Handle> handles() {
        List<Handle> out = new ArrayList<>();
        int n = shape.points().size();
        for (int k = 0; k < n; k++) out.add(new Handle(k, null));
        // A drag keeps the handles it started with, or the one being dragged could thin out from under it.
        int[] size = (drag != null ? drag.grab.box : box).size();
        for (int[] sign : Box.handles(size, ModSettings.handleSize * THIN)) out.add(new Handle(-1, sign));
        return out;
    }

    private static BlockPos target(Minecraft mc) {
        HitResult hit = mc.player.pick(128, 1f, false);
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) return b.getBlockPos().relative(b.getDirection());
        return BlockPos.containing(hit.getLocation());
    }

    // ---------- every tick ----------

    public static void tick(Minecraft mc) {
        if (!active) return;
        if (mc.player == null || mc.level == null) { end(); return; }

        // The menu can change anything while it's open. When it closes, that becomes one edit.
        boolean open = mc.gui.screen() != null;
        if (open && !screenOpen) {
            if (drag != null) endDrag();
            beforeScreen = snapshot();
        }
        // An edit the editor recorded itself while the screen was open isn't the screen's to record again.
        if (rebase && beforeScreen != null) beforeScreen = snapshot();
        rebase = false;
        if (!open && screenOpen && beforeScreen != null) {
            Snapshot before = beforeScreen;
            beforeScreen = null;
            S.fullConnects = BlockChoices.fullBlockConnects();
            before.settings.fullConnects = S.fullConnects;   // it follows the blocks, which undo doesn't put back
            boolean wasDirty = dirty;
            adopt();
            // A screen that changed nothing, such as chat, shouldn't cost a solve, which can take seconds for a large shape.
            if (!wasDirty && solvedFor(false)) dirty = false;
            if (!S.same(before.settings)) record(before);
        }
        screenOpen = open;
        sync();

        if (!locked && !open) {
            int now = playerFacing();
            if (now != facing) {
                while (facing != now) { facing = Orient.clockwise(facing); orient = orient.turn(); }
                turned();
            }
            BlockPos at = target(mc);
            Box want = Box.spawn(at.getX() + follow[0], at.getY() + follow[1], at.getZ() + follow[2], box.size());
            translate(want.x() - box.x(), want.y() - box.y(), want.z() - box.z());
        }

        if (job != null && job.isDone()) {
            Object result = null;
            try { result = job.get(); }
            catch (Exception e) { Placement.say(Component.literal("Couldn't build this shape: " + e.getMessage()).withStyle(ChatFormatting.RED)); }
            int[] at = jobOrigin;
            job = null; jobOrigin = null;
            if (result != null) {
                solved = result;
                lastSolveMillis = System.currentTimeMillis() - jobStarted;
                shown = jobSettings;
                droppingSince = 0;
                // The solve may have settled a size the settings left open, such as an equation's locked height.
                Box fitted = box.refit(worldSize());
                boolean current = !dirty && (!shape.solveUsesOrient() || orient.equals(jobOrient));
                if (!fitted.equals(box)) { box = fitted; pivot = null; current = false; }
                rebuild(current ? new int[]{box.x(), box.y(), box.z()} : at);
            }
        }
        if (dirty && job != null && !jobCancelled.get()) {
            // The shape has moved on from what's being solved. A slow solve is dropped, unless that has gone on too long.
            long now = System.currentTimeMillis();
            if (droppingSince == 0) droppingSince = now;
            // A solve that takes seconds is always dropped: by the time it finished, its blocks would be long out of date.
            if (lastSolveMillis > SLOW_MS || now - jobStarted > SLOW_MS || now - droppingSince < STARVED_MS) jobCancelled.set(true);
        }
        if (dirty && job == null) {
            dirty = false;
            ShapeSettings copy = S.copy();
            jobSettings = S.copy();   // its own copy: a solve may settle values in the one it's given
            EditShape solver = shape;
            Orient o = jobOrient = orient;
            jobOrigin = new int[]{box.x(), box.y(), box.z()};
            AtomicBoolean cancelled = jobCancelled = new AtomicBoolean();
            jobStarted = System.currentTimeMillis();
            job = EXEC.submit(() -> solver.solve(copy, o, cancelled::get));
        }

        if (hologram != null && blockLimit != ModSettings.hologramBlockLimit && solved != null) rebuild(hologramOrigin);
        if (hologram != null) {
            BlockPos at = origin();
            if (!at.equals(viewOrigin) || viewOverwrite != S.overwrite || viewCarve != S.carve || ++viewAge >= 10) {
                hologram.refresh(mc, at, S.overwrite, S.carve);
                viewOrigin = at; viewOverwrite = S.overwrite; viewCarve = S.carve; viewAge = 0;
            }
        }
    }

    // ---------- rendering ----------

    /** Line segments for one frame: six coordinates and a colour each. */
    private static final class Lines {
        float[] xyz = new float[1024];
        int[] color = new int[170];
        int n;

        void add(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
            if (n * 6 + 6 > xyz.length) { xyz = Arrays.copyOf(xyz, xyz.length * 2); color = Arrays.copyOf(color, xyz.length / 6); }
            int o = n * 6;
            xyz[o] = (float) x1; xyz[o + 1] = (float) y1; xyz[o + 2] = (float) z1;
            xyz[o + 3] = (float) x2; xyz[o + 4] = (float) y2; xyz[o + 5] = (float) z2;
            color[n++] = argb;
        }

        /**
         * Draws each segment as a strip facing the camera, in the handles' render type. The game's own line types
         * fade into fog, which at a short render distance hides the far side of a large shape while its handles
         * still show. {@code cam} is the camera and {@code forward} the way it looks, and {@code spread} is how
         * wide half the line is one block in front of the camera, so the strip is as wide as a line on screen.
         */
        void submit(SubmitNodeCollector out, PoseStack ms, double[] cam, double[] forward, double spread) {
            if (n == 0) return;
            Hologram.submit(out, ms, RenderTypes.debugFilledBox(), true, (pose, vc) -> {
                for (int k = 0; k < n; k++) {
                    int o = k * 6;
                    double dx = xyz[o + 3] - xyz[o], dy = xyz[o + 4] - xyz[o + 1], dz = xyz[o + 5] - xyz[o + 2];
                    double ax = cam[0] - xyz[o], ay = cam[1] - xyz[o + 1], az = cam[2] - xyz[o + 2];
                    // Across the line as the camera sees it. It's the same from either end.
                    double px = dy * az - dz * ay, py = dz * ax - dx * az, pz = dx * ay - dy * ax;
                    double len = Math.sqrt(px * px + py * py + pz * pz);
                    if (len < 1e-9) continue;   // no length, or seen end on
                    // The width grows with the depth, and goes negative behind the camera, which keeps the part in view right.
                    double near = -(ax * forward[0] + ay * forward[1] + az * forward[2]);
                    double far = near + dx * forward[0] + dy * forward[1] + dz * forward[2];
                    float a = (float) (near * spread / len), b = (float) (far * spread / len);
                    float ux = (float) px, uy = (float) py, uz = (float) pz;
                    vc.addVertex(pose, xyz[o] - ux * a, xyz[o + 1] - uy * a, xyz[o + 2] - uz * a).setColor(color[k]);
                    vc.addVertex(pose, xyz[o] + ux * a, xyz[o + 1] + uy * a, xyz[o + 2] + uz * a).setColor(color[k]);
                    vc.addVertex(pose, xyz[o + 3] + ux * b, xyz[o + 4] + uy * b, xyz[o + 5] + uz * b).setColor(color[k]);
                    vc.addVertex(pose, xyz[o + 3] - ux * b, xyz[o + 4] - uy * b, xyz[o + 5] - uz * b).setColor(color[k]);
                }
            });
        }
    }

    private static final int WHITE = 0xE6FFFFFF, CURVE = 0xFFFFD24A, POINT = 0xFF3F7BE0, FACE = 0xFFF2F2F2, EDGE = 0xFF7FD4FF,
            CORNER = 0xFFFFA646, HOT = 0xFF6BFF8A, GRID = 0x50FFFFFF, POLYGON = 0xAA6EA0FF;
    /** How strongly a handle shows through terrain and other blocks in front of it, out of 255. */
    private static final int HIDDEN_ALPHA = 90;

    /**
     * Submits the hologram, its wireframe and its handles for this frame. {@code ms} is the world's pose stack and
     * {@code cam} the camera position. The game draws the submitted geometry later in the frame, so it must only
     * capture this frame's data.
     */
    public static void render(SubmitNodeCollector out, PoseStack ms, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.player == null) return;
        sync();
        Vector3fc forward = mc.gameRenderer.mainCamera().forwardVector();
        eye = new double[]{cam.x, cam.y, cam.z};
        look = new double[]{forward.x(), forward.y(), forward.z()};

        // The radial menu keeps the handles, and the one the player was looking at, for "Remove point".
        Screen screen = mc.gui.screen();
        boolean aiming = locked && screen == null, editing = aiming || locked && screen instanceof RadialScreen;
        if (drag != null) {
            if (aiming) updateDrag(mc); else endDrag();
        }
        List<Handle> handles = editing ? handles() : List.of();
        List<double[]> at = new ArrayList<>(handles.size());
        for (Handle h : handles) at.add(handlePosition(h));
        if (aiming && drag == null) {
            int k = HandleMath.pick(eye, look, at, ModSettings.pickRadius);
            hover = k < 0 ? null : handles.get(k);
        } else if (!editing) hover = null;

        // Everything below is relative to the box's corner, which keeps the numbers small far from the world's origin.
        double ox = box.x(), oy = box.y(), oz = box.z();
        double cx = cam.x - ox, cy = cam.y - oy, cz = cam.z - oz;
        float width = mc.getWindow().getAppropriateLineWidth();

        if (hologram != null && hologramOrigin != null) {
            double hx = hologramOrigin[0] - ox, hy = hologramOrigin[1] - oy, hz = hologramOrigin[2] - oz;
            ms.pushPose();
            ms.translate(hx - cx, hy - cy, hz - cz);
            boolean camWet = mc.gameRenderer.mainCamera().getFluidInCamera() == FogType.WATER;
            hologram.submit(out, ms, camWet, (float) ModSettings.hologramOpacity, cx - hx, cy - hy, cz - hz);
            ms.popPose();
        }

        ms.pushPose();
        ms.translate(-cx, -cy, -cz);
        Lines solid = new Lines(), faint = new Lines();
        int sx = box.sx(), sy = box.sy(), sz = box.sz();
        for (int a = 0; a <= 1; a++)
            for (int b = 0; b <= 1; b++) {
                solid.add(0, a * sy, b * sz, sx, a * sy, b * sz, WHITE);
                solid.add(a * sx, 0, b * sz, a * sx, sy, b * sz, WHITE);
                solid.add(a * sx, b * sy, 0, a * sx, b * sy, sz, WHITE);
            }
        double[] curve = worldCurve();
        if (curve != null)
            for (int k = 0; k + 5 < curve.length; k += 6)
                solid.add(curve[k] - ox, curve[k + 1] - oy, curve[k + 2] - oz, curve[k + 3] - ox, curve[k + 4] - oy, curve[k + 5] - oz, CURVE);
        if (editing) {
            // The control polygon: straight lines from each point to the next, and for a grid of points to the one in the next row too.
            int points = 0, cols = shape.pointColumns();
            while (points < handles.size() && handles.get(points).isPoint()) points++;
            for (int k = 0; k < points; k++) {
                double[] p = at.get(k);
                for (int next : new int[]{cols > 0 && (k + 1) % cols == 0 ? -1 : k + 1, cols > 0 ? k + cols : -1}) {
                    if (next < 0 || next >= points) continue;
                    double[] q = at.get(next);
                    faint.add(p[0] - ox, p[1] - oy, p[2] - oz, q[0] - ox, q[1] - oy, q[2] - oz, POLYGON);
                }
            }
        }
        if (drag != null) grid(faint, ox, oy, oz);
        // Half a line's width, in blocks, one block in front of the camera.
        double spread = width / 2 * Math.tan(Math.toRadians(mc.gameRenderer.mainCamera().getFov()) / 2) * 2 / mc.getWindow().getHeight();
        double[] camHere = {cx, cy, cz};
        solid.submit(out, ms, camHere, look, spread);
        faint.submit(out, ms, camHere, look, spread);

        if (!handles.isEmpty()) {
            double base = ModSettings.handleSize;
            // Twice: dimmed through whatever is in the way, then as they are where nothing is.
            for (boolean through : new boolean[]{true, false})
                Hologram.submit(out, ms, through ? Hologram.throughWalls() : RenderTypes.debugFilledBox(), true, (pose, vc) -> {
                    VertexConsumer fill = through ? Hologram.lit(vc) : vc;
                    for (int k = 0; k < handles.size(); k++) {
                        Handle h = handles.get(k);
                        double[] p = at.get(k);
                        boolean hot = h.same(hover);
                        double dist = Math.sqrt((p[0] - eye[0]) * (p[0] - eye[0]) + (p[1] - eye[1]) * (p[1] - eye[1]) + (p[2] - eye[2]) * (p[2] - eye[2]));
                        double r = HandleMath.scaled(base, dist) / 2 * (hot ? 1.35 : 1);
                        int moving = 0;
                        if (!h.isPoint()) for (int s : h.sign) if (s != 0) moving++;
                        int color = hot ? HOT : h.isPoint() ? POINT : moving == 1 ? FACE : moving == 2 ? EDGE : CORNER;
                        if (through) color = ARGB.color(HIDDEN_ALPHA, color);
                        double x = p[0] - ox, y = p[1] - oy, z = p[2] - oz;
                        Hologram.filledBox(pose, fill, x - r, y - r, z - r, x + r, y + r, z + r, color, cx, cy, cz);
                    }
                });
        }
        ms.popPose();
    }

    /** A faint grid on the plane the dragged handle moves in, or a line along its axis. */
    private static void grid(Lines lines, double ox, double oy, double oz) {
        Drag d = drag;
        double[] p = handlePosition(d.handle);
        int reach = 8;
        if (d.normal < 0) {
            double[] a = p.clone(), b = p.clone();
            a[d.axis] -= reach * 2; b[d.axis] += reach * 2;
            lines.add(a[0] - ox, a[1] - oy, a[2] - oz, b[0] - ox, b[1] - oy, b[2] - oz, GRID);
            return;
        }
        int u = (d.normal + 1) % 3, v = (d.normal + 2) % 3;
        double cu = Math.floor(p[u]), cv = Math.floor(p[v]);
        for (int k = -reach; k <= reach + 1; k++) {
            double[] a = p.clone(), b = p.clone();
            a[u] = b[u] = cu + k; a[v] = cv - reach; b[v] = cv + reach + 1;
            lines.add(a[0] - ox, a[1] - oy, a[2] - oz, b[0] - ox, b[1] - oy, b[2] - oz, GRID);
            a = p.clone(); b = p.clone();
            a[v] = b[v] = cv + k; a[u] = cu - reach; b[u] = cu + reach + 1;
            lines.add(a[0] - ox, a[1] - oy, a[2] - oz, b[0] - ox, b[1] - oy, b[2] - oz, GRID);
        }
    }

    // ---------- HUD ----------

    private static String key(KeyMapping k) { return k.getTranslatedKeyMessage().getString(); }

    public static void renderHud(GuiGraphicsExtractor dc) {
        Minecraft mc = Minecraft.getInstance();
        // A screen covers the hints or draws its own, and the menu's labels need the room.
        if (!active || mc.player == null || mc.gui.screen() != null) return;
        StepHold.renderBar(dc);
        var font = mc.font;
        int room = dc.guiWidth() - 12;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(shape.describe() + (locked ? "" : " (following your view)")).withStyle(ChatFormatting.WHITE));
        if (hologram != null && hologram.error != null) lines.add(Component.literal(hologram.error).withStyle(ChatFormatting.RED));
        else if (hologram == null) lines.add(Component.literal("Working…").withStyle(ChatFormatting.WHITE));
        else {
            int skipped = hologram.size() - hologram.placing();
            String what = hologram.placing() + " blocks to place" + (skipped > 0 ? " (" + skipped + " skipped, Replace is off)" : "");
            if (S.carve) what += ", " + hologram.clearing() + " to clear (in red)";
            if (dirty || job != null) what += "  Updating…";
            lines.add(Component.literal(what).withStyle(ChatFormatting.WHITE));
            if (!hologram.isDrawn()) lines.add(Component.literal("Large shape: only its outline is previewed.").withStyle(ChatFormatting.YELLOW));
            if (hologram.size() > VERY_LARGE)
                lines.add(Component.literal(String.format(java.util.Locale.ROOT, "%.1f million blocks. Placing this will take a long time.", hologram.size() / 1e6))
                        .withStyle(ChatFormatting.YELLOW));
        }
        if (!Placement.canPlace(mc))
            lines.add(Component.literal("Placing needs operator permissions. You can still edit and export.").withStyle(ChatFormatting.RED));

        // Key hints, flowed into as many lines as the screen's width needs.
        List<String> hints = new ArrayList<>(List.of(
                key(CurveGenClient.RADIAL) + " menu", key(CurveGenClient.CONFIRM) + " place", key(CurveGenClient.CANCEL) + " cancel",
                key(CurveGenClient.LOCK) + (locked ? " unlock" : " lock"), key(CurveGenClient.ROTATE) + " rotate", key(CurveGenClient.TIP) + " tip",
                key(CurveGenClient.FORWARD) + "/" + key(CurveGenClient.BACK) + " move",
                key(CurveGenClient.BUMP_OUT) + "/" + key(CurveGenClient.BUMP_IN) + " resize the side you face",
                key(CurveGenClient.UNDO) + " undo", key(CurveGenClient.REDO) + " redo",
                key(CurveGenClient.REPLACE) + " Replace: " + CommonComponents.optionStatus(S.overwrite).getString(),
                key(CurveGenClient.CARVE) + " Carve: " + CommonComponents.optionStatus(S.carve).getString()));
        if (locked) {
            hints.add("drag a handle to resize, sneak for both sides");
            if (!shape.points().isEmpty()) {
                if (grid()) {
                    hints.add("drag a point, right-click removes its row, middle-click adds one, sneak for a column");
                    hints.add(key(CurveGenClient.ADD_POINT) + " add a row where you look");
                    hints.add(key(CurveGenClient.REMOVE_POINT) + " remove a row");
                } else {
                    hints.add("drag a point, right-click removes it, middle-click copies it");
                    hints.add(key(CurveGenClient.ADD_POINT) + " add a point");
                    hints.add(key(CurveGenClient.REMOVE_POINT) + " remove a point");
                }
            }
        }
        StringBuilder line = new StringBuilder();
        for (String h : hints) {
            if (line.length() > 0 && font.width(line + ", " + h) > room) {
                lines.add(Component.literal(line.toString()).withStyle(ChatFormatting.GRAY));
                line.setLength(0);
            }
            line.append(line.length() > 0 ? ", " : "").append(h);
        }
        if (line.length() > 0) lines.add(Component.literal(line.toString()).withStyle(ChatFormatting.GRAY));

        int y = 6;
        for (Component t : lines) {
            dc.fill(4, y - 2, 8 + font.width(t), y + 10, 0x90000000);
            dc.text(font, t, 6, y, 0xFFFFFFFF);
            y += 13;
        }
    }
}
