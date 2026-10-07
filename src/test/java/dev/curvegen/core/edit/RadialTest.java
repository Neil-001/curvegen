package dev.curvegen.core.edit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RadialTest {
    @Test
    void theCursorsDirectionPicksTheWedge() {
        // Four wedges: up, right, down, left. Screen y runs downwards.
        assertEquals(0, Radial.wedgeAt(0, -50, 4, 9, 1));
        assertEquals(1, Radial.wedgeAt(50, 0, 4, 9, 1));
        assertEquals(2, Radial.wedgeAt(0, 50, 4, 9, 1));
        assertEquals(3, Radial.wedgeAt(-50, 0, 4, 9, 1));
        // A wedge reaches halfway to its neighbours, however far out the cursor is.
        assertEquals(0, Radial.wedgeAt(40, -50, 4, 9, 1));
        assertEquals(1, Radial.wedgeAt(50, -40, 4, 9, 1));
        assertEquals(0, Radial.wedgeAt(-400, -500, 4, 9, 1));
        assertEquals(3, Radial.wedgeAt(-500, -400, 4, 9, 1));
    }

    @Test
    void theCentreAndAnEmptyMenuPickNothing() {
        assertEquals(-1, Radial.wedgeAt(0, 0, 4, 9, 1));
        assertEquals(-1, Radial.wedgeAt(6, -6, 4, 9, 1));
        assertEquals(0, Radial.wedgeAt(0, -9, 4, 9, 1));
        assertEquals(-1, Radial.wedgeAt(0, -50, 0, 9, 1));
        assertEquals(0, Radial.wedgeAt(30, 30, 1, 9, 1), "a single wedge is every direction");
    }

    @Test
    void aStretchedMenuIsAnOval() {
        // Twice as wide as tall: the corner of a square is now in the top wedge, and the dead centre is twice as wide.
        assertEquals(1, Radial.wedgeAt(50, -40, 4, 9, 1));
        assertEquals(0, Radial.wedgeAt(50, -40, 4, 9, 2));
        assertEquals(1, Radial.wedgeAt(90, -40, 4, 9, 2));
        assertEquals(-1, Radial.wedgeAt(17, 0, 4, 9, 2));
        assertEquals(1, Radial.wedgeAt(19, 0, 4, 9, 2));
        assertEquals(-1, Radial.wedgeAt(0, -8, 4, 9, 2));
    }

    @Test
    void everyDirectionBelongsToOneWedgeAndTheyGoClockwise() {
        for (int n = 1; n <= Radial.MAX_WEDGES; n++) {
            int last = 0, turns = 0;
            for (int step = 0; step < 720; step++) {
                double a = Math.toRadians(step / 2.0);
                int w = Radial.wedgeAt(100 * Math.sin(a), -100 * Math.cos(a), n, 9, 1);
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
                int ringW = (int) Math.ceil(ring * Radial.stretch(r[0], r[1]));
                assertFalse(overlap(x, y, Radial.LABEL_W, Radial.LABEL_H, width / 2 - ringW, height / 2 - ring, 2 * ringW, 2 * ring),
                        n + " wedges: label " + a + " is clear of the ring");
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
                assertEquals(k, Radial.wedgeAt(at[k][0] + Radial.LABEL_W / 2.0 - 213, at[k][1] + Radial.LABEL_H / 2.0 - 120, n, 9, Radial.stretch(r[0], r[1])),
                        n + " wedges: pointing at label " + k + " picks its wedge");
        }
    }

    @Test
    void anEmptyOrderIsTheDefault() {
        assertEquals(Radial.EDIT, Radial.order(Radial.EDIT, List.of()));
        assertEquals(Radial.START, Radial.order(Radial.START, List.of()));
    }

    @Test
    void aSavedOrderComesFirstAndTheRestKeepTheirPlaces() {
        assertEquals(List.of("place", "blocks", "options", "presets", "export", "full", "cancel"),
                Radial.order(Radial.EDIT, List.of("place", "blocks")));
        // Unknown ids, another menu's ids and repeats are skipped.
        assertEquals(List.of("last", "2d", "3d", "presets"), Radial.order(Radial.START, List.of("nonsense", "place", "last", "last")));
    }

    @Test
    void bothMenusOrdersSurviveBeingSavedAsOneList() {
        List<String> start = new ArrayList<>(List.of("last", "presets", "3d", "2d"));
        List<String> edit = new ArrayList<>(List.of("cancel", "place", "full", "export", "presets", "blocks", "options"));
        List<String> saved = Radial.merge(start, edit);
        assertEquals(start, Radial.order(Radial.START, saved));
        assertEquals(edit, Radial.order(Radial.EDIT, saved));
        assertEquals(List.of(), Radial.merge(Radial.START, Radial.EDIT), "the default order is saved as nothing");
        // Presets first in one menu and last in the other.
        start = List.of("presets", "2d", "3d", "last");
        edit = List.of("options", "blocks", "export", "full", "place", "cancel", "presets");
        saved = Radial.merge(start, edit);
        assertEquals(start, Radial.order(Radial.START, saved));
        assertEquals(edit, Radial.order(Radial.EDIT, saved));
    }

    @Test
    void everyWedgeHasAName() {
        for (String id : Radial.START) assertNotEquals(id, Radial.name(id));
        for (String id : Radial.EDIT) assertNotEquals(id, Radial.name(id));
    }
}
