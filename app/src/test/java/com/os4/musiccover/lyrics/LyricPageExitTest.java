package com.os4.musiccover.lyrics;

import static org.junit.Assert.*;
import org.junit.Test;

public class LyricPageExitTest {
    private static final long DURATION = LyricPageExit.DURATION_MS;

    @Test public void dismissalKeepsItsFrameUntilTheMaskHasPassedItsBottom() {
        LyricPageExit exit = new LyricPageExit();
        exit.start(100);
        exit.step(100);
        assertTrue(exit.active());
        assertEquals(0f, exit.progress(), 0f);
        exit.step(100 + DURATION / 2);
        assertEquals(0.5f, exit.progress(), 0f);
        exit.step(100 + DURATION - 1);
        assertTrue(exit.active());
        exit.step(100 + DURATION);
        assertFalse(exit.active());
        assertTrue(exit.closed());
        assertEquals(1f, exit.progress(), 0f);
    }

    @Test public void togglingTwiceReversesExactlyTheVisibleMask() {
        LyricPageExit exit = new LyricPageExit();
        exit.start(100);
        exit.step(100 + DURATION / 2);
        exit.setClosing(false, 100 + DURATION / 2);
        assertEquals(0.5f, exit.progress(), 0f);
        exit.step(100 + DURATION * 3 / 4);
        assertEquals(0.25f, exit.progress(), 0f);
        exit.setClosing(true, 100 + DURATION * 3 / 4);
        assertEquals(0.25f, exit.progress(), 0f);
        exit.step(100 + DURATION);
        assertEquals(0.5f, exit.progress(), 0f);
        exit.setClosing(false, 100 + DURATION);
        exit.step(100 + DURATION * 3 / 2);
        assertFalse(exit.active());
        assertFalse(exit.closed());
        assertEquals(0f, exit.progress(), 0f);
    }

    @Test public void aReversalBetweenFramesImmediatelyHeadsBackFromWhatWasDrawn() {
        LyricPageExit exit = new LyricPageExit();
        exit.start(100);
        exit.step(116);
        float drawn = exit.progress();
        exit.setClosing(false, 129);
        assertEquals(drawn, exit.progress(), 0f);
        exit.step(132);
        assertEquals(13f / DURATION, exit.progress(), 0f);
    }

    @Test public void longFrameGapsAndAnImmediateCutNeedNoExtraAnimation() {
        LyricPageExit exit = new LyricPageExit();
        exit.start(10);
        exit.step(10000);
        assertTrue(exit.closed());
        exit.reset();
        assertFalse(exit.active());
        assertFalse(exit.closed());
        assertEquals(0f, exit.progress(), 0f);
        exit.start(10001);
        exit.setClosing(false, 10001);
        exit.step(10001);
        assertFalse(exit.active());
    }
}
