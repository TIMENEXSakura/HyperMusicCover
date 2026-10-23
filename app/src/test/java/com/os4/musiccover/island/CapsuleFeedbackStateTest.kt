package com.os4.musiccover.island

import org.junit.Assert.*
import org.junit.Test

class CapsuleFeedbackStateTest {
    @Test fun enableWritesWordFromTopLeftAndReturnsToLivePlayback() {
        val state = LyricFeedbackState()
        state.begin(true)
        assertEquals(LyricFeedbackState.Frame(), state.at(0))
        val half = state.at(LyricFeedbackState.WORD_DRAW_MS / 2)
        assertEquals(0.5f, half.reveal)
        assertEquals(0f, half.cross)
        val edge = LyricFeedbackGeometry.revealEdge(half.reveal)
        assertTrue(0.1f + 0.1f < edge) // upper-left is already inked
        assertTrue(0.9f + 0.9f > edge) // lower-right still waits
        assertEquals(1f, state.at(LyricFeedbackState.WORD_DRAW_MS).reveal)
        assertEquals(LyricFeedbackState.Frame(), state.at(state.durationMs))
    }

    @Test fun wordRemainsCompletelyDrawnForThreeHundredMilliseconds() {
        val state = LyricFeedbackState()
        state.begin(true)
        val written = LyricFeedbackState.WORD_DRAW_MS
        for (ms in written..written + LyricFeedbackState.WORD_HOLD_MS) {
            val frame = state.at(ms)
            assertEquals(1f, frame.word)
            assertEquals(1f, frame.reveal)
            assertEquals(0f, frame.cross)
            assertEquals(0f, frame.playback)
        }
        assertTrue(state.at(written + LyricFeedbackState.WORD_HOLD_MS + 1).word < 1f)
    }

    @Test fun closingAfterCompletedHintRedrawsWordBeforeDrawingCross() {
        val state = LyricFeedbackState()
        state.begin(true)
        state.at(state.durationMs)
        state.begin(false)
        assertEquals(LyricFeedbackState.Frame(), state.at(0))
        assertEquals(0f, state.at(LyricFeedbackState.CLOSE_WORD_MS / 2).cross)
        assertEquals(1f, state.at(LyricFeedbackState.CLOSE_WORD_MS).reveal)
        val crossed = state.at(LyricFeedbackState.CLOSE_WORD_MS + LyricFeedbackState.CROSS_DRAW_MS)
        assertEquals(1f, crossed.word)
        assertEquals(1f, crossed.cross)
        assertEquals(0f, crossed.playback)
        assertEquals(LyricFeedbackState.Frame(), state.at(state.durationMs))
    }

    @Test fun crossDrawsTwoCompleteStrokesInSequence() {
        assertEquals(0f, LyricFeedbackGeometry.firstCrossStroke(0f))
        assertEquals(0f, LyricFeedbackGeometry.secondCrossStroke(0f))
        assertEquals(0.5f, LyricFeedbackGeometry.firstCrossStroke(0.25f))
        assertEquals(0f, LyricFeedbackGeometry.secondCrossStroke(0.25f))
        assertEquals(1f, LyricFeedbackGeometry.firstCrossStroke(0.75f))
        assertEquals(0.5f, LyricFeedbackGeometry.secondCrossStroke(0.75f))
        assertEquals(1f, LyricFeedbackGeometry.secondCrossStroke(1f))
    }

    @Test fun rapidReversalsContinueFromCurrentGeometryAndLastRequestWins() {
        val state = LyricFeedbackState()
        state.begin(true)
        val partiallyWritten = state.at(100)
        state.begin(false)
        assertEquals(partiallyWritten, state.at(0))
        val partialCross = state.at(LyricFeedbackState.CLOSE_WORD_MS + 80)
        assertTrue(partialCross.cross > 0f)
        state.begin(true)
        assertEquals(partialCross, state.at(0))
        val uncrossing = state.at(100)
        assertTrue(uncrossing.cross < partialCross.cross)
        assertEquals(1f, uncrossing.reveal)
        for (i in 0..30) {
            val previous = state.frame
            state.begin(i % 2 == 0)
            assertEquals(previous, state.at(0))
            state.at(35)
        }
        state.begin(false)
        assertEquals(1f, state.at(500).cross)
        assertEquals(0f, state.frame.playback)
        state.begin(true)
        assertEquals(0f, state.at(500).cross)
        assertEquals(1f, state.frame.word)
        assertEquals(LyricFeedbackState.Frame(), state.at(state.durationMs))
    }

    @Test fun interruptingFadeRestoresFromItsActualOpacityWithoutJumping() {
        val state = LyricFeedbackState()
        for (on in listOf(true, false)) {
            state.begin(on)
            val fading = state.at(state.durationMs - LyricFeedbackState.RESTORE_MS / 2)
            assertEquals(0.5f, fading.playback)
            assertEquals(0.5f, fading.word)
            assertEquals(1f, fading.reveal)
            state.begin(!on)
            assertEquals(fading, state.at(0))
            state.at(200)
        }
    }

    @Test fun clearOrExpiredTimelineLeavesNoDrawingOrCrossOverCurrentPlayback() {
        val state = LyricFeedbackState()
        state.begin(false)
        state.at(500)
        state.clear()
        assertEquals(LyricFeedbackState.Frame(), state.frame)
        state.begin(true)
        assertEquals(LyricFeedbackState.Frame(), state.at(-100))
        assertEquals(LyricFeedbackState.Frame(), state.at(Long.MAX_VALUE))
        state.begin(false)
        assertEquals(LyricFeedbackState.Frame(), state.at(0))
    }

    @Test fun navigationMessageIsReplacedBeforeNormalTextReturns() {
        assertTrue(CapsuleTextFeedbackState.at(900).message)
        assertTrue(CapsuleTextFeedbackState.at(1019).message)
        assertFalse(CapsuleTextFeedbackState.at(1020).message)
        assertEquals(0f, CapsuleTextFeedbackState.at(1020).alpha)
        assertEquals(1f, CapsuleTextFeedbackState.at(1140).alpha)
    }
}
