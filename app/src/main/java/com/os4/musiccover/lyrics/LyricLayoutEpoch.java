package com.os4.musiccover.lyrics;

/** Layout epochs are advanced on the UI thread and observed by the worker between rows. */
final class LyricLayoutEpoch {
    private volatile long value;

    long current() { return value; }
    void advance() { value++; }
    boolean isCurrent(long epoch) { return epoch == value; }
}
