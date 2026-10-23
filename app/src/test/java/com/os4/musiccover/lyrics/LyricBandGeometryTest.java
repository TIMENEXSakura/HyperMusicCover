package com.os4.musiccover.lyrics;

import org.junit.Test;

import static org.junit.Assert.*;

public class LyricBandGeometryTest {
    private static float add(float previous, boolean drawn, float top, float height) {
        return LyricBandGeometry.include(previous, drawn, 120f, top, 600f, height, 1080f, 2300f);
    }

    private static float bandHeight(float floor) {
        float[] band = new float[2];
        assertTrue(LyricStyle.DEFAULT.writeBand(600f, floor, 3f, 75f, band));
        return band[1] - band[0];
    }

    @Test public void foldedCardDoesNotConsumeTheSpaceAboveTheCapsule() {
        float floor = add(Float.NaN, false, 1500f, 600f);
        floor = add(floor, true, 2160f, 140f);
        assertEquals(2160f, floor, 0f);
        // Exercise the production band calculation: more room means additional full rows,
        // while text size, the gap and the user's existing fill setting are left intact.
        assertTrue(bandHeight(floor) / 75f > bandHeight(1500f) / 75f + 8f);
        assertEquals(25f, LyricStyle.DEFAULT.sizeSp, 0f);
    }

    @Test public void risingMediaContainerContinuouslyPushesTheBandUp() {
        float previous = bandHeight(2160f);
        for (float top : new float[]{2050f, 1900f, 1750f, 1600f, 1500f}) {
            float floor = add(add(Float.NaN, true, 2160f, 140f), true, top, 600f);
            assertEquals(top, floor, 0f);
            float now = bandHeight(floor);
            assertTrue(now < previous);
            previous = now;
        }
    }

    @Test public void cancellingAReverseSwipeRestoresTheSameLargeBand() {
        float expanded = bandHeight(add(Float.NaN, true, 2160f, 140f));
        float previous = bandHeight(1600f);
        for (float top : new float[]{1750f, 1900f, 2050f, 2160f}) {
            float now = bandHeight(add(Float.NaN, true, top, 140f));
            assertTrue(now > previous);
            previous = now;
        }
        assertEquals(expanded, previous, 0f);
    }

    @Test public void highestOfSeveralCapsulesAndMovingCardsWinsIndependentlyOfOrder() {
        float pillFirst = add(add(add(Float.NaN, true, 2160f, 140f), true, 2090f, 180f),
                true, 1770f, 450f);
        float cardFirst = add(add(add(Float.NaN, true, 1770f, 450f), true, 2090f, 180f),
                true, 2160f, 140f);
        assertEquals(1770f, pillFirst, 0f);
        assertEquals(pillFirst, cardFirst, 0f);
    }

    @Test public void commitContinuesUsingMovingGeometryUntilTheNativeCardLands() {
        boolean used = LyricBandGeometry.usesCapsuleSpace(true, true, false, false);
        assertTrue(used);
        used = LyricBandGeometry.usesCapsuleSpace(true, true, true, used);
        assertTrue(used);
        used = LyricBandGeometry.usesCapsuleSpace(false, false, true, used);
        assertTrue(used);
        used = LyricBandGeometry.usesCapsuleSpace(false, false, false, used);
        assertFalse(used);
        // Cancellation keeps the request, so the same policy still reads the pill at rest.
        assertTrue(LyricBandGeometry.usesCapsuleSpace(true, true, false, true));
    }

    @Test public void ordinaryCoverMorphsAndRevokedRequestsNeverAcquireCapsuleSpace() {
        assertFalse(LyricBandGeometry.usesCapsuleSpace(false, false, true, false));
        boolean used = LyricBandGeometry.usesCapsuleSpace(true, true, false, false);
        // The request is closed without opening a card. Release the qualification immediately.
        used = LyricBandGeometry.usesCapsuleSpace(false, false, false, used);
        assertFalse(used);
        assertFalse(LyricBandGeometry.usesCapsuleSpace(false, false, true, used));
        // A reconstructed controller starts with no previous qualification.
        assertFalse(LyricBandGeometry.usesCapsuleSpace(false, false, true, false));
    }

    @Test public void invisibleDetachedOrUnmeasuredSurfacesKeepTheExistingFallback() {
        assertTrue(Float.isNaN(add(Float.NaN, false, 1500f, 500f)));
        assertTrue(Float.isNaN(add(Float.NaN, true, 0f, 0f)));
        assertTrue(Float.isNaN(add(Float.NaN, true, Float.NaN, 120f)));
        assertTrue(Float.isNaN(add(Float.NaN, true, 2100f, Float.POSITIVE_INFINITY)));
        assertEquals(2100f, add(2100f, true, Float.NaN, 120f), 0f);
    }

    @Test public void screenInsetsAndOffscreenGeometryCannotExpandTheBandPastTheWindow() {
        assertEquals(2300f, LyricBandGeometry.usableBottom(2400f, 100f), 0f);
        assertTrue(Float.isNaN(LyricBandGeometry.usableBottom(2400f, 2500f)));
        assertTrue(Float.isNaN(LyricBandGeometry.usableBottom(0f, 0f)));
        assertTrue(Float.isNaN(add(Float.NaN, true, 2320f, 50f)));
        assertTrue(Float.isNaN(LyricBandGeometry.include(Float.NaN, true,
                1200f, 2100f, 100f, 100f, 1080f, 2300f)));
        assertTrue(Float.isNaN(LyricBandGeometry.include(Float.NaN, true,
                100f, 2100f, 100f, 100f, 0f, 2300f)));
        assertEquals(0f, add(Float.NaN, true, -30f, 100f), 0f);
    }
}
