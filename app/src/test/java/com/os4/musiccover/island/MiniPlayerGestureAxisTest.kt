package com.os4.musiccover.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerGestureAxisTest {
    @Test fun horizontalSwipeCannotTurnIntoAnUpwardPullOrLyricFeedback() {
        var axis = MiniPlayerGestureAxis.UNDECIDED
        axis = axis.resolve(12f, -3f, 10f)
        assertEquals(MiniPlayerGestureAxis.HORIZONTAL, axis)
        axis = axis.resolve(70f, -90f, 10f)
        assertEquals(MiniPlayerGestureAxis.HORIZONTAL, axis)
    }

    @Test fun upwardPullRemainsVerticalWhenFingerMovesSidewaysOrReverses() {
        var axis = MiniPlayerGestureAxis.UNDECIDED
        axis = axis.resolve(3f, -12f, 10f)
        assertEquals(MiniPlayerGestureAxis.VERTICAL, axis)
        axis = axis.resolve(90f, -40f, 10f)
        assertEquals(MiniPlayerGestureAxis.VERTICAL, axis)
        axis = axis.resolve(40f, 30f, 10f)
        assertEquals(MiniPlayerGestureAxis.VERTICAL, axis)
    }

    @Test fun slopAndDiagonalHaveOneSharedDecision() {
        val axis = MiniPlayerGestureAxis.UNDECIDED
        assertEquals(axis, axis.resolve(6f, 6f, 10f))
        assertEquals(MiniPlayerGestureAxis.VERTICAL, axis.resolve(8f, -8f, 10f))
        assertEquals(MiniPlayerGestureAxis.HORIZONTAL, axis.resolve(9f, -7f, 10f))
    }

    @Test fun malformedCoordinatesDoNotClaimADirection() {
        val axis = MiniPlayerGestureAxis.UNDECIDED
        assertEquals(axis, axis.resolve(Float.NaN, 30f, 10f))
        assertEquals(axis, axis.resolve(30f, Float.POSITIVE_INFINITY, 10f))
    }

    @Test fun onlyVerticalCardMotionsInvalidateFoldedLyricBand() {
        // A horizontal swap can run while none of these vertical motions is active.
        assertFalse(lyricBandMotionUnstable(false, false, false, false, false, false, false, false))
        assertTrue(lyricBandMotionUnstable(true, false, false, false, false, false, false, false))
        assertTrue(lyricBandMotionUnstable(false, true, false, false, false, false, false, false))
        assertTrue(lyricBandMotionUnstable(false, false, true, false, false, false, false, false))
        assertTrue(lyricBandMotionUnstable(false, false, false, true, false, false, false, false))
        assertTrue(lyricBandMotionUnstable(false, false, false, false, true, false, false, false))
        assertTrue(lyricBandMotionUnstable(false, false, false, false, false, true, false, false))
        assertTrue(lyricBandMotionUnstable(false, false, false, false, false, false, true, false))
        assertTrue(lyricBandMotionUnstable(false, false, false, false, false, false, false, true))
    }
}
