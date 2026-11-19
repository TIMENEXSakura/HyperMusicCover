package com.os4.musiccover.lyrics;

final class LyricTransition {
    /** A measured layout changed under the lyric springs (e.g. new translations or font size). */
    static void rebase(float[] previousBase, float[] base, float[] scroll, float[] aim, float[] nextAim) {
        for (int index = 0; index < base.length; index++) {
            float delta = base[index] - previousBase[index];
            scroll[index] += delta;
            aim[index] += delta;
            nextAim[index] += delta;
        }
    }

    static float motionWeight(int count, float groupHeight, float available) {
        float occupancy = Math.min(1f, groupHeight / Math.max(1f, available));
        // Additional simultaneous voices should not make an unlimited group asymptotically
        // stop scrolling. This caps motion weight, not the number of voices.
        return 1f + 0.12f * (Math.min(4, Math.max(1, count)) - 1) + 0.18f * occupancy;
    }

    static boolean recentSeek(long now, long jumpedAt, long window) {
        return jumpedAt != Long.MIN_VALUE && now >= jumpedAt && now - jumpedAt < window;
    }

    static void shift(float delta, float[] scroll, float[] aim, float[] nextAim) {
        if (delta == 0f) return;
        for (int index = 0; index < scroll.length; index++) {
            scroll[index] += delta;
            aim[index] += delta;
            nextAim[index] += delta;
        }
    }
}
