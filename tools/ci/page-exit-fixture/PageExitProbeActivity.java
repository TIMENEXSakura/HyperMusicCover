package com.os4.musiccover.lyrics;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.PixelCopy;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Only copied into the debug variant on a disposable cloud runner. */
public final class PageExitProbeActivity extends Activity {
    private static final int WIDTH = 540, HEIGHT = 760, TOP = 68, BOTTOM = 696;
    private static final int[] CENTRES = {122, 227, 379, 532, 633};
    private static final int[] CUTS = {TOP, 177, 299, 458, 586, BOTTOM};
    private static final int FIRST = -2, LAST = 8;
    private static final int CLIP_FADE = LAST + 1;
    private static final int SOFT_BLUR = LAST + 2;
    private static final int WIDE_BLUR = LAST + 3;
    private static final int CLIP_FEATHER = 96;
    private int phase = FIRST, retries;
    private File output;
    private Page page;
    private boolean stopped;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        output = new File(getFilesDir(), "page-exit-frames");
        if (!output.isDirectory() && !output.mkdirs()) throw new IllegalStateException("Cannot create frame directory");
        page = new Page(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff382340);
        root.addView(page, new FrameLayout.LayoutParams(WIDTH, HEIGHT, Gravity.CENTER));
        setContentView(root);
        page.post(this::scheduleFrame);
    }

    private void scheduleFrame() {
        if (stopped) return;
        try {
            if (!page.isLaidOut() || !page.isAttachedToWindow()) {
                page.postDelayed(this::scheduleFrame, 32);
                return;
            }
            if (page.getWidth() != WIDTH || page.getHeight() != HEIGHT || !page.isHardwareAccelerated()) {
                throw new IllegalStateException("Hardware window or requested page dimensions unavailable");
            }
            final int requested = phase;
            page.getViewTreeObserver().registerFrameCommitCallback(() -> capture(requested));
            page.invalidate();
        } catch (Exception e) {
            fail(e);
        }
    }

    private void capture(int requested) {
        if (stopped || requested != phase) return;
        try {
            int[] at = new int[2];
            page.getLocationInWindow(at);
            Rect area = new Rect(at[0], at[1], at[0] + WIDTH, at[1] + HEIGHT);
            Bitmap frame = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            PixelCopy.request(getWindow(), area, frame, result -> {
                try {
                    if (result != PixelCopy.SUCCESS) {
                        if (++retries > 3) throw new IllegalStateException("Window PixelCopy failed " + result);
                        page.postDelayed(this::scheduleFrame, 60);
                        return;
                    }
                    if (requested != page.lastPainted) throw new IllegalStateException("Unexpected committed frame");
                    String name = requested == FIRST ? "background" : requested == -1 ? "reference"
                            : requested == CLIP_FADE ? "clip-fade"
                            : requested == SOFT_BLUR ? "blur-soft"
                            : requested == WIDE_BLUR ? "blur-wide"
                            : String.format(Locale.ROOT, "step-%02d", requested);
                    try (FileOutputStream out = new FileOutputStream(new File(output, name + ".png"))) {
                        if (!frame.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IllegalStateException("PNG write failed");
                    }
                    retries = 0;
                    if (phase == WIDE_BLUR) {
                        JSONObject report = new JSONObject();
                        report.put("width", WIDTH).put("height", HEIGHT).put("hardwareCanvas", page.hardwareCanvas);
                        report.put("clipTop", TOP).put("clipBottom", BOTTOM).put("steps", LAST);
                        report.put("clipFeather", CLIP_FEATHER);
                        report.put("cuts", new JSONArray(CUTS)).put("glyphs", new JSONArray(page.glyphCounts));
                        report.put("snapshotRecordings", page.recordings);
                        write("capture.json", report.toString(2));
                        stopped = true;
                    } else {
                        phase++;
                        page.post(this::scheduleFrame);
                    }
                } catch (Exception e) {
                    fail(e);
                } finally {
                    frame.recycle();
                }
            }, new Handler(Looper.getMainLooper()));
        } catch (Exception e) {
            fail(e);
        }
    }

    private void fail(Exception error) {
        stopped = true;
        android.util.Log.e("PageExitProbe", "Capture failed", error);
        try { write("error.txt", android.util.Log.getStackTraceString(error)); }
        catch (Exception ignored) { android.util.Log.e("PageExitProbe", "Could not save failure", ignored); }
    }

    private void write(String name, String body) throws Exception {
        File pending = new File(output, name + ".tmp");
        try (FileOutputStream out = new FileOutputStream(pending)) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        if (!pending.renameTo(new File(output, name))) throw new IllegalStateException("Could not publish " + name);
    }

    private final class Page extends View {
        private final LyricPageExitMask mask = new LyricPageExitMask();
        private final LyricClipFade clipFade = new LyricClipFade();
        private final LyricRenderBlur liveBlur = new LyricRenderBlur();
        private final RenderNode glyphs = new RenderNode("frozen Android lyric layouts");
        private final Paint backdrop = new Paint();
        private final int[] glyphCounts = new int[CENTRES.length];
        private int recordings, lastPainted = Integer.MIN_VALUE;
        private boolean hardwareCanvas;

        Page(Context context) { super(context); }

        @Override protected void onDraw(Canvas c) {
            hardwareCanvas = c.isHardwareAccelerated();
            c.drawColor(0xff192338);
            backdrop.setColor(0xff27354b);
            c.drawRect(236, 0, 308, HEIGHT, backdrop);
            backdrop.setColor(0xff596074);
            c.drawRect(0, TOP - 12, WIDTH, TOP - 7, backdrop);
            c.drawRect(0, BOTTOM + 7, WIDTH, BOTTOM + 12, backdrop);
            // Android can drop an undisplayed node. The background and clip-only phases
            // never read it, so they must not record an extra glyph snapshot.
            if (phase >= -1 && phase < LAST && !glyphs.hasDisplayList()) recordPage();
            if (phase == -1) c.drawRenderNode(glyphs);
            else if (phase == CLIP_FADE) {
                int saved = c.save();
                c.clipRect(0, TOP, WIDTH, BOTTOM);
                clipFade.place(TOP, CLIP_FEATHER);
                int layer = clipFade.beginRow(c, WIDTH, TOP - 40, TOP + 160);
                backdrop.setColor(Color.WHITE);
                c.drawRect(110, TOP - 40, 204, TOP + 160, backdrop);
                clipFade.endRow(c, WIDTH, layer);
                c.restoreToCount(saved);
            } else if (phase == SOFT_BLUR || phase == WIDE_BLUR) {
                Canvas layer = liveBlur.begin(70, 236, 222, 412, phase == SOFT_BLUR ? 2f : 12f);
                backdrop.setColor(Color.WHITE);
                layer.drawRect(140, 280, 172, 360, backdrop);
                liveBlur.finish(c);
            } else if (phase >= 0) {
                mask.draw(c, TOP, BOTTOM, phase / (float) LAST, canvas -> canvas.drawRenderNode(glyphs));
            }
            lastPainted = phase;
        }

        private void recordPage() {
            glyphs.setPosition(0, 0, WIDTH, HEIGHT);
            RecordingCanvas c = glyphs.beginRecording();
            try {
                String[] content = {"晚风穿过长街", "A quiet echo", "山的另一边\n仍有人回应", "A second singer", "落日以后"};
                int[] sizes = {47, 26, 55, 38, 50};
                int[] widths = {WIDTH - 56, WIDTH - 86, WIDTH - 72, WIDTH - 70, WIDTH - 64};
                for (int i = 0; i < content.length; i++) {
                    TextPaint ink = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
                    ink.setColor(i == 1 ? 0xffbec9da : Color.WHITE);
                    ink.setTextSize(sizes[i]);
                    ink.setTypeface(Typeface.create(Typeface.SANS_SERIF, i == 2 ? Typeface.BOLD : Typeface.NORMAL));
                    Layout.Alignment align = i == 1 || i == 3 ? Layout.Alignment.ALIGN_OPPOSITE
                            : i == 4 ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_NORMAL;
                    StaticLayout layout = StaticLayout.Builder.obtain(content[i], 0, content[i].length(), ink, widths[i])
                            .setAlignment(align).setIncludePad(false).build();
                    glyphCounts[i] = layout.getLineCount();
                    int save = c.save();
                    c.translate(24 + i * 3, CENTRES[i] - layout.getHeight() / 2f);
                    layout.draw(c);
                    c.restoreToCount(save);
                }
            } finally {
                glyphs.endRecording();
            }
            recordings++;
        }
    }
}
