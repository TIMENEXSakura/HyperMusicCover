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
        assertFalse(focus.visible(2));
        assertEquals(0, focus.distance(0));
        float[] base = {0, 120, 240, 360, 480};
        float[] heights = {100, 100, 100, 100, 100};
        focus.layout(base, heights, 20);
        assertEquals(120, focus.position(4, base), 0);
        assertEquals(220, focus.height(base, heights), 0);
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
        parent.bg = line(0, 900);
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
        focus.layout(base, new float[]{300, 200}, 20);
        assertEquals(520, focus.height(base, new float[]{300, 200}), 0);
    }

    @Test public void staggeredBackingKeepsFinishedMiddleRowApartUntilItActuallyLeaves() {
        LyricLine first = line(0, 10000);
        LyricLine middle = line(2000, 4000);
        LyricLine last = line(3000, 8000);
        last.bg = line(1000, 8000); // voice three starts before the second lead
        List<LyricLine> lines = Arrays.asList(first, middle, last);
        LyricFocus focus = new LyricFocus();
        float[] heights = {100, 300, 160}; // middle includes its translation/backing rows
        float[] base = {0, 120, 440};
        boolean[] retained = new boolean[3];
        focus.update(lines, 1500, 3, 0, 0);
        assertTrue(focus.contains(0));
        assertTrue(focus.contains(2));
        focus.update(lines, 3000, 3, 2, 2);
        assertEquals(3, focus.count);
        focus.layout(base, heights, 20, retained);
        assertEquals(320, focus.position(2, base) - focus.position(1, base), 0);

        // The middle voice ended but is still being drawn while it waits for its top exit.
        focus.update(lines, 4100, 3, 2, 2);
        assertFalse(focus.contains(1));
        retained[1] = LyricFocus.retainSlot(false, true, false, false, true, 0f);
        focus.layout(base, heights, 20, retained);
        assertEquals(320, focus.position(2, base) - focus.position(1, base), 0);
        // Its frozen 300ms exit pose also retains the space, until it is actually gone.
        retained[1] = LyricFocus.retainSlot(false, false, true, false, true, 0f);
        focus.layout(base, heights, 20, retained);
        assertEquals(320, focus.position(2, base) - focus.position(1, base), 0);
        retained[1] = LyricFocus.retainSlot(false, false, true, true, false, 0f);
        focus.layout(base, heights, 20, retained);
        assertEquals(focus.position(1, base), focus.position(2, base), 0);
    }

    @Test public void fadingAndUnfinishedUnselectedRowsRetainSpaceButHiddenRowsDoNot() {
        assertTrue(LyricFocus.retainSlot(true, false, false, false, false, 0f));
        assertTrue(LyricFocus.retainSlot(false, false, false, false, true, 0.1f));
        assertFalse(LyricFocus.retainSlot(false, false, false, false, false, 0.1f));
        assertFalse(LyricFocus.retainSlot(false, false, false, false, true, 0.003f));
        assertFalse(LyricFocus.retainSlot(true, true, true, true, true, 1f));
    }

    @Test public void enteringVoiceAndUnequalSpringFramesDoNotCrossOnScreen() {
        LyricLine third = line(3000, 8000);
        third.bg = line(1000, 8000);
        List<LyricLine> lines = Arrays.asList(line(0, 10000), line(2000, 4000), third);
        float[] base = {0, 120, 440};
        float[] heights = {100, 300, 160};
        float[] scroll = {0, 0, 0}, aim = {0, 0, 0}, nextAim = {0, 0, 0};
        LyricFocus focus = new LyricFocus();
        focus.update(lines, 1500, 3, 0, 0);
        focus.layout(base, heights, 20);
        LyricTransition transition = new LyricTransition();
        transition.reset(base);
        transition.rebase(focus, base, scroll, aim, nextAim);
        // The previous group's springs have already settled at their common target.
        Arrays.fill(scroll, 0f);
        // The middle row begins after the background of the third row. Rebase preserves
        // their formerly identical screen pose on the first frame of the new group.
        focus.update(lines, 2500, 3, 1, 1);
        focus.layout(base, heights, 20);
        transition.rebase(focus, base, scroll, aim, nextAim);
        assertEquals(transition.position(1) - scroll[1],
                transition.position(2) - scroll[2], 0f);

        // Independent spring velocities, scales, seek retargeting and a frozen departing
        // middle row all produce different intermediate poses. Apply the drawing constraint
        // to those real poses, not merely to the eventual focus layout.
        float[][] intermediateScroll = {
                scroll.clone(), {0, 40, 200}, {50, 250, 100}, {100, 100, 100}
        };
        for (float[] frame : intermediateScroll) {
            float previousBottom = -Float.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                float scale = i == 1 ? 0.85f : 1f;
                float top = LyricTransition.scaledTop(transition.position(i) - frame[i], heights[i], scale);
                top += LyricFocus.separationOffset(top, previousBottom);
                assertTrue(top >= previousBottom);
                previousBottom = top + heights[i] * scale;
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
}
