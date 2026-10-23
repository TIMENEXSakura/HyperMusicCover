package com.os4.musiccover;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.SystemClock;
import android.view.SurfaceControl;
import com.os4.musiccover.immersive.ImmersiveHost;
import com.os4.musiccover.immersive.LiveAlertScene;
import com.os4.musiccover.lyrics.LockLyrics;
import com.os4.musiccover.lyrics.NavigationPixelContrast;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ObjIntConsumer;

/** In-memory, low-resolution sampling of the map's own remote layer subtree, never the display. */
final class NavigationSurfaceContrast {
    private NavigationSurfaceContrast() {}
    private static final long INTERVAL_MS = 1500L, TIMEOUT_MS = 2500L;
    private static final NavigationPixelContrast.Stability STABILITY = new NavigationPixelContrast.Stability();
    private static String sScene, sSurfaceKey = "pending", sLastDiagnostic;
    private static long sGeneration, sContrastEpoch, sNextRead;
    private static boolean sPollPosted, sUnsupported;
    private static Request sPending;
    private static LyricContrastBackground.Reading sReading = unknown("navigation:unknown");
    private static Constructor<?> sCopyControl;

    static LyricContrastBackground.Reading current(String scene) {
        if (!scene.equals(sScene)) {
            stop();
            sScene = scene;
            sReading = unknown(key());
        }
        pollSoon();
        return sReading;
    }

    static void stop() {
        if (sScene == null && !sPollPosted) return;
        sScene = null;
        sGeneration++;
        if (sPending != null) sPending.expired = true;
        sSurfaceKey = "pending";
        sContrastEpoch = 0L;
        sNextRead = 0L;
        STABILITY.reset();
        Main.main().removeCallbacks(POLL);
        sPollPosted = false;
        // An outstanding native capture owns its callback. Never enqueue another one until
        // it returns, even after timeout; any late buffer is still closed by that callback.
    }

    private static boolean wanted() {
        return sScene != null && Main.screenOnCached() && LockLyrics.wantsAttached()
                && !LockLyrics.lockScreenGone()
                && sScene.equals(ImmersiveHost.presentedNavigationKey());
    }

    private static final Runnable POLL = () -> {
        sPollPosted = false;
        try {
            if (!wanted()) { stop(); return; }
            readDue();
        } catch (Throwable error) { fail("sample-host:" + error.getClass().getSimpleName()); }
        pollSoon();
    };

    private static void pollSoon() {
        try {
            if (sPollPosted || !wanted() || sUnsupported) return;
            sPollPosted = Main.main().postDelayed(POLL,
                    Math.max(0L, sNextRead - SystemClock.uptimeMillis()));
        } catch (Throwable ignored) {}
    }

    private static String key() { return "navigation:" + sScene + ":" + sSurfaceKey + ":" + sContrastEpoch; }
    private static LyricContrastBackground.Reading unknown(String key) {
        return new LyricContrastBackground.Reading(key, 0, "navigation-surface-unknown");
    }

    private static void readDue() {
        sNextRead = SystemClock.uptimeMillis() + INTERVAL_MS;
        LiveAlertScene.ContrastSurface target = ImmersiveHost.presentedNavigationSurface();
        if (target == null) {
            STABILITY.accept(null);
            diagnostic("surface-not-ready");
            return;
        }
        String surfaceKey = target.generation + ":" + target.width + "x" + target.height;
        if (!surfaceKey.equals(sSurfaceKey)) {
            sSurfaceKey = surfaceKey;
            sGeneration++;
            if (sPending != null) sPending.expired = true;
            sContrastEpoch = 0L;
            STABILITY.reset();
            sReading = unknown(key());
        }
        if (sPending != null || sUnsupported) return;
        SurfaceControl copy = null;
        try {
            // Copy on the main thread while the scene still owns its handle. The worker never
            // races release() against a borrowed Java SurfaceControl's native pointer.
            if (sCopyControl == null) sCopyControl = SurfaceControl.class.getConstructor(
                    SurfaceControl.class, String.class);
            copy = (SurfaceControl) sCopyControl.newInstance(target.control, "MCLyricContrast");
            Request request = new Request(sScene, sGeneration, target, copy);
            sPending = request;
            // Install the timeout before handing the owned control to another thread. Once
            // post succeeds only that worker releases it, even if later host work fails.
            Main.main().postDelayed(request.timeout, TIMEOUT_MS);
            if (!Main.worker().post(() -> startCapture(request))) {
                sPending = null;
                Main.main().removeCallbacks(request.timeout);
                release(copy);
                fail("worker-unavailable");
                return;
            }
        } catch (Throwable error) {
            if (sPending != null && sPending.control == copy) {
                Main.main().removeCallbacks(sPending.timeout);
                sPending = null;
            }
            release(copy);
            sUnsupported = error instanceof NoSuchMethodException;
            fail("surface-copy:" + error.getClass().getSimpleName());
        }
    }

