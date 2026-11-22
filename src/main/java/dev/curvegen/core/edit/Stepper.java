package dev.curvegen.core.edit;

public final class Stepper {
    private Stepper() {}

    public static double step(double v, double step, double min, double max) {
        return Math.max(min, Math.min(max, v + step));
    }

    public static double next(double v, double grid, int dir, double min, double max) {
        double cells = v / grid, eps = 1e-6;
        double to = dir > 0 ? Math.floor(cells + eps) + 1 : Math.ceil(cells - eps) - 1;
        return Math.max(min, Math.min(max, to * grid));
    }
}
