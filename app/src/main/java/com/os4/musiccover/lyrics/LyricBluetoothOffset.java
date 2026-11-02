package com.os4.musiccover.lyrics;

/** Estimated lyric timing adjustment for an actively routed local Bluetooth output. */
final class LyricBluetoothOffset {
    static final int NONE = 0, CLASSIC = 1, LE = 2;
    static final int MIN_MS = -500, MAX_MS = 500;
    static final int DEFAULT_CLASSIC_MS = 400, DEFAULT_LE_MS = 100;

    private LyricBluetoothOffset() { }

    static int clamp(int ms) { return Math.max(MIN_MS, Math.min(MAX_MS, ms)); }
    static int adjustment(boolean enabled, int route, int classic, int le) {
        if (!enabled) return 0;
        return route == LE ? clamp(le) : route == CLASSIC ? clamp(classic) : 0;
    }
    static int position(long position, int adjustment) {
        if (position < 0L) return 0;
        if (position > (long) Integer.MAX_VALUE - adjustment) return Integer.MAX_VALUE;
        return (int) Math.max(0L, position + (long) adjustment);
    }
}
