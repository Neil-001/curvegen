package dev.curvegen.core;

public final class Mesh3 {
    public final float[][] rects = new float[6][];
    public final byte[][] family = new byte[6][];
    public final int[][] block = new int[6][];
    public final int[] count = new int[6];
    public final int nx, ny, nz;

    public static final int[] AXIS = {2, 0, 2, 0, 1, 1}, A = {0, 2, 0, 2, 0, 0}, B = {1, 1, 1, 1, 2, 2};
    public static final int[] SIGN = {-1, 1, 1, -1, -1, 1};
    private static final int[] OPPOSITE = {2, 3, 0, 1, 5, 4};
    private static final int[] DX = {0, 1, 0, -1, 0, 0}, DY = {0, 0, 0, 0, -1, 1}, DZ = {-1, 0, 1, 0, 0, 0};

    public Mesh3(Solver3.Result r) { this(r.grid(), r.nx(), r.ny(), r.nz()); }

    public Mesh3(short[] grid, int nx, int ny, int nz) {
        this.nx = nx; this.ny = ny; this.nz = nz;
        for (int d = 0; d < 6; d++) { rects[d] = new float[1280]; family[d] = new byte[256]; block[d] = new int[256]; }
        boolean[] hidden = new boolean[6];
        int[] cell = new int[3];
        int k = -1;
        for (int y = 0, c = 0; y < ny; y++)
            for (int z = 0; z < nz; z++)
                for (int x = 0; x < nx; x++, c++) {
                    int s = grid[c];
                    if (s == Pieces3.AIR) continue;
                    k++;
                    boolean all = true;
                    for (int d = 0; d < 6; d++) {
                        int px = x + DX[d], py = y + DY[d], pz = z + DZ[d];
                        hidden[d] = px >= 0 && py >= 0 && pz >= 0 && px < nx && py < ny && pz < nz
                                && Pieces3.FACE[grid[(py * nz + pz) * nx + px]][OPPOSITE[d]];
                        all &= hidden[d];
                    }
                    if (all && s == Pieces3.FULL) continue;
                    cell[0] = x; cell[1] = y; cell[2] = z;
                    byte fam = (byte) Pieces3.FAMILY[s].ordinal();
                    for (int[] b : Pieces3.BOXES[s])
                        for (int d = 0; d < 6; d++) {
                            int at = b[AXIS[d] + (SIGN[d] > 0 ? 3 : 0)];
                            if (hidden[d] && at == (SIGN[d] > 0 ? 16 : 0)) continue;
                            add(d, fam, k, cell[A[d]] + b[A[d]] / 16f, cell[B[d]] + b[B[d]] / 16f, cell[A[d]] + b[A[d] + 3] / 16f,
                                    cell[B[d]] + b[B[d] + 3] / 16f, cell[AXIS[d]] + at / 16f);
                        }
                }
    }

    private void add(int d, byte fam, int at, float a0, float b0, float a1, float b1, float c) {
        int n = count[d]++;
        if (n == family[d].length) {
            family[d] = java.util.Arrays.copyOf(family[d], n * 2);
            block[d] = java.util.Arrays.copyOf(block[d], n * 2);
            rects[d] = java.util.Arrays.copyOf(rects[d], n * 10);
        }
        float[] r = rects[d];
        r[n * 5] = a0; r[n * 5 + 1] = b0; r[n * 5 + 2] = a1; r[n * 5 + 3] = b1; r[n * 5 + 4] = c;
        family[d][n] = fam;
        block[d][n] = at;
    }

    public int faces() {
        int n = 0;
        for (int c : count) n += c;
        return n;
    }
}
