package com.os4.musiccover.lyrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricOverflowFollowTest {
    @Test public void untimedWordsFollowLineTimeAndClampSeeks() {
        assertEquals(0f, LyricOverflowFollow.timeChars(500, 1000, 3000, 100), 0f);
        assertEquals(25f, LyricOverflowFollow.timeChars(1500, 1000, 3000, 100), 0f);
        assertEquals(100f, LyricOverflowFollow.timeChars(4000, 1000, 3000, 100), 0f);
        assertEquals(0f, LyricOverflowFollow.timeChars(0, 10, 10, 100), 0f);
    }

    @Test public void fractionalSungCharactersMoveContinuouslyAcrossWrappedRows() {
        assertEquals(125f, LyricOverflowFollow.rowPosition(25f, 20, 40, 100f, 200f), 0f);
        assertEquals(200f, LyricOverflowFollow.rowPosition(40f, 20, 40, 100f, 200f), 0f);
        assertEquals(200f, LyricOverflowFollow.rowPosition(40f, 40, 60, 200f, 300f), 0f);
    }

    @Test public void viewportShowsBeginningAndEndWithoutExposingEmptySpace() {
        // A 1200 px lead at the 30% focus in a 400 px viewport has normal top -380.
        float normalTop = LyricBandGeometry.singingTop(100f, 500f, 1200f);
        assertEquals(-380f, normalTop, .001f);
        assertEquals(480f, LyricOverflowFollow.offset(0f, 1200f, 100f, 500f, normalTop), .001f);
        assertEquals(0f, LyricOverflowFollow.offset(600f, 1200f, 100f, 500f, normalTop), 0f);
        assertEquals(-320f, LyricOverflowFollow.offset(1200f, 1200f, 100f, 500f, normalTop), .001f);
        assertEquals(0f, LyricOverflowFollow.offset(100f, 300f, 100f, 500f, 150f), 0f);
    }

    @Test public void invalidOrCollapsedViewportDoesNotMoveTheRestOfTheLyrics() {
        assertEquals(0f, LyricOverflowFollow.offset(Float.NaN, 1200f, 100f, 500f, 0f), 0f);
        assertEquals(0f, LyricOverflowFollow.offset(200f, 1200f, 100f, 100f, 0f), 0f);
    }
    @Test public void oversizedLineTracksCardAnchorDuringReversedDrag() {
        float folded = LyricBandGeometry.singingTop(100f, 500f, 1200f, 0f);
        float expanded = LyricBandGeometry.singingTop(100f, 500f, 1200f, 1f);
        float a = LyricOverflowFollow.offset(600f, 1200f, 100f, 500f, folded, 0f);
        float b = LyricOverflowFollow.offset(600f, 1200f, 100f, 500f, expanded, 1f);
        assertEquals(0f, a, 0f);
        assertEquals(0f, b, 0f);
        float middle = LyricBandGeometry.singingTop(100f, 500f, 1200f, .5f);
        assertEquals(0f, LyricOverflowFollow.offset(600f, 1200f, 100f, 500f, middle, .5f), 0f);
    }

    @Test public void scaledVeryLongFinalLineKeepsItsEndWithinTheViewport() {
        float height = 100000f, scale = 1.025f * .94f;
        float top = LyricBandGeometry.singingTop(100f, 500f, height);
        float offset = LyricOverflowFollow.scaledOffset(height, height, height, scale,
                100f, 500f, top, 0f);
        float scaledTop = top + height * (1f - scale) / 2f + offset;
        assertEquals(500f, scaledTop + height * scale, .02f);
    }

    @Test public void scalingThatMakesLineFitKeepsWholeLineWithinViewport() {
        float height = 420f, scale = .94f;
        float top = LyricBandGeometry.singingTop(100f, 500f, height);
        float offset = LyricOverflowFollow.scaledOffset(height, height, height, scale,
                100f, 500f, top, 0f);
        float scaledTop = top + height * (1f - scale) / 2f + offset;
        assertTrue(scaledTop >= 100f);
        assertTrue(scaledTop + height * scale <= 500.001f);
    }
}
