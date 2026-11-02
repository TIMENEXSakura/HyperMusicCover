package com.os4.musiccover.lyrics;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class LyricLineExitTest {
    private static LyricLine line(int start, int end) {
        return new LyricLine("歌词", null, start, end, false, null, null, null);
    }

    private static void capture(LyricLineExit exits, int index, float y) {
        exits.capture(index, 40f, y, 1.1f, 0.9f, 1f, 1f, 0f);
    }

    @Test public void actualEndKeepsLastDrawnPositionAndShrinksWhileFading() {
        List<LyricLine> lines = Arrays.asList(line(0, 1000), line(1000, 2000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 500f);
        exits.step(lines, 999, 199L, true, false, true);
        assertFalse(exits.row(0).departing());
        assertTrue(exits.step(lines, 1000, 200L, true, false, true));
        LyricLineExit.Row row = exits.row(0);
        assertTrue(row.waitingForTop());
        assertFalse(row.departing());
        assertFalse(exits.startAtTop(0, 500f, 300f, 40f, 200L));
        capture(exits, 0, 320f); // the completed line follows its live scroll up to the edge
        assertTrue(exits.startAtTop(0, 320f, 300f, 40f, 200L));
        assertEquals(1f, row.alpha(200L), 0f);
        assertEquals(1f, row.scale(200L), 0f);
        capture(exits, 0, 200f); // once exiting, the captured pose is frozen
        assertEquals(320f, row.y, 0f);
        assertEquals(0.5f, row.alpha(350L), 0f);
        assertEquals(0.925f, row.scale(350L), 0.0001f);
        exits.step(lines, 1300, 500L, true, false, true);
        assertTrue(row.finished);
        assertEquals(0f, row.alpha(500L), 0f);
        assertEquals(0.85f, row.scale(500L), 0.0001f);
        assertFalse(exits.row(1).departing());
        assertFalse(exits.moving());
        assertEquals(2, lines.size()); // presentation never removes lyric data
    }

    @Test public void leadWaitsForEveryBackgroundAndLeavesUnfinishedOverlapVisible() {
        LyricLine lead = line(0, 1000);
        lead.addBackground(line(400, 1800));
        lead.addBackground(line(1200, 2400));
        List<LyricLine> lines = Arrays.asList(lead, line(500, 3000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 400f);
        capture(exits, 1, 700f);
        exits.step(lines, 1000, 200L, true, false, true);
        assertFalse(exits.row(0).departing());
        exits.step(lines, 1800, 1000L, true, false, true);
        assertFalse(exits.row(0).departing());
        exits.step(lines, 2400, 1600L, true, false, true);
        assertTrue(exits.row(0).waitingForTop());
        assertTrue(exits.startAtTop(0, 320f, 300f, 40f, 1600L));
        assertFalse(exits.row(1).departing());
        assertEquals(2, lead.backgrounds().length);
    }

    @Test public void losingFocusToANewerSingerCannotEndAnUnfinishedVoice() {
        List<LyricLine> lines = Arrays.asList(line(0, 10000), line(1000, 2000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 400f);
        LyricFocus focus = new LyricFocus();
        focus.update(lines, 1100, 1, 1, 1);
        assertFalse(focus.contains(0));
        exits.step(lines, 1100, 300L, true, false, true);
        assertFalse(exits.row(0).departing());
    }

    @Test public void wordTimingExtendingTheDeclaredEndStillFinishesBeforeExit() {
        LyricLine timed = new LyricLine("歌词", null, 0, 1000, false,
                new int[]{0, 700}, new int[]{700, 1800}, new int[]{1, 2});
        List<LyricLine> lines = Arrays.asList(timed);
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 400f);
        exits.step(lines, 1000, 200L, true, false, true);
        assertFalse(exits.row(0).departing());
        exits.step(lines, 1800, 1000L, true, false, true);
        assertTrue(exits.row(0).waitingForTop());
        assertFalse(exits.startAtTop(0, 320f, 300f, 40f, 1000L));
        exits.step(lines, 1800, 1000L + LyricLineExit.LAST_LINE_WAIT_MS, true, false, true);
        assertTrue(exits.row(0).departing());
    }

    @Test public void forwardSeekDoesNotReplayEveryCrossedEndAndBackwardSeekCanSingAgain() {
        List<LyricLine> lines = Arrays.asList(line(0, 1000), line(1000, 2000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 400f);
        capture(exits, 1, 600f);
        exits.step(lines, 2500, 200L, true, true, true);
        assertFalse(exits.moving());
        assertFalse(exits.row(0).departing());
        assertFalse(exits.row(1).departing());
        exits.step(lines, 900, 300L, true, true, true);
        capture(exits, 0, 450f);
        exits.step(lines, 950, 550L, true, false, true);
        exits.step(lines, 1000, 600L, true, false, true);
        assertTrue(exits.row(0).waitingForTop());
        assertTrue(exits.startAtTop(0, 320f, 300f, 40f, 600L));
        assertEquals(450f, exits.row(0).y, 0f);
    }

    @Test public void pauseAtEndCanFinishTheExistingExitWithoutAdvancingPlayback() {
        List<LyricLine> lines = Arrays.asList(line(0, 1000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 400f);
        exits.step(lines, 1000, 200L, false, false, true);
        assertTrue(exits.row(0).waitingForTop());
        assertFalse(exits.startAtTop(0, 320f, 300f, 40f, 200L));
        exits.step(lines, 1000, 1400L, false, false, true);
        assertTrue(exits.moving());
        exits.step(lines, 1000, 1550L, false, false, true);
        assertEquals(0.5f, exits.row(0).alpha(1550L), 0f);
        exits.step(lines, 1000, 1700L, false, false, true);
        assertTrue(exits.row(0).finished);
        assertFalse(exits.moving());
    }

    @Test public void sleepStillFramesAndUnseenLinesDoNotAnimateStalePoses() {
        List<LyricLine> lines = Arrays.asList(line(0, 1000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 400f);
        exits.step(lines, 5000, 4200L, true, false, true);
        assertFalse(exits.moving());
        exits.reset(1);
        exits.step(lines, 900, 5000L, true, false, false);
        capture(exits, 0, 400f);
        exits.step(lines, 1000, 5100L, true, false, false);
        assertFalse(exits.moving());
        exits.reset(1);
        exits.step(lines, 900, 6000L, true, false, true);
        exits.step(lines, 1000, 6100L, true, false, true);
        assertFalse(exits.moving());
    }

    @Test public void relayoutKeepsCompletedExitButNewTrackAndLoopResetIt() {
        List<LyricLine> lines = Arrays.asList(line(0, 1000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 400f);
        exits.step(lines, 1000, 200L, true, false, true);
        assertFalse(exits.startAtTop(0, 320f, 300f, 40f, 200L));
        exits.step(lines, 1000, 1400L, true, false, true);
        exits.finishForLayout();
        assertTrue(exits.row(0).finished);
        assertTrue(exits.row(0).departing());
        exits.step(lines, 50, 1500L, true, false, true);
        assertFalse(exits.row(0).departing());
        capture(exits, 0, 500f);
        exits.step(lines, 1000, 2450L, true, false, true);
        assertTrue(exits.row(0).waitingForTop());
        exits.step(lines, 1000, 3650L, true, false, true);
        assertTrue(exits.row(0).departing());
        exits.reset(1); // a different song can have the same row count
        assertFalse(exits.row(0).departing());
        assertFalse(exits.row(0).captured);
    }

    @Test public void fastLinesWaitForTheirOwnTopAndCannotForceAnUnfinishedOverlapOut() {
        List<LyricLine> lines = Arrays.asList(line(0, 350), line(100, 5000), line(350, 650));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 100, 100L, true, false, true);
        for (int i = 0; i < lines.size(); i++) capture(exits, i, 600f + i * 100f);
        exits.step(lines, 350, 350L, true, false, true);
        assertTrue(exits.row(0).waitingForTop());
        assertFalse(exits.startAtTop(0, 450f, 300f, 40f, 350L));
        assertFalse(exits.row(1).waitingForTop());
        assertFalse(exits.startAtTop(1, 300f, 300f, 40f, 350L));
        exits.step(lines, 650, 650L, true, false, true);
        assertTrue(exits.row(2).waitingForTop());
        assertTrue(exits.startAtTop(0, 340f, 300f, 40f, 650L));
        assertFalse(exits.row(1).departing());
    }

    @Test public void lastLineWaitsWithoutContinuousFramesAndCannotFadeBeforeItsEnd() {
        List<LyricLine> lines = Arrays.asList(line(0, 2000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 1000, 100L, true, false, true);
        capture(exits, 0, 500f);
        exits.step(lines, 1999, 1099L, true, false, true);
        assertEquals(-1L, exits.finalWakeAt());
        assertFalse(exits.startAtTop(0, 300f, 300f, 40f, 1099L));
        exits.step(lines, 2000, 1100L, false, false, true); // playback paused exactly at end
        assertTrue(exits.row(0).waitingForTop());
        assertFalse(exits.moving());
        assertEquals(1100L + LyricLineExit.LAST_LINE_WAIT_MS, exits.finalWakeAt());
        exits.step(lines, 2000, 1100L + LyricLineExit.LAST_LINE_WAIT_MS - 1L, false, false, true);
        assertFalse(exits.row(0).departing());
        exits.step(lines, 2000, 1100L + LyricLineExit.LAST_LINE_WAIT_MS, false, false, true);
        assertTrue(exits.row(0).departing());
        assertEquals(-1L, exits.finalWakeAt());
    }

    @Test public void finalFallbackNeverResurrectsAnOffscreenOrRelayoutPose() {
        List<LyricLine> lines = Arrays.asList(line(0, 1000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 500f);
        exits.step(lines, 1000, 200L, true, false, true);
        exits.forgetWaitingPose(0); // a moving/closing clip took the line offscreen
        assertEquals(-1L, exits.finalWakeAt());
        exits.step(lines, 1000, 2000L, false, false, true);
        assertFalse(exits.row(0).departing());
        capture(exits, 0, 600f); // only a newly visible pose can become the fallback
        assertEquals(200L + LyricLineExit.LAST_LINE_WAIT_MS, exits.finalWakeAt());
        exits.step(lines, 1000, 2001L, false, false, true);
        assertTrue(exits.row(0).departing());
        assertEquals(600f, exits.row(0).y, 0f);
    }

    @Test public void seekCancelsTheLastLineFallbackAndAllowsItToPlayAgain() {
        List<LyricLine> lines = Arrays.asList(line(0, 1000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 500f);
        exits.step(lines, 1000, 200L, true, false, true);
        assertTrue(exits.finalWakeAt() > 0L);
        exits.step(lines, 500, 300L, true, true, true);
        assertEquals(-1L, exits.finalWakeAt());
        assertFalse(exits.row(0).departing());
        assertFalse(exits.startAtTop(0, 300f, 300f, 40f, 1600L));
    }

    @Test public void finalLineAtTopRemainsVisibleUntilTimedFade() {
        List<LyricLine> lines = Arrays.asList(line(0, 1000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 250f);
        exits.step(lines, 1000, 200L, true, false, true);
        assertFalse(exits.startAtTop(0, 250f, 300f, 40f, 200L));
        assertFalse(exits.row(0).departing());
        exits.step(lines, 1000, 1400L, true, false, true);
        assertTrue(exits.row(0).departing());
        assertEquals(1f, exits.row(0).alpha(1400L), 0f);
        assertEquals(0.5f, exits.row(0).alpha(1550L), 0f);
    }

    @Test public void delayedFinalTickCanStillStartTheFadeAfterPlaybackStops() {
        List<LyricLine> lines = Arrays.asList(line(0, 1000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 500f);
        exits.step(lines, 1800, 1000L, false, false, true);
        assertTrue(exits.row(0).waitingForTop());
        exits.step(lines, 1800, 2200L, false, false, true);
        assertTrue(exits.row(0).departing());
    }

    @Test public void pausingBeforeFinalLineEndsNeverStartsTheFade() {
        List<LyricLine> lines = Arrays.asList(line(0, 1000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 500f);
        exits.step(lines, 900, 200L, false, false, true);
        exits.step(lines, 900, 3000L, false, false, true);
        assertFalse(exits.row(0).waitingForTop());
        assertFalse(exits.row(0).departing());
        assertEquals(-1L, exits.finalWakeAt());
    }

    @Test public void oldObservationAndSeekNeverReplayAFinalExit() {
        List<LyricLine> lines = Arrays.asList(line(0, 1000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 500f);
        exits.step(lines, 1800, 5000L, true, false, true);
        assertFalse(exits.row(0).waitingForTop());
        exits.reset(1);
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 500f);
        exits.step(lines, 1800, 1000L, true, true, true);
        assertFalse(exits.row(0).waitingForTop());
    }

    @Test public void exitStartsAtLastLiveOpacityEvenWhenLongLineIsClippedAtTop() {
        float clipTop = 300f, clipBottom = 600f, fade = 100f;
        // Before this fix the top edge made the first exit frame zero or 0.2 despite the
        // unfinished line being drawn fully opaque immediately before the end.
        for (float top : new float[] {250f, 300f, 320f}) {
            float bottom = top + 100f;
            float live = LyricBandGeometry.rowAlphaWhilePlaying(top, bottom,
                    clipTop, clipBottom, fade, true);
            assertEquals(live, LyricLineExit.edgeAlpha(live, top, bottom,
                    clipTop, clipBottom, fade), 0f);
        }
        // The timing animation itself still fades the captured pose smoothly.
        LyricLineExit.Row row = new LyricLineExit.Row();
        row.startedAt = 100L;
        assertEquals(1f, row.alpha(100L), 0f);
        assertEquals(0.5f, row.alpha(250L), 0f);
        assertEquals(0f, row.alpha(400L), 0f);
    }

    @Test public void movingCardUsesTheSameClippingAsTheLastLivePose() {
        assertEquals(0.5f, LyricLineExit.edgeAlpha(0.5f, 400f, 500f,
                300f, 525f, 100f), 0f);
        assertEquals(0.5f, LyricLineExit.edgeAlpha(0.5f, 400f, 500f,
                300f, 450f, 100f), 0f);
        assertEquals(0.25f, LyricLineExit.edgeAlpha(0.5f, 400f, 500f,
                300f, 425f, 100f), 0f);
        assertEquals(0f, LyricLineExit.edgeAlpha(0.5f, 400f, 500f,
                300f, 400f, 100f), 0f);
        assertEquals(0f, LyricLineExit.edgeAlpha(1f, 100f, 300f,
                300f, 600f, 100f), 0f);
    }

    @Test public void cardMorphCannotStartAnExitByMovingOnlyTheClip() {
        List<LyricLine> lines = Arrays.asList(line(0, 1000), line(1000, 3000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 400f);
        assertFalse(exits.startAtTop(0, 400f, 300f, 40f, 100L, false, 100f));
        exits.step(lines, 1000, 200L, true, false, true);
        assertTrue(exits.row(0).waitingForTop());
        // The top clip moves to the completed row, while the lyric list has not scrolled.
        assertFalse(exits.startAtTop(0, 400f, 380f, 40f, 210L, true, 100f));
        assertFalse(exits.row(0).departing());
        // A later singing line really pushes the row upward: the existing exit can start.
        assertTrue(exits.startAtTop(0, 399f, 380f, 40f, 220L, true, 99f));
    }

    @Test public void settledCardAllowsExitAndUnfinishedLineNeverExits() {
        List<LyricLine> lines = Arrays.asList(line(0, 1000), line(1000, 3000));
        LyricLineExit exits = new LyricLineExit();
        exits.step(lines, 900, 100L, true, false, true);
        capture(exits, 0, 400f);
        assertFalse(exits.startAtTop(0, 320f, 300f, 40f, 100L, true, 100f));
        assertFalse(exits.startAtTop(0, 319f, 300f, 40f, 110L, true, 99f));
        exits.step(lines, 1000, 200L, true, false, true);
        assertFalse(exits.startAtTop(0, 320f, 300f, 40f, 210L, true, 99f));
        assertTrue(exits.startAtTop(0, 320f, 300f, 40f, 220L, false, 99f));
    }
}
