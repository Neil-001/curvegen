package dev.curvegen.client;

import dev.curvegen.core.paint.Mix;
import dev.curvegen.core.paint.Pattern;
import dev.curvegen.core.paint.Patterns;
import java.util.List;
import java.util.function.Supplier;

/**
 * Where the mix chooser finds the blocks a mix can take: saved patterns, and palettes from another mod. Each is read
 * when the chooser opens, on the client thread. A palette arrives as a ready {@link Mix}: its blocks as cells and its
 * own weights, with the palette's name, which is shown as it is.
 */
public final class MixSources {
    private MixSources() {}

    /** The patterns a mix can take its blocks from. The bundled ones until the pattern library supplies its own. */
    public static Supplier<List<Pattern>> patterns = () -> Patterns.BUNDLED;

    /**
     * Chroma Studio's palettes as mixes, or null when Chroma Studio isn't there to supply them. Only code entered
     * after Chroma Studio is known to be loaded may set this.
     */
    public static Supplier<List<Mix>> palettes = () -> null;
}
