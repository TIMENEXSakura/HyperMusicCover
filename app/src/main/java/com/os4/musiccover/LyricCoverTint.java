package com.os4.musiccover;

/** First valid cover sample per composition; subsequent clock samples cannot retint lyrics. */
final class LyricCoverTint {
    private String identity;
    private int color;

    synchronized void begin(String nextIdentity) {
        if (nextIdentity == null || !nextIdentity.equals(identity)) {
            identity = nextIdentity;
            color = 0;
        }
    }

    synchronized void record(String sampleIdentity, int sample) {
        if (color == 0 && sample != 0 && identity != null && identity.equals(sampleIdentity)) {
            color = sample;
        }
    }

    synchronized int color() { return color; }
}
