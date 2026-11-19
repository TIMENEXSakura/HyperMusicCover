package com.os4.musiccover.lyrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricSyllableBatchTest {
    @Test public void quickUnstartedWordsShareOneRunUntilRowBoundary() {
        int[] starts = {0, 100, 200, 300, 400, 500};
        int[] ends = {2, 4, 6, 8, 10, 12};
        assertEquals(3, LyricSyllableBatch.lastUnstarted(starts, ends, 1, 60, 8));
        assertEquals(5, LyricSyllableBatch.lastUnstarted(starts, ends, 1, 60, 12));
    }

    @Test public void alreadyStartedWordNeverEntersUnstartedBatch() {
        int[] starts = {0, 100, 200, 300};
        int[] ends = {1, 2, 3, 4};
        // The caller passes the first unstarted syllable, including an exact start boundary.
        assertEquals(3, LyricSyllableBatch.lastUnstarted(starts, ends, 2, 200, 4));
        assertEquals(2, LyricSyllableBatch.lastUnstarted(starts, ends, 2, 200, 3));
    }

    @Test public void zeroLengthSyllablesDoNotPreventDrawingRemainingWords() {
        int[] starts = {0, 100, 200};
        int[] ends = {1, 1, 2};
        assertEquals(2, LyricSyllableBatch.lastUnstarted(starts, ends, 0, 0, 2));
    }
}
