package com.os4.musiccover.lyrics;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class LyricFocusTest {
    private LyricLine line(int start, int end) {
        return new LyricLine("voice", null, start, end, false, null, null, null);
    }

    @Test public void unfinishedOldVoiceSurvivesManyNewLines() {
        List<LyricLine> lines = Arrays.asList(line(0, 10000), line(1000, 2000),
                line(2000, 3000), line(3000, 4000), line(4000, 5000));
        LyricFocus focus = new LyricFocus();
        focus.update(lines, 4500, 3, 4, 4);
        assertEquals(2, focus.count);
        assertTrue(focus.contains(0));
        assertTrue(focus.contains(4));
        assertFalse(focus.contains(2));
        assertEquals(0, focus.distance(0));
        float[] base = {0, 120, 240, 360, 480};
        float[] heights = {100, 100, 100, 100, 100};
        assertEquals(580, focus.height(base, heights), 0);
    }

    @Test public void newestFourWinAndLimitOneIsRespected() {
        List<LyricLine> lines = Arrays.asList(line(0, 1000), line(0, 1000),
                line(0, 1000), line(0, 1000), line(0, 1000));
        LyricFocus focus = new LyricFocus();
        focus.update(lines, 500, 99, 4, 4);
        assertEquals(4, focus.count);
        assertFalse(focus.contains(0));
        assertEquals(1, focus.first);
        assertTrue(focus.update(lines, 500, 1, 4, 4));
        assertEquals(1, focus.count);
        assertTrue(focus.contains(4));
        assertEquals(1, LyricFocus.clampLimit(-1));
    }

    @Test public void backingVoiceExtendsParentWithoutTakingSlot() {
        LyricLine parent = line(100, 200);
        parent.addBackground(line(0, 900));
        LyricFocus focus = new LyricFocus();
        List<LyricLine> lines = Arrays.asList(parent, line(300, 800));
        focus.update(lines, 50, 2, 0, -1);
        assertTrue(focus.contains(0));
        focus.update(lines, 500, 2, 1, 1);
        assertEquals(2, focus.count);
        focus.update(lines, 850, 2, 1, 1);
        assertEquals(2, focus.count);
        assertTrue(focus.contains(0));
        assertEquals(1, focus.last);
    }

    @Test public void finishedLeadStaysFocusedWhileOlderVoiceContinuesUntilNextStarts() {
        List<LyricLine> lines = Arrays.asList(line(0, 9000), line(1000, 2000),
                line(4000, 5000));
        LyricFocus focus = new LyricFocus();
        focus.update(lines, 1500, 3, 1, 1);
        assertEquals(1, focus.last);
        assertFalse(focus.update(lines, 2200, 3, 1, 1));
        assertEquals(2, focus.count);
        assertTrue(focus.contains(0));
        assertTrue(focus.contains(1));
        assertEquals(1, focus.last);
        assertFalse(focus.update(lines, 2300, 3, 1, 1));
        focus.update(lines, 3500, 3, 2, 1); // next line has not started
        assertEquals(1, focus.last);
        focus.update(lines, 4100, 3, 2, 2);
        assertEquals(2, focus.last);
        assertTrue(focus.contains(0));
        assertTrue(focus.contains(2));
    }

    @Test public void heldFocusObeysLimitAndSeeksBackWithoutStickyHistory() {
        List<LyricLine> lines = Arrays.asList(line(0, 9000), line(1000, 2000));
        LyricFocus focus = new LyricFocus();
        focus.update(lines, 2500, 1, 1, 1);
        assertEquals(1, focus.count);
        assertEquals(1, focus.last);
        assertTrue(focus.contains(1));
        assertFalse(focus.contains(0));
        focus.update(lines, 500, 1, 0, 0); // user seeks back
        assertEquals(0, focus.last);
        assertTrue(focus.contains(0));
        focus.reset();
        focus.update(Arrays.asList(line(0, 100)), 50, 1, 0, 0);
        assertEquals(0, focus.last);
    }

    @Test public void seekAndExclusiveEndDoNotRetainStaleSelection() {
        LyricFocus focus = new LyricFocus();
        List<LyricLine> lines = Arrays.asList(line(0, 100), line(50, 200));
        assertTrue(focus.update(lines, 75, 3, 1, 1));
        assertFalse(focus.update(lines, 80, 3, 1, 1));
        assertTrue(focus.update(lines, 100, 3, 1, 1));
        assertFalse(focus.contains(0));
        focus.update(lines, 25, 3, 0, 0);
        assertEquals(0, focus.last);
        focus.update(lines, 200, 3, 1, 1);
        assertEquals(0, focus.count);
        assertEquals(1, focus.last);
        focus.reset();
        assertEquals(-1, focus.first);
    }

    @Test public void layoutIncludesFullBackingAndTranslationHeight() {
        LyricFocus focus = new LyricFocus();
        focus.update(Arrays.asList(line(0, 100), line(0, 100)), 50, 4, 1, 1);
        float[] base = {0, 320};
        assertEquals(520, focus.height(base, new float[]{300, 200}), 0);
    }

    @Test public void staggeredBackingsKeepTheirMeasuredRowsWhenTheMiddleGroupFinishes() {
        LyricLine first = line(0, 10000);
        LyricLine middle = line(2000, 3000);
        middle.addBackground(line(1500, 3500));
        middle.addBackground(line(2600, 4000));
        LyricLine third = line(3000, 8000);
        third.addBackground(line(1000, 8000)); // third group can sing before the second lead
        List<LyricLine> lines = Arrays.asList(first, middle, third, line(6000, 12000));
        LyricFocus focus = new LyricFocus();
        float[] heights = {100, 300, 160, 120};
        float[] base = {0, 120, 440, 620};
        for (int limit : new int[]{3, 0}) {
            focus.update(lines, 3999, limit, 2, 2);
            assertTrue(focus.contains(1));
            assertEquals(600, focus.height(base, heights), 0);
            for (int ms : new int[]{4000, 4900, 5999}) {
                focus.update(lines, ms, limit, 2, 2);
                assertFalse(focus.contains(1));
                assertEquals(2, focus.last);
                assertEquals(600, focus.height(base, heights), 0);
            }
            focus.update(lines, 6000, limit, 3, 3);
            assertEquals(3, focus.last);
            assertEquals(740, focus.height(base, heights), 0);
        }
    }

    @Test public void enteringVoiceAndUnequalSpringFramesDoNotCrossOnScreen() {
        float[] base = {0, 120, 440};
        float[] heights = {100, 300, 160};

        // Apply the drawing constraint to the actual intermediate spring poses.
        float[][] intermediateScroll = {
                {0, 0, 0}, {0, 40, 200}, {50, 250, 100}, {100, 100, 100}
        };
        for (float[] frame : intermediateScroll) {
            float previousBottom = -Float.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                float top = base[i] - frame[i];
                top += LyricFocus.separationOffset(top, previousBottom);
                assertTrue(top >= previousBottom);
                previousBottom = top + heights[i];
            }
        }
    }

    @Test public void unlimitedReallyIncludesEveryVoiceAndRespondsToSeekAndLimitChanges() {
        java.util.ArrayList<LyricLine> lines = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) lines.add(line(i, 10000));
        LyricFocus focus = new LyricFocus();
        assertEquals(0, LyricFocus.clampLimit(0));
        assertTrue(focus.update(lines, 9000, 0, 999, 999));
        assertEquals(1000, focus.count);
        assertEquals(0, focus.first);
        assertEquals(999, focus.last);
        assertTrue(focus.contains(0));
        assertTrue(focus.contains(999));
        assertTrue(focus.update(lines, 9000, 4, 999, 999));
        assertEquals(4, focus.count);
        assertFalse(focus.contains(995));
        assertTrue(focus.contains(996));
        assertTrue(focus.update(lines, 8, 0, 8, 8));
        assertEquals(9, focus.count);
        assertEquals(8, focus.last);
        assertFalse(focus.contains(999));
        focus.reset();
        focus.update(Arrays.asList(line(0, 100)), 50, 0, 0, 0);
        assertEquals(1, focus.count);
        assertTrue(focus.contains(0));
    }
    @org.junit.Test public void departingHarmonyKeepsSpaceThenReleasesWithoutAJump() {
        float held = 600f;
        assertEquals(held, LyricFocus.releaseSeparation(held, held, 1f / 120f), 0f);
        float first = LyricFocus.releaseSeparation(held, 0f, 1f / 120f);
        assertTrue(first > 500f && first < held);
        float at60 = held, at120 = held;
        for (int i = 0; i < 30; i++) at60 = LyricFocus.releaseSeparation(at60, 0f, 1f / 60f);
        for (int i = 0; i < 60; i++) at120 = LyricFocus.releaseSeparation(at120, 0f, 1f / 120f);
        assertEquals(at60, at120, 0.01f);
        assertEquals(700f, LyricFocus.releaseSeparation(at120, 700f, 1f / 120f), 0f);
        for (int i = 0; i < 120; i++) at120 = LyricFocus.releaseSeparation(at120, 0f, 1f / 120f);
        assertEquals(0f, at120, 0f);
    }

}
