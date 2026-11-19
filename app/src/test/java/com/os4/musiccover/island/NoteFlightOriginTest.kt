package com.os4.musiccover.island

import com.os4.musiccover.cover.CoverMorphMotion.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFlightOriginTest {
    @Test fun notificationRowJoiningAfterAQuickPullStartsAtTheVisiblePillEvenIfTheFingerAlreadyLifted() {
        // The provisional pill already travelled 280px; the late row is only 360px away.
        // Applying the nominal pose at its rest slot (1840) would jump back down to 1650px.
        val join = NoteFlightOrigin(1560f, 360f, .4f, 46f)
        assertEquals(1560f, join.top(.4f, 46f), 0f)
        assertEquals(1560f, join.top(.4f, 46f), 0f) // parent's next pre-draw; no new MOVE
        assertEquals(1532f, join.top(.5f, 38f), .001f)
        assertEquals(1560f, join.top(.4f, 46f), 0f) // immediate finger reversal
    }

    @Test fun aRowApproachingFromBelowDoesNotReverseTheFirstFrame() {
        val join = NoteFlightOrigin(1070f, -180f, .2f, 24f)
        assertEquals(1070f, join.top(.2f, 24f), 0f)
        assertEquals(1084f, join.top(.3f, 20f), .001f)
    }

    @Test fun sparseMovesAndEarlyLiftContinueFromTheLastDisplayedFrame() {
        // The recording gives pill tops and frame gaps; the gesture log gives total
        // finger travel and lift time. Deliver the travel in a burst to exercise batching.
        fun checkPull(joinedTop: Float, totalPull: Float, frameTimes: List<Int>, liftMs: Int) {
            val density = 3f
            val threshold = MiniPlayerRuntime.dragThresholdPx(density)
            val limit = MiniPlayerRuntime.dragNudgePx(density)
            val home = Box(365f, 2330f, 470f, 130f)
            val row = Box(65f, 1460f, 1070f, 340f)
            val travel = home.y - row.y
            val held = MiniCardMorph.rubber(threshold, limit)
            val joinedPull = threshold + (home.y - held - joinedTop).coerceAtLeast(0f)
            fun pose(distance: Float) = MiniPlayerRuntime.pose(distance, false, threshold, travel, limit)
            val (joinedProgress, joinedNudge) = pose(joinedPull)
            val origin = NoteFlightOrigin(joinedTop, travel, joinedProgress, joinedNudge)
            val frames = NotePullFrames(joinedTop, 0f, travel)
            fun move(distance: Float) {
                val (progress, nudge) = pose(distance)
                frames.target(progress, origin.top(progress, nudge), 0f)
            }
            move(joinedPull)
            move(totalPull - 60f)
            assertEquals(0f, frames.progress, 0f)
            assertEquals(joinedTop, frames.top, 0f)

            val handoff = MiniCardMorph.NoteHandoff(home)
            val start = handoff.frame(home, row, frames.progress, 0L)
            assertEquals(home.w, start.w, 0f)
            assertEquals(0f, MiniCardMorph.pillBlur(frames.progress), 0f)
            var shown = start
            var previousTop = joinedTop
            var previousTime = 0
            for (time in frameTimes) {
                frames.advance((time - previousTime) / 1000f)
                val next = handoff.frame(home, row, frames.progress, time.toLong())
                assertTrue(previousTop - frames.top in 1f..90f)
                assertTrue(next.w - shown.w in 1f..60f)
                previousTop = frames.top
                previousTime = time
                shown = next
            }
            val lastProgress = frames.progress
            val lastTop = frames.top

            // Android may deliver a final motion sample with UP, with no display frame between.
            move(totalPull)
            val (opens, _) = MiniPlayerRuntime.lift(totalPull, 0f, false, threshold, travel, false)
            assertTrue(opens)
            handoff.release(liftMs.toLong())
            assertTrue(pose(totalPull).first > lastProgress + .5f)
            assertEquals(lastProgress, frames.progress, 0f)
            assertEquals(lastTop, frames.top, 0f)
            val nudge = MiniCardMorph.heldNudge(shown.y, handoff.direction, lastTop)
            val departing = handoff.frame(home, row, frames.progress, liftMs.toLong())
            assertEquals(shown.w, departing.w, 0f)
            assertEquals(lastTop, departing.y + handoff.direction * nudge, .001f)
        }

        checkPull(joinedTop = 2190f, totalPull = 1048f, frameTimes = listOf(24, 48), liftMs = 51)
        checkPull(joinedTop = 2251f, totalPull = 881f, frameTimes = listOf(21), liftMs = 27)
    }

    @Test fun immediateReleaseKeepsTheHeldPillWhileHomeAndNativeRowAreRepositioned() {
        val home = Box(240f, 1800f, 460f, 90f)
        val row = Box(40f, 1400f, 1020f, 270f)
        val join = MiniCardMorph.NoteHandoff(home)
        val held = join.frame(home, row, .4f, 1000L)
        val nudge = MiniCardMorph.heldNudge(held.y, join.direction, 1590f)
        join.release(1000L)
        val newHome = Box(270f, 1840f, 400f, 100f)
        val newRow = Box(60f, 1320f, 850f, 330f)
        val first = join.frame(newHome, newRow, .4f, 1000L)
        assertEquals(held.x, first.x, .001f)
        assertEquals(held.w, first.w, .001f)
        assertEquals(held.h, first.h, .001f)
        assertEquals(1590f, first.y + join.direction * nudge, .001f)
        val middle = join.frame(newHome, newRow, .6f, 1048L)
        assertEquals(1544f, middle.y, .001f) // Halfway between the old and new .6 spring paths.
        val last = join.frame(newHome, newRow, 1f, 1096L)
        assertEquals(newRow.y, last.y, .001f)
        assertEquals(newRow.w, last.w, .001f)
    }

    @Test fun ordinaryMovementAndReversalTrackTheFingerOnTheNextFrame() {
        val frames = NotePullFrames(1400f, 0f, 500f)
        frames.target(.025f, 1388f, 3f)
        frames.advance(1f / 120f)
        assertEquals(.025f, frames.progress, 0f)
        assertEquals(1388f, frames.top, 0f)
        assertEquals(3f, frames.offsetX, 0f)
        frames.target(.01f, 1395f, -1f)
        frames.advance(1f / 120f)
        assertEquals(.01f, frames.progress, .00001f)
        assertEquals(1395f, frames.top, 0f)
        assertEquals(-1f, frames.offsetX, 0f)
    }
}
