package com.os4.musiccover.lyrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricSessionIdentityTest {
    @Test public void sameSongInAnotherSessionInvalidatesPreviousWork() {
        assertTrue(LyricSessionIdentity.changed("session-a", "session-b"));
        assertTrue(LyricSessionIdentity.changed("session-a", null));
        assertFalse(LyricSessionIdentity.changed("session-a", new String("session-a")));
        assertFalse(LyricSessionIdentity.changed(null, "session-b"));
    }
}
