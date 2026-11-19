package com.os4.musiccover.island

import org.junit.Assert.assertEquals
import org.junit.Test
import com.os4.musiccover.island.CapsuleTapPolicy.Action

class CapsuleTapPolicyTest {
    @Test fun specialTypesWinBeforeGenericScenesAndNotifications() {
        assertEquals(Action.MUSIC, CapsuleTapPolicy.resolve(true, false, true, true, true))
        assertEquals(Action.NAVIGATION, CapsuleTapPolicy.resolve(false, false, true, true, true))
        assertEquals(Action.CLOCK, CapsuleTapPolicy.resolve(false, false, false, true, true))
        assertEquals(Action.SCENE, CapsuleTapPolicy.resolve(false, false, false, false, true))
        assertEquals(Action.NOTIFICATION, CapsuleTapPolicy.resolve(false, false, false, false, false))
    }

    @Test fun unavailableClockEntryStillOwnsTheTap() {
        assertEquals(Action.CLOCK, CapsuleTapPolicy.resolve(false, false, false, true, false))
    }

    @Test fun hiddenNotificationContentKeepsOemAuthenticationRoute() {
        assertEquals(Action.NOTIFICATION, CapsuleTapPolicy.resolve(false, true, true, true, true))
    }
}
