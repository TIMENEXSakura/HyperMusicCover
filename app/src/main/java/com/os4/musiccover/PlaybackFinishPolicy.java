package com.os4.musiccover;

import android.media.session.PlaybackState;

final class PlaybackFinishPolicy {
    private static final long END_TOLERANCE_MS = 240;

    private PlaybackFinishPolicy() {}

    static boolean finished(int state, long position, long before, long duration) {
        if (duration <= END_TOLERANCE_MS) return false;
        long end = duration - END_TOLERANCE_MS;
        if (state == PlaybackState.STATE_PAUSED) return position >= end;
        if (state != PlaybackState.STATE_STOPPED && state != PlaybackState.STATE_NONE) return false;
        return position >= end || (position <= 0L && before >= end);
    }
}
