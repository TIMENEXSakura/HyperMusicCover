package com.os4.musiccover;

import android.media.session.PlaybackState;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackFinishPolicyTest {
    @Test public void endCanBeReportedAsPausedOrAsAStopThatResetsPosition() {
        assertTrue(PlaybackFinishPolicy.finished(PlaybackState.STATE_PAUSED, 179950, 179800, 180000));
        assertTrue(PlaybackFinishPolicy.finished(PlaybackState.STATE_STOPPED, 0, 179920, 180000));
    }

    @Test public void pausesSkipsAndMissingDurationDoNotEndTheScene() {
        assertFalse(PlaybackFinishPolicy.finished(PlaybackState.STATE_PAUSED, 83000, 83000, 180000));
        assertFalse(PlaybackFinishPolicy.finished(PlaybackState.STATE_BUFFERING, 180000, 179900, 180000));
        assertFalse(PlaybackFinishPolicy.finished(PlaybackState.STATE_STOPPED, 0, 83000, 180000));
        assertFalse(PlaybackFinishPolicy.finished(PlaybackState.STATE_STOPPED, 0, 179920, 0));
    }
}
