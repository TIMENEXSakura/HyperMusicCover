package com.os4.musiccover;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkSessionStateTest {
    @Test public void capsuleOwnerWinsAndCardUpdatesDoNotRetargetIt() {
        ArtworkSessionState<String, String> s = new ArtworkSessionState<>();
        s.card("card-a", "song-a");
        s.capsule(true, "player-b", "token-b", "song-b");
        ArtworkSessionState.Snapshot<String, String> b = s.snapshot();
        s.card("card-c", "song-c");
        assertTrue(s.isCurrent(b));
        assertFalse(s.isCardCurrent(b));
        assertEquals("player-b", s.snapshot().owner);
        assertEquals("card-c", s.snapshot().cardToken);
        s.capsule(false, null, null, null);
        assertFalse(s.isCurrent(b));
        assertEquals("card-c", s.snapshot().cardToken);
    }

    @Test public void sameOwnerSongChangeAndCardChangeInvalidatePriorResults() {
        ArtworkSessionState<String, String> s = new ArtworkSessionState<>();
        s.card("card-a", "song-a");
        ArtworkSessionState.Snapshot<String, String> oldCard = s.snapshot();
        s.card("card-a", "song-b");
        assertFalse(s.isCurrent(oldCard));
        s.capsule(true, "player", "token", "song-a");
        ArtworkSessionState.Snapshot<String, String> oldSong = s.snapshot();
        s.capsule(true, "other controller object", "token", "song-b");
        assertFalse(s.isCurrent(oldSong));
        ArtworkSessionState.Snapshot<String, String> newSong = s.snapshot();
        s.capsule(true, "another instance", "token", "song-b");
        assertTrue(s.isCurrent(newSong));
    }

    @Test public void activeMissingCapsuleOwnerIsNotOrdinaryCardMode() {
        ArtworkSessionState<String, String> s = new ArtworkSessionState<>();
        s.card("card", "song");
        s.capsule(true, null, null, "");
        assertTrue(s.snapshot().capsule);
        assertNull(s.snapshot().owner);
    }
    @Test public void fallbackAndCardAbaInvalidateOldResults() {
        ArtworkSessionState<String, String> s = new ArtworkSessionState<>();
        s.fallback("player-a", "a", "song-a");
        ArtworkSessionState.Snapshot<String, String> oldFallback = s.snapshot();
        s.fallback("player-b", "b", "song-b");
        assertFalse(s.isCurrent(oldFallback));
        s.capsule(true, "player-b", "b", "song-b");
        s.card("b", "song-b");
        ArtworkSessionState.Snapshot<String, String> oldCard = s.snapshot();
        s.card("c", "song-c");
        s.card("b", "song-b");
        assertTrue(s.isCurrent(oldCard)); // The selected capsule owner did not change.
        assertFalse(s.isCardCurrent(oldCard)); // But this is not the same card instance/state.
    }
    @Test public void missingTitleNeverProvesCardIsTheCurrentSong() {
        assertFalse(ArtworkSessionState.hasTrackTitle(null));
        assertFalse(ArtworkSessionState.hasTrackTitle("pkg"));
        assertFalse(ArtworkSessionState.hasTrackTitle("pkg||artist"));
        assertFalse(ArtworkSessionState.hasTrackTitle("pkg|null|artist"));
        assertTrue(ArtworkSessionState.hasTrackTitle("pkg|song|artist|album"));
    }
    @Test public void sameCardFallbackMetadataDoesNotCancelAnInFlightCover() {
        ArtworkSessionState<String, String> s = new ArtworkSessionState<>();
        s.card("token", "pkg|song|artist");
        s.fallback("card", "token", "pkg|song|artist");
        ArtworkSessionState.Snapshot<String, String> running = s.snapshot();
        s.fallback("card", "token", "pkg|song|artist|album");
        assertTrue(s.isCurrent(running));
        s.fallback("other", "other-token", "pkg2|song|artist");
        assertFalse(s.isCurrent(running));
    }
    @Test public void sameSongInAnotherSessionInvalidatesOldArtwork() {
        ArtworkSessionState<String, String> s = new ArtworkSessionState<>();
        s.capsule(true, "player-a", "session-a", "pkg|same song|artist");
        ArtworkSessionState.Snapshot<String, String> old = s.snapshot();
        s.capsule(true, "player-b", "session-b", "pkg|same song|artist");
        assertFalse(s.isCurrent(old));
        assertEquals("session-b", s.snapshot().ownerToken);
    }
    @Test public void playingFallbackCannotUsePausedPlayersCardArtwork() {
        ArtworkSessionState<String, String> s = new ArtworkSessionState<>();
        s.card("paused-a", "pkg-a|song|artist");
        s.fallback("playing-b", "playing-b", "pkg-b|song|artist");
        assertTrue(ArtworkSessionState.preferFallback(s.snapshot()));
        assertFalse(ArtworkSessionState.ordinaryCardMatches(s.snapshot(), "paused-a"));
        s.card("playing-b", "pkg-b|song|artist");
        assertFalse(ArtworkSessionState.preferFallback(s.snapshot()));
        assertTrue(ArtworkSessionState.ordinaryCardMatches(s.snapshot(), "playing-b"));
        assertFalse(ArtworkSessionState.ordinaryCardMatches(s.snapshot(), null));
    }
}
