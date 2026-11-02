package com.os4.musiccover.lyrics;

/** Exit latch shared by the keyguard child and the independent lyric window. */
final class LyricUnlockGate {
    private boolean leaving;
    private long startedAt;

    synchronized void mark(long now) {
        leaving = true;
        startedAt = now;
    }

    synchronized void clear() { leaving = false; }
    synchronized boolean leaving() { return leaving; }

    /** Hide at exit once, and give a cancelled exit back after the bounded fallback window. */
    synchronized boolean hide(boolean locked, long now, long timeout) {
        // A cached unlocked reading must not clear the latch; a fresh locked reading arriving
        // just after it would otherwise flash the page back during the exit.
        if (locked && leaving && now - startedAt > timeout) leaving = false;
        return !locked || leaving;
    }

    /** A newer exit extends an already queued recheck to the newer deadline. */
    synchronized long remaining(long now, long timeout) {
        return leaving ? timeout + 1L - (now - startedAt) : 0L;
    }
}