    private static final class Request {
        final String scene;
        final long generation, surfaceGeneration;
        final int width, height;
        final SurfaceControl control;
        final AtomicBoolean callbackSeen = new AtomicBoolean();
        volatile boolean expired;
        Object listener; // Keep the native listener and its weakly-held consumer alive.
        ObjIntConsumer<Object> consumer;
        final Runnable timeout;
        Request(String scene, long generation, LiveAlertScene.ContrastSurface target, SurfaceControl control) {
            this.scene = scene;
            this.generation = generation;
            surfaceGeneration = target.generation;
            width = target.width;
            height = target.height;
            this.control = control;
            timeout = () -> {
                if (sPending != this) return;
                expired = true;
                if (generation == sGeneration) {
                    STABILITY.accept(null);
                    fail("capture-timeout");
                }
            };
        }
    }

    private static final class Api {
        final Constructor<?> builder, listener;
        final Method crop, scale, children, secure, protectedContent, build, capture, buffer, bitmap;
        Api() throws ReflectiveOperationException {
            Class<?> captureClass = Class.forName("android.window.ScreenCapture");
            Class<?> args = Class.forName("android.window.ScreenCapture$LayerCaptureArgs");
            Class<?> b = Class.forName("android.window.ScreenCapture$LayerCaptureArgs$Builder");
            Class<?> l = Class.forName("android.window.ScreenCapture$ScreenCaptureListener");
            Class<?> result = Class.forName("android.window.ScreenCapture$ScreenshotHardwareBuffer");
            builder = b.getConstructor(SurfaceControl.class);
            listener = l.getConstructor(ObjIntConsumer.class);
            crop = b.getMethod("setSourceCrop", Rect.class);
            scale = b.getMethod("setFrameScale", float.class, float.class);
            children = b.getMethod("setChildrenOnly", boolean.class);
            secure = b.getMethod("setCaptureSecureLayers", boolean.class);
            protectedContent = b.getMethod("setAllowProtected", boolean.class);
            build = b.getMethod("build");
            capture = captureClass.getMethod("captureLayers", args, l);
            buffer = result.getMethod("getHardwareBuffer");
            bitmap = result.getMethod("asBitmap");
        }
    }
    private static Api sApi; // Worker only.

    private static void startCapture(Request request) {
        try {
            if (request.expired) { complete(request, null, "capture-obsolete", false); return; }
            if (!request.control.isValid()) { complete(request, null, "surface-invalid", false); return; }
            if (sApi == null) sApi = new Api();
            Api api = sApi;
            Rect area = new Rect(Math.round(request.width * 0.08f), Math.round(request.height * 0.25f),
                    Math.round(request.width * 0.92f), Math.round(request.height * 0.80f));
            if (area.isEmpty()) { complete(request, null, "surface-empty", false); return; }
            Object builder = api.builder.newInstance(request.control);
            api.crop.invoke(builder, area);
            // Downsample inside SurfaceFlinger. No full-resolution bitmap is ever requested.
            api.scale.invoke(builder, Math.min(1f, 48f / area.width()), Math.min(1f, 64f / area.height()));
            api.children.invoke(builder, false);
            api.secure.invoke(builder, false);
            api.protectedContent.invoke(builder, false);
            request.consumer = (captured, status) -> {
                if (!request.callbackSeen.compareAndSet(false, true)) { closeBuffer(api, captured); return; }
                try {
                    if (Main.worker().post(() -> consume(api, request, captured, status))) return;
                } catch (Throwable ignored) {}
                closeBuffer(api, captured);
                complete(request, null, "worker-unavailable", false);
            };
            request.listener = api.listener.newInstance(request.consumer);
            int status = ((Number) api.capture.invoke(null, api.build.invoke(builder), request.listener)).intValue();
            if (status != 0) complete(request, null, "capture-status:" + status, false);
        } catch (Throwable error) {
            complete(request, null, "capture-api:" + error.getClass().getSimpleName(),
                    error instanceof ClassNotFoundException || error instanceof NoSuchMethodException);
        } finally {
            release(request.control);
        }
    }

