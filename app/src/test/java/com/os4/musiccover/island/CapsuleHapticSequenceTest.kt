package com.os4.musiccover.island

import org.junit.Assert.*
import org.junit.Test

class CapsuleHapticSequenceTest {
    private class Fake {
        var eligible = true
        var acceptsPulse = true
        var count = 0
        var callback: Runnable? = null
        var listening = false
        val sequence = CapsuleHapticSequence(
            eligible = { eligible },
            pulse = { count++; acceptsPulse },
            schedule = { task, delay ->
                assertEquals(110L, delay)
                callback = task
                true
            },
            remove = { callback = null },
            pendingChanged = { listening = it },
        )
        fun fire() { val task = callback; callback = null; task?.run() }
    }

    @Test fun acceptedClicksGiveOneTickEachWithoutThrottling() {
        val f = Fake()
        repeat(3) { f.sequence.click() }
        assertEquals(3, f.count)
        assertNull(f.callback)
    }

    @Test fun unavailableTicksTwiceAndReleasesTheDetachListener() {
        val f = Fake()
        f.sequence.unavailable()
        assertEquals(1, f.count)
        assertTrue(f.listening)
        f.fire()
        assertEquals(2, f.count)
        assertFalse(f.listening)
    }

    @Test fun disabledOrDetachedBeforeSecondTickSuppressesIt() {
        val f = Fake()
        f.sequence.unavailable()
        f.eligible = false
        f.fire()
        assertEquals(1, f.count)
        assertFalse(f.listening)
        f.sequence.click()
        f.sequence.unavailable()
        assertEquals(1, f.count)
    }

    @Test fun cancellationAndANewClickCannotLeaveASecondTick() {
        val f = Fake()
        f.sequence.unavailable()
        val old = f.callback!!
        f.sequence.click()
        assertEquals(2, f.count)
        assertNull(f.callback)
        assertFalse(f.listening)
        old.run() // Even a stale callback that escaped removal does nothing.
        assertEquals(2, f.count)
        f.sequence.unavailable()
        f.sequence.cancel()
        f.fire()
        assertEquals(3, f.count)
    }

    @Test fun systemRejectingFirstPulseDoesNotScheduleAnother() {
        val f = Fake()
        f.acceptsPulse = false
        f.sequence.unavailable()
        assertEquals(1, f.count)
        assertNull(f.callback)
        assertFalse(f.listening)
    }
}
