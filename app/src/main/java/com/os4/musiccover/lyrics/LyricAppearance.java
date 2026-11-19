package com.os4.musiccover.lyrics;

import org.json.JSONObject;

/** Persisted drawing preferences; defaults reproduce upstream colors and opacity. */
public final class LyricAppearance {
    public static final int OFFICIAL = 0, COVER = 1, CUSTOM = 2, SYSTEM = 3;
    /**
     * The translation's own row joined the other two on 2026-11-15. Its official colour is the
     * pale cover tint it was always drawn in, at the 0.62 it always had, so a JSON written
     * before the row existed reads back looking exactly as it did.
     */
    public static final float DEFAULT_TRANS_OPACITY = 0.62f;
    public static final LyricAppearance DEFAULT =
            new LyricAppearance(0, 0, -1, -1, 1f, 0.72f, 0, -1, DEFAULT_TRANS_OPACITY);
    public final boolean duetEnabled;
    public final int duetMode, duetColor;
    public final float duetOpacity, duetIntensity;
    public final int mainMode, bgMode, transMode, mainColor, bgColor, transColor;
    public final float mainOpacity, bgOpacity, transOpacity;
    public static final float DEFAULT_INTENSITY = 0.65f;
    public final float mainIntensity, bgIntensity, transIntensity;

    public LyricAppearance(int mainMode, int bgMode, int mainColor, int bgColor,
                           float mainOpacity, float bgOpacity) {
        this(mainMode, bgMode, mainColor, bgColor, mainOpacity, bgOpacity,
                OFFICIAL, -1, DEFAULT_TRANS_OPACITY);
    }

    public LyricAppearance(int mainMode, int bgMode, int mainColor, int bgColor,
                           float mainOpacity, float bgOpacity,
                           int transMode, int transColor, float transOpacity) {
        this(mainMode, bgMode, mainColor, bgColor, mainOpacity, bgOpacity, transMode, transColor,
                transOpacity, DEFAULT_INTENSITY, DEFAULT_INTENSITY, DEFAULT_INTENSITY);
    }

    public LyricAppearance(int mainMode, int bgMode, int mainColor, int bgColor,
                           float mainOpacity, float bgOpacity, int transMode, int transColor,
                           float transOpacity, float mainIntensity, float bgIntensity, float transIntensity) {
        this(mainMode, bgMode, mainColor, bgColor, mainOpacity, bgOpacity, transMode, transColor,
                transOpacity, mainIntensity, bgIntensity, transIntensity, true,
                mainMode, mainColor, mainOpacity, mainIntensity);
    }

    public LyricAppearance(int mainMode, int bgMode, int mainColor, int bgColor,
                           float mainOpacity, float bgOpacity, int transMode, int transColor,
                           float transOpacity, float mainIntensity, float bgIntensity, float transIntensity,
                           boolean duetEnabled, int duetMode, int duetColor, float duetOpacity, float duetIntensity) {
        this.duetEnabled = duetEnabled;
        this.duetMode = mode(duetMode);
        this.duetColor = duetColor | 0xff000000;
        this.duetOpacity = opacity(duetOpacity, 1f);
        this.duetIntensity = opacity(duetIntensity, DEFAULT_INTENSITY);
        this.mainIntensity = opacity(mainIntensity, DEFAULT_INTENSITY);
        this.bgIntensity = opacity(bgIntensity, DEFAULT_INTENSITY);
        this.transIntensity = opacity(transIntensity, DEFAULT_INTENSITY);
        this.mainMode = mode(mainMode);
        this.bgMode = mode(bgMode);
        this.transMode = mode(transMode);
        this.mainColor = mainColor | 0xff000000;
        this.bgColor = bgColor | 0xff000000;
        this.transColor = transColor | 0xff000000;
        this.mainOpacity = opacity(mainOpacity, 1f);
        this.bgOpacity = opacity(bgOpacity, 0.72f);
        this.transOpacity = opacity(transOpacity, DEFAULT_TRANS_OPACITY);
    }

    /** Pure RGB blend used by both the app preview and cached renderer colours. */
    public static int soften(int color, float intensity) {
        float amount = opacity(intensity, DEFAULT_INTENSITY);
        int r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
        int neutral = Math.max(r, Math.max(g, b));
        return 0xff000000 | Math.round(neutral + (r - neutral) * amount) << 16
                | Math.round(neutral + (g - neutral) * amount) << 8
                | Math.round(neutral + (b - neutral) * amount);
    }

    private static int mode(int value) { return value >= OFFICIAL && value <= SYSTEM ? value : OFFICIAL; }
    private static float opacity(float value, float fallback) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : fallback;
    }

    public static LyricAppearance parse(String text) {
        if (text == null || text.isEmpty()) return DEFAULT;
        try {
            JSONObject json = new JSONObject(text);
            int mainMode = json.optInt("mainMode", OFFICIAL);
            int mainColor = json.optInt("mainColor", -1);
            float mainOpacity = (float) json.optDouble("mainOpacity", 1d);
            float mainIntensity = (float) json.optDouble("mainIntensity", DEFAULT_INTENSITY);
            return new LyricAppearance(mainMode, json.optInt("bgMode", 0),
                    mainColor, json.optInt("bgColor", -1),
                    mainOpacity,
                    (float) json.optDouble("bgOpacity", 0.72d),
                    json.optInt("transMode", OFFICIAL), json.optInt("transColor", -1),
                    (float) json.optDouble("transOpacity", DEFAULT_TRANS_OPACITY),
                    mainIntensity,
                    (float) json.optDouble("bgIntensity", DEFAULT_INTENSITY),
                    (float) json.optDouble("transIntensity", DEFAULT_INTENSITY),
                    json.optBoolean("duetEnabled", true), json.optInt("duetMode", mainMode),
                    json.optInt("duetColor", mainColor), (float) json.optDouble("duetOpacity", mainOpacity),
                    (float) json.optDouble("duetIntensity", mainIntensity));
        } catch (Exception ignored) { return DEFAULT; }
    }

    public String toJson() {
        try {
            return new JSONObject().put("mainMode", mainMode).put("bgMode", bgMode)
                    .put("transMode", transMode)
                    .put("mainColor", mainColor).put("bgColor", bgColor).put("transColor", transColor)
                    .put("mainOpacity", (double) mainOpacity).put("bgOpacity", (double) bgOpacity)
                    .put("transOpacity", (double) transOpacity)
                    .put("mainIntensity", (double) mainIntensity).put("bgIntensity", (double) bgIntensity)
                    .put("transIntensity", (double) transIntensity)
                    .put("duetEnabled", duetEnabled).put("duetMode", duetMode).put("duetColor", duetColor)
                    .put("duetOpacity", (double) duetOpacity).put("duetIntensity", (double) duetIntensity)
                    .toString();
        } catch (Exception ignored) { return "{}"; }
    }
}
