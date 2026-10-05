package dev.curvegen.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks every fence, pane and wall the solver outputs against an independent re-implementation of
 * Minecraft's rules: what connects to what, when a wall side is tall, and when a wall has a post.
 */
class ConnectionRulesTest {
    private static boolean sturdy(int s, int face) { return !Pieces.isConnector(s) && Pieces.STURDY[s][face]; }
    private static boolean conn(int st, int nb, int face) {
        return Pieces.isConnector(nb) ? Pieces.joins(Pieces.FAMILY[st], Pieces.FAMILY[nb]) : sturdy(nb, face);
    }

    private static List<ShapeSettings> shapes() {
        List<ShapeSettings> out = new ArrayList<>();
        ShapeSettings base = new ShapeSettings();
        for (int k = 0; k < 8; k++) {   // also with chains and end rods among the connectors, which must ignore them
            boolean floor = (k & 1) != 0;
            int depth = (k & 2) != 0 ? 3 : 1;
            base.chain = base.rod = (k & 4) != 0;
            for (ShapeSettings.EllipseMode m : ShapeSettings.EllipseMode.values())
                for (int w : new int[]{7, 19, 31, 64})
                    for (int h : new int[]{5, 13, 40}) {
                        ShapeSettings c = base.copy();
                        c.floor = floor; c.depth = depth; c.eMode = m; c.eW = w; c.eH = h; c.eT = 1.5;
                        out.add(c);
                    }
            for (String eq : new String[]{"y=2sin(x)", "y = 9 - x^2/4", "x^2+y^2=16", "y=tan(x)"})
                for (ShapeSettings.EqMode qm : ShapeSettings.EqMode.values()) {
                    ShapeSettings c = base.copy();
                    c.floor = floor; c.depth = depth; c.gen = ShapeSettings.Gen.EQUATION; c.src = eq; c.qMode = qm; c.qLW = 1.3;
                    out.add(c);
                }
        }
        return out;
    }

