package com.os4.musiccover.lyrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricTransitionTest {
    @Test public void unlimitedVoiceCountDoesNotSlowAnimationWithoutBound() {
        float four = LyricTransition.motionWeight(4, 10000f, 500f);
        assertEquals(four, LyricTransition.motionWeight(1000, 10000f, 500f), 0f);
        assertTrue(LyricTransition.motionWeight(1, 100f, 500f) < four);
    }

    @Test public void centeringRetargetKeepsPositionAndVelocityDisplacementContinuous() {
        float anchor = 300;
        float[] scroll = {40, 70}, aim = {100, 100}, next = {120, 120};
        float oldY = anchor + 200 - scroll[1];
        float oldDisplacement = scroll[1] - aim[1];
        LyricTransition.shift(80, scroll, aim, next);
        anchor += 80;
        assertEquals(oldY, anchor + 200 - scroll[1], 0);
        assertEquals(oldDisplacement, scroll[1] - aim[1], 0);
        assertEquals(20, next[1] - aim[1], 0);
    }

    @Test public void relayoutPreservesEveryRowsScreenPoseAndRunningSpring() {
        float[] oldBase = {0, 100, 200}, measured = {0, 140, 320};
        float[] scroll = {30, 40, 50}, aim = {60, 60, 60}, pending = {70, 70, 70};
        LyricTransition.rebase(oldBase, measured, scroll, aim, pending);
        assertArrayEquals(new float[]{30, 80, 170}, scroll, 0f);
        assertArrayEquals(new float[]{60, 100, 180}, aim, 0f);
        assertArrayEquals(new float[]{70, 110, 190}, pending, 0f);
        assertEquals(150f, measured[2] - scroll[2], 0f);
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
