package com.os4.musiccover.immersive;

import org.junit.Test;
import static org.junit.Assert.*;

public class ImmersiveOpenTimingTest {
    @Test public void navigationOpensSoonerWithoutChangingExitsOrOtherScenes() {
        assertEquals(240L, ImmersiveOpenTiming.duration(370, 1f, true, true));
        assertEquals(370L, ImmersiveOpenTiming.duration(370, 1f, true, false));
        assertEquals(370L, ImmersiveOpenTiming.duration(370, 1f, false, true));
        assertEquals(175L, ImmersiveOpenTiming.duration(175, 1f, true, true));
    }

    @Test public void reversalKeepsRemainingDistanceAndSwapDelayIsBounded() {
        assertEquals(185L, ImmersiveOpenTiming.duration(370, .5f, true, true));
        assertEquals(185L, ImmersiveOpenTiming.duration(370, .5f, true, false));
        assertEquals(1L, ImmersiveOpenTiming.duration(370, 0f, true, true));
        assertEquals(40f / 240f, ImmersiveOpenTiming.swapDelay(.4f, 240, true), .00001f);
        assertEquals(.4f, ImmersiveOpenTiming.swapDelay(.4f, 370, false), 0f);
    }
}
