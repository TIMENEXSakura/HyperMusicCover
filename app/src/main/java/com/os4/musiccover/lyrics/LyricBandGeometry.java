package com.os4.musiccover.lyrics;

/** Screen-space obstacles for lyrics opened while the native media card is folded. */
public final class LyricBandGeometry {
    private LyricBandGeometry() {}

    /** The singing group sits in the upper part of the configured reading band. */
    static final float SINGING_FRACTION = 0.30f;

    static float singingTop(float top, float bottom, float groupHeight) {
        return singingTop(top, bottom, groupHeight, 0f);
    }

    /** A music card uses the middle; the folded capsule places singing slightly higher. */
    static float singingFraction(float cardProgress) {
        float p = Float.isFinite(cardProgress) ? Math.max(0f, Math.min(1f, cardProgress)) : 0f;
        return SINGING_FRACTION + (0.5f - SINGING_FRACTION) * p;
    }

    static float singingTop(float top, float bottom, float groupHeight, float cardProgress) {
        return top + (bottom - top) * singingFraction(cardProgress) - groupHeight / 2f;
    }

    /** Align the interlude slot's centre with the current singing position. */
    static float interludeAnchorFix(float bandHeight, float slotHeight, float cardProgress,
                                   float positionDelta) {
        return singingTop(0f, bandHeight, slotHeight, cardProgress)
                - SINGING_FRACTION * bandHeight - positionDelta;
    }

    /** Use the same progress as the music surface, including a finger reversing its drag. */
    static float morphEdge(float mini, float card, float cardProgress) {
        float p = Float.isFinite(cardProgress) ? Math.max(0f, Math.min(1f, cardProgress)) : 0f;
        return mini + (card - mini) * p;
    }

    /** Preserve readable overflow: a tall wrapped row cannot fit between both fade edges. */
    static float rowAlpha(float top, float bottom, float clipTop, float clipBottom, float fade) {
        if (bottom <= clipTop || top >= clipBottom || clipBottom <= clipTop) return 0f;
        if (fade <= 0f) return 1f;
        float distance = bottom - top > clipBottom - clipTop - 2f * fade
                ? Math.min(bottom - clipTop, clipBottom - top)
                : Math.min(top - clipTop, clipBottom - bottom);
        return Math.max(0f, Math.min(1f, distance / fade));
    }

    /** Keep unfinished, already-started rows opaque as they move through the top edge. */
    static float rowAlphaWhilePlaying(float top, float bottom, float clipTop, float clipBottom,
                                      float fade, boolean unfinished) {
        if (unfinished) {
            if (bottom <= clipTop || top >= clipBottom || clipBottom <= clipTop) return 0f;
            return fade <= 0f ? 1f : Math.max(0f, Math.min(1f, (clipBottom - top) / fade));
        }
        return rowAlpha(top, bottom, clipTop, clipBottom, fade);
    }

    /** One measured clock destination, kept across phase changes but never across old hosts. */
    public static final class ClockTarget {
        private java.lang.ref.WeakReference<Object> owner = new java.lang.ref.WeakReference<>(null);
        private int width, height;
        private float bottom = Float.NaN;

        public void clear() { owner.clear(); bottom = Float.NaN; }

        public void record(Object target, int screenWidth, int screenHeight, float value) {
            if (target == null || screenWidth <= 0 || screenHeight <= 0 || !Float.isFinite(value)) {
                clear();
                return;
            }
            if (owner.get() != target) owner = new java.lang.ref.WeakReference<>(target);
            width = screenWidth;
            height = screenHeight;
            bottom = value;
        }

        public float read(Object target, int screenWidth, int screenHeight) {
            return target != null && owner.get() == target && width == screenWidth && height == screenHeight
                    ? bottom : Float.NaN;
        }
    }

    /** A page keeps its first usable reading band; only a new hidden page or real resize reflows it. */
    public static final class FixedBand {
        private long request = Long.MIN_VALUE;
        private int width, height;
        private Object style;
        private float top = Float.NaN, bottom = Float.NaN;
        public void clear() { top = bottom = Float.NaN; style = null; }
        public boolean update(long nextRequest, int w, int h, Object nextStyle, boolean visible,
                              float candidateTop, float candidateBottom) {
            if (width != w || height != h || style != nextStyle || request != nextRequest && !visible)
                clear();
            request = nextRequest; width = w; height = h; style = nextStyle;
            if (!Float.isFinite(top) && w > 0 && h > 0 && Float.isFinite(candidateTop)
                    && Float.isFinite(candidateBottom) && candidateBottom > candidateTop) {
                top = candidateTop; bottom = candidateBottom;
            }
            return Float.isFinite(top);
        }
        public float top() { return top; }
        public float bottom() { return bottom; }
    }

