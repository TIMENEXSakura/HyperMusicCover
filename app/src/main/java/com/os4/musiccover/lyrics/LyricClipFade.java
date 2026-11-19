package com.os4.musiccover.lyrics;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;

/** Softens just the lyric ink approaching the viewport's upper cut. */
public final class LyricClipFade {
    private final LinearGradient erase = new LinearGradient(0f, 0f, 0f, 1f,
            Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP);
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint();
    private float cutoff, end;

    public LyricClipFade() {
        paint.setShader(erase);
        paint.setBlendMode(BlendMode.DST_OUT);
    }

    public void place(float top, float feather) {
        if (top == cutoff && top + feather == end) return;
        cutoff = top;
        end = top + feather;
        matrix.setScale(1f, feather);
        matrix.postTranslate(0f, top);
        erase.setLocalMatrix(matrix);
    }

    /** Only rows intersecting the feather need an offscreen layer. */
    public int beginRow(Canvas canvas, float width, float top, float bottom) {
        if (end <= cutoff || top >= end || bottom <= cutoff) return -1;
        return canvas.saveLayer(0f, Math.max(cutoff, top), width, bottom, null);
    }

    public void endRow(Canvas canvas, float width, int layer) {
        if (layer < 0) return;
        canvas.drawRect(0f, cutoff, width, end, paint);
        canvas.restoreToCount(layer);
    }
}
