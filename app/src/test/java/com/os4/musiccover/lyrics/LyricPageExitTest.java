package com.os4.musiccover.lyrics;

import static org.junit.Assert.*;
import org.junit.Test;

public class LyricPageExitTest {
    private static final long DURATION = LyricPageExit.DURATION_MS;

    @Test public void dismissalKeepsItsFrameUntilTheMaskHasPassedItsBottom() {
        LyricPageExit exit = new LyricPageExit();
        exit.start(100);
        exit.step(100, true);
        assertTrue(exit.active());
        assertEquals(0f, exit.progress(), 0f);
        exit.step(100 + DURATION / 2, true);
        assertEquals(0.5f, exit.progress(), 0f);
        exit.step(100 + DURATION - 1, true);
        assertTrue(exit.active());
        exit.step(100 + DURATION, true);
        assertFalse(exit.active());
        assertTrue(exit.closed());
        assertEquals(1f, exit.progress(), 0f);
    }

    @Test public void togglingTwiceReversesExactlyTheVisibleMask() {
        LyricPageExit exit = new LyricPageExit();
        exit.start(100);
        exit.step(100 + DURATION / 2, true);
        exit.step(100 + DURATION / 2, false);
        assertEquals(0.5f, exit.progress(), 0f);
        exit.step(100 + DURATION * 3 / 4, false);
        assertEquals(0.25f, exit.progress(), 0f);
        exit.step(100 + DURATION * 3 / 4, true);
        assertEquals(0.25f, exit.progress(), 0f);
        exit.step(100 + DURATION, true);
        assertEquals(0.5f, exit.progress(), 0f);
        exit.step(100 + DURATION, false);
        exit.step(100 + DURATION * 3 / 2, false);
        assertFalse(exit.active());
        assertFalse(exit.closed());
        assertEquals(0f, exit.progress(), 0f);
    }

    @Test public void longFrameGapsAndAnImmediateCutNeedNoExtraAnimation() {
        LyricPageExit exit = new LyricPageExit();
        exit.start(10);
        exit.step(10000, true);
        assertTrue(exit.closed());
        exit.reset();
        assertFalse(exit.active());
        assertFalse(exit.closed());
        assertEquals(0f, exit.progress(), 0f);
        exit.start(10001);
        exit.step(10001, false);
        assertFalse(exit.active());
    }
}
