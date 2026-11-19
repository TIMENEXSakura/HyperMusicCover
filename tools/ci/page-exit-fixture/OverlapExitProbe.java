package com.os4.musiccover.lyrics;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.util.Arrays;
import java.util.List;

/** Render the settled lyric poses immediately around a shorter group's end and the terminal wipe. */
final class OverlapExitProbe {
    static final int[] TIMES = {3999, 4000, 5200, 6300, 9499, 9660, 10140};
    private static final int TOP = 68, BOTTOM = 696, GAP = 12;
    private final List<LyricLine> lines = Arrays.asList(line("Sustain", 0, 7500),
            line("Second", 1200, 2900), line("Third", 3000, 8700), line("Final", 6000, 9500));
    private final StaticLayout[][] voices = new StaticLayout[lines.size()][];
    private final float[] heights = new float[lines.size()], base = new float[lines.size()];
    private final Paint eraser = new Paint();
    private final LyricClipFade topFade = new LyricClipFade();

    OverlapExitProbe() {
        lines.get(1).addBackground(line("Harmony A", 2000, 3500));
        lines.get(1).addBackground(line("Harmony B", 2400, 4000));
        for (int i = 0; i < lines.size(); i++) {
            LyricLine line = lines.get(i);
            LyricLine[] backing = line.backgrounds();
            voices[i] = new StaticLayout[1 + backing.length];
            for (int k = 0; k < voices[i].length; k++) {
                TextPaint ink = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                ink.setColor(i == 1 ? 0xffffffff : i == lines.size() - 1 ? 0xfffd88cc : 0xff879fba);
                ink.setTextSize(k == 0 ? 32 : 24);
                String words = k == 0 ? line.text : backing[k - 1].text;
                StaticLayout layout = StaticLayout.Builder.obtain(words, 0, words.length(), ink, 195)
                        .setIncludePad(false).build();
                voices[i][k] = layout;
                heights[i] += layout.getHeight();
            }
            if (i > 0) base[i] = base[i - 1] + heights[i - 1] + GAP;
        }
        eraser.setBlendMode(BlendMode.DST_OUT);
    }

    private static LyricLine line(String text, int start, int end) {
        return new LyricLine(text, null, start, end, false, null, null, null);
    }

    void draw(Canvas canvas, int frame, int width) {
        int ms = TIMES[frame], last = lines.size() - 1;
        int latest = last;
        while (latest > 0 && lines.get(latest).start > ms) latest--;
        LyricFocus singing = new LyricFocus();
        singing.update(lines, ms, 0, latest, latest);
        int focus = singing.last;
        float band = BOTTOM - TOP;
        float anchor = TOP + .30f * band + LyricBandGeometry.singingAnchorFix(band,
                singing.height(base, heights), heights[focus], base[focus] - base[singing.first], 0f, .30f);
        float origin = anchor - base[focus];
        int end = LyricFocus.songEnd(lines);
        LyricFinalHold hold = new LyricFinalHold();
        hold.step(ms, lines.get(last).start, end, Math.max(0, ms - end) / 1000f, false);
        float push = hold.precedingScroll(origin + base[last - 1] + heights[last - 1], TOP);
        int clipped = canvas.save();
        canvas.clipRect(0, TOP, width, BOTTOM);
        topFade.place(TOP, 24f);
        for (int i = 0; i < lines.size(); i++) {
            float progress = hold.rowProgress(i, lines.size());
            if (progress >= 1f) continue;
            int y = Math.round(origin + base[i] - (i == last ? 0f : push));
            int layer = topFade.beginRow(canvas, width, y, y + heights[i]);
            int save = canvas.save();
            canvas.translate(i == 1 ? 30 : 310, y);
            for (StaticLayout layout : voices[i]) {
                if (progress > 0f) {
                    int maskLayer = canvas.saveLayer(0f, 0f, layout.getWidth(), layout.getHeight(), null);
                    layout.draw(canvas);
                    LyricExitMask wipe = new LyricExitMask(0f, layout.getHeight());
                    wipe.add(layout, null, 0f, 0f, 0f);
                    wipe.erase(canvas, eraser, progress, 1f);
                    canvas.restoreToCount(maskLayer);
                } else layout.draw(canvas);
                canvas.translate(0f, layout.getHeight());
            }
            canvas.restoreToCount(save);
            topFade.endRow(canvas, width, layer);
        }
        canvas.restoreToCount(clipped);
    }
}
