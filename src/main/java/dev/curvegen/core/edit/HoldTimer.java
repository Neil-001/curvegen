package dev.curvegen.core.edit;

/**
 * Tells a tap from a hold for the keys that move or resize the hologram. A key acts once when it goes down. Held, a
 * progress bar shows after a short delay, and at the full hold time the number box opens. The game repeats a held key
 * like any other, and those repeats are ignored.
 */
public final class HoldTimer {
    public enum Event { NONE, STEP, OPEN }

    private int key = -1;
    private long since;
    /** Set when the hold was called off: the key is still down and still ignored, but no bar shows and no box opens. */
    private boolean cancelled;
    /** A bit for each key that went down afresh since the last {@link #takePress}. */
    private long fresh;

    /** The key being held, or -1. */
    public int key() { return key; }

    public void reset() { key = -1; }

    /**
     * Calls the hold off without forgetting the key, whose repeats must still be ignored until it comes up. Undo and
     * redo do this, because the number box would otherwise replace a step that is no longer the last edit.
     */
    public void cancel() { cancelled = true; }

    /**
     * Call every tick. {@code pressed} is a key that went down since the last call, or -1, and {@code down} whether
     * the key being held still is. STEP means {@code key()} should act once. OPEN means the number box should open
     * for the key this returned STEP for, which is then no longer held.
     */
    public Event update(int pressed, boolean down, long nowMillis, double holdSeconds) {
        // A press of the key that was held is one of its repeats, even if the key has come up since.
        int held = key;
        if (key >= 0 && !down) key = -1;
        if (pressed >= 0 && pressed != held) {
            key = pressed;
            since = nowMillis;
            cancelled = false;
            return Event.STEP;
        }
        if (key >= 0 && !cancelled && nowMillis - since >= holdSeconds * 1000) return Event.OPEN;
        return Event.NONE;
    }

    /** Call after acting on OPEN. */
    public void opened() { key = -1; }

    /**
     * A key went down afresh, not as one of a held key's repeats. Call this from the key event itself. The game also
     * queues a "click" for every repeat, and nothing read later can tell those from presses: a key still down when
     * the number box closes goes on repeating, and would act again.
     */
    public void press(int key) { fresh |= 1L << key; }

    /**
     * The key to pass to {@link #update} as pressed: one that went down since the last call, or -1. With several,
     * any other than the key being held comes first.
     */
    public int takePress() {
        int pressed = -1;
        for (int k = 0; k < 64; k++) if ((fresh >> k & 1) != 0 && (pressed < 0 || pressed == key)) pressed = k;
        fresh = 0;
        return pressed;
    }

    /** How full the progress bar is, from 0 to 1, or -1 while it doesn't show. */
    public double progress(long nowMillis, double barDelaySeconds, double holdSeconds) {
        if (key < 0 || cancelled) return -1;
        double held = (nowMillis - since) / 1000.0;
        if (held < barDelaySeconds || holdSeconds <= 0) return -1;
        return Math.min(1, held / holdSeconds);
    }
}
