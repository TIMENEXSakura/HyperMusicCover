package com.os4.musiccover.lyrics;

/** Screen-space obstacles for lyrics opened while the native media card is folded. */
public final class LyricBandGeometry {
    private LyricBandGeometry() {}

    /** A committed swipe clears the request before its moving container reaches the card. */
    public static boolean usesCapsuleSpace(boolean requested, boolean keptMini,
                                          boolean musicMoving, boolean previouslyUsed) {
        return requested || keptMini || musicMoving && previouslyUsed;
    }

    /** Do not expand into a navigation bar/cutout, including while a capsule is being rebuilt. */
    public static float usableBottom(float screenHeight, float bottomInset) {
        if (!Float.isFinite(screenHeight) || screenHeight <= 0f) return Float.NaN;
        if (!Float.isFinite(bottomInset) || bottomInset < 0f || bottomInset >= screenHeight)
            return Float.NaN;
        return screenHeight - bottomInset;
    }

    /**
     * Accumulates the highest actually drawn obstacle. NaN means no measured geometry; callers
     * must then retain the existing card fallback instead of inventing a screen-sized band.
     * In particular, a hidden native card's still-valid layout rectangle is not an obstacle.
     */
    public static float include(float nearest, boolean drawn, float left, float top,
                                float width, float height, float screenWidth, float bottom) {
        if (!drawn || !Float.isFinite(left) || !Float.isFinite(top)
                || !Float.isFinite(width) || !Float.isFinite(height)
                || !Float.isFinite(screenWidth) || !Float.isFinite(bottom)
                || width <= 0f || height <= 0f || screenWidth <= 0f || bottom <= 0f
                || !Float.isFinite(left + width) || !Float.isFinite(top + height)
                || left >= screenWidth || left + width <= 0f
                || top >= bottom || top + height <= 0f) return nearest;
        float edge = Math.max(0f, top);
        return Float.isFinite(nearest) ? Math.min(nearest, edge) : edge;
    }
}
