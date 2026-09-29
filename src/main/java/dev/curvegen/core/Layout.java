package dev.curvegen.core;

import java.util.ArrayList;
import java.util.List;

/** A solved shape trimmed to its non-empty cells: x from 0 to width-1 (left to right), y from 0 (bottom). */
public record Layout(List<Cell> cells, int width, int height) {
    public record Cell(int x, int y, int piece) {}

    public static Layout of(Solver.Result r) {
        int i0 = Integer.MAX_VALUE, j0 = Integer.MAX_VALUE, i1 = -1, j1 = -1;
        for (int j = 0; j < r.ny(); j++)
            for (int i = 0; i < r.nx(); i++)
                if (r.at(i, j) != Pieces.EMPTY) {
                    i0 = Math.min(i0, i); i1 = Math.max(i1, i);
                    j0 = Math.min(j0, j); j1 = Math.max(j1, j);
                }
        List<Cell> cells = new ArrayList<>();
        if (i1 < 0) return new Layout(cells, 0, 0);
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++) {
                int p = r.at(i, j);
                if (p != Pieces.EMPTY) cells.add(new Cell(i - i0, j - j0, p));
            }
        return new Layout(cells, i1 - i0 + 1, j1 - j0 + 1);
    }

    public boolean isEmpty() { return cells.isEmpty(); }
}
