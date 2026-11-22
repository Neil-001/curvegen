package dev.curvegen.core.edit;

public final class HoldTimer {
    public enum Event { NONE, STEP, OPEN }

    private int key = -1;
    private long since;
    private boolean cancelled;
    private long fresh;

    public int key() { return key; }

    public void reset() { key = -1; }

    public void cancel() { cancelled = true; }

    public Event update(int pressed, boolean down, long nowMillis, double holdSeconds) {
        if (key >= 0 && !down) key = -1;
        if (pressed >= 0) {
            key = pressed;
            since = nowMillis;
            cancelled = false;
            return Event.STEP;
        }
        if (key >= 0 && !cancelled && nowMillis - since >= holdSeconds * 1000) return Event.OPEN;
        return Event.NONE;
    }

    public void opened() { key = -1; }

    public void press(int key) { fresh |= 1L << key; }

    public int takePress() {
        int pressed = -1;
        for (int k = 0; k < 64; k++) if ((fresh >> k & 1) != 0 && (pressed < 0 || pressed == key)) pressed = k;
        fresh = 0;
        return pressed;
    }

    public double progress(long nowMillis, double barDelaySeconds, double holdSeconds) {
        if (key < 0 || cancelled) return -1;
        double held = (nowMillis - since) / 1000.0;
        if (held < barDelaySeconds || holdSeconds <= 0) return -1;
        return Math.min(1, held / holdSeconds);
    }
}
