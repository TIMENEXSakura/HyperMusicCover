package com.os4.musiccover.lyrics;

/** The reveal and its reversal share one clock. Progress zero is the last unobscured lyric frame. */
final class LyricPageExit {
    static final long DURATION_MS = 620;
    private long last, travel;
    private boolean active, closing;

    void start(long now) {
        active = closing = true;
        travel = 0;
        last = now;
    }

    void setClosing(boolean hide, long now) {
        if (!active || closing == hide) return;
        // Reverse from the last displayed progress. An input between vsyncs must
        // not advance the mask in the old direction on the next frame.
        closing = hide;
        last = Math.max(last, now);
    }

    void step(long now) {
        if (!active) return;
        long elapsed = Math.max(0L, Math.min(DURATION_MS, now - last));
        travel = Math.max(0L, Math.min(DURATION_MS, travel + (closing ? elapsed : -elapsed)));
        last = Math.max(last, now);
        active = closing ? travel < DURATION_MS : travel > 0;
    }

    float progress() { return travel / (float) DURATION_MS; }
    boolean active() { return active; }
    boolean closed() { return travel == DURATION_MS && !active; }
    void reset() { active = closing = false; travel = last = 0; }
}
