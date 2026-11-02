package com.os4.musiccover.lyrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricPanelProgressTest {
    @Test public void waitsForMorphThenTracksItsProgressIncludingReversal() {
        LyricPanelProgress p = new LyricPanelProgress();
        float value = p.update(true, false, Float.NaN, true, 1f, 1000L, .016f);
        assertEquals(1f, value, 0f);
        assertTrue(p.moving());
        value = p.update(true, false, Float.NaN, true, value, 1032L, .016f);
        assertEquals(1f, value, 0f);
        value = p.update(true, true, .8f, true, value, 1048L, .016f);
        assertEquals(.8f, value, 0f);
        assertFalse(p.moving());
        value = p.update(true, true, .4f, false, value, 1064L, .016f);
        assertEquals(.4f, value, 0f);
        value = p.update(true, true, .7f, false, value, 1080L, .016f);
        assertEquals(.7f, value, 0f);
    }

    @Test public void absentMorphTransitionsSmoothlyAndStopsScheduling() {
        LyricPanelProgress p = new LyricPanelProgress();
        float value = 1f;
        for (int i = 0; i < 150; i++) {
            float before = value;
            value = p.update(true, false, Float.NaN, true, value, 1000L + i * 16L, .016f);
            assertTrue(value <= before);
            assertTrue(value >= 0f);
        }
        assertEquals(0f, value, 0f);
        assertFalse(p.moving());
    }

    @Test public void cancelledSelectionReturnsToCurrentEndpointWithoutStaleAnimation() {
        LyricPanelProgress p = new LyricPanelProgress();
        float value = p.update(true, false, Float.NaN, true, 1f, 1000L, .016f);
        value = p.update(true, false, Float.NaN, false, value, 1016L, .016f);
        assertEquals(1f, value, 0f);
        assertFalse(p.moving());
    }

    @Test public void ineligiblePageDoesNotStartNavigationFallback() {
        LyricPanelProgress p = new LyricPanelProgress();
        assertEquals(0f, p.update(false, false, Float.NaN, true, 1f, 1000L, .016f), 0f);
        assertFalse(p.moving());
    }
}
