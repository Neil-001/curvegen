package dev.curvegen.core.edit;

import dev.curvegen.core.paint.Paint;
import dev.curvegen.core.paint.Paint.Fit;
import dev.curvegen.core.paint.Paint.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The radial menu's Paint wedges: Source, then the current source's own options. The menu puts its Choose wedge,
 * which opens a screen, second. Every source shows the same wedges in the same places, greyed where they don't apply,
 * so the player can still see and turn paint on. A new source's own options go in its case in {@link #of}.
 */
public final class PaintOptions {
    private PaintOptions() {}

    /** Why the pattern's wedges are greyed with a mix. */
    public static final String MIX_OFF = "curvegen.radial.paint.mix_tip";

    public static List<Option> of(Paint p) {
        List<Option> out = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Source s : Source.values()) names.add("curvegen.paint.source." + s.name().toLowerCase(Locale.ROOT));
        out.add(new Option.Cycler("curvegen.paint.source", null, names, () -> p.source.ordinal(), v -> p.source = Source.values()[v]));
        switch (p.source) {
            case OFF -> out.addAll(pattern(p, "curvegen.paint.off_tip"));
            case PATTERN -> out.addAll(pattern(p, null));
            case MIX -> out.addAll(pattern(p, MIX_OFF));
        }
        return out;
    }

    /** A pattern's Turn, Slide along, Slide across, Size and Mirror, all greyed with {@code off} when it isn't null. */
    private static List<Option> pattern(Paint p, String off) {
        String sizeOff = off != null ? off : p.fit == Fit.STRETCH ? "curvegen.paint.size_stretch_tip" : null;
        return List.of(
                new Option.Number("curvegen.paint.turn", off, () -> p.turn, v -> p.turn = (int) v, 0, 359, 15, true, true),
                new Option.Number("curvegen.radial.paint.along", off, () -> p.along, v -> p.along = (int) v, -Paint.MAX_SLIDE, Paint.MAX_SLIDE, 1, true),
                new Option.Number("curvegen.radial.paint.across", off, () -> p.across, v -> p.across = (int) v, -Paint.MAX_SLIDE, Paint.MAX_SLIDE, 1, true),
                new Option.Number("curvegen.paint.size", sizeOff, () -> p.size, v -> p.size = (int) v, 1, Paint.MAX_SIZE, 1, true),
                Option.toggle("curvegen.paint.mirror", off, () -> p.mirror, v -> p.mirror = v));
    }
}
