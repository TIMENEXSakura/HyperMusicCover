package com.os4.musiccover.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwapBlurProgressTest {
    @Test fun arrivingStackCanBounceAfterItIsSharpWithoutASecondBlurPulse() {
        val focus = SwapBlurProgress()
        assertEquals(0f, focus.pulse(0f), 0f)
        assertEquals(1f, focus.pulse(.5f), .0001f)
        assertTrue(focus.pulse(.95f) < .2f)
        for (rebound in floatArrayOf(1.08f, .97f, 1.01f, .999f))
            assertEquals(0f, focus.pulse(rebound), 0f)
        assertEquals(1f, SwapBlurProgress().pulse(.5f), .0001f)
    }

    @Test fun theArrivingFocusAndRetainedGhostKeepTheirOwnAnimationProgress() {
        val arrival = SwapBlurProgress()
        val ghost = SwapBlurProgress()
        assertEquals(.75f, arrival.advance(.75f), 0f)
        assertEquals(.75f, arrival.advance(.64f), 0f)
        assertEquals(.3f, ghost.advance(.3f), 0f)
        assertEquals(1f, ghost.advance(1.02f), 0f)
        assertEquals(1f, ghost.advance(.97f), 0f)
        assertEquals(.9f, arrival.advance(.9f), 0f)
    }
}
