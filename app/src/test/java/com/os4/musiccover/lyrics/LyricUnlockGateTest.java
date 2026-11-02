package com.os4.musiccover.lyrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricUnlockGateTest {
    @Test public void unlockCutsThePageBeforeTheKeyguardReportsUnlocked() {
        LyricUnlockGate gate = new LyricUnlockGate();
        gate.mark(100L);
        assertTrue(gate.hide(true, 100L, 800L));
        assertTrue(gate.hide(false, 110L, 800L));
        assertTrue(gate.hide(true, 120L, 800L)); // stale unlocked read did not clear the latch
        assertTrue(gate.hide(false, 1100L, 800L));
    }

    @Test public void cancelledExitRecoversWhenItsTimeoutExpires() {
        LyricUnlockGate gate = new LyricUnlockGate();
        gate.mark(100L);
        assertTrue(gate.hide(true, 900L, 800L));
        assertFalse(gate.hide(true, 901L, 800L));
        assertFalse(gate.leaving());
    }

    @Test public void screenOffStartsANewLockEvenAfterAConfirmedUnlock() {
        LyricUnlockGate gate = new LyricUnlockGate();
        gate.mark(100L);
        assertTrue(gate.hide(false, 120L, 800L));
        gate.clear(); // a screen-off event begins the next keyguard session
        assertFalse(gate.hide(true, 150L, 800L));
        assertTrue(gate.hide(false, 150L, 800L)); // desktop is still never treated as locked
    }

    @Test public void aSecondExitExtendsThePendingRecheck() {
        LyricUnlockGate gate = new LyricUnlockGate();
        gate.mark(100L);
        gate.mark(500L);
        assertEquals(401L, gate.remaining(900L, 800L));
        assertTrue(gate.hide(true, 900L, 800L));
        assertFalse(gate.hide(true, 1301L, 800L));
    }
}
