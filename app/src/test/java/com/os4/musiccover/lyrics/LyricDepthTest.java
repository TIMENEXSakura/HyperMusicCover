package com.os4.musiccover.lyrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricDepthTest {
    @Test public void blurIncreasesWithVisualDistanceAndIsStrongerAbove() {
        float advance = 72f;
        for (int rows = 1; rows < LyricDepth.LEVELS - 1; rows++) {
            assertTrue(LyricDepth.target(rows * advance, advance, true)
                    > LyricDepth.target(rows * advance, advance, false));
            assertTrue(LyricDepth.target((rows + 1) * advance, advance, false)
                    > LyricDepth.target(rows * advance, advance, false));
        }
        assertTrue(LyricDepth.target(1000f, advance, true) > LyricDepth.target(1000f, advance, false));
        assertEquals(0f, LyricDepth.radius(0f), 0f);
    }

    @Test public void SpringPositionsCanCrossBlurLevelsWithoutSnapping() {
        float advance = 80f;
        float before = LyricDepth.target(2f * advance, advance, true);
        float middle = LyricDepth.target(2.5f * advance, advance, true);
        float after = LyricDepth.target(3f * advance, advance, true);
        assertEquals((before + after) / 2f, middle, 0.001f);
    }
}
