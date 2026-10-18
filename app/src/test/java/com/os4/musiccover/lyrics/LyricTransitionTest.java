package com.os4.musiccover.lyrics;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class LyricTransitionTest {
    private LyricFocus focusAt(int position, float[] base, float[] heights) {
        LyricFocus focus = new LyricFocus();
        focus.update(Arrays.asList(
                new LyricLine("held", null, 0, 1000, false, null, null, null),
                new LyricLine("short", null, 0, 100, false, null, null, null),
                new LyricLine("new", null, 50, 1000, false, null, null, null)), position, 4, 2);
        focus.layout(base, heights, 20);
        return focus;
    }

    @Test public void compactAndExpandPreserveScreenPositionsAndSpringDisplacement() {
        for (float font : new float[]{18, 25, 36}) {
            float height = 100 * font / 25;
            float[] base = {0, height + 20, 2 * (height + 20)};
            float[] heights = {height, height, height};
            LyricTransition transition = new LyricTransition();
            transition.reset(base);
            float[] scroll = {30, 40, 50}, aim = {60, 60, 60}, next = {70, 70, 70};
            float oldY = base[2] - scroll[2];
            float displacement = scroll[2] - aim[2];
            float pending = next[2] - aim[2];
            transition.rebase(focusAt(150, base, heights), base, scroll, aim, next);
            assertEquals(oldY, transition.position(2) - scroll[2], 0.0001f);
            assertEquals(displacement, scroll[2] - aim[2], 0.0001f);
            assertEquals(pending, next[2] - aim[2], 0.0001f);
            transition.rebase(focusAt(75, base, heights), base, scroll, aim, next);
            assertEquals(oldY, transition.position(2) - scroll[2], 0.0001f);
        }
    }

    @Test public void fadeCanReverseWithoutJumpAndEventuallyStops() {
        float[] base = {0, 120, 240}, heights = {100, 100, 100};
        LyricTransition transition = new LyricTransition();
        transition.reset(base);
        LyricFocus compact = focusAt(150, base, heights);
        transition.update(compact, base, -1, 0.6f, 0.5f, 0.016f, false);
        assertTrue(transition.alpha(1) > 0 && transition.alpha(1) < 1);
        assertTrue(transition.fit > 0.5f && transition.fit < 1);
        float previousAlpha = transition.alpha(1);
        LyricFocus expanded = focusAt(75, base, heights);
        transition.update(expanded, base, -1, 0.6f, 1, 0, false);
        assertEquals(previousAlpha, transition.alpha(1), 0);
        for (int frame = 0; frame < 200; frame++) {
            transition.update(expanded, base, -1, 0.6f, 1, 0.016f, false);
        }
        assertEquals(1, transition.alpha(1), 0);
        assertEquals(1, transition.fit, 0);
        assertFalse(transition.moving);
    }

    @Test public void stillModeSettlesImmediately() {
        float[] base = {0, 120, 240}, heights = {100, 100, 100};
        LyricTransition transition = new LyricTransition();
        transition.reset(base);
        transition.update(focusAt(150, base, heights), base, -1, 0.6f, 0.5f, 0, true);
        assertEquals(0, transition.alpha(1), 0);
        assertEquals(0.5f, transition.fit, 0);
        assertFalse(transition.moving);
    }

    @Test public void uninitializedSeekTimestampCannotOverflowIntoRecentSeek() {
        assertFalse(LyricTransition.recentSeek(10000, Long.MIN_VALUE, 250));
        assertTrue(LyricTransition.recentSeek(10000, 9900, 250));
        assertFalse(LyricTransition.recentSeek(10000, 9750, 250));
        assertFalse(LyricTransition.recentSeek(10000, 11000, 250));
    }

    @Test public void motionAdaptsToCountAndMeasuredHeight() {
        float single = LyricTransition.motionWeight(1, 100, 600);
        float four = LyricTransition.motionWeight(4, 460, 600);
        assertTrue(four > single);
        assertTrue(LyricTransition.motionWeight(4, 660, 600) > four);
        assertTrue(LyricTransition.motionWeight(4, 660, 0) < 2);
    }
}
