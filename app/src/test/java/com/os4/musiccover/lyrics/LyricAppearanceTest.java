package com.os4.musiccover.lyrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricAppearanceTest {
    @Test public void intensitySoftensCachedColorWithoutChangingOpacityOrCustomColor() {
        int tint = 0xffe07020;
        assertEquals(tint, LyricAppearance.soften(tint, 1f));
        assertEquals(0xffe0e0e0, LyricAppearance.soften(tint, 0f));
        LyricAppearance a = LyricAppearance.parse("{\"mainMode\":1,\"mainIntensity\":0.3,\"bgIntensity\":0.8,\"transIntensity\":0.2}");
        LyricAppearance restored = LyricAppearance.parse(a.toJson());
        assertEquals(0.3f, restored.mainIntensity, 0f);
        assertEquals(0.8f, restored.bgIntensity, 0f);
        assertEquals(0.2f, restored.transIntensity, 0f);
        assertEquals(1f, restored.mainOpacity, 0f);
    }

    @Test public void defaultsMatchOfficialBackingStyle() {
        LyricAppearance a = LyricAppearance.parse("{}");
        assertEquals(LyricAppearance.OFFICIAL, a.bgMode);
        assertEquals(0.72f, a.bgOpacity, 0f);
        assertEquals(17f, LyricStyle.DEFAULT.bgSizeSp, 0f);
        assertEquals(17f, LyricStyle.DEFAULT.with("size", 36f).bgSizeSp, 0f);
        assertEquals(25f, LyricStyle.DEFAULT.with("bgsize", 30f).sizeSp, 0f);
        assertFalse(LyricStyle.DEFAULT.sameLayout(LyricStyle.DEFAULT.with("bgsize", 20f)));
    }
    @Test public void theTranslationSizeIsItsOwn() {
        assertEquals(15f, LyricStyle.DEFAULT.transSizeSp, 0f);
        assertEquals(25f, LyricStyle.DEFAULT.with("transsize", 30f).sizeSp, 0f);
        assertEquals(17f, LyricStyle.DEFAULT.with("transsize", 30f).bgSizeSp, 0f);
        assertFalse(LyricStyle.DEFAULT.sameLayout(LyricStyle.DEFAULT.with("transsize", 20f)));
    }
    @Test public void theTranslationLookIsItsOwn() {
        LyricAppearance a = LyricAppearance.DEFAULT;
        assertEquals(LyricAppearance.OFFICIAL, a.transMode);
        assertEquals(0.62f, a.transOpacity, 0f);
        // A JSON from before the translation had a row reads back looking as it did.
        LyricAppearance old = LyricAppearance.parse(
                "{\"mainMode\":1,\"mainOpacity\":0.5,\"bgMode\":2,\"bgOpacity\":0.3}");
        assertEquals(1, old.mainMode);
        assertEquals(0, old.transMode);
        assertEquals(0.62f, old.transOpacity, 0f);
        LyricAppearance t = new LyricAppearance(0, 0, -1, -1, 1f, 0.72f,
                LyricAppearance.CUSTOM, 0x123456, 0.4f);
        assertEquals(t.toJson(), LyricAppearance.parse(t.toJson()).toJson());
        assertEquals(0xff123456, LyricAppearance.parse(t.toJson()).transColor);
        assertEquals(0.4f, LyricAppearance.parse(t.toJson()).transOpacity, 0f);
    }
    @Test public void independentAppearanceSurvivesPersistence() {
        LyricAppearance a = new LyricAppearance(1, 2, 0x123456, 0xabcdef, .4f, .8f);
        LyricAppearance b = LyricAppearance.parse(a.toJson());
        assertEquals(a.toJson(), b.toJson());
        assertEquals(0xffabcdef, b.bgColor);
    }
    @Test public void systemColorsAndIndependentFontBoundsSurviveConfiguration() {
        LyricAppearance system = new LyricAppearance(LyricAppearance.SYSTEM, LyricAppearance.CUSTOM,
                0x123456, 0xabcdef, 1f, .72f);
        assertEquals(LyricAppearance.SYSTEM, LyricAppearance.parse(system.toJson()).mainMode);
        LyricStyle style = LyricStyle.DEFAULT.with("size", 99f).with("bgsize", -1f);
        assertEquals(36f, style.sizeSp, 0f);
        assertEquals(10f, style.bgSizeSp, 0f);
        assertSame(style, style.with("bgsize", Float.NaN));
    }
    @Test public void oldSettingsGiveTheSecondSingerTheExistingLeadControls() {
        LyricAppearance old = LyricAppearance.parse(
                "{\"mainMode\":2,\"mainColor\":1193046,\"mainOpacity\":0.4,\"mainIntensity\":0.8}");
        assertTrue(old.duetEnabled);
        assertEquals(old.mainMode, old.duetMode);
        assertEquals(old.mainColor, old.duetColor);
        assertEquals(old.mainOpacity, old.duetOpacity, 0f);
        assertEquals(old.mainIntensity, old.duetIntensity, 0f);
    }

    @Test public void independentDuetColourSurvivesDisablingAndReloading() {
        LyricAppearance a = new LyricAppearance(0, 1, -1, -1, 1f, .72f, 3, -1, .62f,
                .65f, .65f, .65f, false, LyricAppearance.CUSTOM, 0x123456, .4f, .8f);
        LyricAppearance saved = LyricAppearance.parse(a.toJson());
        assertFalse(saved.duetEnabled);
        assertEquals(LyricAppearance.CUSTOM, saved.duetMode);
        assertEquals(0xff123456, saved.duetColor);
        assertEquals(.4f, saved.duetOpacity, 0f);
        assertEquals(.8f, saved.duetIntensity, 0f);
        assertEquals(a.mainColor, saved.mainColor);
        assertEquals(a.bgMode, saved.bgMode);
        assertEquals(a.transMode, saved.transMode);
    }

    @Test public void disablingDuetOnlyChangesTheSecondLeadAndPreservesWords() {
        LyricLine first = new LyricLine("first", null, 100, 300, false, null, null, null);
        LyricLine second = new LyricLine("second", null, 300, 500, true,
                new int[] { 300 }, new int[] { 500 }, new int[] { 6 });
        LyricLine backing = new LyricLine("echo", null, 350, 550, true, null, null, null);
        second.addBackground(backing);
        assertEquals(LyricLine.Voice.LEAD, first.voice(true));
        assertFalse(first.oppositeSide(true));
        assertEquals(LyricLine.Voice.DUET, second.voice(true));
        assertTrue(second.oppositeSide(true));
        assertEquals(LyricLine.Voice.LEAD, second.voice(false));
        assertFalse(second.oppositeSide(false));
        assertEquals("second", second.text);
        assertEquals(3f, second.sungChars(400), 0f);
        assertEquals(LyricLine.Voice.BACKING, backing.voice(true));
        assertEquals(LyricLine.Voice.BACKING, backing.voice(false));
        assertTrue(backing.oppositeSide(false));
        assertSame(backing, second.backgrounds()[0]);
    }

    @Test public void standaloneBackingKeepsItsRoleWhenItsTranslationArrives() {
        LyricLine source = new LyricLine("echo", null, 0, 200, true, null, null, null).asBacking();
        LyricLine copy = new LyricLine("echo", "回声", 0, 200, true, null, null, null);
        copy.copyBackgrounds(source);
        assertEquals(LyricLine.Voice.BACKING, copy.voice(true));
        assertEquals(LyricLine.Voice.BACKING, copy.voice(false));
        assertTrue(copy.oppositeSide(false));
    }

    @Test public void invalidSettingsHaveSafeDefaults() {
        assertSame(LyricAppearance.DEFAULT, LyricAppearance.parse("invalid"));
        LyricAppearance a = new LyricAppearance(-1, 9, -1, -1, Float.NaN, 5f);
        assertEquals(0, a.mainMode);
        assertEquals(0, a.bgMode);
        assertEquals(1f, a.mainOpacity, 0f);
        assertEquals(1f, a.bgOpacity, 0f);
    }
}
