package dev.curvegen.core;

import org.junit.jupiter.api.Test;

import static dev.curvegen.core.Pieces3.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks every stair, fence, pane and wall the 3D solver outputs against an independent re-implementation of
 * Minecraft's rules, worked out from the finished states and their shapes rather than the solver's tables.
 */
class Rules3Test {
    private static int at(Solver3.Result r, int x, int y, int z) {
        return x < 0 || y < 0 || z < 0 || x >= r.nx() || y >= r.ny() || z >= r.nz() ? AIR : r.at(x, y, z);
    }

    /** Is the state's side face in horizontal direction d a full square? */
    private static boolean fullFace(int s, int d) {
        boolean[] v = voxels(s);
        for (int a = 0; a < 16; a++)
            for (int y = 0; y < 16; y++) {
                int x = d == W ? 0 : d == E ? 15 : a, z = d == N ? 0 : d == S ? 15 : a;
                if (!v[(y * 16 + z) * 16 + x]) return false;
            }
        return true;
    }

    private static boolean attaches(int connector, int neighbour, int d, boolean fullConnects) {
        if (isConnector(neighbour)) return Pieces.joins(FAMILY[connector], FAMILY[neighbour]);
        if (neighbour == FULL && !fullConnects) return false;
        return fullFace(neighbour, (d + 2) % 4);
    }

    /** The bottom of a state's collision shape: does it cover x0..x1 by z0..z1? */
    private static boolean floorCovers(int s, int x0, int z0, int x1, int z1) {
        boolean[] v = voxels(s);
        if (FAMILY[s] == Pieces.Family.FENCE) {   // collision arms are 6 to 10 wide and solid
            v = new boolean[4096];
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    boolean post = x >= 6 && x < 10 && z >= 6 && z < 10;
                    boolean arm = (side(s, N) > 0 && x >= 6 && x < 10 && z < 10) || (side(s, S) > 0 && x >= 6 && x < 10 && z >= 6)
                            || (side(s, W) > 0 && z >= 6 && z < 10 && x < 10) || (side(s, E) > 0 && z >= 6 && z < 10 && x >= 6);
                    v[z * 16 + x] = post || arm;
                }
        }
        for (int z = z0; z < z1; z++) for (int x = x0; x < x1; x++) if (!v[z * 16 + x]) return false;
        return true;
    }

    private static int expectedStairShape(Solver3.Result r, int x, int y, int z) {
        int s = r.at(x, y, z), f = facing(s), left = (f + 3) % 4;
        int front = at(r, x + DX[f], y, z + DZ[f]), back = at(r, x - DX[f], y, z - DZ[f]);
        if (isStairs(front) && half(front) == half(s) && facing(front) % 2 != f % 2) {
            int ff = facing(front), side = at(r, x - DX[ff], y, z - DZ[ff]);
            if (!(isStairs(side) && facing(side) == f && half(side) == half(s))) return ff == left ? OUTER_LEFT : OUTER_RIGHT;
        }
        if (isStairs(back) && half(back) == half(s) && facing(back) % 2 != f % 2) {
            int bf = facing(back), side = at(r, x + DX[bf], y, z + DZ[bf]);
            if (!(isStairs(side) && facing(side) == f && half(side) == half(s))) return bf == left ? INNER_LEFT : INNER_RIGHT;
        }
        return STRAIGHT;
    }

    @Test
    void everyStateFollowsTheGameRules() {
        int connectors = 0, walls = 0, tall = 0, noPost = 0, corners = 0;
        for (Shapes3Cases.Case c : Shapes3Cases.all()) {
            Solver3.Result r = c.solve();
            assertTrue(r.sweeps() <= Solver3.MAX_SWEEPS, c.name() + " didn't settle");
            boolean fullConnects = c.settings().fullConnects;
            for (int y = 0; y < r.ny(); y++)
                for (int z = 0; z < r.nz(); z++)
                    for (int x = 0; x < r.nx(); x++) {
                        int s = r.at(x, y, z);
                        String where = c.name() + ": " + name(s) + " at " + x + "," + y + "," + z;
                        assertTrue(c.settings().allows(FAMILY[s]), where + " is switched off");
                        assertNotEquals(WALL, s, where);
                        if (isStairs(s)) {
                            assertEquals(expectedStairShape(r, x, y, z), stairShape(s), where);
                            if (stairShape(s) != STRAIGHT) corners++;
                        }
                        if (!isConnector(s)) continue;
                        connectors++;
                        boolean[] on = new boolean[4];
                        for (int d = 0; d < 4; d++) {
                            on[d] = attaches(s, at(r, x + DX[d], y, z + DZ[d]), d, fullConnects);
                            if (!isWall(s)) assertEquals(on[d] ? 1 : 0, side(s, d), where + " side " + d);
                        }
                        if (!isWall(s)) continue;
                        walls++;
                        int above = at(r, x, y + 1, z);
                        int n = on[N] ? (floorCovers(above, 7, 0, 9, 9) ? 2 : 1) : 0, e = on[E] ? (floorCovers(above, 7, 7, 16, 9) ? 2 : 1) : 0;
                        int so = on[S] ? (floorCovers(above, 7, 7, 9, 16) ? 2 : 1) : 0, w = on[W] ? (floorCovers(above, 0, 7, 9, 9) ? 2 : 1) : 0;
                        boolean post;
                        if (isWall(above) && up(above)) post = true;
                        else if ((n == 0 && e == 0 && so == 0 && w == 0) || (n == 0) != (so == 0) || (e == 0) != (w == 0)) post = true;
                        else if ((n == 2 && so == 2) || (e == 2 && w == 2)) post = false;
                        else post = floorCovers(above, 7, 7, 9, 9);
                        assertEquals(wall(n, e, so, w, post), s, where + " under " + name(above));
                        if (n == 2 || e == 2 || so == 2 || w == 2) tall++;
                        if (!post) noPost++;
                    }
        }
        assertTrue(connectors > 5000 && walls > 2000 && tall > 300 && noPost > 100 && corners > 2000,
                connectors + " connectors, " + walls + " walls, " + tall + " tall, " + noPost + " without a post, " + corners + " corner stairs");
    }

    @Test
    void nothingAttachesToAFullBlockThatRefuses() {
        ShapeSettings s = new ShapeSettings();
        s.slab = s.stair = s.trap = s.shelf = false;
        s.fullConnects = false;
        Solver3.Result r = Solver3.solve(Shapes3Cases.sheets(0.45), s, () -> false);
        int beside = 0;
        for (int y = 0; y < r.ny(); y++)
            for (int z = 0; z < r.nz(); z++)
                for (int x = 0; x < r.nx(); x++) {
                    int st = r.at(x, y, z);
                    if (!isConnector(st)) continue;
                    for (int d = 0; d < 4; d++)
                        if (at(r, x + DX[d], y, z + DZ[d]) == FULL) { beside++; assertEquals(0, side(st, d)); }
                }
        assertTrue(beside > 0);
    }
}
