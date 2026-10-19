package dev.curvegen.core.edit;

import java.util.List;

/**
 * The camera of the menu's 3D preview. It looks at a target point from a direction given by two angles, without
 * perspective: every look ray runs the same way, so a point dragged across the screen stays under the cursor and a
 * block is the same size wherever it is. Space is x east, y up and z south, in blocks. The screen is x right and
 * y down, in whatever pixels {@link #scale} is given in.
 */
public final class Orbit {
    public static final double MIN_SCALE = 0.25, MAX_SCALE = 160, MAX_PITCH = Math.toRadians(89);

    /** Radians. At yaw 0 the camera is south of the target, looking north; a positive yaw moves it east. A positive pitch lifts it. */
    public double yaw = Math.toRadians(-35), pitch = Math.toRadians(28);
    /** Pixels to a block. */
    public double scale = 8;
    /** The point the camera turns about, and where on the screen it is drawn. */
    public double tx, ty, tz, ox, oy;

    public Orbit copy() {
        Orbit o = new Orbit();
        o.yaw = yaw; o.pitch = pitch; o.scale = scale; o.tx = tx; o.ty = ty; o.tz = tz; o.ox = ox; o.oy = oy;
        return o;
    }

    /** The same view on a screen with {@code k} times as many pixels each way. */
    public Orbit times(double k) {
        Orbit o = copy();
        o.scale *= k; o.ox *= k; o.oy *= k;
        return o;
    }

    public boolean same(Orbit o) {
        return o != null && yaw == o.yaw && pitch == o.pitch && scale == o.scale && tx == o.tx && ty == o.ty && tz == o.tz && ox == o.ox && oy == o.oy;
    }

    /** The way to the camera: opposite to where it looks. */
    public double[] back() {
        double c = Math.cos(pitch);
        return new double[]{c * Math.sin(yaw), Math.sin(pitch), c * Math.cos(yaw)};
    }
    /** The way the camera looks. */
    public double[] forward() { double[] b = back(); return new double[]{-b[0], -b[1], -b[2]}; }
    /** The direction in space that runs to the right on the screen. */
    public double[] right() { return new double[]{Math.cos(yaw), 0, -Math.sin(yaw)}; }
    /** The direction in space that runs up the screen. */
    public double[] up() {
        double s = Math.sin(pitch);
        return new double[]{-s * Math.sin(yaw), Math.cos(pitch), -s * Math.cos(yaw)};
    }

    /** Where a point is drawn: {screen x, screen y, depth}. Depth grows away from the camera and is 0 at the target. */
    public double[] project(double x, double y, double z) {
        double[] r = right(), u = up(), f = forward();
        double dx = x - tx, dy = y - ty, dz = z - tz;
        return new double[]{ox + scale * (dx * r[0] + dy * r[1] + dz * r[2]), oy - scale * (dx * u[0] + dy * u[1] + dz * u[2]),
                dx * f[0] + dy * f[1] + dz * f[2]};
    }

    /** The point in space drawn at a spot on the screen, at a given depth. The inverse of {@link #project}. */
    public double[] unproject(double sx, double sy, double depth) {
        double[] r = right(), u = up(), f = forward();
        double a = (sx - ox) / scale, b = -(sy - oy) / scale;
        return new double[]{tx + a * r[0] + b * u[0] + depth * f[0], ty + a * r[1] + b * u[1] + depth * f[1], tz + a * r[2] + b * u[2] + depth * f[2]};
    }

    /** The look ray through a spot on the screen: {origin, direction}, starting {@code behind} blocks nearer than the target. */
    public double[][] ray(double sx, double sy, double behind) {
        return new double[][]{unproject(sx, sy, -behind), forward()};
    }

    /**
     * Where a dragged point goes: the spot under the cursor in the plane through the point that faces the camera.
     * The point keeps its depth, so it's the look ray's crossing of that plane.
     */
    public double[] drag(double[] point, double sx, double sy) {
        return unproject(sx, sy, project(point[0], point[1], point[2])[2]);
    }

    /** Turns the camera about its target for a mouse movement in pixels. Dragging right brings the left side round. */
    public void orbit(double dx, double dy) {
        yaw -= dx * TURN;
        pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch + dy * TURN));
    }
    /** Radians turned per pixel dragged. */
    public static final double TURN = Math.toRadians(0.6);

    /** Slides the picture across the screen. */
    public void pan(double dx, double dy) { ox += dx; oy += dy; }

    /** Zooms by a factor, keeping what is under the cursor where it is. */
    public void zoom(double sx, double sy, double factor) {
        double next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor)), k = next / scale;
        ox = sx - (sx - ox) * k;
        oy = sy - (sy - oy) * k;
        scale = next;
    }

    /**
     * Looks at the middle of a box and zooms so that, from where the camera is now, all of it is inside a screen
     * rectangle and {@code margin} pixels clear of the edges.
     */
    public void fit(double nx, double ny, double nz, double x0, double y0, double x1, double y1, double margin) {
        tx = nx / 2; ty = ny / 2; tz = nz / 2;
        ox = (x0 + x1) / 2; oy = (y0 + y1) / 2;
        double[] r = right(), u = up();
        // Half the box's width and height on the screen, in blocks.
        double across = Math.max(0.5, (Math.abs(r[0]) * nx + Math.abs(r[1]) * ny + Math.abs(r[2]) * nz) / 2);
        double high = Math.max(0.5, (Math.abs(u[0]) * nx + Math.abs(u[1]) * ny + Math.abs(u[2]) * nz) / 2);
        double roomX = Math.max(8, x1 - x0 - 2 * margin), roomY = Math.max(8, y1 - y0 - 2 * margin);
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.min(roomX / (2 * across), roomY / (2 * high))));
    }

    /**
     * The point drawn within {@code radius} pixels of a spot on the screen, or -1. Of several, the one nearest the
     * spot wins, and of two equally near, the one nearer the camera.
     */
    public int pick(List<double[]> points, double sx, double sy, double radius) {
        int best = -1;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int k = 0; k < points.size(); k++) {
            double[] p = points.get(k), s = project(p[0], p[1], p[2]);
            double d = Math.max(Math.abs(s[0] - sx), Math.abs(s[1] - sy));
            if (d > radius) continue;
            double score = d + s[2] * 1e-6;
            if (score < bestScore) { bestScore = score; best = k; }
        }
        return best;
    }
}
