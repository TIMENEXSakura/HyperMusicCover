package com.os4.musiccover.lyrics;

import java.util.Objects;

/** A few players reset their position to zero when stopping at the end of a track. */
final class LyricStopPosition {
    private static final long END_WINDOW_MS = 2000L;
    private String key;
    private Object session;
    private boolean wasPlaying;
    private long heldPosition = -1L;
    private boolean changed;
    private long duration;

    long select(String nextKey, Object nextSession, boolean playing, boolean stopped,
                long reported, boolean havePrevious, long previous, long lyricEnd, int offset) {
        changed = !Objects.equals(key, nextKey) || !Objects.equals(session, nextSession);
        if (changed) {
            key = nextKey;
            session = nextSession;
            wasPlaying = false;
            heldPosition = -1L;
            duration = durationFromKey(nextKey);
        }
        if (stopped && reported <= 0L && heldPosition >= 0L) return heldPosition;
        heldPosition = -1L;
        if (stopped && reported == 0L && wasPlaying && havePrevious && previous > 0L
                && nextKey != null && !nextKey.isEmpty() && nextSession != null
                && (near(previous, duration) || near(previous + (long) offset, lyricEnd))) {
            // Keep the observed position: STOPPED is not evidence that any later word played.
            heldPosition = previous;
        }
        wasPlaying = playing;
        return heldPosition >= 0L ? heldPosition : reported;
    }

    boolean identityChanged() { return changed; }

    void reset() {
        key = null;
        session = null;
        wasPlaying = false;
        heldPosition = -1L;
        changed = false;
        duration = 0L;
    }

    private static boolean near(long position, long end) {
        return end > 0L && position >= Math.max(0L, end - END_WINDOW_MS)
                && (position <= end || position - end <= END_WINDOW_MS);
    }

    /** Metadata-derived keys end in duration; shorter fallback keys do not. */
    private static long durationFromKey(String key) {
        if (key == null) return 0L;
        int first = key.indexOf('|');
        int second = first < 0 ? -1 : key.indexOf('|', first + 1);
        int last = key.lastIndexOf('|');
        if (second < 0 || last <= second || last == key.length() - 1) return 0L;
        try { return Math.max(0L, Long.parseLong(key.substring(last + 1))); }
        catch (NumberFormatException ignored) { return 0L; }
    }
}
