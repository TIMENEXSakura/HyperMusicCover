package com.os4.musiccover.lyrics;

import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;

/** Applies the current blur radius to the glyph display list at the window's resolution. */
public final class LyricRenderBlur {
    private final RenderNode node = new RenderNode("Lyric row");
    private float radius = -1f;

    public Canvas begin(int left, int top, int right, int bottom, float pixels) {
        if (radius != pixels) {
            radius = pixels;
            node.setRenderEffect(pixels > 0f
                    ? RenderEffect.createBlurEffect(pixels, pixels, Shader.TileMode.DECAL) : null);
        }
        node.setPosition(left, top, right, bottom);
        Canvas canvas = node.beginRecording(right - left, bottom - top);
        canvas.translate(-left, -top);
        return canvas;
    }

    public void finish(Canvas canvas) {
        node.endRecording();
        canvas.drawRenderNode(node);
    }
}
