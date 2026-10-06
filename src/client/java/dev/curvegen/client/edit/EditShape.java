package dev.curvegen.client.edit;

import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.edit.Orient;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The part of the in-world editor that depends on the kind of shape: which handles it has, what dragging or bumping
 * them does to {@link ShapeSettings}, and how it becomes blocks. {@link Editor} does the rest, for any shape.
 *
 * <p>A shape has its own axes x, y and z and a box measured along them, in blocks from its own minimum corner.
 * An {@link Orient} says which way those axes point in the world. Everything a shape takes or returns here is in its
 * own axes, except {@link Content}, which is in world axes because that's what gets placed.
 *
 * <p>Edits change the live settings ({@code CurveGenClient.SETTINGS}). While a handle is dragged, the editor puts the
 * settings back as they were when the drag started before every call, so each call starts from the same state and
 * needn't be reversible.
 */
public interface EditShape {
    /** One block of a solved shape: its offset from the minimum corner of the shape's box in world axes, and what goes there. */
    record Placed(int dx, int dy, int dz, BlockState state) {}

    /**
     * What a solve hands the hologram. {@code carve} lists the offsets Carve clears, {@code topColours} says whether
     * the hologram tints blocks by their top texture rather than their side, and {@code error} is a message for the
     * player when the shape couldn't be solved (the lists are then empty), or null.
     */
    record Content(List<Placed> blocks, List<BlockPos> carve, boolean topColours, String error) {}

    /** The shape for the current settings. */
    static EditShape of(ShapeSettings s) { return new Shape2D(s); }

    /** The box the handles sit on, along the shape's own axes. */
    int[] size();

    /**
     * Asks for a new box. {@code dragged} says which own axes the player moved; a shape may change the others to keep
     * its proportions. The shape applies what its limits allow, and the editor reads {@link #size} back.
     */
    void resize(int[] want, boolean[] dragged);

    /** The orientation these settings allow that is closest to {@code current}. Called after anything changes them. */
    default Orient orient(Orient current) { return current; }

    /** A quarter-turn tip for a player facing the horizontal direction {@code forward}. May change the settings. */
    default Orient tip(Orient current, int forward) { return current.tip(forward); }

    /** Control points the player can drag, as {x, y, z} in own axes. Empty for shapes without any. */
    default List<double[]> points() { return List.of(); }

    /** The own axis the points can't move along, for a shape drawn on a plane, or -1 when they move freely. */
    default int pointPlane() { return -1; }

    /**
     * Moves a point, applying the shape's own snapping. Returns how many cells the box's own minimum corner moved
     * along each own axis, for a shape that grows to keep the point inside, or null when it didn't move.
     */
    default int[] movePoint(int index, double[] to) { return null; }

    /** Removes a point (a whole row or column, on a surface). False when the shape can't lose one. */
    default boolean removePoint(int index) { return false; }

    /** Adds a point where the player looks at the curve. Returns its index, or -1 when the shape can't take one. */
    default int insertPoint(double[] at) { return -1; }

    /** Copies a point, half a block towards the next. Returns the copy's index, or -1 when the shape can't take one. */
    default int duplicatePoint(int index) { return -1; }

    /**
     * The ideal curve as line segments {x1,y1,z1,x2,y2,z2,...} in own axes, or null. The editor draws it every frame,
     * so this has to be cheap. {@code solved} is the last result of {@link #solve}, or null before the first.
     */
    double[] curve(Object solved);

    /**
     * Solves the shape. Runs on a worker thread with a private copy of the settings, so it mustn't touch the game or
     * the live settings. The result goes to {@link #build} and {@link #curve}.
     */
    Object solve(ShapeSettings copy, Orient orient);

    /** False when {@link #solve} ignores the orientation, so turning the shape only needs {@link #build} again. */
    default boolean solveUsesOrient() { return true; }

    /** Turns a solve into blocks for an orientation. Runs on the client thread, once per solve or turn. */
    Content build(Object solved, Orient orient);

    /** One line for the HUD that names the shape and its size. */
    String describe();
}
