package dev.curvegen.core.edit;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.Function;

import dev.curvegen.core.Message;

public sealed interface Option {
    String label();

    String off();

    record Number(String label, String off, DoubleSupplier get, DoubleConsumer set, double min, double max, double step, boolean whole, boolean round) implements Option {
        public Number(String label, String off, DoubleSupplier get, DoubleConsumer set, double min, double max, double step, boolean whole) {
            this(label, off, get, set, min, max, step, whole, false);
        }

        public double fit(double v) {
            double c = Math.max(min, Math.min(max, v));
            return whole ? Math.rint(c) : c;
        }

        public double stepped(double v, int dir) {
            if (!round) return fit(v + dir * step);
            double span = max - min + (whole ? 1 : 0), to = Stepper.next(v - min, step, dir, -step, span);
            return fit(min + (to % span + span) % span);
        }
    }

    record Cycler(String label, String off, List<String> names, IntSupplier get, IntConsumer set) implements Option {}

    record Text(String label, String off, Supplier<String> get, Consumer<String> set, Function<String, Message> check) implements Option {}

    record Action(String label, String off, Runnable run) implements Option {}

    static Cycler toggle(String label, String off, BooleanSupplier get, Consumer<Boolean> set) {
        return new Cycler(label, off, List.of("curvegen.choice.off", "curvegen.choice.on"), () -> get.getAsBoolean() ? 1 : 0, v -> set.accept(v == 1));
    }
}
