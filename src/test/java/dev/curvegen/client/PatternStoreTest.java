package dev.curvegen.client;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.paint.Patterns;
import org.junit.jupiter.api.Test;

class PatternStoreTest {
    @Test
    void aPatternFileRoundTrips() {
        PatternStore.Saved s = PatternStore.read(PatternStore.write(Patterns.TWO_LANE_ROAD, true, 1234));
        assertNotNull(s);
        assertEquals(Patterns.TWO_LANE_ROAD, s.pattern);
        assertTrue(s.pinned);
        assertEquals(1234, s.saved);
    }

    @Test
    void badOrNewerFilesAreSkipped() {
        assertNull(PatternStore.read("[]"));
        assertNull(PatternStore.read("{\"name\": \"x\"}"));
        String newer = PatternStore.write(Patterns.CHECKER, false, 0).replace("\"version\": 1", "\"version\": 2");
        assertNull(PatternStore.read(newer));
        // A file without a pin or a time still reads.
        String plain = PatternStore.write(Patterns.CHECKER, false, 0).replaceAll("\\s*\"(pinned|saved)\": [a-z0-9]+,", "");
        PatternStore.Saved s = PatternStore.read(plain);
        assertNotNull(s);
        assertFalse(s.pinned);
    }
}
