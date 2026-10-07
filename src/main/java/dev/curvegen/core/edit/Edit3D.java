package dev.curvegen.core.edit;

import dev.curvegen.core.PresetData;
import dev.curvegen.core.Shape3;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.ShapeSettings.Gen;

/**
 * What the in-world editor's handles and keys do to a 3D shape's {@link ShapeSettings}. A shape's own axes are the
 * ones its settings are written in: x its width, y its height and z its depth, before it's turned or tipped.
 *
 * <p>A torus has five numbers for a box that only needs three. Here the box (tW, tH, tD) is what the handles and the
 * bump keys change, and the ring and tube sizes stay as they are: they describe the round ring that is stretched to
 * fill the box, so stretching keeps the hole the same share of the ring. Changing the ring or the tube size itself,
 * with {@link #setRing} and {@link #setTube}, scales the box along with it, which keeps whatever stretch it has.
 */
public final class Edit3D {
    private Edit3D() {}

    private static int clamp(int v) { return Math.max(1, Math.min(Shape3.MAX_SIZE, v)); }

    /** The box the handles sit on, along the shape's own axes. It leaves out the room an outwards shell adds. */
    public static int[] size(ShapeSettings s) {
        return s.gen == Gen.TORUS ? new int[]{s.tW, s.tH, s.tD} : new int[]{s.e3W, s.e3H, s.e3D};
    }

    /** Sets the box, within the size limit. */
    public static void resize(ShapeSettings s, int[] want, boolean[] dragged) {
        int x = clamp(want[0]), y = clamp(want[1]), z = clamp(want[2]);
        if (s.gen == Gen.TORUS) { s.tW = x; s.tH = y; s.tD = z; }
        else { s.e3W = x; s.e3H = y; s.e3D = z; }
    }

    /** A box size after the ring or tube it was stretched from changed. An unstretched size follows exactly. */
    private static int scaled(int size, int from, int to) {
        return size == from ? to : clamp((int) Math.round(size * (double) to / from));
    }

    /** Sets a torus's ring size, the distance across it, and scales its width and depth to match. */
    public static void setRing(ShapeSettings s, int ring) {
        ring = clamp(ring);
        s.tW = scaled(s.tW, s.tRing, ring);
        s.tD = scaled(s.tD, s.tRing, ring);
        s.tRing = ring;
        if (s.tTube > ring) setTube(s, ring);
    }

    /** Sets a torus's tube thickness and scales its height to match. The tube can't be thicker than the ring is across. */
    public static void setTube(ShapeSettings s, int tube) {
        tube = Math.min(clamp(tube), s.tRing);
        s.tH = scaled(s.tH, s.tTube, tube);
        s.tTube = tube;
    }

    /** One line that names the shape and its size. */
    public static String describe(ShapeSettings s) {
        String name = PresetData.defaultName(s, s.gen);
        if (s.gen == Gen.TORUS && (s.tW != s.tRing || s.tD != s.tRing || s.tH != s.tTube)) name += ", stretched from a " + s.tRing + " ring";
        return name;
    }
}
