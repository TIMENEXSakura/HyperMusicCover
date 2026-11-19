package com.os4.musiccover.lyrics;

/** Smoothly joins a late music morph or its late endpoint measurements to the last drawn band. */
final class LyricNavigationHandoff {
    private static final float TAU_SECONDS = 0.12f;
    private static final float PROGRESS_EPSILON = 0.002f, PIXEL_EPSILON = 0.25f;
    private final float[] offset = new float[4];
    private float progressOffset;
    private boolean moving;

    void begin(float currentProgress, float actualProgress) {
        progressOffset = currentProgress - actualProgress;
        moving = true;
    }

    float progress(float actualProgress) {
        return Math.max(0f, Math.min(1f, actualProgress + progressOffset));
    }

    /** A fallback already uses displayed progress and must not add the offset again. */
    float progress(boolean actualMorph, float input) {
        return actualMorph ? progress(input) : input;
    }

    void capture(float top, float bottom, float clipTop, float clipBottom,
                 float targetTop, float targetBottom, float targetClipTop, float targetClipBottom) {
        offset[0] = top - targetTop;
        offset[1] = bottom - targetBottom;
        offset[2] = clipTop - targetClipTop;
        offset[3] = clipBottom - targetClipBottom;
    }

    void advance(float dt) {
        if (!moving || dt <= 0f) return;
        float factor = (float) Math.exp(-dt / TAU_SECONDS);
        progressOffset *= factor;
        if (Math.abs(progressOffset) < PROGRESS_EPSILON) progressOffset = 0f;
        boolean pending = progressOffset != 0f;
        for (int i = 0; i < offset.length; i++) {
            offset[i] *= factor;
            if (Math.abs(offset[i]) < PIXEL_EPSILON) offset[i] = 0f;
            pending |= offset[i] != 0f;
        }
        moving = pending;
    }

    void apply(float[] band) {
        for (int i = 0; i < offset.length; i++) band[i] += offset[i];
    }

    boolean moving() { return moving; }
    void reset() {
        java.util.Arrays.fill(offset, 0f);
        progressOffset = 0f;
        moving = false;
    }
}
