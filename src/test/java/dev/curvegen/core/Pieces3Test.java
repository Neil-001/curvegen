package dev.curvegen.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static dev.curvegen.core.Pieces3.*;
import static org.junit.jupiter.api.Assertions.*;

/** The 3D pieces' shapes, in the numbers Minecraft 26.3 uses for its voxel shapes. GameRules3Test checks them all against the game. */
class Pieces3Test {
    private static int[] b(int x0, int y0, int z0, int x1, int y1, int z1) { return new int[]{x0, y0, z0, x1, y1, z1}; }

    private static void same(int state, int[]... boxes) {
        assertArrayEquals(voxels(boxes), voxels(state), name(state));
    }

    private static void within(int state, int[]... outline) {
        boolean[] ours = voxels(state), game = voxels(outline);
        for (int v = 0; v < 4096; v++) assertTrue(!ours[v] || game[v], name(state));
    }

    @Test
    void shapesMatchTheGame() {
        same(FULL, b(0, 0, 0, 16, 16, 16));
        same(SLAB_B, b(0, 0, 0, 16, 8, 16));
        same(SLAB_T, b(0, 8, 0, 16, 16, 16));
        same(stair(0, N, STRAIGHT), b(0, 0, 0, 16, 8, 16), b(0, 8, 0, 16, 16, 8));
        same(stair(0, N, INNER_LEFT), b(0, 0, 0, 16, 8, 16), b(0, 8, 0, 8, 16, 16), b(8, 8, 0, 16, 16, 8));
        same(stair(0, N, INNER_RIGHT), b(0, 0, 0, 16, 8, 16), b(0, 8, 0, 16, 16, 8), b(8, 8, 8, 16, 16, 16));
        same(stair(0, N, OUTER_LEFT), b(0, 0, 0, 16, 8, 16), b(0, 8, 0, 8, 16, 8));
        same(stair(0, N, OUTER_RIGHT), b(0, 0, 0, 16, 8, 16), b(8, 8, 0, 16, 16, 8));
        same(stair(1, E, INNER_LEFT), b(0, 8, 0, 16, 16, 16), b(0, 0, 0, 16, 8, 8), b(8, 0, 8, 16, 8, 16));
        same(stair(1, E, OUTER_RIGHT), b(0, 8, 0, 16, 16, 16), b(8, 0, 8, 16, 8, 16));
        same(stair(0, S, STRAIGHT), b(0, 0, 0, 16, 8, 16), b(0, 8, 8, 16, 16, 16));
        same(stair(0, W, STRAIGHT), b(0, 0, 0, 16, 8, 16), b(0, 8, 0, 8, 16, 16));
        same(TRAP_B, b(0, 0, 0, 16, 3, 16));
        same(TRAP_T, b(0, 13, 0, 16, 16, 16));
        // An open trapdoor's panel, and a shelf's, is on the side opposite its facing.
        same(TRAP_OPEN + N, b(0, 0, 13, 16, 16, 16));
        same(TRAP_OPEN + E, b(0, 0, 0, 3, 16, 16));
        same(TRAP_OPEN + S, b(0, 0, 0, 16, 16, 3));
        same(TRAP_OPEN + W, b(13, 0, 0, 16, 16, 16));
        same(SHELF + N, b(0, 0, 11, 16, 4, 16), b(0, 4, 13, 16, 16, 16), b(0, 12, 11, 16, 16, 13));
        same(SHELF + E, b(0, 0, 0, 5, 4, 16), b(0, 4, 0, 3, 16, 16), b(3, 12, 0, 5, 16, 16));
        same(PANE, b(7, 0, 7, 9, 16, 9));
        same(PANE + 1 + 2, b(7, 0, 0, 9, 16, 9), b(9, 0, 7, 16, 16, 9));
        same(PANE + 4 + 8, b(7, 0, 7, 9, 16, 16), b(0, 0, 7, 7, 16, 9));
        same(WALL_POST, b(4, 0, 4, 12, 16, 12));
        same(wall(1, 0, 0, 0, true), b(4, 0, 4, 12, 16, 12), b(5, 0, 0, 11, 14, 4));
        same(wall(1, 0, 2, 0, true), b(4, 0, 4, 12, 16, 12), b(5, 0, 0, 11, 14, 4), b(5, 0, 12, 11, 16, 16));
        same(wall(1, 0, 2, 0, false), b(5, 0, 0, 11, 14, 16), b(5, 14, 5, 11, 16, 16));
        same(wall(2, 0, 2, 0, false), b(5, 0, 0, 11, 16, 16));
        same(wall(0, 1, 0, 1, false), b(0, 0, 5, 16, 14, 11));
        same(wall(1, 1, 0, 0, true), b(4, 0, 4, 12, 16, 12), b(5, 0, 0, 11, 14, 4), b(12, 0, 5, 16, 14, 11));
        same(wall(2, 2, 2, 2, false), b(5, 0, 0, 11, 16, 16), b(0, 0, 5, 16, 16, 11));

        // Scored as drawn, which is thinner than the outline the game gives them.
        same(FENCE, b(6, 0, 6, 10, 16, 10));
        same(FENCE + 1, b(6, 0, 6, 10, 16, 10), b(7, 6, 0, 9, 9, 6), b(7, 12, 0, 9, 15, 6));
        within(FENCE + 1 + 2, b(6, 0, 0, 10, 16, 10), b(10, 0, 6, 16, 16, 10));
        same(CHAIN, b(0, 7, 7, 16, 9, 9));
        same(CHAIN + 1, b(7, 0, 7, 9, 16, 9));
        same(CHAIN + 2, b(7, 7, 0, 9, 9, 16));
        within(CHAIN + 1, b(6, 0, 6, 10, 16, 10));   // the game's chain is 6.5 to 9.5
        same(ROD + UP, b(6, 0, 6, 10, 1, 10), b(7, 1, 7, 9, 16, 9));
        same(ROD + DOWN, b(6, 15, 6, 10, 16, 10), b(7, 0, 7, 9, 15, 9));
        same(ROD + N, b(6, 6, 15, 10, 10, 16), b(7, 7, 0, 9, 9, 15));
        same(ROD + E, b(0, 6, 6, 1, 10, 10), b(1, 7, 7, 16, 9, 9));
        within(ROD + E, b(0, 6, 6, 16, 10, 10));
    }

