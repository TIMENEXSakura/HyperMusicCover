package com.os4.musiccover;

import org.junit.Test;
import static org.junit.Assert.*;

public class NavigationLyricBackgroundTest {
    @Test public void lyricOwnedCoverDoesNotReturnAfterLyricsWereClosedOverMap() {
        NavigationLyricBackground state = new NavigationLyricBackground();
        state.requestedOver("amap-nav");
        assertTrue(state.pending());
        assertFalse(state.finish("amap-nav", true, true, false, false));
        assertFalse(state.pending());
        assertFalse(state.finish("amap-nav", true, true, true, false));
    }

    @Test public void aVisibleLyricPageRestoresItsBackgroundAfterNavigationCloses() {
        NavigationLyricBackground state = new NavigationLyricBackground();
        state.requestedOver("amap-nav");
        assertTrue(state.finish("amap-nav", true, true, true, false));
        assertFalse(state.pending());
    }

    @Test public void anIndependentCoverRemainsWhenLyricsWereClosed() {
        NavigationLyricBackground state = new NavigationLyricBackground();
        state.requestedOver("amap-nav");
        assertTrue(state.finish("amap-nav", true, true, false, true));
        assertFalse(state.pending());
    }

    @Test public void hidingThenReopeningLyricsBeforeMapClosesUsesCurrentVisibility() {
        NavigationLyricBackground state = new NavigationLyricBackground();
        state.requestedOver("amap-nav");
        assertTrue(state.protects("amap-nav"));
        // The lyric is reopened before map close; no stale hide decision is retained.
        state.requestedOver("amap-nav");
        assertTrue(state.finish("amap-nav", true, true, true, false));
        assertFalse(state.finish("amap-nav", true, true, true, false));
    }

    @Test public void disabledOrDestroyedLyricRequestDoesNotRestoreAnOwnedCover() {
        NavigationLyricBackground state = new NavigationLyricBackground();
        state.requestedOver("amap-nav");
        assertTrue(state.protects("amap-nav"));
        // Disable or a destroyed session leaves the map protected until its own close.
        assertFalse(state.finish("amap-nav", true, false, false, false));
        assertFalse(state.pending());
        assertFalse(state.finish("amap-nav", true, true, false, false));
    }

    @Test public void unrelatedSceneCloseCannotRestoreOrConsumeTheMusicRequest() {
        NavigationLyricBackground state = new NavigationLyricBackground();
        state.requestedOver("amap-nav");
        assertFalse(state.finish("countdown", true, true, true, false));
        assertTrue(state.belongsTo("amap-nav"));
        assertTrue(state.finish("amap-nav", true, true, true, false));
    }

    @Test public void disabledCoverAndEndedSessionDoNotBringBackgroundBackLater() {
        NavigationLyricBackground state = new NavigationLyricBackground();
        state.requestedOver("amap-nav");
        assertFalse(state.finish("amap-nav", false, true, true, false));
        assertFalse(state.finish("amap-nav", true, true, true, false));
        state.requestedOver("amap-nav");
        assertFalse(state.finish("amap-nav", true, false, true, false));
        assertFalse(state.pending());
    }

    @Test public void destructionCancelsWhileANewExplicitRequestCanStartAgain() {
        NavigationLyricBackground state = new NavigationLyricBackground();
        state.requestedOver("amap-nav");
        state.cancel();
        assertFalse(state.finish("amap-nav", true, true, true, false));
        state.requestedOver("amap-nav");
        assertTrue(state.finish("amap-nav", true, true, true, false));
    }

    @Test public void hidingLyricsDoesNotExposeCoverWritesWhileMapIsStillRequested() {
        NavigationLyricBackground state = new NavigationLyricBackground();
        state.requestedOver("amap-nav");
        assertTrue(state.protects("amap-nav"));
        assertFalse(state.protects(null));
        assertFalse(state.protects("other"));
        assertTrue(state.finish("amap-nav", true, true, true, false));
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
        assertFalse(state.finish("amap-nav", true, true, true, false));
    }
}
