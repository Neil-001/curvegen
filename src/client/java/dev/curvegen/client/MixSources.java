package dev.curvegen.client;

import dev.curvegen.core.paint.Mix;
import dev.curvegen.core.paint.Pattern;
import java.util.List;
import java.util.function.Supplier;

public final class MixSources {
    private MixSources() {}

    public static Supplier<List<Pattern>> patterns = () -> PatternStore.list().stream().map(s -> s.pattern).toList();

    public static Supplier<List<Mix>> palettes = () -> null;
}
