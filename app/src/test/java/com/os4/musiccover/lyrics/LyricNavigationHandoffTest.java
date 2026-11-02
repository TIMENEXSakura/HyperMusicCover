package com.os4.musiccover.lyrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricNavigationHandoffTest {
    @Test public void delayedMorphStartsFromTheCurrentlyDrawnProgressAndBand() {
        LyricNavigationHandoff h = new LyricNavigationHandoff();
        h.begin(.92f, .62f);
        assertEquals(.92f, h.progress(.62f), .0001f);
        h.capture(400f, 1800f, 350f, 1300f, 600f, 1500f, 200f, 1200f);
        float[] band = {600f, 1500f, 200f, 1200f};
        h.apply(band);
        assertArrayEquals(new float[]{400f, 1800f, 350f, 1300f}, band, .001f);
    }

    @Test public void missingEndpointCanJoinLateAndStillSettles() {
        LyricNavigationHandoff h = new LyricNavigationHandoff();
        h.begin(.8f, .4f);
        h.capture(400f, 1800f, 350f, 1300f, 600f, 1500f, 200f, 1200f);
        float previous = h.progress(.4f);
        for (int i = 0; i < 150; i++) {
            h.advance(.016f);
            float value = h.progress(.4f);
            assertTrue(value <= previous);
            assertTrue(value >= .4f);
            previous = value;
        }
        assertEquals(.4f, h.progress(.4f), .0001f);
        assertFalse(h.moving());
        float[] band = {600f, 1500f, 200f, 1200f};
        h.apply(band);
        assertArrayEquals(new float[]{600f, 1500f, 200f, 1200f}, band, 0f);
    }

    @Test public void changesToRealProgressArePreservedDuringHandoff() {
        LyricNavigationHandoff h = new LyricNavigationHandoff();
        h.begin(.8f, .6f);
        h.capture(0f, 100f, 0f, 100f, 0f, 100f, 0f, 100f);
        h.advance(.016f);
        assertTrue(h.progress(.4f) < h.progress(.6f));
        h.reset();
        assertFalse(h.moving());
        assertEquals(.6f, h.progress(.6f), 0f);
    }

    @Test public void finishingRealMorphDoesNotAddOffsetAgainToDisplayedFallbackProgress() {
        LyricNavigationHandoff h = new LyricNavigationHandoff();
        h.begin(.3f, .15f);
        h.capture(0f, 100f, 0f, 100f, 0f, 100f, 0f, 100f);
        assertEquals(.3f, h.progress(true, .15f), .0001f);
        // After the real morph goes away the input is the already adjusted display value.
        assertEquals(.3f, h.progress(false, .3f), .0001f);
        h.advance(.016f);
        assertEquals(.25f, h.progress(false, .25f), .0001f);
    }
}
