package dev.curvegen.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LastPlacementTest {
    @Test
    void undoAndRedoTakeTurns() {
        LastPlacement<String> last = new LastPlacement<>();
        assertNull(last.undo());
        assertNull(last.redo());
        last.placed("arch");
        assertNull(last.redo(), "nothing was undone yet");
        assertEquals("arch", last.undo());
        assertNull(last.undo(), "it's already gone");
        assertEquals("arch", last.redo());
        assertNull(last.redo(), "it's already back");
        // Redo hands the placement over, and it counts once it's placed again.
        assertNull(last.undo());
        last.placed("arch again");
        assertEquals("arch again", last.undo());
        assertEquals("arch again", last.redo());
    }

    @Test
    void aNewPlacementLeavesNothingToRedo() {
        LastPlacement<String> last = new LastPlacement<>();
        last.placed("arch");
        last.undo();
        last.placed("dome");
        assertNull(last.redo());
        assertEquals("dome", last.undo());
    }
}
