package com.os4.musiccover.lyrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricContrastStateTest {
    @Test public void ordinaryLyricsAndNavigationEndingLeaveSavedColorsInControl() {
        LyricContrastState state = new LyricContrastState();
        assertEquals(0, state.update(false, "cover", 1L, LyricContrastState.BLACK));
        assertEquals(LyricContrastState.BLACK, state.update(true, "cover", 1L, LyricContrastState.BLACK));
        assertEquals(0, state.update(false, "cover", 1L, LyricContrastState.BLACK));
        assertEquals(LyricContrastState.WHITE, state.update(true, "cover", 1L, LyricContrastState.WHITE));
    }

    @Test public void repeatedNotificationsAndDynamicFramesCannotFlipTheChosenInk() {
        LyricContrastState state = new LyricContrastState();
        assertEquals(LyricContrastState.BLACK, state.update(true, "cover:1", 4L, LyricContrastState.BLACK));
        for (int next : new int[] {0, LyricContrastState.WHITE, 0xff00ff00, LyricContrastState.BLACK}) {
            assertEquals(LyricContrastState.BLACK, state.update(true, "cover:1", 4L, next));
        }
    }

    @Test public void asyncCoverAnswerCompletesOnlyTheStillUnresolvedChoice() {
        LyricContrastState state = new LyricContrastState();
        assertEquals(LyricContrastState.WHITE, state.update(true, "loading", 1L, 0));
        assertEquals(LyricContrastState.WHITE, state.update(true, "loading", 1L, 0xff778899));
        assertEquals(LyricContrastState.BLACK, state.update(true, "loading", 1L, LyricContrastState.BLACK));
        assertEquals(LyricContrastState.BLACK, state.update(true, "loading", 1L, LyricContrastState.WHITE));
    }

    @Test public void reopeningSamplesAgainEvenWhenNoHiddenFrameWasDrawn() {
        LyricContrastState state = new LyricContrastState();
        state.update(true, "wallpaper", 5L, LyricContrastState.BLACK);
        assertEquals(LyricContrastState.WHITE, state.update(true, "wallpaper", 7L, LyricContrastState.WHITE));
    }

    @Test public void backgroundOrThemeChangeDoesNotReuseThePreviousScenesInk() {
        LyricContrastState state = new LyricContrastState();
        state.update(true, "map:day", 3L, LyricContrastState.BLACK);
        assertEquals(LyricContrastState.WHITE, state.update(true, "cover:pending", 3L, 0));
        assertEquals(LyricContrastState.BLACK, state.update(true, "cover:pending", 3L, LyricContrastState.BLACK));
        assertEquals(LyricContrastState.WHITE, state.update(true, "map:night", 3L, LyricContrastState.WHITE));
    }

    @Test public void detachedViewReleasesItsPreviousChoice() {
        LyricContrastState state = new LyricContrastState();
        state.update(true, "same", 1L, LyricContrastState.BLACK);
        state.clear();
        assertEquals(LyricContrastState.WHITE, state.update(true, "same", 1L, LyricContrastState.WHITE));
    }
}
