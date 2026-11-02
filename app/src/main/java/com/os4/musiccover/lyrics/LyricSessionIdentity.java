package com.os4.musiccover.lyrics;

/** A song key can recur across distinct sessions of the same player. */
final class LyricSessionIdentity {
    private LyricSessionIdentity() {}

    static boolean changed(Object previous, Object current) {
        return previous != null && !previous.equals(current);
    }
}
