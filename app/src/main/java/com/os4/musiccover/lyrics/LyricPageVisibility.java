package com.os4.musiccover.lyrics;

/** The lyric page's own progress; an island page does not unfold the media card. */
final class LyricPageVisibility {
    private LyricPageVisibility() {}

    static float progress(boolean capsulePage, boolean coverMode, float cardProgress) {
        // Cover mode still owns its card/lyric transition, including a page first opened from
        // an island. Outside cover mode the card is deliberately kept at zero progress.
        if (coverMode) return Math.max(0f, Math.min(1f, cardProgress));
        return capsulePage ? 1f : 0f;
    }
}
