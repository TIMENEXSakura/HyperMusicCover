package com.os4.musiccover;

/** An explicit music-page background deferred while navigation owns the lock-screen backdrop. */
final class NavigationLyricBackground {
    private String scene;

    void requestedOver(String navigationScene) {
        if (navigationScene != null) scene = navigationScene;
    }

    boolean pending() { return scene != null; }
    boolean belongsTo(String navigationScene) { return scene != null && scene.equals(navigationScene); }

    /** The page still protects navigation's backdrop even when its lyrics are hidden. */
    boolean protects(String requestedNavigationScene) { return belongsTo(requestedNavigationScene); }

    /** During an immersive page the native media card can temporarily report itself hidden. */
    static boolean allowsToggle(boolean mediaEligible, boolean islandsEligible,
                                boolean capsuleRequested, boolean navigationRequested) {
        return mediaEligible || (islandsEligible && (capsuleRequested || navigationRequested));
    }

    /** Consume once, including when current settings/session no longer allow a music background. */
    boolean finish(String navigationScene, boolean coverEnabled, boolean sessionAvailable,
                   boolean lyricsVisible, boolean independentCover) {
        if (!belongsTo(navigationScene)) return false;
        scene = null;
        return coverEnabled && sessionAvailable && (lyricsVisible || independentCover);
    }

    void cancel() { scene = null; }
}
