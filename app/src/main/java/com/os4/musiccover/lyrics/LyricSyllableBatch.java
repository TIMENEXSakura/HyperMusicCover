package com.os4.musiccover.lyrics;

/** Coalesce contiguous not-yet-sung syllables which share the row's unchanged baseline. */
final class LyricSyllableBatch {
    private LyricSyllableBatch() { }

    static int lastUnstarted(int[] starts, int[] charEnds, int first, int ms, int rowEnd) {
        int last = first;
        while (last + 1 < starts.length && charEnds[last] < rowEnd && ms <= starts[last + 1]) {
            last++;
        }
        return last;
    }
}
