package dev.curvegen.core.edit;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.curvegen.core.paint.Paint;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class RadialTest {
    @Test
    void theCursorsDirectionPicksTheWedge() {
        // Four wedges: up, right, down, left. Screen y runs downwards.
        assertEquals(0, Radial.wedgeAt(0, -50, 4, 9, 1, true));
        assertEquals(1, Radial.wedgeAt(50, 0, 4, 9, 1, true));
        assertEquals(2, Radial.wedgeAt(0, 50, 4, 9, 1, true));
        assertEquals(3, Radial.wedgeAt(-50, 0, 4, 9, 1, true));
        // A wedge reaches halfway to its neighbours, however far out the cursor is.
        assertEquals(0, Radial.wedgeAt(40, -50, 4, 9, 1, true));
        assertEquals(1, Radial.wedgeAt(50, -40, 4, 9, 1, true));
        assertEquals(0, Radial.wedgeAt(-400, -500, 4, 9, 1, true));
        assertEquals(3, Radial.wedgeAt(-500, -400, 4, 9, 1, true));
    }

    @Test
    void theCentreAndAnEmptyMenuPickNothing() {
        assertEquals(-1, Radial.wedgeAt(0, 0, 4, 9, 1, true));
        assertEquals(-1, Radial.wedgeAt(6, -6, 4, 9, 1, true));
        assertEquals(0, Radial.wedgeAt(0, -9, 4, 9, 1, true));
        assertEquals(-1, Radial.wedgeAt(0, -50, 0, 9, 1, true));
        assertEquals(0, Radial.wedgeAt(30, 30, 1, 9, 1, true), "a single wedge is every direction");
    }

    @Test
    void aStretchedMenusWedgesAreSlicesOfAnOval() {
        // Twice as wide as tall: the corner of a square is now in the top wedge. The dead centre stays round.
        assertEquals(1, Radial.wedgeAt(50, -40, 4, 9, 1, true));
        assertEquals(0, Radial.wedgeAt(50, -40, 4, 9, 2, true));
        assertEquals(1, Radial.wedgeAt(90, -40, 4, 9, 2, true));
        assertEquals(-1, Radial.wedgeAt(8, 0, 4, 9, 2, true));
        assertEquals(1, Radial.wedgeAt(10, 0, 4, 9, 2, true));
        assertEquals(-1, Radial.wedgeAt(0, -8, 4, 9, 2, true));
        assertEquals(0, Radial.wedgeAt(0, -10, 4, 9, 2, true));
    }

    @Test
    void theStretchedRingsDeadCentreIsAnOvalToo() {
        // The same directions as the round ring's, but the dead centre is twice as wide as it is tall.
        assertEquals(0, Radial.wedgeAt(50, -40, 4, 9, 2, false));
        assertEquals(1, Radial.wedgeAt(90, -40, 4, 9, 2, false));
        assertEquals(-1, Radial.wedgeAt(17, 0, 4, 9, 2, false));
        assertEquals(1, Radial.wedgeAt(19, 0, 4, 9, 2, false));
        assertEquals(-1, Radial.wedgeAt(0, -8, 4, 9, 2, false));
        assertEquals(0, Radial.wedgeAt(0, -10, 4, 9, 2, false));
        // Unstretched, the two rings pick alike.
        for (double x = -30; x <= 30; x += 1.5)
            for (double y = -30; y <= 30; y += 1.5)
                assertEquals(Radial.wedgeAt(x, y, 5, 9, 1, true), Radial.wedgeAt(x, y, 5, 9, 1, false));
    }

    /** Screens of several shapes and GUI scales, as the size the menu is laid out in. */
    private static final int[][] SCREENS = {{427, 240}, {320, 240}, {640, 360}, {960, 540}, {1920, 1080}, {860, 360}, {300, 500}};

    @Test
    void theRoundRingIsACircleOnEveryScreen() {
        int size = 2 * Radial.RING;
        for (int[] screen : SCREENS) {
            int[] r = Radial.radii(screen[0], screen[1]);
            double stretch = Radial.stretch(r[0], r[1]);
            for (int n = 1; n <= Radial.MAX_WEDGES; n++) {
                byte[] ring = Radial.ring(n, stretch, true);
                assertEquals(size * size, ring.length);
                int[] lo = {size, size}, hi = {-1, -1};
                for (int j = 0; j < size; j++)
                    for (int i = 0; i < size; i++) {
                        double dx = i + 0.5 - Radial.RING, dy = j + 0.5 - Radial.RING, d = Math.hypot(dx, dy);
                        boolean drawn = ring[j * size + i] >= 0;
                        if (d > Radial.RING || d < Radial.RING_IN) assertFalse(drawn, "nothing outside the ring or in its hole");
                        else if (n == 1) assertTrue(drawn, "one wedge fills the ring");
                        if (!drawn) continue;
                        lo[0] = Math.min(lo[0], i); hi[0] = Math.max(hi[0], i);
                        lo[1] = Math.min(lo[1], j); hi[1] = Math.max(hi[1], j);
                    }
                assertArrayEquals(new int[]{0, 0}, lo, screen[0] + " wide, " + n + " wedges: it reaches its square's top and left");
                assertArrayEquals(new int[]{size - 1, size - 1}, hi, "and its bottom and right, so it's as wide as it is tall");
            }
        }
    }

    @Test
    void theRoundRingShowsTheWedgeTheCursorPicks() {
        int size = 2 * Radial.RING;
        for (int[] screen : SCREENS) {
            int[] r = Radial.radii(screen[0], screen[1]);
            double stretch = Radial.stretch(r[0], r[1]);
            for (int n = 1; n <= Radial.MAX_WEDGES; n++) {
                byte[] ring = Radial.ring(n, stretch, true);
                int[] pixels = new int[n];
                for (int j = 0; j < size; j++)
                    for (int i = 0; i < size; i++) {
                        int w = ring[j * size + i];
                        if (w < 0) continue;
                        pixels[w]++;
                        // Anywhere on the pixel, not only at its centre.
                        for (double[] in : new double[][]{{0.5, 0.5}, {0.05, 0.05}, {0.95, 0.05}, {0.05, 0.95}, {0.95, 0.95}})
                            assertEquals(w, Radial.wedgeAt(i + in[0] - Radial.RING, j + in[1] - Radial.RING, n, 0, stretch, true),
                                    screen[0] + " wide, " + n + " wedges: pixel " + i + ", " + j);
                    }
                for (int k = 0; k < n; k++) assertTrue(pixels[k] >= 20, screen[0] + " wide, " + n + " wedges: wedge " + k + " has a slice to show");
                // Each slice points at its own label.
                int[][] at = Radial.labels(n, 0, 0, r[0], r[1]);
                for (int k = 0; k < n; k++) {
                    double lx = at[k][0] + Radial.LABEL_W / 2.0, ly = at[k][1] + Radial.LABEL_H / 2.0, far = Math.hypot(lx, ly);
                    double px = lx / far * (Radial.RING + Radial.RING_IN) / 2.0, py = ly / far * (Radial.RING + Radial.RING_IN) / 2.0;
                    assertEquals(k, ring[(int) Math.floor(py + Radial.RING) * size + (int) Math.floor(px + Radial.RING)],
                            screen[0] + " wide, " + n + " wedges: the ring towards label " + k);
                }
            }
        }
    }

    @Test
    void theStretchedRingIsAnOvalInTheMenusProportions() {
        int high = 2 * Radial.RING;
        for (int[] screen : SCREENS) {
            int[] r = Radial.radii(screen[0], screen[1]);
            double stretch = Radial.stretch(r[0], r[1]);
            int half = Radial.ringHalf(stretch, false), wide = 2 * half;
            assertEquals((int) Math.ceil(Radial.RING * stretch), half);
            assertEquals(Radial.RING, Radial.ringHalf(stretch, true), "the round ring's box is square");
            for (int n = 1; n <= Radial.MAX_WEDGES; n++) {
                byte[] ring = Radial.ring(n, stretch, false);
                assertEquals(wide * high, ring.length);
                int[] lo = {wide, high}, hi = {-1, -1};
                for (int j = 0; j < high; j++)
                    for (int i = 0; i < wide; i++) {
                        double d = Math.hypot((i + 0.5 - half) / stretch, j + 0.5 - Radial.RING);
                        boolean drawn = ring[j * wide + i] >= 0;
                        if (d > Radial.RING || d < Radial.RING_IN) assertFalse(drawn, "nothing outside the ring or in its hole");
                        else if (n == 1) assertTrue(drawn, "one wedge fills the ring");
                        if (!drawn) continue;
                        lo[0] = Math.min(lo[0], i); hi[0] = Math.max(hi[0], i);
                        lo[1] = Math.min(lo[1], j); hi[1] = Math.max(hi[1], j);
                    }
                // The oval can end up to a pixel inside its box, which is a whole number of pixels wide.
                assertTrue(lo[0] <= 1 && hi[0] >= wide - 2, screen[0] + " wide, " + n + " wedges: it reaches its box's sides");
                assertArrayEquals(new int[]{0, high - 1}, new int[]{lo[1], hi[1]}, "and its top and bottom");
            }
        }
    }

    @Test
    void theStretchedRingShowsTheWedgeTheCursorPicksInEvenSlices() {
        int high = 2 * Radial.RING;
        for (int[] screen : SCREENS) {
            int[] r = Radial.radii(screen[0], screen[1]);
            double stretch = Radial.stretch(r[0], r[1]);
            int half = Radial.ringHalf(stretch, false), wide = 2 * half;
            for (int n = 1; n <= Radial.MAX_WEDGES; n++) {
                byte[] ring = Radial.ring(n, stretch, false);
                int[] pixels = new int[n];
                for (int j = 0; j < high; j++)
                    for (int i = 0; i < wide; i++) {
                        int w = ring[j * wide + i];
                        if (w < 0) continue;
                        pixels[w]++;
                        // The gap between two slices is about a pixel, so most of a drawn pixel is inside its wedge.
                        for (double[] in : new double[][]{{0.5, 0.5}, {0.25, 0.25}, {0.75, 0.25}, {0.25, 0.75}, {0.75, 0.75}})
                            assertEquals(w, Radial.wedgeAt(i + in[0] - half, j + in[1] - Radial.RING, n, 0, stretch, false),
                                    screen[0] + " wide, " + n + " wedges: pixel " + i + ", " + j);
                        // The hole is the picking's dead centre.
                        assertEquals(w, Radial.wedgeAt(i + 0.5 - half, j + 0.5 - Radial.RING, n, Radial.RING_IN, stretch, false));
                    }
                int least = Integer.MAX_VALUE, most = 0;
                for (int k = 0; k < n; k++) { least = Math.min(least, pixels[k]); most = Math.max(most, pixels[k]); }
                assertTrue(least >= 20, screen[0] + " wide, " + n + " wedges: every wedge has a slice to show");
                assertTrue(most - least <= 0.15 * most, screen[0] + " wide, " + n + " wedges: slices of " + least + " to " + most + " pixels are even");
                // Each slice points at its own label.
                int[][] at = Radial.labels(n, 0, 0, r[0], r[1]);
                for (int k = 0; k < n; k++) {
                    double lx = (at[k][0] + Radial.LABEL_W / 2.0) / stretch, ly = at[k][1] + Radial.LABEL_H / 2.0, far = Math.hypot(lx, ly);
                    double px = lx / far * (Radial.RING + Radial.RING_IN) / 2.0 * stretch, py = ly / far * (Radial.RING + Radial.RING_IN) / 2.0;
                    assertEquals(k, ring[(int) Math.floor(py + Radial.RING) * wide + (int) Math.floor(px + half)],
                            screen[0] + " wide, " + n + " wedges: the ring towards label " + k);
                }
            }
        }
    }

    @Test
    void theStretchedRingPicksFromTheMiddleOfTheScreenAsItDid() {
        assertEquals(213.5, Radial.centre(427, false));
        assertEquals(213, Radial.centre(427, true));
        assertEquals(120, Radial.centre(240, false));
        assertEquals(120, Radial.centre(240, true));
        // A 427 by 240 screen with four wedges: these two cursors are where the half pixel shows.
        int[] r = Radial.radii(427, 240);
        double stretch = Radial.stretch(r[0], r[1]);
        assertEquals(0, Radial.wedgeAt(247 - Radial.centre(427, false), 103 - Radial.centre(240, false), 4, Radial.RING_IN, stretch, false));
        assertEquals(-1, Radial.wedgeAt(231 - Radial.centre(427, false), 120 - Radial.centre(240, false), 4, Radial.RING_IN, stretch, false));
    }

    /** The round ring's slices are uneven on a stretched menu, which is what the stretched ring is for. */
    @Test
    void onlyTheRoundRingsSlicesAreUneven() {
        int[] r = Radial.radii(427, 240);
        byte[] ring = Radial.ring(4, Radial.stretch(r[0], r[1]), true);
        int[] pixels = new int[4];
        for (byte w : ring) if (w >= 0) pixels[w]++;
        assertTrue(pixels[0] > 1.5 * pixels[1], "the top slice of " + pixels[0] + " pixels is wider than the side one of " + pixels[1]);
    }

    /**
     * The stretched ring's pixels and picking are as they were before the round ring came: these two numbers were
     * worked out with that code. Change them only on purpose.
     */
    @Test
    void theStretchedRingIsAsItWas() {
        int pixels = 0;
        long picks = 0;
        for (int[] screen : SCREENS) {
            int[] r = Radial.radii(screen[0], screen[1]);
            double stretch = Radial.stretch(r[0], r[1]);
            for (int n = 1; n <= Radial.MAX_WEDGES; n++) {
                pixels = 31 * pixels + java.util.Arrays.hashCode(Radial.ring(n, stretch, false));
                for (double y = -40; y <= 40; y += 0.25)
                    for (double x = -120; x <= 120; x += 0.25)
                        picks = 31 * picks + Radial.wedgeAt(x, y, n, Radial.RING_IN, stretch, false);
            }
        }
        assertEquals(368694880, pixels);
        assertEquals(619584678303506043L, picks);
    }

    @Test
    void everyDirectionBelongsToOneWedgeAndTheyGoClockwise() {
        for (int n = 1; n <= Radial.MAX_WEDGES; n++) {
            int last = 0, turns = 0;
            for (int step = 0; step < 720; step++) {
                double a = Math.toRadians(step / 2.0);
                int w = Radial.wedgeAt(100 * Math.sin(a), -100 * Math.cos(a), n, 9, 1, true);
                assertTrue(w >= 0 && w < n);
                if (w != last) { assertEquals((last + 1) % n, w); turns++; }
                last = w;
            }
            assertEquals(n == 1 ? 0 : n, turns, "once round passes every wedge once");
        }
    }

    /** The convention is that nothing overlaps on a 427 px wide screen, which is 240 high. */
    @Test
    void labelsFitTheSmallestScreenWithoutOverlapping() {
        int width = 427, height = 240, ring = Radial.RING;
        int[] r = Radial.radii(width, height);
        for (int n = 1; n <= Radial.MAX_WEDGES; n++) {
            int[][] at = Radial.labels(n, width / 2, height / 2, r[0], r[1]);
            for (int a = 0; a < n; a++) {
                int x = at[a][0], y = at[a][1];
                assertTrue(x >= 0 && x + Radial.LABEL_W <= width && y >= 0, n + " wedges: label " + a + " is on the screen");
                assertTrue(y + Radial.LABEL_H <= height - Radial.FOOT, n + " wedges: label " + a + " is clear of the text at the bottom");
                for (boolean round : new boolean[]{false, true}) {
                    int ringW = Radial.ringHalf(Radial.stretch(r[0], r[1]), round);
                    assertFalse(overlap(x, y, Radial.LABEL_W, Radial.LABEL_H, width / 2 - ringW, height / 2 - ring, 2 * ringW, 2 * ring),
                            n + " wedges: label " + a + " is clear of the " + (round ? "round" : "stretched") + " ring");
                }
                for (int b = a + 1; b < n; b++)
                    assertFalse(overlap(x, y, Radial.LABEL_W, Radial.LABEL_H, at[b][0], at[b][1], Radial.LABEL_W, Radial.LABEL_H),
                            n + " wedges: labels " + a + " and " + b + " overlap");
            }
        }
    }

    private static boolean overlap(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah;
    }

    @Test
    void aLabelSitsInItsWedgesDirection() {
        int[] r = Radial.radii(427, 240);
        for (int n = 1; n <= Radial.MAX_WEDGES; n++) {
            int[][] at = Radial.labels(n, 213, 120, r[0], r[1]);
            for (int k = 0; k < n; k++)
                for (boolean round : new boolean[]{false, true})
                    assertEquals(k, Radial.wedgeAt(at[k][0] + Radial.LABEL_W / 2.0 - 213, at[k][1] + Radial.LABEL_H / 2.0 - 120, n, 9, Radial.stretch(r[0], r[1]), round),
                            n + " wedges: pointing at label " + k + " picks its wedge");
        }
    }

    @Test
    void anEmptyOrderIsTheDefault() {
        assertEquals(Radial.EDIT, Radial.order(Radial.EDIT, List.of()));
        assertEquals(Radial.START, Radial.order(Radial.START, List.of()));
    }

    @Test
    void aSavedOrderKeepsItsOrderAndTheRestFollowTheirNeighbours() {
        assertEquals(List.of("options", "place", "cancel", "blocks", "paint", "presets", "export", "full"),
                Radial.order(Radial.EDIT, List.of("place", "blocks")));
        // A wedge added since the order was saved turns up after the one before it by default: Paint beside Blocks.
        assertEquals(List.of("cancel", "place", "full", "export", "presets", "blocks", "paint", "options"),
                Radial.order(Radial.EDIT, List.of("cancel", "place", "full", "export", "presets", "blocks", "options")));
        // Unknown ids, another menu's ids and repeats are skipped.
        assertEquals(List.of("2d", "3d", "presets", "last", "capture"), Radial.order(Radial.START, List.of("nonsense", "place", "last", "last")));
    }

    @Test
    void bothMenusOrdersSurviveBeingSavedAsOneList() {
        List<String> start = new ArrayList<>(List.of("last", "capture", "presets", "3d", "2d"));
        List<String> edit = new ArrayList<>(List.of("cancel", "place", "full", "export", "presets", "paint", "blocks", "options"));
        List<String> saved = Radial.merge(start, edit);
        assertEquals(start, Radial.order(Radial.START, saved));
        assertEquals(edit, Radial.order(Radial.EDIT, saved));
        assertEquals(List.of(), Radial.merge(Radial.START, Radial.EDIT), "the default order is saved as nothing");
        // Presets first in one menu and last in the other.
        start = List.of("presets", "2d", "capture", "3d", "last");
        edit = List.of("options", "paint", "blocks", "export", "full", "place", "cancel", "presets");
        saved = Radial.merge(start, edit);
        assertEquals(start, Radial.order(Radial.START, saved));
        assertEquals(edit, Radial.order(Radial.EDIT, saved));
    }

    @Test
    void everyWedgeHasAName() {
        for (String id : Radial.START) assertNotEquals(id, Radial.name(id));
        for (String id : Radial.EDIT) assertNotEquals(id, Radial.name(id));
    }

    /**
     * The game's default font's advances, from its bitmaps as the game works them out: the last column with any pixel,
     * scaled to the line, plus one. The rest of the characters come from Unifont, which the game downloads, so they
     * count as Unifont's usual wide glyph: 16 pixels at half size, plus one.
     */
    private static Map<Integer, Integer> fontAdvances() throws Exception {
        Map<Integer, Integer> advance = new HashMap<>();
        advance.put((int) ' ', 4);
        JsonObject font;
        try (InputStream in = RadialTest.class.getResourceAsStream("/assets/minecraft/font/include/default.json")) {
            assertNotNull(in, "the game's font is on the test classpath");
            font = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for (var e : font.getAsJsonArray("providers")) {
            JsonObject p = e.getAsJsonObject();
            if (!p.get("type").getAsString().equals("bitmap")) continue;
            String file = p.get("file").getAsString().replace("minecraft:", "");
            BufferedImage img;
            try (InputStream in = RadialTest.class.getResourceAsStream("/assets/minecraft/textures/" + file)) { img = ImageIO.read(in); }
            var rows = p.getAsJsonArray("chars");
            int cols = (int) rows.get(0).getAsString().codePoints().count();
            int cw = img.getWidth() / cols, ch = img.getHeight() / rows.size();
            double scale = (p.has("height") ? p.get("height").getAsInt() : 8) / (double) ch;
            for (int r = 0; r < rows.size(); r++) {
                int[] cps = rows.get(r).getAsString().codePoints().toArray();
                for (int c = 0; c < cps.length; c++) {
                    if (cps[c] == 0 || advance.containsKey(cps[c])) continue;   // the first provider with a character wins
                    int last = -1;
                    for (int x = 0; x < cw; x++)
                        for (int y = 0; y < ch; y++)
                            if ((img.getRGB(c * cw + x, r * ch + y) >>> 24) != 0) last = x;
                    advance.put(cps[c], (int) (0.5 + (last + 1) * scale) + 1);
                }
            }
        }
        return advance;
    }


    /**
     * The radial menu's top page and its Paint page, in every language: each wedge's name and each value it can show in
     * words fits the 72 pixels inside a label, so none is cut. Names of patterns, mixes and gradients are left out: the
     * menu cuts a long one and the wedge's tip gives the whole name.
     */
    @Test
    void theTopAndPaintWedgesFitTheirLabelsInEveryLanguage() throws Exception {
        Map<Integer, Integer> advance = fontAdvances();
        // Widths the game itself measured, in English, Russian and Japanese.
        for (Object[] seen : new Object[][]{{"Choose a gradient", 92}, {"Выбрать градиент", 95}, {"グラデーションを選ぶ", 90}, {"Mélange moussu", 79}})
            assertEquals(seen[1], ((String) seen[0]).codePoints().map(cp -> advance.getOrDefault(cp, 9)).sum(), (String) seen[0]);
        Set<String> keys = new LinkedHashSet<>(List.of("curvegen.radial.back", "curvegen.radial.paint.choose"));
        for (String id : Radial.START) keys.add(Radial.name(id));
        for (String id : Radial.EDIT) keys.add(Radial.name(id));
        for (Paint.Source src : Paint.Source.values())
            for (boolean gradients : new boolean[]{false, true})
                for (boolean wraps : new boolean[]{false, true}) {
                    Paint p = new Paint();
                    p.source = src;
                    for (Option o : PaintOptions.of(p, gradients, wraps)) {
                        keys.add(o.label());
                        if (o instanceof Option.Cycler c) keys.addAll(c.names());
                    }
                }
        int room = Radial.LABEL_W - 8;
        List<String> cut = new ArrayList<>();
        try (var files = Files.list(Path.of("../src/main/resources/assets/curvegen/lang"))) {
            for (Path f : files.sorted().toList()) {
                JsonObject lang = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                for (String key : keys) {
                    assertTrue(lang.has(key), f.getFileName() + " has " + key);
                    String text = lang.get(key).getAsString();
                    int w = text.codePoints().map(cp -> advance.getOrDefault(cp, 9)).sum();
                    if (w > room) cut.add(f.getFileName() + " " + key + " \"" + text + "\" is " + w + " pixels");
                }
            }
        }
        assertEquals(List.of(), cut, "wider than a label's " + room + " pixels");
    }
}
