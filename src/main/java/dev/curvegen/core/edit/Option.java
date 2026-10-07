package dev.curvegen.core.edit;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * One of a shape's own settings, as the radial menu's "Shape options" shows it. A shape lists its options and the
 * menu draws a wedge for each, whatever the shape.
 *
 * <p>Getters read the live settings and setters change them. A setter only has to make the change: the editor
 * records the undo step and fits the box afterwards.
 */
public sealed interface Option {
    /** The wedge's name. Keep it under about 13 characters, or the wedge cuts it short. */
    String label();

    /** Null while the option applies. Otherwise why it doesn't, for the greyed-out wedge's tooltip. */
    String off();

    /**
     * A number. Scrolling changes it by {@code step}, and selecting it opens the number box. The menu keeps what it
     * passes to {@code set} within the limits, and whole when {@code whole} is set.
     */
    record Number(String label, String off, DoubleSupplier get, DoubleConsumer set, double min, double max, double step, boolean whole) implements Option {
        /** The value nearest to {@code v} that the option accepts. */
        public double fit(double v) {
            double c = Math.max(min, Math.min(max, v));
            return whole ? Math.rint(c) : c;
        }
    }

    /** One of a few named choices, by index. Scrolling and selecting both step through them. */
    record Cycler(String label, String off, List<String> names, IntSupplier get, IntConsumer set) implements Option {}

    /** A line of text, typed into the text box. {@code check} returns what is wrong with a text, or null when it's fine. */
    record Text(String label, String off, Supplier<String> get, Consumer<String> set, UnaryOperator<String> check) implements Option {}

    /** Something to do rather than a value, such as adding a point. */
    record Action(String label, String off, Runnable run) implements Option {}

    /** A choice between off and on. */
    static Cycler toggle(String label, String off, BooleanSupplier get, Consumer<Boolean> set) {
        return new Cycler(label, off, List.of("OFF", "ON"), () -> get.getAsBoolean() ? 1 : 0, v -> set.accept(v == 1));
    }
}
