package com.os4.musiccover.island

import org.junit.Assert.*
import org.junit.Test

class ReleasedNotificationGraceTest {
    @Test fun repostAfterRemovalKeepsCardEvenWhenDeliveredOutOfOrder() {
        val grace = ReleasedNotificationGrace()
        val removal = grace.nextEvent()
        val repost = grace.nextEvent()
        grace.posted("navigation", repost) // Main thread handles this first.
        assertTrue(grace.removed("navigation", removal))
        assertFalse(grace.expired("navigation", removal))
    }

    @Test fun latestRemovalExpiresOnlyWithoutLaterRepost() {
        val grace = ReleasedNotificationGrace()
        val oldRemoval = grace.nextEvent()
        val repost = grace.nextEvent()
        val newRemoval = grace.nextEvent()
        assertTrue(grace.removed("timer", newRemoval))
        assertFalse(grace.removed("timer", oldRemoval))
        grace.posted("timer", repost)
        assertFalse(grace.expired("timer", oldRemoval))
        assertTrue(grace.expired("timer", newRemoval))
    }

    @Test fun recaptureOrNewReleaseInvalidatesOldDelayedRemoval() {
        val grace = ReleasedNotificationGrace()
        val removal = grace.nextEvent()
        assertTrue(grace.removed("navigation", removal))
        grace.forget("navigation")
        assertFalse(grace.expired("navigation", removal))
        // The old event may still be waiting to reach the main thread after a new release.
        assertFalse(grace.removed("navigation", removal))
        val fresh = grace.nextEvent()
        assertTrue(grace.removed("navigation", fresh))
        assertTrue(grace.expired("navigation", fresh))
    }
}
