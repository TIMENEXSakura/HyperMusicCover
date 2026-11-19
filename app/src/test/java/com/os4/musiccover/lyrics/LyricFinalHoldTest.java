package com.os4.musiccover.lyrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricFinalHoldTest {
    @Test public void finishedLineBecomesTransparentAndStopsSchedulingFrames() {
        LyricFinalHold hold = new LyricFinalHold();
        assertFalse(hold.step(999, 0, 1000, 0.1f, false));
        assertEquals(0f, hold.progress(), 0f);
        assertTrue(hold.step(1000, 0, 1000, 0.1f, false));
        assertTrue(hold.completed());
        assertTrue(hold.moving());
        assertTrue(hold.progress() > 0f && hold.progress() < 1f);
        for (int i = 0; i < 100 && hold.moving(); i++) hold.step(1000, 0, 1000, 0.1f, false);
        assertFalse(hold.moving());
        assertEquals(1f, hold.progress(), 0.00001f);
        assertFalse(hold.step(1000, 0, 1000, 60f, false));
    }

    @Test public void lastLineUsesTheFieldClockAndDisappearsWhenItCompletes() {
        LyricFinalHold hold = new LyricFinalHold();
        float half = LyricExitWipe.DURATION_MS / 2000f;
        hold.step(1000, 0, 1000, half, false);
        assertEquals(0.5f, hold.progress(), 0.0001f);
        assertEquals(0f, LyricExitWipe.opacity(0, 3, hold.progress()), 0f);
        assertEquals(1f, LyricExitWipe.opacity(2, 3, hold.progress()), 0f);
        hold.step(1100, 0, 1000, half, false);
        assertEquals(1f, hold.progress(), 0.0001f);
        assertFalse(hold.moving());
    }

    @Test public void terminalWipeBelongsOnlyToTheLastGroupEvenWhenNeighboursRemainInView() {
        LyricFinalHold hold = new LyricFinalHold();
        int count = 4;
        float half = LyricExitWipe.DURATION_MS / 2000f;
        hold.step(999, 500, 1000, half, false);
        assertEquals(0f, hold.rowProgress(3, count), 0f);
        for (int step = 1; step <= 2; step++) {
            hold.step(1000, 500, 1000, half, false);
            for (int i = 0; i < count - 1; i++) assertEquals(0f, hold.rowProgress(i, count), 0f);
            assertEquals(step * .5f, hold.rowProgress(3, count), 0.0001f);
        }
    }

    @Test public void pauseBeforeEndNeverRelaxesLastLine() {
        LyricFinalHold hold = new LyricFinalHold();
        hold.step(999, 0, 1000, 10f, false);
        assertFalse(hold.completed());
        assertFalse(hold.moving());
        assertEquals(0f, hold.progress(), 0f);
    }

    @Test public void seekBackAndReplayRestoreHighlight() {
        LyricFinalHold hold = new LyricFinalHold();
        hold.step(1100, 0, 1000, 1f, true);
        assertEquals(1f, hold.progress(), 0.00001f);
        assertEquals(200f, hold.precedingScroll(450f, 250f), 0f);
        hold.step(700, 0, 1000, 0.1f, false);
        assertFalse(hold.completed());
        assertTrue(hold.progress() < 1f);
        assertTrue(hold.precedingScroll(90000f, 250f) < 200f);
        for (int i = 0; i < 100 && hold.moving(); i++) hold.step(700, 0, 1000, 0.1f, false);
        assertEquals(0f, hold.progress(), 0f);
        assertEquals(0f, hold.precedingScroll(90000f, 250f), 0f);
    }

    @Test public void backgroundVoicesMustAlsoFinish() {
        LyricLine lead = new LyricLine("主唱", null, 0, 1000, false, null, null, null);
        lead.addBackground(new LyricLine("和声", null, 0, 2000, false, null, null, null));
        LyricFinalHold hold = new LyricFinalHold();
        hold.step(1500, LyricFocus.startOf(lead), LyricFocus.endOf(lead), 1f, true);
        assertFalse(hold.completed());
        hold.step(2000, LyricFocus.startOf(lead), LyricFocus.endOf(lead), 1f, true);
        assertTrue(hold.completed());
        assertEquals(1f, hold.progress(), 0.00001f);
    }

    @Test public void invalidEndCannotMarkAnUnfinishedLineAndResetClearsState() {
        LyricFinalHold hold = new LyricFinalHold();
        hold.step(1000, 1000, 1000, 1f, true);
        assertFalse(hold.completed());
        hold.step(1000, 0, 1000, 1f, true);
        assertTrue(hold.completed());
        hold.reset();
        assertFalse(hold.completed());
        assertEquals(0f, hold.progress(), 0f);
        assertFalse(hold.moving());
    }

    @Test public void earlierOverlappingVoiceCanEndAfterTheLastWrittenLine() {
        LyricLine earlierVoice = new LyricLine("earlier", null, 0, 2700, false, null, null, null);
        LyricLine last = new LyricLine("last", null, 1800, 2300, false, null, null, null);
        int end = LyricFocus.songEnd(java.util.Arrays.asList(earlierVoice, last));
        LyricFinalHold hold = new LyricFinalHold();
        assertEquals(2700, end);
        assertFalse(hold.step(2300, last.start, end, 1f, false));
        assertEquals(0f, hold.progress(), 0f);
        assertTrue(hold.step(2700, last.start, end, 1f, false));
        assertEquals(1f, hold.progress(), 0f);
    }
}
