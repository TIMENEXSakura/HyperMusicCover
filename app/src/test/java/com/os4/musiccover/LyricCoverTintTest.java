package com.os4.musiccover;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricCoverTintTest {
    @Test public void sameCoverKeepsItsFirstValidColor() {
        LyricCoverTint tint = new LyricCoverTint();
        tint.begin("cover-a");
        tint.record("cover-a", 0); // a failed measurement can be retried
        tint.record("cover-a", 0xff123456);
        tint.begin("cover-a");
        tint.record("cover-a", 0xffabcdef);
        assertEquals(0xff123456, tint.color());
    }

    @Test public void newCoverWaitsForItsOwnSampleAndRejectsLateWork() {
        LyricCoverTint tint = new LyricCoverTint();
        tint.begin("cover-a");
        tint.record("cover-a", 0xff123456);
        tint.begin("cover-b");
        assertEquals(0, tint.color());
        tint.record("cover-a", 0xffabcdef);
        assertEquals(0, tint.color());
        tint.record("cover-b", 0xff112233);
        assertEquals(0xff112233, tint.color());
    }
}