    private static void consume(Api api, Request request, Object captured, int status) {
        Bitmap hardware = null, software = null;
        NavigationPixelContrast.Counts counts = null;
        String error = null;
        try {
            if (request.expired || status != 0 || captured == null) {
                error = request.expired ? "capture-late" : "capture-status:" + status;
            } else {
                HardwareBuffer buffer = (HardwareBuffer) api.buffer.invoke(captured);
                if (buffer == null || buffer.isClosed() || buffer.getWidth() > 96 || buffer.getHeight() > 96
                        || (long) buffer.getWidth() * buffer.getHeight() > 8192) {
                    error = "buffer-size-invalid";
                } else {
                    hardware = (Bitmap) api.bitmap.invoke(captured);
                    software = hardware == null ? null : hardware.copy(Bitmap.Config.ARGB_8888, false);
                    if (software != null) {
                        int w = software.getWidth(), h = software.getHeight();
                        int[] pixels = new int[w * h];
                        software.getPixels(pixels, 0, w, 0, 0, w, h);
                        counts = NavigationPixelContrast.count(pixels);
                        if (!counts.usable()) error = "pixels-unavailable";
                    } else error = "bitmap-unavailable";
                }
            }
        } catch (Throwable failure) {
            error = "buffer-read:" + failure.getClass().getSimpleName();
        } finally {
            recycle(software);
            recycle(hardware);
            closeBuffer(api, captured);
        }
        complete(request, counts, error, false);
    }

    private static void closeBuffer(Api api, Object captured) {
        if (captured == null) return;
        try {
            HardwareBuffer buffer = (HardwareBuffer) api.buffer.invoke(captured);
            if (buffer != null && !buffer.isClosed()) buffer.close();
        } catch (Throwable ignored) {}
    }

    private static void recycle(Bitmap bitmap) {
        try { if (bitmap != null) bitmap.recycle(); } catch (Throwable ignored) {}
    }
    private static void release(SurfaceControl control) {
        try { if (control != null) control.release(); } catch (Throwable ignored) {}
    }

    private static void complete(Request request, NavigationPixelContrast.Counts counts,
                                 String error, boolean unsupported) {
        try {
            Main.main().post(() -> publish(request, counts, error, unsupported));
        } catch (Throwable ignored) {}
    }

    private static void publish(Request request, NavigationPixelContrast.Counts counts,
                                String error, boolean unsupported) {
        try {
            if (sPending != request) return;
            sPending = null;
            Main.main().removeCallbacks(request.timeout);
            if (request.expired || request.generation != sGeneration
                    || !request.scene.equals(sScene) || !wanted()) return;
            LiveAlertScene.ContrastSurface current = ImmersiveHost.presentedNavigationSurface();
            if (current == null || current.generation != request.surfaceGeneration
                    || current.width != request.width || current.height != request.height) return;
            if (unsupported) sUnsupported = true;
            if (error != null || counts == null) {
                STABILITY.accept(null);
                fail(error == null ? "capture-empty" : error);
                return;
            }
            int before = STABILITY.color();
            int color = STABILITY.accept(counts);
            if (before != 0 && color != before) sContrastEpoch++;
            LyricContrastBackground.Reading previous = sReading;
            sReading = new LyricContrastBackground.Reading(key(), color,
                    color == 0 ? "navigation-surface-unknown" : "navigation-surface-ratio");
            diagnostic("pixels " + counts.bright + " bright/" + counts.dark + " dark/"
                    + counts.total + " total -> " + (color == 0 ? "unknown" : color == 0xff000000 ? "black" : "white"));
            if (previous.color != sReading.color || !previous.key.equals(sReading.key)
                    || !previous.source.equals(sReading.source)) LockLyrics.refresh();
        } catch (Throwable failure) {
            fail("sample-publish:" + failure.getClass().getSimpleName());
        }
    }

    private static void fail(String reason) {
        sReading = unknown(key());
        diagnostic(reason);
    }
    private static void diagnostic(String message) {
        // Only state/color transitions, not every map frame. Never include pixels or map text.
        String state = sGeneration + ":" + (message.startsWith("pixels ") ? sReading.color : message);
        if (state.equals(sLastDiagnostic)) return;
        sLastDiagnostic = state;
        Xp.log("MCLyricContrast: navigation " + message);
    }
}
