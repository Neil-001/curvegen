package dev.curvegen.core.edit;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.edit.HoldTimer.Event;
import org.junit.jupiter.api.Test;

class HoldTimerTest {
    private static final double HOLD = 3, DELAY = 0.3;

    @Test
    void aTapActsOnceOnThePress() {
        HoldTimer t = new HoldTimer();
        assertEquals(Event.STEP, t.update(2, true, 1000, HOLD));
        assertEquals(2, t.key());
        assertEquals(Event.NONE, t.update(-1, true, 1050, HOLD));
        assertEquals(Event.NONE, t.update(-1, false, 1100, HOLD));
        assertEquals(-1, t.key());
        assertEquals(-1, t.progress(1100, DELAY, HOLD));
    }

    @Test
    void aTapShorterThanATickStillActs() {
        HoldTimer t = new HoldTimer();
        assertEquals(Event.STEP, t.update(0, false, 1000, HOLD));
        assertEquals(Event.NONE, t.update(-1, false, 1050, HOLD));
        assertEquals(Event.STEP, t.update(0, false, 1100, HOLD), "and the next tap acts again");
    }

    @Test
    void theBarShowsAfterItsDelayAndFillsByTheHoldTime() {
        HoldTimer t = new HoldTimer();
        t.update(0, true, 1000, HOLD);
        assertEquals(-1, t.progress(1000, DELAY, HOLD));
        assertEquals(-1, t.progress(1299, DELAY, HOLD));
        assertEquals(0.1, t.progress(1300, DELAY, HOLD), 1e-9);
        assertEquals(0.5, t.progress(2500, DELAY, HOLD), 1e-9);
        assertEquals(1, t.progress(9000, DELAY, HOLD));
        assertEquals(0.5, t.progress(2500, 0, HOLD), 1e-9, "with no delay it shows from the start");
        assertEquals(-1, t.progress(2500, 5, HOLD), "a delay past the hold time never shows it");
    }

    @Test
    void holdingOpensTheNumberBoxOnce() {
        HoldTimer t = new HoldTimer();
        assertEquals(Event.STEP, t.update(1, true, 0, HOLD));
        // The game repeats a held key. The repeats don't act again.
        for (long now = 50; now < 3000; now += 50) assertEquals(Event.NONE, t.update(1, true, now, HOLD));
        assertEquals(Event.OPEN, t.update(1, true, 3000, HOLD));
        assertEquals(1, t.key(), "the key the box is for");
        t.opened();
        assertEquals(-1, t.key());
        assertEquals(Event.NONE, t.update(-1, false, 3050, HOLD));
    }

    @Test
    void aRepeatThatArrivesWithTheReleaseDoesNotActAgain() {
        HoldTimer t = new HoldTimer();
        assertEquals(Event.STEP, t.update(1, true, 0, HOLD));
        assertEquals(Event.NONE, t.update(1, false, 600, HOLD), "the key repeated and came up within one tick");
        assertEquals(-1, t.key());
        assertEquals(Event.STEP, t.update(1, true, 700, HOLD), "a new press acts");
    }

    @Test
    void aCancelledHoldOpensNothingAndStillIgnoresRepeats() {
        HoldTimer t = new HoldTimer();
        t.update(1, true, 0, HOLD);
        t.cancel();
        assertEquals(-1, t.progress(2000, DELAY, HOLD));
        assertEquals(Event.NONE, t.update(1, true, 2000, HOLD));
        assertEquals(Event.NONE, t.update(1, true, 5000, HOLD));
        assertEquals(Event.NONE, t.update(-1, false, 5050, HOLD));
        assertEquals(Event.STEP, t.update(1, true, 5100, HOLD), "the next press starts a hold of its own");
        assertEquals(Event.OPEN, t.update(-1, true, 8100, HOLD));
    }

    @Test
    void lettingGoBeforeTheHoldTimeOpensNothing() {
        HoldTimer t = new HoldTimer();
        t.update(1, true, 0, HOLD);
        assertEquals(Event.NONE, t.update(-1, true, 2950, HOLD));
        assertEquals(Event.NONE, t.update(-1, false, 3000, HOLD));
        assertEquals(Event.NONE, t.update(-1, false, 9000, HOLD));
    }

    @Test
    void anotherKeyTakesOverAndStartsItsOwnHold() {
        HoldTimer t = new HoldTimer();
        t.update(1, true, 0, HOLD);
        assertEquals(Event.STEP, t.update(4, true, 2000, HOLD));
        assertEquals(4, t.key());
        assertEquals(Event.NONE, t.update(-1, true, 3000, HOLD), "the hold counts from the second key's press");
        assertEquals(Event.OPEN, t.update(-1, true, 5000, HOLD));
    }

    @Test
    void resettingForgetsTheHeldKey() {
        HoldTimer t = new HoldTimer();
        t.update(1, true, 0, HOLD);
        t.reset();
        assertEquals(-1, t.key());
        assertEquals(Event.NONE, t.update(-1, true, 5000, HOLD));
    }
}
