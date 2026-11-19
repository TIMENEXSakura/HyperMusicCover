package com.os4.musiccover.lyrics;

/** Blur levels between the sharp active lyrics and the farthest visible rows. */
final class LyricDepth {
    static final int LEVELS = 5;
    private static final float FIRST_DP = 0.9f;
    private static final float STEP_DP = 1.8f;

    private LyricDepth() {}

    static float radius(float level) {
        return level <= 0f ? 0f : FIRST_DP + STEP_DP * (Math.min(LEVELS, level) - 1f);
    }

    static float target(float distance, float rowAdvance, boolean above) {
        float rows = Math.max(1f, distance / Math.max(1f, rowAdvance));
        return radius(above ? rows + 1f : Math.min(LEVELS - 1f, rows));
    }
}
