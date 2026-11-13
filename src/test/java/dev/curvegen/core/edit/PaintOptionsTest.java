package dev.curvegen.core.edit;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.paint.Paint;
import dev.curvegen.core.paint.Paint.Fit;
import dev.curvegen.core.paint.Paint.Source;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The radial menu's Paint wedges. */
class PaintOptionsTest {
    private static Option find(List<Option> options, String label) {
        return options.stream().filter(o -> o.label().equals(label)).findFirst().orElseThrow();
    }

    private static void scroll(Option.Number n, int dir) { n.set().accept(n.stepped(n.get().getAsDouble(), dir)); }

    @Test
    void theWedgesAreTheSameForEverySourceAndOnlyAPatternsAreLive() {
        Paint p = new Paint();
        List<String> labels = null;
        for (Source s : Source.values()) {
            p.source = s;
            List<Option> options = PaintOptions.of(p);
            List<String> now = options.stream().map(Option::label).toList();
            if (labels == null) labels = now; else assertEquals(labels, now, "the same places for " + s);
            assertNull(options.getFirst().off(), "Source is never greyed, so paint can be turned on");
            for (Option o : options.subList(1, options.size()))
                assertEquals(s == Source.PATTERN, o.off() == null, o.label() + " with " + s);
        }
        p.source = Source.MIX;
        assertEquals(PaintOptions.MIX_OFF, find(PaintOptions.of(p), "curvegen.paint.turn").off());
        p.source = Source.OFF;
        assertEquals("curvegen.paint.off_tip", find(PaintOptions.of(p), "curvegen.paint.mirror").off());
    }

    @Test
    void sourceCyclesThroughEverySourceAndBack() {
        Paint p = new Paint();
        Option.Cycler source = (Option.Cycler) PaintOptions.of(p).getFirst();
        assertEquals(Source.values().length, source.names().size());
        assertEquals("curvegen.paint.source.mix", source.names().get(Source.MIX.ordinal()));
        source.set().accept(Source.PATTERN.ordinal());
        assertEquals(Source.PATTERN, p.source);
    }

    @Test
    void turnStepsFifteenDegreesOntoTheGridAndGoesRound() {
        Paint p = new Paint();
        p.source = Source.PATTERN;
        Option.Number turn = (Option.Number) find(PaintOptions.of(p), "curvegen.paint.turn");
        scroll(turn, 1);
        assertEquals(15, p.turn);
        scroll(turn, -1); scroll(turn, -1);
        assertEquals(345, p.turn, "down from 0 goes round");
        scroll(turn, 1);
        assertEquals(0, p.turn, "up from 345 goes round");
        p.turn = 7;
        scroll(turn, 1);
        assertEquals(15, p.turn, "a typed angle steps onto the grid");
        p.turn = 7;
        scroll(turn, -1);
        assertEquals(0, p.turn);
    }

    @Test
    void slidesMoveABlockAndSizeStaysInItsLimits() {
        Paint p = new Paint();
        p.source = Source.PATTERN;
        List<Option> options = PaintOptions.of(p);
        scroll((Option.Number) find(options, "curvegen.radial.paint.along"), -1);
        scroll((Option.Number) find(options, "curvegen.radial.paint.across"), 1);
        assertEquals(-1, p.along);
        assertEquals(1, p.across);
        Option.Number size = (Option.Number) find(options, "curvegen.paint.size");
        scroll(size, -1);
        assertEquals(1, p.size);
        p.size = Paint.MAX_SIZE;
        scroll(size, 1);
        assertEquals(Paint.MAX_SIZE, p.size);
        Option.Cycler mirror = (Option.Cycler) find(options, "curvegen.paint.mirror");
        mirror.set().accept(1);
        assertTrue(p.mirror);
    }

    @Test
    void sizeIsGreyedUnderStretch() {
        Paint p = new Paint();
        p.source = Source.PATTERN;
        p.fit = Fit.STRETCH;
        assertEquals("curvegen.paint.size_stretch_tip", find(PaintOptions.of(p), "curvegen.paint.size").off());
        p.fit = null;   // each shape's default repeats
        assertNull(find(PaintOptions.of(p), "curvegen.paint.size").off());
    }

    @Test
    void aPlainNumberStillStepsAsBefore() {
        double[] v = {2.5};
        Option.Number n = new Option.Number("x", null, () -> v[0], x -> v[0] = x, 0, 3, 1, false);
        assertEquals(3, n.stepped(v[0], 1));
        assertEquals(1.5, n.stepped(v[0], -1));
    }
}
