package com.os4.musiccover.lyrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricBlurWorkTest {
    @Test public void rapidOpeningDiscardsPastPrewarmAndRetainsNearbyRows() {
        LyricBlurWork past = new LyricBlurWork(2);
        LyricBlurWork next = new LyricBlurWork(13);
        assertFalse(past.obsolete(3));
        assertTrue(past.obsolete(9));
        assertFalse(next.obsolete(9));
        assertTrue(next.obsolete(0)); // A backward seek invalidates distant future work too.
    }

    @Test public void aVisibleRowPromotesOnlyOnceAndCanRemainOutsideTheFocusWindow() {
        LyricBlurWork row = new LyricBlurWork(1);
        assertTrue(row.promote());
        assertFalse(row.promote());
        assertFalse(row.obsolete(12)); // A tall or overlapping row can still be on screen.
        assertTrue(row.claim());
        assertFalse(row.claim()); // Reposting while the worker starts never rasterizes twice.
    }

    @Test public void promotionAfterTheWorkerStartsStillProtectsVisibleOutput() {
        LyricBlurWork row = new LyricBlurWork(1);
        assertTrue(row.claim());
        assertFalse(row.promote());
        assertFalse(row.obsolete(20));
        assertFalse(row.claim());
    }
}
