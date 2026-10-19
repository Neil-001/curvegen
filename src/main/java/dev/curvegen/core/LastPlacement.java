package dev.curvegen.core;

/**
 * The last placement, which undo takes back and redo puts back again. Only one is kept: a new placement replaces it
 * and leaves nothing to redo.
 */
public final class LastPlacement<T> {
    private T placed, undone;

    /** A new placement, or one that redo has put back. */
    public void placed(T placement) { placed = placement; undone = null; }

    /** The placement to take back, or null when there's none in the world. */
    public T undo() {
        T p = placed;
        if (p != null) { undone = p; placed = null; }
        return p;
    }

    /** The placement that undo took back, or null. Pass what it becomes to {@link #placed}. */
    public T redo() {
        T p = undone;
        undone = null;
        return p;
    }
}
