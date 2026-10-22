package com.os4.musiccover.lyrics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LyricPageVisibilityTest {
    @Test public void capsulePageIsVisibleWhileDetailedMediaCardRemainsFolded() {
        // Regression: MediaCard.setCardProgressFrom clamps the card to zero outside cover mode.
        // Using that number for both pages made an attached, populated capsule page invisible.
        assertEquals(1f, LyricPageVisibility.progress(true, false, 0f), 0f);
    }

    @Test public void closeAndImmediateReopenDoNotRequireAChangeInMediaCardProgress() {
        assertEquals(1f, LyricPageVisibility.progress(true, false, 0f), 0f);
        assertEquals(0f, LyricPageVisibility.progress(false, false, 0f), 0f);
        assertEquals(1f, LyricPageVisibility.progress(true, false, 0f), 0f);
    }

    @Test public void coverPageStillFollowsCardDuringEntryAndAfterCapsuleHandoff() {
        for (boolean capsulePage : new boolean[]{false, true}) {
            assertEquals(0f, LyricPageVisibility.progress(capsulePage, true, 0f), 0f);
            assertEquals(.35f, LyricPageVisibility.progress(capsulePage, true, .35f), 0f);
            assertEquals(1f, LyricPageVisibility.progress(capsulePage, true, 1f), 0f);
        }
    }

    @Test public void staleCardProgressCannotKeepAClosedPageVisible() {
        assertEquals(0f, LyricPageVisibility.progress(false, false, 1f), 0f);
    }
}