    @Test
    void everyConnectorFollowsTheGameRules() {
        int checked = 0;
        for (ShapeSettings c : shapes()) {
            Solver.Result r = Solver.run(c);
            int nx = r.nx(), ny = r.ny();
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int st = r.at(i, j);
                    // Flat builds only use pieces that look different from above; upright builds never use flat states.
                    boolean uprightShelf = st == Pieces.SH_L || st == Pieces.SH_R;
                    boolean flatState = st >= Pieces.F_TD_U && st < Pieces.CHAIN_H && !uprightShelf;
                    boolean uprightOnly = (st >= Pieces.SLAB_B && st <= Pieces.TD_T) || (st >= Pieces.FENCE && st < Pieces.F_TD_U) || uprightShelf;
                    assertFalse(c.floor ? uprightOnly : flatState, "wrong orientation: " + Pieces.NAME[st]);
                    if (!Pieces.isConnector(st)) continue;
                    checked++;
                    int L0 = i > 0 ? r.at(i - 1, j) : 0, R0 = i < nx - 1 ? r.at(i + 1, j) : 0;
                    int U0 = j < ny - 1 ? r.at(i, j + 1) : 0, D0 = j > 0 ? r.at(i, j - 1) : 0;
                    boolean L = conn(st, L0, 3), R = conn(st, R0, 2);
                    String where = Pieces.NAME[st] + " at " + i + "," + j + (c.floor ? " (flat)" : "") + " depth " + c.depth;
                    if (c.floor) {
                        int bits = (L ? 1 : 0) | (R ? 2 : 0) | (conn(st, U0, 0) ? 4 : 0) | (conn(st, D0, 1) ? 8 : 0);
                        assertEquals(bits, Pieces.floorBits(st), where);
                    } else {
                        assertEquals(L, Pieces.connectsLeft(st), where);
                        assertEquals(R, Pieces.connectsRight(st), where);
                        if (Pieces.FAMILY[st] == Pieces.Family.WALL) {
                            int cov = Pieces.BOTTOM[U0];
                            int l = L ? ((cov & 0x01FF) == 0x01FF ? 2 : 1) : 0;
                            int rr = R ? ((cov & 0xFF80) == 0xFF80 ? 2 : 1) : 0;
                            boolean covered = (cov & 0x0180) == 0x0180;
                            // Depth 1: the game's post rule. Depth > 1: the front wall connects backwards only, so it has a post.
                            boolean post = c.depth > 1 || !(L && R) || (!(l == 2 && rr == 2) && covered);
                            assertEquals(l, Pieces.wallLeft(st), where + " left side");
                            assertEquals(rr, Pieces.wallRight(st), where + " right side");
                            assertEquals(post, Pieces.wallPost(st), where + " post");
                        }
                    }
                }
        }
        assertTrue(checked > 5000, "only " + checked + " connectors checked");
    }

    @Test
    void straightWallsGainPostsWhenMoreThanOneDeep() {
        ShapeSettings s = new ShapeSettings();
        s.gen = ShapeSettings.Gen.EQUATION;
        s.src = "y = 1.875"; s.xmin = "0"; s.xmax = "12"; s.ymin = "0"; s.ymax = "3";
        s.qW = 12; s.qLock = false; s.qH = 3; s.qMode = ShapeSettings.EqMode.UNDER;
        s.slab = s.stair = s.trap = s.fence = s.pane = false;
        for (int depth : new int[]{1, 2}) {
            s.depth = depth;
            Solver.Result r = Solver.run(s);
            int walls = 0;
            for (int i = 1; i < 11; i++) {
                int st = r.at(i, 1);
                assertEquals(Pieces.Family.WALL, Pieces.FAMILY[st], "depth " + depth + " column " + i);
                assertEquals(depth > 1, Pieces.wallPost(st), "depth " + depth + " column " + i);
                walls++;
            }
            assertEquals(10, walls);
        }
    }

    @Test
    void familiesThatJoin() {
        assertTrue(Pieces.joins(Pieces.Family.FENCE, Pieces.Family.FENCE));
        assertTrue(Pieces.joins(Pieces.Family.PANE, Pieces.Family.WALL));
        assertTrue(Pieces.joins(Pieces.Family.WALL, Pieces.Family.PANE));
        assertFalse(Pieces.joins(Pieces.Family.FENCE, Pieces.Family.PANE));
        assertFalse(Pieces.joins(Pieces.Family.FENCE, Pieces.Family.WALL));
    }

    @Test
    void shelvesOfferOnlyTheirPanel() {
        // Faces are bottom, top, left, right. A shelf's lips don't fill the faces beside its panel.
        assertArrayEquals(new boolean[]{false, false, true, false}, Pieces.STURDY[Pieces.SH_L]);
        assertArrayEquals(new boolean[]{false, false, false, true}, Pieces.STURDY[Pieces.SH_R]);
        assertArrayEquals(new boolean[]{false, false, true, false}, Pieces.STURDY[Pieces.F_SH_L]);
        assertArrayEquals(new boolean[]{false, false, false, true}, Pieces.STURDY[Pieces.F_SH_R]);
        assertArrayEquals(new boolean[]{false, true, false, false}, Pieces.STURDY[Pieces.F_SH_U]);
        assertArrayEquals(new boolean[]{true, false, false, false}, Pieces.STURDY[Pieces.F_SH_D]);
        // The shelf above a wall reaches 5 pixels in, short of the 9 a tall side needs.
        assertNotEquals(0x01FF, Pieces.BOTTOM[Pieces.SH_L] & 0x01FF);
        assertNotEquals(0xFF80, Pieces.BOTTOM[Pieces.SH_R] & 0xFF80);
        for (int s = Pieces.SH_L; s <= Pieces.F_SH_D; s++) assertFalse(Pieces.isConnector(s) || Pieces.isFloorConnector(s), Pieces.NAME[s]);
    }

    @Test
    void fullBlocksThatRefuseConnections() {
        // With a full block fences can't attach to (leaves, pumpkins…), no connector may attach to one.
        ShapeSettings s = new ShapeSettings();
        s.fullConnects = false;
        Solver.Result r = Solver.run(s);
        for (int j = 0; j < r.ny(); j++)
            for (int i = 0; i < r.nx(); i++) {
                int st = r.at(i, j);
                if (!Pieces.isConnector(st)) continue;
                if (Pieces.connectsLeft(st)) assertNotEquals(Pieces.FULL, r.at(i - 1, j));
                if (Pieces.connectsRight(st)) assertNotEquals(Pieces.FULL, r.at(i + 1, j));
            }
    }
}
