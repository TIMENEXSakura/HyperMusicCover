package com.os4.musiccover.lyrics;

/** The last sung line stays in place; completion gently relaxes its appearance. */
final class LyricFinalHold {
    private static final float TIME_SECONDS = 0.22f;
    private float amount;
    private float target;

    boolean step(int position, int start, int end, float dt, boolean snap) {
        target = end > start && position >= end ? 1f : 0f;
        float before = amount;
        if (snap) amount = target;
        else if (amount != target && dt > 0f) {
            amount += (target - amount) * (1f - (float) Math.exp(-dt / TIME_SECONDS));
            if (Math.abs(target - amount) < 0.003f) amount = target;
        }
        return before != amount;
    }

    boolean completed() { return target == 1f; }
    boolean moving() { return amount != target; }
    float alpha() { return 1f - 0.45f * amount; }
    float scale() { return 1f - 0.06f * amount; }
    void reset() { amount = target = 0f; }
}
