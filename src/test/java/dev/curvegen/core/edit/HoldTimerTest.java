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
        for (long now = 50; now < 3000; now += 50) assertEquals(Event.NONE, t.update(-1, true, now, HOLD));
        assertEquals(Event.OPEN, t.update(-1, true, 3000, HOLD));
        assertEquals(1, t.key(), "the key the box is for");
        t.opened();
        assertEquals(-1, t.key());
        assertEquals(Event.NONE, t.update(-1, false, 3050, HOLD));
    }

    @Test
    void aKeyLetGoAndPressedAgainBetweenTwoTicksActsAgainAndStartsItsHoldAfresh() {
        HoldTimer t = new HoldTimer();
        t.press(1);
        assertEquals(Event.STEP, t.update(t.takePress(), true, 0, HOLD));
        // It came up and went down again before the next tick, so it looks held throughout.
        t.press(1);
        assertEquals(Event.STEP, t.update(t.takePress(), true, 600, HOLD));
        assertEquals(1, t.key());
        assertEquals(Event.NONE, t.update(t.takePress(), true, 3000, HOLD), "the hold counts from the second press");
        assertEquals(Event.OPEN, t.update(t.takePress(), true, 3600, HOLD));
    }

    @Test
    void aTapAndAnotherWithinOneTickEachAct() {
        HoldTimer t = new HoldTimer();
        assertEquals(Event.STEP, t.update(1, false, 0, HOLD));
        assertEquals(Event.STEP, t.update(1, false, 50, HOLD));
        assertEquals(Event.NONE, t.update(-1, false, 100, HOLD));
        assertEquals(-1, t.progress(400, DELAY, HOLD), "neither is being held");
    }

    @Test
    void aCancelledHoldOpensNothingUntilAKeyIsPressedAgain() {
        HoldTimer t = new HoldTimer();
        t.update(1, true, 0, HOLD);
        t.cancel();
        assertEquals(-1, t.progress(2000, DELAY, HOLD));
        assertEquals(Event.NONE, t.update(-1, true, 2000, HOLD));
        assertEquals(Event.NONE, t.update(-1, true, 5000, HOLD));
        // Pressed again without ever looking let go: a fresh press, which starts a hold of its own.
        assertEquals(Event.STEP, t.update(1, true, 5100, HOLD));
        assertTrue(t.progress(5600, DELAY, HOLD) > 0);
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

    @Test
    void onlyAFreshPressCounts() {
        HoldTimer t = new HoldTimer();
        assertEquals(-1, t.takePress());
        t.press(3);
        assertEquals(3, t.takePress());
        assertEquals(-1, t.takePress(), "taken once");
    }

    @Test
    void aKeyHeldThroughTheNumberBoxDoesNothingMoreUntilItIsPressedAgain() {
        HoldTimer t = new HoldTimer();
        t.press(3);
        assertEquals(Event.STEP, t.update(t.takePress(), true, 0, 3));
        assertEquals(Event.OPEN, t.update(t.takePress(), true, 3000, 3));
        t.opened();
        t.reset();   // the box is a screen, which resets the timer every tick
        // The box closes with the key still down. It repeats, and is let go before the next tick: neither is a press.
        assertEquals(Event.NONE, t.update(t.takePress(), false, 4000, 3));
        assertEquals(Event.NONE, t.update(t.takePress(), false, 4050, 3));
        // Let go and pressed again, even within one tick, is.
        t.press(3);
        assertEquals(Event.STEP, t.update(t.takePress(), true, 4100, 3));
    }

    @Test
    void anotherKeysPressComesBeforeTheHeldKeysOwn() {
        HoldTimer t = new HoldTimer();
        t.press(5);
        assertEquals(Event.STEP, t.update(t.takePress(), true, 0, 3));
        t.press(5);
        t.press(2);
        assertEquals(Event.STEP, t.update(t.takePress(), true, 100, 3));
        assertEquals(2, t.key());
        t.press(2);
        t.press(5);
        assertEquals(5, t.takePress(), "2 is the key held now");
    }
}
