package dev.curvegen.core;

/**
 * The faces of a solved 3D shape that can be seen from outside, for the menu's preview. It's built once per solve.
 * Every face is a rectangle square to the axes, kept with the others that face the same way, so a camera can leave
 * out the three directions it can't see without looking at a single face.
 *
 * <p>Coordinates are in blocks from the grid's lowest corner, x east, y up and z south. Directions are numbered as
 * in {@link Pieces3}: north, east, south, west, down, up. A face lies at {@code c} along the axis it faces and spans
 * a0 to a1 and b0 to b1 along the other two: x and y for north and south, z and y for east and west, x and z for
 * down and up.
 */
public final class Mesh3 {
    /** Per direction, five numbers a face: a0, b0, a1, b1, c. */
    public final float[][] rects = new float[6][];
    /** Per direction and face, the ordinal of the piece family the face belongs to. */
    public final byte[][] family = new byte[6][];
    public final int[] count = new int[6];
    public final int nx, ny, nz;

    /** The axis a direction faces along, 0 x, 1 y or 2 z, and the two axes its faces span. */
    public static final int[] AXIS = {2, 0, 2, 0, 1, 1}, A = {0, 2, 0, 2, 0, 0}, B = {1, 1, 1, 1, 2, 2};
    /** 1 for the directions that face the positive way along their axis. */
    public static final int[] SIGN = {-1, 1, 1, -1, -1, 1};
    private static final int[] OPPOSITE = {2, 3, 0, 1, 5, 4};
    private static final int[] DX = {0, 1, 0, -1, 0, 0}, DY = {0, 0, 0, 0, -1, 1}, DZ = {-1, 0, 1, 0, 0, 0};

    public Mesh3(Solver3.Result r) { this(r.grid(), r.nx(), r.ny(), r.nz()); }

    public Mesh3(short[] grid, int nx, int ny, int nz) {
        this.nx = nx; this.ny = ny; this.nz = nz;
        for (int d = 0; d < 6; d++) { rects[d] = new float[1280]; family[d] = new byte[256]; }
        boolean[] hidden = new boolean[6];
        int[] cell = new int[3];
        for (int y = 0, c = 0; y < ny; y++)
            for (int z = 0; z < nz; z++)
                for (int x = 0; x < nx; x++, c++) {
                    int s = grid[c];
                    if (s == Pieces3.AIR) continue;
                    // A face on the block's own boundary is hidden when the neighbour fills the face they share.
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
                            add(d, fam, cell[A[d]] + b[A[d]] / 16f, cell[B[d]] + b[B[d]] / 16f, cell[A[d]] + b[A[d] + 3] / 16f,
                                    cell[B[d]] + b[B[d] + 3] / 16f, cell[AXIS[d]] + at / 16f);
                        }
                }
    }

    private void add(int d, byte fam, float a0, float b0, float a1, float b1, float c) {
        int n = count[d]++;
        if (n == family[d].length) {
            family[d] = java.util.Arrays.copyOf(family[d], n * 2);
            rects[d] = java.util.Arrays.copyOf(rects[d], n * 10);
        }
        float[] r = rects[d];
        r[n * 5] = a0; r[n * 5 + 1] = b0; r[n * 5 + 2] = a1; r[n * 5 + 3] = b1; r[n * 5 + 4] = c;
        family[d][n] = fam;
    }

    public int faces() {
        int n = 0;
        for (int c : count) n += c;
        return n;
    }
}
