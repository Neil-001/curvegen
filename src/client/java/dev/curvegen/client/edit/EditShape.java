package dev.curvegen.client.edit;

import dev.curvegen.core.Message;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.paint.Catalog;
import dev.curvegen.core.paint.Paint;
import dev.curvegen.core.edit.Option;
import dev.curvegen.core.edit.Orient;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public interface EditShape {
    record Placed(int dx, int dy, int dz, BlockState state) {}

    record Content(List<Placed> blocks, List<BlockPos> carve, boolean topColours, Message error) {}

    static EditShape of(ShapeSettings s) { return s.is3d() ? new Shape3D(s) : new Shape2D(s); }

    static boolean supports(ShapeSettings.Gen gen) { return gen != null; }

    int[] size();

    void resize(int[] want, boolean[] dragged);

    default int[] bump(int axis, int side, int amount) { return null; }

    default Orient orient(Orient current) { return current; }

    default Orient tip(Orient current, int forward) { return current.tip(forward); }

    default List<double[]> points() { return List.of(); }

    default int pointPlane() { return -1; }

    default int[] movePoint(int index, double[] to) { return null; }

    default boolean removePoint(int index) { return false; }

    default int insertPoint(double[] at) { return -1; }

    default int duplicatePoint(int index) { return -1; }

    default int pointColumns() { return 0; }
    default boolean removePoint(int index, boolean alt) { return removePoint(index); }
    default int insertPoint(double[] at, boolean alt) { return insertPoint(at); }
    default int duplicatePoint(int index, boolean alt) { return duplicatePoint(index); }

    default int[] settle() { return null; }

    default double[] lookAt(double[] origin, double[] dir) { return null; }

    double[] curve(Object solved);

    Object solve(ShapeSettings copy, Orient orient, BooleanSupplier cancelled);

    default Object paint(Object solved, Paint paint, Catalog catalog) { return solved; }

    default boolean solveUsesCarve() { return false; }

    default boolean solveUsesOrient() { return true; }

    Content build(Object solved, Orient orient);

    default List<Option> options() { return List.of(); }

    String describe();
}
