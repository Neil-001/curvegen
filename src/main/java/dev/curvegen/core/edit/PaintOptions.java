package dev.curvegen.core.edit;

import dev.curvegen.core.paint.Paint;
import dev.curvegen.core.paint.Paint.Edges;
import dev.curvegen.core.paint.Paint.Fit;
import dev.curvegen.core.paint.Paint.Flow;
import dev.curvegen.core.paint.Paint.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The radial menu's Paint wedges: Source, then the current source's own options. The menu puts its Choose wedge,
 * which opens a screen, second. Off, Pattern and Mix show the same wedges in the same places, greyed where they don't
 * apply, so the player can still see and turn paint on. A gradient has its own: Direction, Reverse and Edges. A new
 * source's own options go in its case in {@link #of}.
 */
public final class PaintOptions {
    private PaintOptions() {}

    /** Why the pattern's wedges are greyed with a mix. */
    public static final String MIX_OFF = "curvegen.radial.paint.mix_tip";

    /** Why a gradient's wedges are greyed before one is chosen. */
    public static final String NO_GRADIENT = "curvegen.paint.status_no_gradient";

    /** Every source's wedges, as with Chroma Studio installed. */
    public static List<Option> of(Paint p) { return of(p, true); }

    /**
     * The wedges for {@code p}. Without {@code gradients}, which come from Chroma Studio, Source leaves Gradient out, as
     * a cycle can't grey one choice; the menu's tooltip says why.
     */
    public static List<Option> of(Paint p, boolean gradients) {
        List<Option> out = new ArrayList<>();
        List<Source> sources = new ArrayList<>(List.of(Source.values()));
        if (!gradients && p.source != Source.GRADIENT) sources.remove(Source.GRADIENT);
        List<String> names = new ArrayList<>();
        for (Source s : sources) names.add("curvegen.paint.source." + s.name().toLowerCase(Locale.ROOT));
        out.add(new Option.Cycler("curvegen.paint.source", null, names, () -> sources.indexOf(p.source), v -> p.source = sources.get(v)));
        switch (p.source) {
            case OFF -> out.addAll(pattern(p, "curvegen.paint.off_tip"));
            case PATTERN -> out.addAll(pattern(p, null));
            case MIX -> out.addAll(pattern(p, MIX_OFF));
            case GRADIENT -> out.addAll(gradient(p, p.gradient == null ? NO_GRADIENT : null));
        }
        return out;
    }

    /** A gradient's Direction, Reverse and Edges, greyed with {@code off} when it isn't null. */
    private static List<Option> gradient(Paint p, String off) {
        List<String> flows = new ArrayList<>();
        for (Flow f : Flow.values()) flows.add("curvegen.radial.paint.flow." + f.name().toLowerCase(Locale.ROOT));
        List<String> edges = new ArrayList<>();
        for (Edges e : Edges.values()) edges.add("curvegen.paint.edges." + e.name().toLowerCase(Locale.ROOT));
        return List.of(
                new Option.Cycler("curvegen.paint.flow", off, flows, () -> p.flow.ordinal(), v -> p.flow = Flow.values()[v]),
                Option.toggle("curvegen.paint.reverse", off, () -> p.reverse, v -> p.reverse = v),
                new Option.Cycler("curvegen.paint.edges", off, edges, () -> p.edges.ordinal(), v -> p.edges = Edges.values()[v]));
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
