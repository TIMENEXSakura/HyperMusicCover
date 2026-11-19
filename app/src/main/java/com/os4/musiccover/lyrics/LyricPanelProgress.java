package com.os4.musiccover.lyrics;

/** Bridge a navigation panel choice until its music morph reports its own progress. */
final class LyricPanelProgress {
    private static final long MORPH_GRACE_MS = 48L;
    private static final float TAU_SECONDS = 0.16f;
    private static final float EPSILON = 0.002f;
    private boolean moving;
    private long changedAt;

    float update(boolean eligible, boolean morph, float actual, boolean keptMini,
                 float previous, long now, float dt) {
        if (morph) {
            moving = false;
            return Math.max(0f, Math.min(1f, actual));
        }
        if (!eligible) {
            moving = false;
            return keptMini ? 0f : 1f;
        }
        float target = keptMini ? 0f : 1f;
        if (Math.abs(target - previous) <= EPSILON) {
            moving = false;
            return target;
        }
        if (!moving) {
            changedAt = now;
            moving = true;
        }
        if (now - changedAt < MORPH_GRACE_MS || dt <= 0f) return previous;
        float result = previous + (target - previous) * (1f - (float) Math.exp(-dt / TAU_SECONDS));
        if (Math.abs(target - result) <= EPSILON) {
            moving = false;
            return target;
        }
        return result;
    }

    boolean moving() { return moving; }
    void reset() { moving = false; changedAt = 0L; }
}
