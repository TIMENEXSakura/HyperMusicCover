package com.os4.musiccover;

import org.junit.Test;
import static org.junit.Assert.*;

public class NavigationLyricBackgroundTest {
    @Test public void explicitMusicRequestSurvivesLyricHidingUntilItsMapCloses() {
        NavigationLyricBackground state = new NavigationLyricBackground();
        state.requestedOver("amap-nav");
        // Lyric visibility is deliberately not background ownership: a body/two-finger hide
        // must not cancel the requested background or be switched back on when it resumes.
        assertTrue(state.pending());
        assertTrue(state.finish("amap-nav", true, true));
        assertFalse(state.pending());
        assertFalse(state.finish("amap-nav", true, true));
    }

    @Test public void unrelatedSceneCloseCannotRestoreOrConsumeTheMusicRequest() {
        NavigationLyricBackground state = new NavigationLyricBackground();
        state.requestedOver("amap-nav");
        assertFalse(state.finish("countdown", true, true));
        assertTrue(state.belongsTo("amap-nav"));
        assertTrue(state.finish("amap-nav", true, true));
    }

    @Test public void disabledCoverAndEndedSessionDoNotBringBackgroundBackLater() {
        NavigationLyricBackground state = new NavigationLyricBackground();
        state.requestedOver("amap-nav");
        assertFalse(state.finish("amap-nav", false, true));
        assertFalse(state.finish("amap-nav", true, true));
        state.requestedOver("amap-nav");
        assertFalse(state.finish("amap-nav", true, false));
        assertFalse(state.pending());
    }

    @Test public void destructionCancelsWhileANewExplicitRequestCanStartAgain() {
        NavigationLyricBackground state = new NavigationLyricBackground();
        state.requestedOver("amap-nav");
        state.cancel();
        assertFalse(state.finish("amap-nav", true, true));
        state.requestedOver("amap-nav");
        assertTrue(state.finish("amap-nav", true, true));
    }

    @Test public void hidingLyricsDoesNotExposeCoverWritesWhileMapIsStillRequested() {
        NavigationLyricBackground state = new NavigationLyricBackground();
        state.requestedOver("amap-nav");
        assertTrue(state.protects("amap-nav"));
        assertFalse(state.protects(null));
        assertFalse(state.protects("other"));
        assertTrue(state.finish("amap-nav", true, true));
        assertFalse(state.protects("amap-nav"));
    }

    @Test public void immersiveTapsSurviveTemporarilyHiddenNativeMediaCard() {
        assertTrue(NavigationLyricBackground.allowsToggle(true, false, false, false));
        assertTrue(NavigationLyricBackground.allowsToggle(false, true, true, false));
        assertTrue(NavigationLyricBackground.allowsToggle(false, true, false, true));
        assertFalse(NavigationLyricBackground.allowsToggle(false, false, true, true));
        assertFalse(NavigationLyricBackground.allowsToggle(false, true, false, false));
    }

    @Test public void navigationWithoutAnExplicitMusicPageKeepsItsExistingBackground() {
        NavigationLyricBackground state = new NavigationLyricBackground();
        assertFalse(state.finish("amap-nav", true, true));
    }
}
