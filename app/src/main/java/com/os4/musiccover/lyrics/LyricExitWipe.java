package com.os4.musiccover.lyrics;

import java.text.BreakIterator;
import java.util.Arrays;
import java.util.Locale;

/** The field-by-field fade used by both the lock screen and its settings preview. */
public final class LyricExitWipe {
    public static final long DURATION_MS = 640L;

    private LyricExitWipe() {}

    public static float opacity(int index, int count, float progress) {
        if (progress <= 0f) return 1f;
        if (progress >= 1f) return 0f;
        float stagger = count <= 1 ? 0f : 0.65f;
        float delay = count <= 1 ? 0f : stagger * index / (count - 1);
        float t = Math.max(0f, Math.min(1f, (progress - delay) / (1f - stagger)));
        return 1f - t * t * (3f - 2f * t);
    }

    /** Map each visible UTF-16 offset to its timed field, or to a complete grapheme for LRC. */
    public static int[] fields(String text, int from, int to, int[] timedEnds) {
        BreakIterator graphemes = BreakIterator.getCharacterInstance(Locale.ROOT);
        graphemes.setText(text);
        int[] units = new int[to - from];
        int field = 0;
        int timed = 0;
        boolean segmented = timedEnds != null && timedEnds.length > 1;
        for (int start = from; start < to; ) {
            while (segmented && timed < timedEnds.length && timedEnds[timed] <= start) timed++;
            int end;
            if (segmented && timed < timedEnds.length) {
                end = Math.min(to, timedEnds[timed]);
                if (!graphemes.isBoundary(end)) end = Math.min(to, graphemes.following(end));
            } else {
                end = Math.min(to, graphemes.following(start));
            }
            Arrays.fill(units, start - from, end - from, field++);
            start = end;
        }
        return units;
    }
}