    /** Stable layout reference for one displayed page, independent of the visible clip. */
    static final class Reference {
        private long request = Long.MIN_VALUE;
        private float fraction = Float.NaN;

        float floor(long newRequest, float screenHeight, float restingCardTop) {
            if (!Float.isFinite(screenHeight) || screenHeight <= 0f) return Float.NaN;
            if (request != newRequest || !Float.isFinite(fraction)) {
                if (!Float.isFinite(restingCardTop) || restingCardTop <= 0f
                        || restingCardTop >= screenHeight) return Float.NaN;
                fraction = restingCardTop / screenHeight;
                request = newRequest;
            }
            return fraction * screenHeight;
        }
    }

    /**
     * Extra room extends downward only. A rising card changes this clip, never the layout
     * band's anchor, focus fit, scroll target or spring. Preserve the configured bottom gap.
     */
    static float visibleBottom(float layoutTop, float layoutBottom, float layoutFloor,
                               float obstacleFloor) {
        if (!Float.isFinite(layoutTop) || !Float.isFinite(layoutBottom)
                || !Float.isFinite(layoutFloor) || !Float.isFinite(obstacleFloor)
                || layoutBottom <= layoutTop || layoutFloor < layoutBottom) return layoutBottom;
        return Math.max(layoutTop, obstacleFloor - (layoutFloor - layoutBottom));
    }

    /** A screen-sized band is only safe with a measured, folded bottom capsule. */
    static boolean useScreenCapsuleBand(boolean independent, boolean capsule, boolean requested,
                                        boolean enabled, boolean keptMini, boolean navigation,
                                        boolean transitioning, float obstacle, float screenHeight) {
        return independent && capsule && requested && enabled && keptMini && !navigation
                && !transitioning && Float.isFinite(obstacle) && Float.isFinite(screenHeight)
                && screenHeight > 0f && obstacle >= screenHeight * 0.5f && obstacle < screenHeight;
    }

    /** Never use the travelling full-size clock as a capsule's temporary layout anchor. */
    static float layoutClock(boolean capsule, float drawnClock, float coverClock) {
        return capsule ? coverClock : drawnClock;
    }

    /** Invisible rows have no old screen position to preserve with a scroll rebase. */
    static boolean initialPlacement(int focus, boolean startingPage) {
        return focus < 0 || startingPage;
    }

    static boolean readyToPlace(boolean bandReady, float visible, float targetVisibility) {
        return bandReady && (visible > 0f || targetVisibility > 0f);
    }

    static void snapScroll(float destination, float[] scroll, float[] aim, float[] nextAim, float[] velocity) {
        java.util.Arrays.fill(scroll, destination);
        java.util.Arrays.fill(aim, destination);
        java.util.Arrays.fill(nextAim, destination);
        java.util.Arrays.fill(velocity, 0f);
    }

    /**
     * Reading positions stay in the fixed band, but independent lyrics may travel above that
     * band until the screen's safe top. The clock-following mode retains its occlusion.
     * All coordinates are in screen pixels before conversion into the lyric view.
     */
    static float visibleTop(boolean independent, float layoutTop, float layoutClock,
                            float drawnClock, float safeScreenTop) {
        if (!Float.isFinite(layoutTop)) return layoutTop;
        if (independent) {
            float safe = Float.isFinite(safeScreenTop) ? safeScreenTop : 0f;
            return Math.min(layoutTop, safe);
        }
        if (!Float.isFinite(layoutClock) || !Float.isFinite(drawnClock)) return layoutTop;
        return Math.max(layoutTop, drawnClock + Math.max(0f, layoutTop - layoutClock));
    }


    /** Match the outer canvas translation/scale when testing a row against a fixed clip. */
    static float presentedY(float y, float center, float offset, float scale) {
        if (!Float.isFinite(y) || !Float.isFinite(center) || !Float.isFinite(offset)
                || !Float.isFinite(scale) || scale <= 0f) return y;
        return offset + center + (y - center) * scale;
    }

    /** Keep the capsule's geometry through panel handoffs and any closing container morph. */
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
