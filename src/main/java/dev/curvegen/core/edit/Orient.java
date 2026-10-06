package dev.curvegen.core.edit;

/**
 * Which way a shape's own axes point in the world. Each axis points along one of the six directions, numbered as
 * Minecraft's {@code Direction} is: 0 down, 1 up, 2 north (-z), 3 south (+z), 4 west (-x), 5 east (+x).
 * A shape's box is measured in its own axes, from its own minimum corner. The world box has a minimum corner too,
 * and an own axis that points the negative way runs backwards from the world box's far side.
 */
public record Orient(int x, int y, int z) {
    public static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    /** North, east, south, west: each one a quarter turn clockwise (seen from above) from the one before. */
    private static final int[] CLOCKWISE = {DOWN, UP, EAST, WEST, NORTH, SOUTH};

    public Orient {
        if (worldAxis(x) == worldAxis(y) || worldAxis(x) == worldAxis(z) || worldAxis(y) == worldAxis(z))
            throw new IllegalArgumentException("axes must be perpendicular: " + x + ", " + y + ", " + z);
    }

    /** The world axis (0 x, 1 y, 2 z) a direction runs along. */
    public static int worldAxis(int dir) { return dir < 2 ? 1 : dir < 4 ? 2 : 0; }
    public static int sign(int dir) { return (dir & 1) == 1 ? 1 : -1; }
    public static int opposite(int dir) { return dir ^ 1; }
    public static int clockwise(int dir) { return CLOCKWISE[dir]; }
    /** The direction along a world axis, the positive or the negative way. */
    public static int direction(int worldAxis, boolean positive) { return (worldAxis == 0 ? 4 : worldAxis == 1 ? 0 : 2) + (positive ? 1 : 0); }

    /** For a player facing a horizontal direction: x to their right, y up and z away from them. */
    public static Orient facing(int forward) { return new Orient(clockwise(forward), UP, forward); }

    public int dir(int ownAxis) { return ownAxis == 0 ? x : ownAxis == 1 ? y : z; }

    /** The own axis that runs along a world axis. */
    public int ownAxis(int worldAxis) {
        for (int a = 0; a < 3; a++) if (worldAxis(dir(a)) == worldAxis) return a;
        throw new IllegalStateException();
    }

    private Orient map(java.util.function.IntUnaryOperator f) { return new Orient(f.applyAsInt(x), f.applyAsInt(y), f.applyAsInt(z)); }

    /** A quarter turn clockwise about the vertical axis, seen from above. */
    public Orient turn() { return map(Orient::clockwise); }

    /**
     * A quarter turn about the horizontal axis that runs to the right of a player facing {@code forward}. The top tips
     * away from them: up goes to forward, forward to down.
     */
    public Orient tip(int forward) {
        int back = opposite(forward);
        return map(d -> d == UP ? forward : d == forward ? DOWN : d == DOWN ? back : d == back ? UP : d);
    }

    /** A size in own axes, as a size in world axes. */
    public int[] worldSize(int[] own) {
        int[] w = new int[3];
        for (int a = 0; a < 3; a++) w[worldAxis(dir(a))] = own[a];
        return w;
    }

    /** A size in world axes, as a size in own axes. */
    public int[] ownSize(int[] world) {
        int[] o = new int[3];
        for (int a = 0; a < 3; a++) o[a] = world[worldAxis(dir(a))];
        return o;
    }

    /** A point in own axes (blocks from the own minimum corner) as blocks from the world box's minimum corner. */
    public double[] toWorld(double[] own, int[] ownSize) {
        double[] w = new double[3];
        for (int a = 0; a < 3; a++) w[worldAxis(dir(a))] = sign(dir(a)) > 0 ? own[a] : ownSize[a] - own[a];
        return w;
    }

    /** The inverse of {@link #toWorld}. */
    public double[] toOwn(double[] world, int[] ownSize) {
        double[] o = new double[3];
        for (int a = 0; a < 3; a++) {
            double v = world[worldAxis(dir(a))];
            o[a] = sign(dir(a)) > 0 ? v : ownSize[a] - v;
        }
        return o;
    }

    /** A cell in own axes as a block offset from the world box's minimum corner. Cells outside the box map outside it. */
    public int[] cell(int i, int j, int k, int[] ownSize) {
        int[] w = new int[3];
        int[] c = {i, j, k};
        for (int a = 0; a < 3; a++) w[worldAxis(dir(a))] = sign(dir(a)) > 0 ? c[a] : ownSize[a] - 1 - c[a];
        return w;
    }
}
