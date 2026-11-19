package com.os4.musiccover.island

import com.os4.musiccover.cover.CoverMorphMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StackPullHandoffTest {
    @Test fun delayedRowStartsAtTheLiftedSeatAndStillLandsAtTheNativeCard() {
        val home = CoverMorphMotion.Box(82f, 2060f, 620f, 132f)
        val card = CoverMorphMotion.Box(30f, 1060f, 1000f, 555f)
        // While the destination is being measured the real pill is already at (-8,-95).
        val start = liftedSpreadOrigin(home, -8f, -95f)
        val first = MiniCardMorph.containerFrame(start, card, 0f)
        assertEquals(74f, first.x, 0f)
        assertEquals(1965f, first.y, 0f)
        assertEquals(home.w, first.w, 0f)
        assertEquals(home.h, first.h, 0f)
        var y = first.y
        for (progress in listOf(.01f, .1f, .4f, 1f)) {
            val next = MiniCardMorph.containerFrame(start, card, progress)
            assertTrue(next.y < y)
            y = next.y
        }
        assertEquals(card.y, y, .001f)
        assertEquals(card.x, MiniCardMorph.containerFrame(start, card, 1f).x, .001f)
    }
}
