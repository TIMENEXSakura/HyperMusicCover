package com.os4.musiccover.island
import org.junit.Assert.*
import org.junit.Test

class CapsuleFeedbackStateTest {
    @Test fun enableShowsWordThenCheckAndReturnsToLivePlayback() {
        val state = LyricFeedbackState()
        state.begin(true)
        assertEquals(1f, state.at(200).word)
        assertEquals(1f, state.at(600).mark)
        assertEquals(0f, state.frame.cross)
        assertEquals(1f, state.at(1100).playback)
        assertEquals(0f, state.frame.mark)
    }
    @Test fun closingAfterCompletedHintStillStartsWithCheck() {
        val state = LyricFeedbackState()
        state.begin(true)
        state.at(1100)
        state.begin(false)
        assertEquals(0f, state.at(0).cross)
        assertEquals(1f, state.frame.mark)
        assertEquals(1f, state.at(200).cross)
        assertEquals(1f, state.at(1100).playback)
    }
    @Test fun rapidReversalsContinueFromCurrentFrameAndLastRequestWins() {
        val state = LyricFeedbackState()
        state.begin(true)
        val word = state.at(200)
        state.begin(false)
        assertEquals(word, state.at(0))
        val cross = state.at(90)
        state.begin(true)
        assertEquals(cross, state.at(0))
        for (i in 0..20) { state.begin(i % 2 == 0); state.at(35) }
        state.begin(false)
        assertEquals(1f, state.at(600).cross)
        assertEquals(0f, state.frame.playback)
        state.clear()
        assertEquals(LyricFeedbackState.Frame(), state.frame)
    }
    @Test fun navigationMessageIsReplacedBeforeNormalTextReturns() {
        assertTrue(CapsuleTextFeedbackState.at(900).message)
        assertTrue(CapsuleTextFeedbackState.at(1019).message)
        assertFalse(CapsuleTextFeedbackState.at(1020).message)
        assertEquals(0f, CapsuleTextFeedbackState.at(1020).alpha)
        assertEquals(1f, CapsuleTextFeedbackState.at(1140).alpha)
    }
}
