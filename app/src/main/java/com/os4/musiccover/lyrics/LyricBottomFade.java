package com.os4.musiccover.lyrics;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;

/** Bottom reading-edge feather applied to glyphs at their normal size, before blurring. */
final class LyricBottomFade {
    private final LinearGradient erase = new LinearGradient(0f, 0f, 0f, 1f,
            Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
    private final Matrix matrix = new Matrix();
    private final Rect clip = new Rect();
    private final Paint paint = new Paint();
    private float start = Float.NaN, span;

    LyricBottomFade() {
        paint.setShader(erase);
        paint.setBlendMode(BlendMode.DST_OUT);
    }

    int beginRow(Canvas c, float width, float top, float bottom, float floor, float feather) {
        float from = floor - feather;
        if (!Float.isFinite(floor) || !(feather > 0f) || bottom <= from || !c.getClipBounds(clip))
            return -1;
        if (start != from || span != feather) {
            start = from;
            span = feather;
            matrix.setScale(1f, feather);
            matrix.postTranslate(0f, start);
            erase.setLocalMatrix(matrix);
        }
        // On the final line the canvas is translated to the layout's origin. Include
        // overhanging glyphs to its left as well as both sides of an aligned duet.
        return c.saveLayer(Math.min(0f, clip.left), top, Math.max(width, clip.right), bottom, null);
    }

    void endRow(Canvas c, float width, float bottom, int layer) {
        if (layer < 0) return;
        try {
            // CLAMP erases the remainder below the soft edge. No translucent line is cut
            // halfway by a moving rectangle and no ink underneath the controls is revealed.
            c.drawRect(Math.min(0f, clip.left), start, Math.max(width, clip.right), bottom, paint);
        } finally { c.restoreToCount(layer); }
    }
}
