package com.os4.musiccover.lyrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricStopPositionTest {
    private static final String TRACK = "player|artist|album|10000";
    private static final Object SESSION = new Object();

    @Test public void stoppedResetAtTrackEndHoldsObservedPosition() {
        LyricStopPosition gate = new LyricStopPosition();
        assertEquals(9800L, gate.select(TRACK, SESSION, true, false, 9800L, false, 0L, 7000L, 0));
        assertEquals(9800L, gate.select(TRACK, SESSION, false, true, 0L, true, 9800L, 7000L, 0));
        assertEquals(9800L, gate.select(TRACK, SESSION, false, true, 0L, true, 9800L, 7000L, 0));
        assertEquals(9800L, gate.select(TRACK, SESSION, false, true, -1L, true, 9800L, 7000L, 0));
    }

    @Test public void lastLyricAlsoQualifiesWhenDurationUnavailable() {
        LyricStopPosition gate = new LyricStopPosition();
        gate.select("player|title", SESSION, true, false, 5900L, false, 0L, 6000L, 400);
        assertEquals(5900L, gate.select("player|title", SESSION, false, true, 0L, true, 5900L, 6000L, 400));
    }

    @Test public void pausedSeekToZeroAndStopFarFromEndUseReportedPosition() {
        LyricStopPosition gate = new LyricStopPosition();
        gate.select(TRACK, SESSION, true, false, 9800L, false, 0L, 7000L, 0);
        assertEquals(0L, gate.select(TRACK, SESSION, false, false, 0L, true, 9800L, 7000L, 0));
        gate.reset();
        gate.select(TRACK, SESSION, true, false, 1000L, false, 0L, 7000L, 0);
        assertEquals(0L, gate.select(TRACK, SESSION, false, true, 0L, true, 1000L, 7000L, 0));
    }

    @Test public void resumeOrNewTrackOrSessionClearsTheHold() {
        LyricStopPosition gate = new LyricStopPosition();
        gate.select(TRACK, SESSION, true, false, 9800L, false, 0L, 7000L, 0);
        assertEquals(9800L, gate.select(TRACK, SESSION, false, true, 0L, true, 9800L, 7000L, 0));
        assertEquals(0L, gate.select(TRACK, SESSION, true, false, 0L, true, 9800L, 7000L, 0));
        gate.select(TRACK, SESSION, true, false, 9800L, true, 0L, 7000L, 0);
        assertEquals(0L, gate.select("player|artist|new album|10000", SESSION, false, true, 0L, true, 9800L, 7000L, 0));
        gate.select(TRACK, SESSION, true, false, 9800L, false, 0L, 7000L, 0);
        assertEquals(0L, gate.select(TRACK, new Object(), false, true, 0L, true, 9800L, 7000L, 0));
    }

    @Test public void missingEvidenceNeverPretendsTheLineFinished() {
        LyricStopPosition gate = new LyricStopPosition();
        gate.select(TRACK, SESSION, true, false, 9800L, false, 0L, 10000L, 0);
        assertEquals(0L, gate.select(TRACK, SESSION, false, true, 0L, false, 0L, 10000L, 0));
        gate.reset();
        gate.select(TRACK, null, true, false, 9800L, false, 0L, 10000L, 0);
        assertEquals(0L, gate.select(TRACK, null, false, true, 0L, true, 9800L, 10000L, 0));
    }
}
