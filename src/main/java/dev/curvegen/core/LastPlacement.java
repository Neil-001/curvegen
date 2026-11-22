package dev.curvegen.core;

public final class LastPlacement<T> {
    private T placed, undone;

    public void placed(T placement) { placed = placement; undone = null; }

    public T undo() {
        T p = placed;
        if (p != null) { undone = p; placed = null; }
        return p;
    }

    public T redo() {
        T p = undone;
        undone = null;
        return p;
    }
}
