package dev.curvegen.core.edit;

import java.util.List;

public final class Orbit {
    public static final double MIN_SCALE = 0.25, MAX_SCALE = 160, MAX_PITCH = Math.toRadians(89);

    public double yaw = Math.toRadians(-35), pitch = Math.toRadians(28);
    public double scale = 8;
    public double tx, ty, tz, ox, oy;

    public Orbit copy() {
        Orbit o = new Orbit();
        o.yaw = yaw; o.pitch = pitch; o.scale = scale; o.tx = tx; o.ty = ty; o.tz = tz; o.ox = ox; o.oy = oy;
        return o;
    }

    public Orbit times(double k) {
        Orbit o = copy();
        o.scale *= k; o.ox *= k; o.oy *= k;
        return o;
    }

    public boolean same(Orbit o) {
        return o != null && yaw == o.yaw && pitch == o.pitch && scale == o.scale && tx == o.tx && ty == o.ty && tz == o.tz && ox == o.ox && oy == o.oy;
    }

    public double[] back() {
        double c = Math.cos(pitch);
        return new double[]{c * Math.sin(yaw), Math.sin(pitch), c * Math.cos(yaw)};
    }
    public double[] forward() { double[] b = back(); return new double[]{-b[0], -b[1], -b[2]}; }
    public double[] right() { return new double[]{Math.cos(yaw), 0, -Math.sin(yaw)}; }
    public double[] up() {
        double s = Math.sin(pitch);
        return new double[]{-s * Math.sin(yaw), Math.cos(pitch), -s * Math.cos(yaw)};
    }

    public double[] project(double x, double y, double z) {
        double[] r = right(), u = up(), f = forward();
        double dx = x - tx, dy = y - ty, dz = z - tz;
        return new double[]{ox + scale * (dx * r[0] + dy * r[1] + dz * r[2]), oy - scale * (dx * u[0] + dy * u[1] + dz * u[2]),
                dx * f[0] + dy * f[1] + dz * f[2]};
    }

    public double[] unproject(double sx, double sy, double depth) {
        double[] r = right(), u = up(), f = forward();
        double a = (sx - ox) / scale, b = -(sy - oy) / scale;
        return new double[]{tx + a * r[0] + b * u[0] + depth * f[0], ty + a * r[1] + b * u[1] + depth * f[1], tz + a * r[2] + b * u[2] + depth * f[2]};
    }

    public double[][] ray(double sx, double sy, double behind) {
        return new double[][]{unproject(sx, sy, -behind), forward()};
    }

    public double[] drag(double[] point, double sx, double sy) {
        return unproject(sx, sy, project(point[0], point[1], point[2])[2]);
    }

    public void orbit(double dx, double dy) {
        yaw -= dx * TURN;
        pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch + dy * TURN));
    }
    public static final double TURN = Math.toRadians(0.6);

    public void pan(double dx, double dy) { ox += dx; oy += dy; }

    public void zoom(double sx, double sy, double factor) {
        double next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor)), k = next / scale;
        ox = sx - (sx - ox) * k;
        oy = sy - (sy - oy) * k;
        scale = next;
    }

    public void fit(double nx, double ny, double nz, double x0, double y0, double x1, double y1, double margin) {
        tx = nx / 2; ty = ny / 2; tz = nz / 2;
        ox = (x0 + x1) / 2; oy = (y0 + y1) / 2;
        double[] r = right(), u = up();
        double across = Math.max(0.5, (Math.abs(r[0]) * nx + Math.abs(r[1]) * ny + Math.abs(r[2]) * nz) / 2);
        double high = Math.max(0.5, (Math.abs(u[0]) * nx + Math.abs(u[1]) * ny + Math.abs(u[2]) * nz) / 2);
        double roomX = Math.max(8, x1 - x0 - 2 * margin), roomY = Math.max(8, y1 - y0 - 2 * margin);
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.min(roomX / (2 * across), roomY / (2 * high))));
    }

    public int pick(List<double[]> points, double sx, double sy, double radius) {
        int best = -1;
        double bestDistance = Double.POSITIVE_INFINITY, bestDepth = Double.POSITIVE_INFINITY;
        for (int k = 0; k < points.size(); k++) {
            double[] p = points.get(k), s = project(p[0], p[1], p[2]);
            double d = Math.max(Math.abs(s[0] - sx), Math.abs(s[1] - sy));
            if (d <= radius && (d < bestDistance || d == bestDistance && s[2] < bestDepth)) {
                best = k;
                bestDistance = d;
                bestDepth = s[2];
            }
        }
        return best;
    }
}
