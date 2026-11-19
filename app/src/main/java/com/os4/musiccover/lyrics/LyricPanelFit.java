package com.os4.musiccover.lyrics;

import android.graphics.Canvas;

/** The same text, translations, voices, interlude dots and blur share one uniform transform. */
final class LyricPanelFit {
    static void apply(Canvas canvas, float pivotX, float pivotY, float scale) {
        if (scale != 1f) canvas.scale(scale, scale, pivotX, pivotY);
    }

    static float sourceY(float screenY, float pivotY, float scale) {
        return pivotY + (screenY - pivotY) / scale;
    }
}
