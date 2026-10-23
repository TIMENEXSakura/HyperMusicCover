package com.os4.musiccover.lyrics;

import org.junit.Test;

import static org.junit.Assert.*;

public class LyricBandGeometryTest {
    private static float add(float previous, boolean drawn, float top, float height) {
        return LyricBandGeometry.include(previous, drawn, 120f, top, 600f, height, 1080f, 2300f);
    }

    private static float[] band(float floor) {
        float[] band = new float[2];
        assertTrue(LyricStyle.DEFAULT.writeBand(600f, floor, 3f, 75f, band));
        return band;
    }

    private static float clip(float[] band, float layoutFloor, float floor) {
        return LyricBandGeometry.visibleBottom(band[0], band[1], layoutFloor, floor);
    }

    @Test public void foldedCardDoesNotConsumeTheSpaceAboveTheCapsule() {
        float floor = add(Float.NaN, false, 1500f, 600f);
        floor = add(floor, true, 2160f, 140f);
        assertEquals(2160f, floor, 0f);
        float[] layout = band(1500f);
        float bottom = clip(layout, 1500f, floor);
        // Extra rows extend only below the original layout. Its centre is now above the
        // visible area's centre rather than moving down to centre the larger list.
        assertEquals(660f, bottom - layout[1], 0f);
        assertTrue((bottom - layout[1]) / 75f > 8f);
        assertTrue((layout[0] + layout[1]) / 2f < (layout[0] + bottom) / 2f);
        assertEquals(25f, LyricStyle.DEFAULT.sizeSp, 0f);
    }

    @Test public void risingMediaContainerOnlyClipsBottomWithoutRecenteringOrRescaling() {
        LyricBandGeometry.Reference reference = new LyricBandGeometry.Reference();
        float layoutFloor = reference.floor(1L, 2400f, 1500f);
        float[] layout = band(layoutFloor);
        float previous = clip(layout, layoutFloor, 2160f);
        float focusFit = LyricFocus.fit(1000f, layout[1] - layout[0]);
        for (float top : new float[]{2050f, 1900f, 1750f, 1600f, 1500f}) {
            float floor = add(add(Float.NaN, true, 2160f, 140f), true, top, 600f);
            assertEquals(top, floor, 0f);
            float stableFloor = reference.floor(1L, 2400f, top);
            float[] next = band(stableFloor);
            assertArrayEquals(layout, next, 0f);
            assertEquals(focusFit, LyricFocus.fit(1000f, next[1] - next[0]), 0f);
            float now = clip(next, stableFloor, floor);
            assertTrue(now < previous);
            previous = now;
        }
        assertEquals(layout[1], previous, 0f);
    }

    @Test public void cancellingAReverseSwipeRestoresBottomRowsAtTheirOriginalPositions() {
        float[] layout = band(1500f);
        float expanded = clip(layout, 1500f, 2160f);
        float previous = clip(layout, 1500f, 1600f);
        for (float top : new float[]{1750f, 1900f, 2050f, 2160f}) {
            float now = clip(layout, 1500f, add(Float.NaN, true, top, 140f));
            assertTrue(now > previous);
            previous = now;
        }
        assertEquals(expanded, previous, 0f);
        assertArrayEquals(band(1500f), layout, 0f);
    }

    @Test public void referenceSurvivesRebuildAndHandoffButNewRequestCanChooseANewAnchor() {
        LyricBandGeometry.Reference reference = new LyricBandGeometry.Reference();
        assertEquals(1500f, reference.floor(7L, 2400f, 1500f), 0f);
        assertEquals(1500f, reference.floor(7L, 2400f, 1900f), 0f);
        // Recreated views ask the same request-owned reference; rotation scales that reference.
        assertEquals(750f, reference.floor(7L, 1200f, 1000f), 0f);
        assertEquals(1400f, reference.floor(8L, 2400f, 1400f), 0.001f);
    }

    @Test public void aFullyOccludingCardHidesRowsWithoutShrinkingTheLayout() {
        float[] layout = band(1500f);
        assertEquals(layout[0], clip(layout, 1500f, 400f), 0f);
        assertEquals(layout[1], clip(layout, 1500f, Float.NaN), 0f);
        assertArrayEquals(band(1500f), layout, 0f);
        LyricStyle style = LyricStyle.DEFAULT.with("fill", 0.5f).with("pos", 0.8f);
        float[] custom = new float[2];
        assertTrue(style.writeBand(600f, 1500f, 3f, 75f, custom));
        assertEquals(660f, clip(custom, 1500f, 2160f) - custom[1], 0f);
        assertEquals(1500f - custom[1], 2160f - clip(custom, 1500f, 2160f), 0f);
    }

    @Test public void invalidReferenceCannotPoisonTheNextValidLayout() {
        LyricBandGeometry.Reference reference = new LyricBandGeometry.Reference();
        assertTrue(Float.isNaN(reference.floor(1L, 0f, 1500f)));
        assertTrue(Float.isNaN(reference.floor(1L, 2400f, Float.NaN)));
        assertEquals(1500f, reference.floor(1L, 2400f, 1500f), 0f);
        assertEquals(1500f, reference.floor(1L, 2400f, Float.NaN), 0f);
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
