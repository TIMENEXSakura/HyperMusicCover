package com.os4.musiccover.immersive;

/** Navigation opens quickly while exits and other scenes keep their existing timing. */
final class ImmersiveOpenTiming {
    private static final long NAVIGATION_OPEN_MS = 240L;
    private static final long NAVIGATION_SWAP_DELAY_MS = 40L;

    static long duration(long fullDuration, float distance, boolean navigation, boolean opening) {
        long duration = Math.max(1L, Math.round(fullDuration * Math.abs(distance)));
        return navigation && opening ? Math.min(duration, NAVIGATION_OPEN_MS) : duration;
    }

    static float swapDelay(float original, long duration, boolean navigation) {
        return navigation ? Math.min(original, NAVIGATION_SWAP_DELAY_MS / (float) duration) : original;
    }
}
