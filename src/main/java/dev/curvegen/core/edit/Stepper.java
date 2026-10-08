package dev.curvegen.core.edit;

/** What an arrow click or a notch of the mouse wheel does to a number field's value. */
public final class Stepper {
    private Stepper() {}

    /** The value a step further on, kept within the limits. A value already past a limit comes back to it. */
    public static double step(double v, double step, double min, double max) {
        return Math.max(min, Math.min(max, v + step));
    }

    /**
     * The next multiple of {@code grid} above the value for a {@code dir} of 1, or below it for -1, kept within the
     * limits. A value on the grid moves a whole step, and one off it moves onto it.
     */
    public static double next(double v, double grid, int dir, double min, double max) {
        double cells = v / grid, eps = 1e-6;
        double to = dir > 0 ? Math.floor(cells + eps) + 1 : Math.ceil(cells - eps) - 1;
        return Math.max(min, Math.min(max, to * grid));
    }
}
