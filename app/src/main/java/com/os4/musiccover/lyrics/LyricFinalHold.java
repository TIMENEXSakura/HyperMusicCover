package com.os4.musiccover.lyrics;

/** The last sung line finishes at its current position, with no following row to scroll to. */
final class LyricFinalHold {
    private float amount;
    private float target;
    private float precedingDistance;

    boolean step(int position, int start, int end, float dt, boolean snap) {
        target = end > start && position >= end ? 1f : 0f;
        float before = amount;
        if (snap || position < start) amount = target;
        else if (amount != target && dt > 0f) {
            float travel = dt * 1000f / LyricExitWipe.DURATION_MS;
            amount = target > amount ? Math.min(target, amount + travel) : Math.max(target, amount - travel);
        }
        return before != amount;
    }

    boolean completed() { return target == 1f; }
    boolean moving() { return amount != target; }
    float progress() { return amount; }
    float rowProgress(int index, int count) { return index == count - 1 ? amount : 0f; }

    /** At song end, the rows above the last leave together through the usual viewport edge. */
    float precedingScroll(float precedingBottom, float clipTop) {
        // A backward seek can move the penultimate row many screens below the viewport.
        // Return using the last departure's distance while the terminal wipe reverses.
        if (completed()) precedingDistance = Math.max(0f, precedingBottom - clipTop);
        return precedingDistance * amount;
    }

    void reset() { amount = target = precedingDistance = 0f; }
}
