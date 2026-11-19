package com.os4.musiccover.lyrics;

/** Vertical viewport movement for a single lead lyric taller than the visible region. */
final class LyricOverflowFollow {
    private LyricOverflowFollow() {}

    static float timeChars(int position, int start, int end, int length) {
        if (length <= 0 || end <= start) return 0f;
        float progress = (float) (((double) position - start) / ((double) end - start));
        return Math.max(0f, Math.min(1f, progress)) * length;
    }

    /** Continuously crosses a wrapped row rather than jumping at its first character. */
    static float rowPosition(float character, int start, int end, float top, float bottom) {
        float fraction = end <= start ? 0f : (character - start) / (end - start);
        return top + Math.max(0f, Math.min(1f, fraction)) * (bottom - top);
    }

    /** Offset from the normal centred group. Never moves past either end of the lead text. */
    static float offset(float focusY, float leadHeight, float viewportTop, float viewportBottom,
                        float centredTop) {
        return offset(focusY, leadHeight, viewportTop, viewportBottom, centredTop, 0f);
    }

    static float offset(float focusY, float leadHeight, float viewportTop, float viewportBottom,
                        float centredTop, float cardProgress) {
        float viewport = viewportBottom - viewportTop;
        if (!Float.isFinite(focusY) || !Float.isFinite(leadHeight) || !Float.isFinite(viewport)
                || !Float.isFinite(centredTop) || viewport <= 0f || leadHeight <= viewport) return 0f;
        float scroll = Math.max(0f, Math.min(leadHeight - viewport,
                focusY - viewport * LyricBandGeometry.singingFraction(cardProgress)));
        return viewportTop - scroll - centredTop;
    }
}
