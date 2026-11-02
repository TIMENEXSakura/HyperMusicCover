package com.os4.musiccover.lyrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricFinalHoldTest {
    @Test public void finishedLineSmoothlyRelaxesAndStaysVisibleWithoutIdleFrames() {
        LyricFinalHold hold = new LyricFinalHold();
        assertFalse(hold.step(999, 0, 1000, 0.1f, false));
        assertEquals(1f, hold.alpha(), 0f);
        assertEquals(1f, hold.scale(), 0f);
        assertTrue(hold.step(1000, 0, 1000, 0.1f, false));
        assertTrue(hold.completed());
        assertTrue(hold.moving());
        assertTrue(hold.alpha() > 0.55f && hold.alpha() < 1f);
        assertTrue(hold.scale() > 0.94f && hold.scale() < 1f);
        for (int i = 0; i < 100 && hold.moving(); i++) hold.step(1000, 0, 1000, 0.1f, false);
        assertFalse(hold.moving());
        assertEquals(0.55f, hold.alpha(), 0.00001f);
        assertEquals(0.94f, hold.scale(), 0.00001f);
        assertFalse(hold.step(1000, 0, 1000, 60f, false));
    }

    @Test public void pauseBeforeEndNeverRelaxesLastLine() {
        LyricFinalHold hold = new LyricFinalHold();
        hold.step(999, 0, 1000, 10f, false);
        assertFalse(hold.completed());
        assertFalse(hold.moving());
        assertEquals(1f, hold.alpha(), 0f);
    }

    @Test public void seekBackAndReplayRestoreHighlight() {
        LyricFinalHold hold = new LyricFinalHold();
        hold.step(1100, 0, 1000, 1f, true);
        assertEquals(0.55f, hold.alpha(), 0.00001f);
        hold.step(700, 0, 1000, 0.1f, false);
        assertFalse(hold.completed());
        assertTrue(hold.alpha() > 0.55f);
        for (int i = 0; i < 100 && hold.moving(); i++) hold.step(700, 0, 1000, 0.1f, false);
        assertEquals(1f, hold.alpha(), 0f);
        assertEquals(1f, hold.scale(), 0f);
    }

    @Test public void backgroundVoicesMustAlsoFinish() {
        LyricLine lead = new LyricLine("主唱", null, 0, 1000, false, null, null, null);
        lead.addBackground(new LyricLine("和声", null, 0, 2000, false, null, null, null));
        LyricFinalHold hold = new LyricFinalHold();
        hold.step(1500, LyricFocus.startOf(lead), LyricFocus.endOf(lead), 1f, true);
        assertFalse(hold.completed());
        hold.step(2000, LyricFocus.startOf(lead), LyricFocus.endOf(lead), 1f, true);
        assertTrue(hold.completed());
        assertEquals(0.55f, hold.alpha(), 0.00001f);
    }

    @Test public void invalidEndCannotMarkAnUnfinishedLineAndResetClearsState() {
        LyricFinalHold hold = new LyricFinalHold();
        hold.step(1000, 1000, 1000, 1f, true);
        assertFalse(hold.completed());
        hold.step(1000, 0, 1000, 1f, true);
        assertTrue(hold.completed());
        hold.reset();
        assertFalse(hold.completed());
        assertEquals(1f, hold.alpha(), 0f);
        assertFalse(hold.moving());
    }
}
