package com.os4.musiccover.lyrics;

/** A normal dismissal sweeps the visible band. A capsule tap fades the whole last frame. */
final class LyricPageExit {
    static final long DURATION_MS = 620;
    static final long CAPSULE_FADE_MS = 120;
    private long last, wipe, fade;
    private boolean active, closing, capsule;

    void start(long now) {
        reset();
        active = closing = true;
        last = now;
    }

    void closeFromCapsule(long now) {
        if (!active) start(now);
        if (!capsule || !closing) last = Math.max(last, now);
        // Keep any already-visible wipe boundary stationary under the short group fade.
        // A later cover teardown can repeat the close, but cannot change its presentation.
        closing = capsule = true;
    }

    void setClosing(boolean hide, long now) {
        if (!active || closing == hide) return;
        closing = hide;
        last = Math.max(last, now);
    }

    void step(long now) {
        if (!active) return;
        long elapsed = Math.max(0L, Math.min(DURATION_MS, now - last));
        if (!closing) {
            wipe = Math.max(0L, wipe - elapsed);
            fade = Math.max(0L, fade - elapsed);
        } else if (capsule) {
            fade = Math.min(CAPSULE_FADE_MS, fade + elapsed);
        } else {
            wipe = Math.min(DURATION_MS, wipe + elapsed);
        }
        last = Math.max(last, now);
        active = closing ? wipe < DURATION_MS && fade < CAPSULE_FADE_MS : wipe > 0 || fade > 0;
    }

    float progress() { return wipe / (float) DURATION_MS; }
    float opacity() { return 1f - fade / (float) CAPSULE_FADE_MS; }
    boolean capsuleFade() { return capsule; }
    boolean active() { return active; }
    boolean closed() { return !active && (wipe == DURATION_MS || fade == CAPSULE_FADE_MS); }
    void reset() { active = closing = capsule = false; wipe = fade = last = 0; }
}