    @Test
    void everyStateIsItsPartsAndNothingElse() {
        for (int s = 0; s < COUNT; s++) {
            int[] seen = new int[4096];
            for (int p : PARTS[s]) {
                boolean[] v = voxels(PART_BOXES[p]);
                for (int q = 0; q < 4096; q++) if (v[q]) seen[q]++;
            }
            boolean[] shape = voxels(s);
            int vol = 0;
            for (int q = 0; q < 4096; q++) {
                assertEquals(shape[q] ? 1 : 0, seen[q], name(s) + " voxel " + q);
                vol += seen[q];
            }
            assertEquals(vol, VOL[s], name(s));
        }
        for (int p = 0; p < PART_COUNT; p++) {
            int n = 0;
            for (boolean v : voxels(PART_BOXES[p])) if (v) n++;
            assertEquals(n, PART_VOL[p], "part " + p + " has boxes that overlap");
        }
    }

    @Test
    void mirrorsFlipTheShape() {
        for (int s = 0; s < COUNT; s++) {
            boolean[] v = voxels(s), mx = voxels(MX[s]), my = voxels(MY[s]), mz = voxels(MZ[s]);
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        boolean here = v[(y * 16 + z) * 16 + x];
                        assertEquals(here, mx[(y * 16 + z) * 16 + 15 - x], name(s) + " in x");
                        // Fences and walls have no upside-down state, though their rails and low sides aren't symmetric.
                        if (!isConnector(s) || FAMILY[s] == Pieces.Family.PANE) assertEquals(here, my[((15 - y) * 16 + z) * 16 + x], name(s) + " in y");
                        assertEquals(here, mz[(y * 16 + 15 - z) * 16 + x], name(s) + " in z");
                    }
            assertEquals(s, MX[MX[s]]); assertEquals(s, MY[MY[s]]); assertEquals(s, MZ[MZ[s]]);
            assertEquals(FAMILY[s], FAMILY[MX[s]]);
        }
    }

    @Test
    void statesDecodeToDistinctProperties() {
        assertTrue(COUNT > 127, "3D grids can't be bytes");
        Set<String> seen = new HashSet<>();
        for (int s = 0; s < COUNT; s++) assertTrue(seen.add(name(s)), name(s) + " twice");
        assertEquals("facing=east,half=top,shape=outer_right", props(stair(1, E, OUTER_RIGHT)));
        assertEquals("type=top", props(SLAB_T));
        assertEquals("facing=north,half=top,open=false", props(TRAP_T));
        assertEquals("facing=west,half=bottom,open=true", props(TRAP_OPEN + W));
        assertEquals("facing=south", props(SHELF + S));
        assertEquals("axis=z", props(CHAIN + 2));
        assertEquals("facing=down", props(ROD + DOWN));
        assertEquals("north=true,east=false,south=false,west=true", props(FENCE + 1 + 8));
        assertEquals("north=false,east=true,south=true,west=false", props(PANE + 2 + 4));
        assertEquals("north=none,east=low,south=tall,west=none,up=true", props(wall(0, 1, 2, 0, true)));
        int w = wall(2, 0, 1, 2, false);
        assertArrayEquals(new int[]{2, 0, 1, 2}, new int[]{side(w, N), side(w, E), side(w, S), side(w, W)});
        assertFalse(up(w));
        assertTrue(up(WALL_POST));
    }

    @Test
    void fullFacesAndWhatAWallBelowFeels() {
        // Faces are north, east, south, west, down, up.
        assertArrayEquals(new boolean[]{true, true, true, true, true, true}, FACE[FULL]);
        assertArrayEquals(new boolean[]{false, false, false, false, true, false}, FACE[SLAB_B]);
        assertArrayEquals(new boolean[]{true, false, false, false, true, false}, FACE[stair(0, N, STRAIGHT)]);
        assertArrayEquals(new boolean[]{true, false, false, true, true, false}, FACE[stair(0, N, INNER_LEFT)]);
        assertArrayEquals(new boolean[]{false, false, false, false, true, false}, FACE[stair(0, N, OUTER_LEFT)]);
        assertArrayEquals(new boolean[]{false, false, true, false, false, false}, FACE[TRAP_OPEN + N]);
        assertArrayEquals(new boolean[]{false, false, true, false, false, false}, FACE[SHELF + N]);
        assertArrayEquals(new boolean[]{false, false, false, false, true, false}, FACE[TRAP_B]);
        for (int s = FENCE; s < COUNT; s++) assertArrayEquals(new boolean[6], FACE[s], name(s));
        for (int s = CHAIN; s < FENCE; s++) assertArrayEquals(new boolean[6], FACE[s], name(s));

        int all = COVER_POST | 15;
        assertEquals(all, COVER[FULL]);
        assertEquals(all, COVER[SLAB_B]);
        assertEquals(0, COVER[SLAB_T]);
        assertEquals(all, COVER[stair(0, N, STRAIGHT)]);
        assertEquals(0, COVER[stair(1, N, STRAIGHT)]);          // its step reaches 8 pixels in, and a side needs 9
        assertEquals(all, COVER[TRAP_B]);
        assertEquals(0, COVER[TRAP_T]);
        assertEquals(0, COVER[SHELF + N]);
        assertEquals(COVER_POST, COVER[CHAIN + 1]);
        assertEquals(0, COVER[CHAIN]);
        assertEquals(COVER_POST, COVER[ROD + UP]);
        assertEquals(COVER_POST, COVER[ROD + DOWN]);
        assertEquals(0, COVER[ROD + E]);
        assertEquals(COVER_POST, COVER[FENCE]);
        assertEquals(COVER_POST | 1 << N, COVER[FENCE + 1]);    // a fence's collision arm is solid from the ground up
        assertEquals(COVER_POST | 1 << E | 1 << W, COVER[PANE + 2 + 8]);
        assertEquals(COVER_POST, COVER[WALL_POST]);
        assertEquals(COVER_POST | 1 << N | 1 << S, COVER[wall(1, 0, 1, 0, false)]);
    }
}
