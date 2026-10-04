package dev.curvegen.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A solved shape trimmed to its non-empty cells: x from 0 to width-1 (left to right), y from 0 (bottom).
 * Carve cells use the same origin and may lie outside that box (they're cleared to air, not built).
 */
public record Layout(List<Cell> cells, List<Cell> carve, int width, int height) {
    /** above is the piece in the cell above, which an upright wall takes its height from. */
    public record Cell(int x, int y, int piece, int above) {}

    public static Layout of(Solver.Result r) {
        int i0 = Integer.MAX_VALUE, j0 = Integer.MAX_VALUE, i1 = -1, j1 = -1;
        for (int j = 0; j < r.ny(); j++)
            for (int i = 0; i < r.nx(); i++)
                if (r.at(i, j) != Pieces.EMPTY) {
                    i0 = Math.min(i0, i); i1 = Math.max(i1, i);
                    j0 = Math.min(j0, j); j1 = Math.max(j1, j);
                }
        List<Cell> cells = new ArrayList<>(), carve = new ArrayList<>();
        if (i1 < 0) return new Layout(cells, carve, 0, 0);
        boolean[] cv = r.target().carve;
        for (int j = 0; j < r.ny(); j++)
            for (int i = 0; i < r.nx(); i++) {
                int p = r.at(i, j);
                if (p != Pieces.EMPTY) cells.add(new Cell(i - i0, j - j0, p, j + 1 < r.ny() ? r.at(i, j + 1) : Pieces.EMPTY));
                else if (cv != null && cv[j * r.nx() + i]) carve.add(new Cell(i - i0, j - j0, Pieces.EMPTY, Pieces.EMPTY));
            }
        return new Layout(cells, carve, i1 - i0 + 1, j1 - j0 + 1);
    }

    public boolean isEmpty() { return cells.isEmpty(); }
}
