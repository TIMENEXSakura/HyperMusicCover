package com.os4.musiccover.lyrics;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.RenderNode;

/** Reveals the backdrop from the top of the lyric band without transforming its contents. */
public final class LyricPageExitMask {
    public interface Content { void draw(Canvas canvas); }

    private final LinearGradient alpha = new LinearGradient(0, 0, 0, 1,
            Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
    private final Matrix matrix = new Matrix();
    private final Paint mask = new Paint();

    public LyricPageExitMask() {
        mask.setShader(alpha);
        mask.setBlendMode(BlendMode.DST_IN);
    }

    void drawFrame(Canvas canvas, float top, float bottom, LyricPageExit exit, Content snapshot) {
        // The captured page already has its live edge treatment. A direct tap must not
        // add a moving cut, and the normal exit still starts at its original viewport.
        if (exit.capsuleFade() && exit.progress() == 0f) snapshot.draw(canvas);
        else draw(canvas, top, bottom, exit.progress(), snapshot);
    }

    static void drawSnapshot(Canvas canvas, RenderNode recorded, float opacity, Content live) {
        if (canvas.isHardwareAccelerated() && recorded != null && recorded.hasDisplayList()) {
            recorded.setAlpha(opacity);
            canvas.drawRenderNode(recorded);
        } else if (opacity < 1f) {
            int saved = canvas.saveLayerAlpha(null, Math.round(255f * opacity));
            try { live.draw(canvas); }
            finally { canvas.restoreToCount(saved); }
        } else live.draw(canvas);
    }

    public void draw(Canvas canvas, float top, float bottom, float progress, Content content) {
        if (bottom <= top || progress >= 1f) return;
        if (progress <= 0f) {
            int saved = canvas.save();
            try {
                if (canvas.clipRect(0f, top, canvas.getWidth(), bottom)) content.draw(canvas);
            } finally { canvas.restoreToCount(saved); }
            return;
        }
        float height = bottom - top;
        float feather = height * 0.22f;
        float edge = top - feather + progress * (height + feather);
        matrix.setScale(1f, feather);
        matrix.postTranslate(0f, edge);
        alpha.setLocalMatrix(matrix);
        float visibleTop = Math.max(top, edge);
        // The opaque tail needs only a clip. Allocate a buffer for the feather alone
        // when replaying the hardware recording, so its size stays bounded during a close.
        float fadeBottom = canvas.isHardwareAccelerated() ? Math.min(bottom, edge + feather) : bottom;
        if (fadeBottom < bottom) {
            int saved = canvas.save();
            try {
                if (canvas.clipRect(0f, fadeBottom, canvas.getWidth(), bottom)) content.draw(canvas);
            } finally {
                canvas.restoreToCount(saved);
            }
        }
        if (fadeBottom <= visibleTop) return;
        int saved = canvas.save();
        try {
            if (!canvas.clipRect(0f, visibleTop, canvas.getWidth(), fadeBottom)) return;
            canvas.saveLayer(0f, visibleTop, canvas.getWidth(), fadeBottom, null);
            content.draw(canvas);
            canvas.drawRect(0f, visibleTop, canvas.getWidth(), fadeBottom, mask);
        } finally {
            canvas.restoreToCount(saved);
        }
    }
}
