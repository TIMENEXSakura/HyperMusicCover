package com.os4.musiccover;

import org.junit.Test;
import static org.junit.Assert.*;

public class CapsuleSessionPolicyTest {
    @Test public void visibleCardWinsOverPreviousCapsuleOrActiveSession() {
        assertEquals("card", CapsuleSessionPolicy.preferred("card", true, "playing", true, "old"));
        assertEquals("card", CapsuleSessionPolicy.preferred("card", false, null, false, "paused"));
    }
    @Test public void cardGapUsesActiveCandidateThenRetainsOwner() {
        assertEquals("playing", CapsuleSessionPolicy.preferred(null, false, "playing", true, "paused"));
        assertEquals("old", CapsuleSessionPolicy.preferred(null, false, null, false, "old"));
        assertNull(CapsuleSessionPolicy.preferred(null, false, null, false, null));
    }
    @Test public void reloadWaitsForCapsuleAndNavigationOwnership() {
        assertFalse(CapsuleSessionPolicy.blocksReload(false, false));
        assertTrue(CapsuleSessionPolicy.blocksReload(true, false));
        assertTrue(CapsuleSessionPolicy.blocksReload(false, true));
        assertTrue(CapsuleSessionPolicy.blocksReload(true, true));
    }
    @Test public void selectedSessionMatchesByTokenNotControllerInstance() {
        assertTrue(CapsuleSessionPolicy.same(new String("selected"), new String("selected")));
        assertFalse(CapsuleSessionPolicy.same("selected", "card"));
        assertFalse(CapsuleSessionPolicy.same(null, null));
        assertFalse(CapsuleSessionPolicy.same("selected", null));
    }
    @Test public void pausedCardYieldsToPlayingSession() {
        assertEquals("playing", CapsuleSessionPolicy.preferred("paused-card", false,
                "playing", true, "paused-card"));
        assertEquals("paused-card", CapsuleSessionPolicy.preferred("paused-card", false,
                "other-paused", false, "other-paused"));
        assertEquals("card", CapsuleSessionPolicy.preferred("card", true,
                "playing", true, "card"));
    }
}
