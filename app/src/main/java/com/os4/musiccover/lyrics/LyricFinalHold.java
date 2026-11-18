package com.os4.musiccover.lyrics;

/** The last sung line stays in place; completion gently relaxes its appearance. */
final class LyricFinalHold {
    static final float FADE_FRACTION = 0.45f;
    private float amount;
    private float target;

    boolean step(int position, int start, int end, float dt, boolean snap) {
        target = end > start && position >= end ? 1f : 0f;
        float before = amount;
        if (snap) amount = target;
        else if (amount != target && dt > 0f) {
            float travel = dt * 1000f / LyricExitWipe.DURATION_MS;
            amount = target > amount ? Math.min(target, amount + travel) : Math.max(target, amount - travel);
        }
        return before != amount;
    }

    boolean completed() { return target == 1f; }
    boolean moving() { return amount != target; }
    float progress() { return amount; }
    float alpha() { return 1f - FADE_FRACTION * amount; }
    void reset() { amount = target = 0f; }
}
