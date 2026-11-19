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

import com.os4.musiccover.cover.CoverMorphMotion;
import com.os4.musiccover.cover.CoverMorphMotion.Box;
import com.os4.musiccover.island.FlightState;
import com.os4.musiccover.island.MiniCardMorph;
import com.os4.musiccover.island.NoteFlightOrigin;
import com.os4.musiccover.island.NotePullFrames;

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
    private static final int FIRST = -3, CAPSULE_HALF = -2, LAST = 8;
    private static final int CLIP_FADE = LAST + 1;
    private static final int SOFT_BLUR = LAST + 2;
    private static final int WIDE_BLUR = LAST + 3;
    private static final int MORPH_START = WIDE_BLUR + 1, MORPH_LAST = MORPH_START + 8;
    // Release, overshoot, rebound twice, then explicitly drag away from the folded end.
    private static final float[] LANDING = {.55f, .25f, 0f, -.14f, .15f, -.03f, .05f, 0f, .30f};
    private static final int LANDING_START = MORPH_LAST + 1, LANDING_LAST = MORPH_LAST + LANDING.length;
    private static final int PULL_START = LANDING_LAST + 1, PULL_LAST = PULL_START + 8;
    private static final int OVERLAP_START = PULL_LAST + 1;
    private static final int OVERLAP_LAST = OVERLAP_START + OverlapExitProbe.TIMES.length - 1;
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
                    String name = requested == FIRST ? "background" : requested == CAPSULE_HALF ? "capsule-half" : requested == -1 ? "reference"
                            : requested >= OVERLAP_START ? "overlap-" + (requested - OVERLAP_START)
                            : requested >= PULL_START ? "pull-" + (requested - PULL_START)
                            : requested >= LANDING_START ? "landing-" + (requested - LANDING_START)
                            : requested >= MORPH_START ? "panel-" + (requested - MORPH_START)
                            : requested == CLIP_FADE ? "clip-fade"
                            : requested == SOFT_BLUR ? "blur-soft"
                            : requested == WIDE_BLUR ? "blur-wide"
                            : String.format(Locale.ROOT, "step-%02d", requested);
                    try (FileOutputStream out = new FileOutputStream(new File(output, name + ".png"))) {
                        if (!frame.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IllegalStateException("PNG write failed");
                    }
                    retries = 0;
                    if (phase == OVERLAP_LAST) {
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
        private final OverlapExitProbe overlap = new OverlapExitProbe();
        private final LyricClipFade clipFade = new LyricClipFade();
        private final LyricRenderBlur liveBlur = new LyricRenderBlur();
        private final LyricRenderBlur floorBlur = new LyricRenderBlur();
        private final RenderNode glyphs = new RenderNode("frozen Android lyric layouts");
        private final com.os4.musiccover.island.CapsuleMorphFocus landingFocus =
                new com.os4.musiccover.island.CapsuleMorphFocus();
        private final LyricRenderBlur landingBlur = new LyricRenderBlur();
        private final Paint backdrop = new Paint();
        private final Paint lyricInk = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final LyricBottomFade bottomFade = new LyricBottomFade();
        private final LyricRenderBlur harmonyBlur = new LyricRenderBlur();
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
            if (phase >= CAPSULE_HALF && phase < LAST && !glyphs.hasDisplayList()) recordPage();
            if (phase == CAPSULE_HALF) {
                LyricPageExit exit = new LyricPageExit();
                exit.closeFromCapsule(0);
                exit.step(60);
                mask.drawFrame(c, TOP, BOTTOM, exit, target ->
                        LyricPageExitMask.drawSnapshot(target, glyphs, exit.opacity(),
                                unused -> { throw new AssertionError("Missing hardware snapshot"); }));
            } else if (phase == -1) {
                glyphs.setAlpha(1f);
                c.drawRenderNode(glyphs);
            }
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
                if (phase == WIDE_BLUR) {
                    Canvas floor = floorBlur.begin(320, 252, 480, 420, 12f, 336f);
                    floor.drawRect(340, 284, 384, 384, backdrop); // a line crossing the floor
                    floor.drawRect(422, 340, 454, 386, backdrop); // a fully occluded line
                    floorBlur.finish(c);
                }
            } else if (phase >= OVERLAP_START) {
                overlap.draw(c, phase - OVERLAP_START, WIDTH);
            } else if (phase >= PULL_START) {
                drawLatePull(c, phase - PULL_START);
            } else if (phase >= LANDING_START) {
                int i = phase - LANDING_START;
                float raw = LANDING[i];
                float optical = landingFocus.sample(raw, i != LANDING.length - 1);
                float radius = 28f * com.os4.musiccover.island.MiniCardMorph.Companion.pillBlur(optical);
                // A still-pending swap radius must not replace the active morph's sharp zero.
                radius = com.os4.musiccover.island.CapsuleMorphFocusKt.foregroundBlur(radius, -1f, 9f);
                int pose = c.save();
                try {
                    c.translate(Math.round(40 * raw), 0);
                    Canvas layer = landingBlur.begin(55, 280, 212, 392, radius);
                    backdrop.setColor(Color.WHITE);
                    layer.drawRect(110, 308, 120, 364, backdrop);
                    layer.drawRect(136, 308, 146, 364, backdrop);
                    landingBlur.finish(c);
                } finally { c.restoreToCount(pose); }
            } else if (phase >= MORPH_START) {
                int position = phase - MORPH_START;
                drawPanelLyrics(c, Math.min(position, 8 - position) / 4f);
            } else if (phase >= 0) {
                mask.draw(c, TOP, BOTTOM, phase / (float) LAST, canvas -> canvas.drawRenderNode(glyphs));
            }
            lastPainted = phase;
        }

        // Source and flight paint independently with translucent ink so a missing frame,
        // duplicate at rest or even two perfectly overlapping silhouettes are observable.
        private final FlightState pullState = new FlightState();
        private final Paint pullInk = new Paint(Paint.ANTI_ALIAS_FLAG);

        private void drawLatePull(Canvas c, int frame) {
            Box pill = new Box(176, 640, 188, 52);
            float thresholdNudge = MiniCardMorph.Companion.rubber(72f, 48f, .8f);
            float heldTop = pill.y - thresholdNudge - 96f;
            boolean rowReady = frame >= 2;
            pullState.setRowWaitKey(rowReady ? null : "fixture");
            pullInk.setColor(0xbfffffff);
            if (!pullState.takesPill(rowReady)) {
                float top = frame == 0 ? pill.y - thresholdNudge : heldTop;
                paintPill(c, pill.x, rowReady ? pill.y : top, pill.w, pill.h);
            }
            if (!rowReady) return;

            // Replay for each capture so a repeated onDraw/PixelCopy retry cannot
            // consume another animation frame. Geometry uses one third of the phone's pixels.
            float travel = 220f;
            float joinedProgress = 96f / travel;
            float joinedNudge = thresholdNudge * (1f - joinedProgress);
            NoteFlightOrigin origin = new NoteFlightOrigin(heldTop, travel, joinedProgress, joinedNudge);
            NotePullFrames frames = new NotePullFrames(heldTop, 0f, travel);
            frames.target(joinedProgress, heldTop, 0f);
            if (frame >= 3) frames.advance(.008f);
            // The native card moves under a stationary finger between onDraw and pre-draw.
            Box card = new Box(96, frame <= 3 ? 420 : 464, 348, 100);
            if (frame >= 5) {
                // Coalesced MOVE near the end of the 881px pull at t=21ms.
                float progress = ((881f - 60f) / 3f - 72f) / travel;
                float nudge = thresholdNudge * (1f - progress);
                frames.target(progress, origin.top(progress, nudge), 0f);
                frames.advance(.013f);
            }
            if (frame >= 6) {
                // The final input at t=27ms arrives before another display frame.
                float progress = (881f / 3f - 72f) / travel;
                frames.target(progress, origin.top(progress, 0f), 0f);
            }

            MiniCardMorph.NoteHandoff handoff = new MiniCardMorph.NoteHandoff(pill);
            Box box = handoff.frame(pill, card, frames.getProgress(), 21L);
            float top = frames.getTop();
            if (frame >= 6) {
                handoff.release(27L);
                CoverMorphMotion spring = new CoverMorphMotion();
                spring.value = frames.getProgress();
                spring.aim(true);
                spring.velocity = 5f;
                CoverMorphMotion nudge = new CoverMorphMotion();
                nudge.value = MiniCardMorph.Companion.heldNudge(box.y, handoff.getDirection(), top) / 100f;
                for (int i = 7; i <= frame; i++) {
                    spring.step(1f / 120f, .4f);
                    nudge.step(1f / 120f, .4f);
                    box = handoff.frame(pill, card, spring.value, i == 7 ? 30L : 38L);
                    top = box.y + handoff.getDirection() * nudge.value * 100f;
                }
            }
            paintPill(c, box.x, top, box.w, box.h);
        }

        private void paintPill(Canvas c, float x, float y, float w, float h) {
            c.drawRoundRect(x, y, x + w, y + h, h / 2f, h / 2f, pullInk);
        }

        // A three-line verse with a sharp opening and a soft backing line at the foot.
        // A phone-sized viewport loses half its reading room during expansion. Feed the
        // same anchor, edge mask and live backing blur as the lock-screen rows.
        private void drawPanelLyrics(Canvas c, float progress) {
            float top = LyricBandGeometry.morphEdge(100, 52, progress);
            float floor = LyricBandGeometry.morphEdge(660, 340, progress);
            int base = Math.round(LyricBandGeometry.singingTop(top, floor, 0f, progress) + 20f);
            lyricInk.setColor(Color.WHITE);
            lyricInk.setTypeface(Typeface.DEFAULT_BOLD);
            lyricInk.setTextSize(46f);
            sampleRow(c, "Stay", 43, base, floor);
            // Keep the descender of "Again" inside the clear area even at full expansion:
            // baseline 270, ink to 280; the soft edge starts at 282.
            sampleRow(c, "Again", 43, base + 54, floor);
            int harmonyBase = base + 169;
            Canvas layer = harmonyBlur.begin(35, harmonyBase - 63, 481, harmonyBase + 28, 4f);
            try { sampleRow(layer, "Through the night", 54, harmonyBase, floor); }
            finally { harmonyBlur.finish(c); }
        }

        private void sampleRow(Canvas canvas, String text, int x, int base, float floor) {
            int token = bottomFade.beginRow(canvas, WIDTH, base - 56, base + 16, floor, 58f);
            try { canvas.drawText(text, x, base, lyricInk); }
            finally { bottomFade.endRow(canvas, WIDTH, base + 16, token); }
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
