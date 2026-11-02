package com.os4.musiccover.lyrics;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import com.os4.musiccover.Gen;
import com.os4.musiccover.Main;
import java.util.List;

/** Cached output route, checked off the UI thread rather than once per animation frame. */
final class LyricBluetoothRoute {
    private static final long CHECK_MS = 1000L;
    private static MediaController controller;
    private static Object token;
    private static int epoch;
    private static long checkedAt;
    private static boolean pending;
    private static Handler worker;
    private static volatile int route = LyricBluetoothOffset.NONE;

    private LyricBluetoothRoute() { }

    /** Main thread: forget the old route as soon as the selected player changes. */
    static void select(MediaController next) {
        Object nextToken = next == null ? null : next.getSessionToken();
        if (!java.util.Objects.equals(token, nextToken)) {
            token = nextToken;
            epoch++;
            pending = false;
            checkedAt = 0L;
            route = LyricBluetoothOffset.NONE;
        }
        controller = next;
        refresh();
    }

    /** Called by the existing lyric tick; handles routing changes without permanent listeners. */
    static void refresh() {
        if (!LockLyrics.sBluetoothSync || controller == null || Main.sAppCtx == null || pending || !Gen.live()) return;
        long now = SystemClock.uptimeMillis();
        if (checkedAt != 0L && now - checkedAt < CHECK_MS) return;
        checkedAt = now;
        final int request = epoch;
        final MediaController selected = controller;
        if (worker == null) {
            try {
                HandlerThread thread = Gen.handlerThread("MCLyricAudioRoute", android.os.Process.THREAD_PRIORITY_BACKGROUND);
                worker = Gen.liveHandler(thread.getLooper());
            } catch (Throwable ignored) {
                // A route check must never prevent SystemUI from displaying lyrics.
                return;
            }
        }
        pending = true;
        if (!worker.post(() -> {
            int found = LyricBluetoothOffset.NONE;
            try {
                MediaController.PlaybackInfo info = selected.getPlaybackInfo();
                if (info != null && info.getPlaybackType() == MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL) {
                    AudioAttributes attributes = info.getAudioAttributes();
                    AudioManager audio = Main.sAppCtx.getSystemService(AudioManager.class);
                    if (attributes != null && audio != null) {
                        List<AudioDeviceInfo> devices = audio.getAudioDevicesForAttributes(attributes);
                        if (devices != null) for (AudioDeviceInfo device : devices) {
                            if (device == null || !device.isSink()) continue;
                            found = LyricBluetoothDevice.combine(found,
                                    LyricBluetoothDevice.kind(device.getType()));
                        }
                    }
                }
            } catch (Throwable ignored) {
                // An unsupported or inaccessible route is not proof of Bluetooth playback.
            }
            final int result = found;
            Main.main().post(() -> {
                if (!Gen.live() || !LockLyrics.sBluetoothSync || request != epoch) return;
                pending = false;
                if (route != result) {
                    route = result;
                    LockLyrics.onBluetoothRouteChanged();
                }
            });
        })) pending = false;
    }

    static int current() { return route; }

    /** Disabling compensation invalidates any in-flight reply and stops further route checks. */
    static void settingChanged(boolean enabled) {
        if (!enabled) {
            epoch++;
            pending = false;
            checkedAt = 0L;
            route = LyricBluetoothOffset.NONE;
        } else refresh();
    }

    /** Gen owns the worker thread; reject any late reply before the thread is swept. */
    static void retire() {
        epoch++;
        controller = null;
        token = null;
        pending = false;
        checkedAt = 0L;
        route = LyricBluetoothOffset.NONE;
        worker = null;
    }
}
