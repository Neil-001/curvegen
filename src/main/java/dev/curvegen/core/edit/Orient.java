package dev.curvegen.core.edit;

import dev.curvegen.core.Message;

public record Orient(int x, int y, int z) {
    public static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    private static final int[] CLOCKWISE = {DOWN, UP, EAST, WEST, NORTH, SOUTH};

    public Orient {
        if (worldAxis(x) == worldAxis(y) || worldAxis(x) == worldAxis(z) || worldAxis(y) == worldAxis(z))
            throw new IllegalArgumentException(Message.of("curvegen.diagnostic.perpendicular_axes", x, y, z).toString());
    }

    public static int worldAxis(int dir) { return dir < 2 ? 1 : dir < 4 ? 2 : 0; }
    public static int sign(int dir) { return (dir & 1) == 1 ? 1 : -1; }
    public static int opposite(int dir) { return dir ^ 1; }
    public static int clockwise(int dir) { return CLOCKWISE[dir]; }
    public static int direction(int worldAxis, boolean positive) { return (worldAxis == 0 ? 4 : worldAxis == 1 ? 0 : 2) + (positive ? 1 : 0); }

    public static Orient facing(int forward) { return new Orient(clockwise(forward), UP, forward); }

    public int dir(int ownAxis) { return ownAxis == 0 ? x : ownAxis == 1 ? y : z; }

    public int ownAxis(int worldAxis) {
        for (int a = 0; a < 3; a++) if (worldAxis(dir(a)) == worldAxis) return a;
        throw new IllegalStateException();
    }

    private Orient map(java.util.function.IntUnaryOperator f) { return new Orient(f.applyAsInt(x), f.applyAsInt(y), f.applyAsInt(z)); }

    public Orient turn() { return map(Orient::clockwise); }

    public Orient tip(int forward) {
        int back = opposite(forward);
        return map(d -> d == UP ? forward : d == forward ? DOWN : d == DOWN ? back : d == back ? UP : d);
    }

    public int[] worldSize(int[] own) {
        int[] w = new int[3];
        for (int a = 0; a < 3; a++) w[worldAxis(dir(a))] = own[a];
        return w;
    }

    public int[] ownSize(int[] world) {
        int[] o = new int[3];
        for (int a = 0; a < 3; a++) o[a] = world[worldAxis(dir(a))];
        return o;
    }

    public double[] toWorld(double[] own, int[] ownSize) {
        double[] w = new double[3];
        for (int a = 0; a < 3; a++) w[worldAxis(dir(a))] = sign(dir(a)) > 0 ? own[a] : ownSize[a] - own[a];
        return w;
    }

    public double[] toOwn(double[] world, int[] ownSize) {
        double[] o = new double[3];
        for (int a = 0; a < 3; a++) {
            double v = world[worldAxis(dir(a))];
            o[a] = sign(dir(a)) > 0 ? v : ownSize[a] - v;
        }
        return o;
    }

    public double[] offsetPoint(double[] point, double[] worldFrom, double[] worldTo) {
        double[] out = new double[3];
        for (int a = 0; a < 3; a++) {
            int world = worldAxis(dir(a));
            out[a] = point[a] + sign(dir(a)) * (worldTo[world] - worldFrom[world]);
        }
        return out;
    }

    public int[] cell(int i, int j, int k, int[] ownSize) {
        int[] w = new int[3];
        int[] c = {i, j, k};
        for (int a = 0; a < 3; a++) w[worldAxis(dir(a))] = sign(dir(a)) > 0 ? c[a] : ownSize[a] - 1 - c[a];
        return w;
    }
}
