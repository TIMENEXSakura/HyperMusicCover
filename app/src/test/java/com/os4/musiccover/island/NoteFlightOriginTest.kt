package com.os4.musiccover.island

import com.os4.musiccover.cover.CoverMorphMotion.Box
import org.junit.Assert.assertEquals
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

    @Test fun delayedJoinKeepsPillOutlineAndFocusThenAdmitsOnlyPendingProgress() {
        val home = Box(240f, 1800f, 460f, 90f)
        val row = Box(40f, 1400f, 1020f, 270f)
        val start = MiniCardMorph.NoteHandoff(home, .4f, 1000L)
        val first = start.frame(home, row, .4f, 1000L)
        assertEquals(home.x, first.x, .001f)
        assertEquals(home.w, first.w, .001f)
        assertEquals(home.h, first.h, .001f)
        assertEquals(0f, start.visualProgress, 0f)
        assertEquals(0f, MiniCardMorph.pillBlur(start.visualProgress), 0f)
        assertEquals(1640f, first.y, .001f) // Finger offset is applied against the physical path.
        val middle = start.frame(home, row, .4f, 1048L)
        assertEquals(.2f, start.visualProgress, .001f)
        assertEquals(572f, middle.w, .001f)
        assertEquals(first.y, middle.y, .001f)
        start.frame(home, row, .55f, 1048L)
        assertEquals(.35f, start.visualProgress, .001f) // Fresh 0.15 movement passes straight through.
        assertEquals(.55f, MiniCardMorph.handoffProgress(.55f, .4f, 96L), 0f)
    }

    @Test fun immediateReleaseKeepsTheHeldPillWhileHomeAndNativeRowAreRepositioned() {
        val home = Box(240f, 1800f, 460f, 90f)
        val row = Box(40f, 1400f, 1020f, 270f)
        val join = MiniCardMorph.NoteHandoff(home, .4f, 1000L)
        val held = join.frame(home, row, .4f, 1000L)
        val nudge = MiniCardMorph.heldNudge(held.y, join.direction, 1590f)
        join.release(1000L)
        val newHome = Box(270f, 1840f, 400f, 100f)
        val newRow = Box(40f, 1320f, 1020f, 270f)
        val first = join.frame(newHome, newRow, .4f, 1000L)
        assertEquals(held.x, first.x, .001f)
        assertEquals(held.w, first.w, .001f)
        assertEquals(held.h, first.h, .001f)
        assertEquals(1590f, first.y + join.direction * nudge, .001f)
        val middle = join.frame(newHome, newRow, .6f, 1048L)
        assertEquals(1544f, middle.y, .001f) // Halfway between the old and new .6 spring paths.
        assertEquals(.4f, join.visualProgress, .001f)
        val last = join.frame(newHome, newRow, 1f, 1096L)
        assertEquals(newRow.y, last.y, .001f)
        assertEquals(newRow.w, last.w, .001f)
    }

    @Test fun zeroProgressJoinRespondsToTheNextMoveWithoutDelay() {
        val home = Box(200f, 1400f, 400f, 90f)
        val row = Box(30f, 900f, 940f, 240f)
        val join = MiniCardMorph.NoteHandoff(home, 0f, 1000L)
        val frame = join.frame(home, row, .3f, 1001L)
        val direct = MiniCardMorph.containerFrame(home, row, .3f)
        assertEquals(direct.x, frame.x, 0f)
        assertEquals(direct.y, frame.y, 0f)
        assertEquals(direct.w, frame.w, 0f)
        assertEquals(direct.h, frame.h, 0f)
        assertEquals(.3f, join.visualProgress, 0f)
    }
}
