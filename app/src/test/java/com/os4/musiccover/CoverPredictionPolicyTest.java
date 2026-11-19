package com.os4.musiccover;

import org.junit.Test;
import static org.junit.Assert.*;

public class CoverPredictionPolicyTest {
    @Test public void aConfirmedTrackAlwaysGetsARealPushWhenCoverIsActive() {
        // This decision does not depend on a guessed queue title, which can match even when
        // its speculative push was cancelled by an owner/track change before it was sent.
        assertEquals(CoverPredictionPolicy.Action.PUSH_REAL,
                CoverPredictionPolicy.afterTrack(true, false, true));
        assertEquals(CoverPredictionPolicy.Action.PUSH_REAL,
                CoverPredictionPolicy.afterTrack(true, true, false));
    }

    @Test public void closedCoverStillRespectsTheUsersEntryPreference() {
        assertEquals(CoverPredictionPolicy.Action.STAY_CLOSED,
                CoverPredictionPolicy.afterTrack(false, false, true));
        assertEquals(CoverPredictionPolicy.Action.ENTER_COVER,
                CoverPredictionPolicy.afterTrack(false, true, true));
        assertEquals(CoverPredictionPolicy.Action.ENTER_COVER,
                CoverPredictionPolicy.afterTrack(false, false, false));
    }
}
