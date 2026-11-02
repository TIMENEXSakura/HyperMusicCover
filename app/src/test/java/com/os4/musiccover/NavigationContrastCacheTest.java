package com.os4.musiccover;

import org.junit.Test;
import com.os4.musiccover.lyrics.NavigationPixelContrast;
import static org.junit.Assert.*;

public class NavigationContrastCacheTest {
    private static final int BLACK = 0xff000000, WHITE = 0xffffffff;

    @Test public void keepsLastMeasuredChoiceForEachSceneWithoutBorrowingAnotherScene() {
        NavigationContrastCache cache = new NavigationContrastCache();
        assertEquals(0, cache.get("amap"));
        cache.remember("amap", BLACK);
        assertEquals(BLACK, cache.get("amap"));
        assertEquals(0, cache.get("transit"));
        cache.remember("transit", WHITE);
        cache.remember("amap", WHITE);
        assertEquals(WHITE, cache.get("amap"));
        assertEquals(WHITE, cache.get("transit"));
    }

    @Test public void invalidOrMissingSamplesNeverOverwriteAValidHint() {
        NavigationContrastCache cache = new NavigationContrastCache();
        cache.remember("amap", BLACK);
        cache.remember("amap", 0);
        cache.remember("amap", 0xff999999);
        cache.remember(null, WHITE);
        cache.remember("", WHITE);
        assertEquals(BLACK, cache.get("amap"));
        assertEquals(0, cache.get(null));
        assertEquals(0, cache.get(""));
    }

    @Test public void balancedLiveSampleKeepsHintUntilARealChoiceArrives() {
        NavigationContrastCache cache = new NavigationContrastCache();
        cache.remember("amap", BLACK);
        NavigationPixelContrast.Stability live = new NavigationPixelContrast.Stability();
        int[] balanced = new int[100];
        java.util.Arrays.fill(balanced, 0, 50, 0xffffffff);
        java.util.Arrays.fill(balanced, 50, 100, 0xff000000);
        int color = live.accept(NavigationPixelContrast.count(balanced));
        assertEquals(0, color);
        cache.remember("amap", color);
        assertEquals(BLACK, cache.get("amap"));
        java.util.Arrays.fill(balanced, 0xff000000);
        color = live.accept(NavigationPixelContrast.count(balanced));
        assertEquals(WHITE, color);
        cache.remember("amap", color);
        assertEquals(WHITE, cache.get("amap"));
    }

    @Test public void hotReloadTransfersOnlySceneAndColorAndRejectsMalformedEntries() {
        NavigationContrastCache original = new NavigationContrastCache();
        original.remember("amap", BLACK);
        original.remember("transit", WHITE);
        NavigationContrastCache restored = new NavigationContrastCache();
        restored.adopt(original.save());
        assertEquals(BLACK, restored.get("amap"));
        assertEquals(WHITE, restored.get("transit"));
        restored.adopt(new Object[]{"valid", WHITE, "bad", 42, "wrong", "black", 12, BLACK, "incomplete"});
        assertEquals(WHITE, restored.get("valid"));
        assertEquals(0, restored.get("amap"));
        assertEquals(0, restored.get("bad"));
        assertEquals(0, restored.get("wrong"));
        restored.adopt(null);
        assertEquals(0, restored.get("valid"));
    }

    @Test public void cacheIsBoundedAndKeepsRecentlyUsedScenes() {
        NavigationContrastCache cache = new NavigationContrastCache();
        for (int i = 0; i < 8; i++) cache.remember("scene" + i, BLACK);
        assertEquals(BLACK, cache.get("scene0"));
        cache.remember("new", WHITE);
        assertEquals(BLACK, cache.get("scene0"));
        assertEquals(0, cache.get("scene1"));
        assertEquals(WHITE, cache.get("new"));
        assertEquals(16, cache.save().length);
    }
}
