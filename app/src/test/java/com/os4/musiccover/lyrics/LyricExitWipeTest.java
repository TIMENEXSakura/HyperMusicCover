package com.os4.musiccover.lyrics;

import org.junit.Test;

import static org.junit.Assert.*;

public class LyricExitWipeTest {
    @Test public void firstSyllableIsGoneWhileLastStillHasItsFullInk() {
        for (int count : new int[] {2, 6, 80}) {
            assertEquals(0f, LyricExitWipe.opacity(0, count, 0.5f), 0f);
            assertEquals(1f, LyricExitWipe.opacity(count - 1, count, 0.5f), 0f);
            for (int field = 0; field < count; field++) {
                float previous = 1f;
                for (int frame = 0; frame <= 120; frame++) {
                    float alpha = LyricExitWipe.opacity(field, count, frame / 120f);
                    assertTrue(alpha <= previous);
                    previous = alpha;
                }
                assertEquals(0f, previous, 0f);
            }
        }
        assertEquals(0.5f, LyricExitWipe.opacity(0, 1, 0.5f), 0.0001f);
    }

    @Test public void mappedSyllablesStayTogetherEvenWhenOnlyTheWrappedTailIsVisible() {
        assertArrayEquals(new int[]{0, 0, 1, 1, 2},
                LyricExitWipe.fields("夜空 星光", 0, 5, new int[]{2, 4, 5}));
        assertArrayEquals(new int[]{0, 0, 1},
                LyricExitWipe.fields("夜空 星光", 2, 5, new int[]{2, 4, 5}));
    }

    @Test public void unsegmentedLyricFadesByGraphemeIncludingSupplementaryAndCombiningCharacters() {
        String text = "夜🌙e\u0301光";
        int[] byGrapheme = {0, 1, 1, 2, 2, 3};
        assertArrayEquals(byGrapheme, LyricExitWipe.fields(text, 0, text.length(), null));
        assertArrayEquals(byGrapheme, LyricExitWipe.fields(text, 0, text.length(), new int[]{text.length()}));
        assertArrayEquals(new int[]{0,1,1,2,2,3}, LyricExitWipe.fields(text, 0, text.length(),
                new int[]{1, 2, 4}));
    }
}
