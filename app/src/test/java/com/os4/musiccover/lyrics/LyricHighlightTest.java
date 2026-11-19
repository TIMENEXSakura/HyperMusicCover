package com.os4.musiccover.lyrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricHighlightTest {
    @Test public void lastSyllableEndsHighlightBeforePaddedRowEnds() {
        LyricLine lead = new LyricLine("aa", null, 100, 900, false,
                new int[]{100, 320}, new int[]{300, 580}, new int[]{1, 2});
        assertFalse(lead.highlightsAt(99));
        assertTrue(lead.highlightsAt(579));
        assertFalse(lead.highlightsAt(580));
        assertEquals(580, lead.nextHighlightChange(420));
        assertEquals(900, LyricFocus.endOf(lead));
    }

    @Test public void backingVocalDoesNotExtendLeadHighlight() {
        LyricLine lead = new LyricLine("lead", null, 100, 500, false, null, null, null);
        LyricLine backing = new LyricLine("backing", null, 450, 1000, false, null, null, null);
        lead.addBackground(backing);
        LyricLine translated = new LyricLine(lead.text, "translation", lead.start, lead.voiceEnd,
                false, null, null, null);
        translated.copyBackgrounds(lead);
        assertEquals(1000, LyricFocus.endOf(translated));
        assertTrue(translated.highlightsAt(499));
        assertFalse(translated.highlightsAt(500));
        assertTrue(translated.backgrounds()[0].highlightsAt(500));
        assertFalse(translated.backgrounds()[0].highlightsAt(1000));
        assertEquals(Integer.MAX_VALUE, translated.nextHighlightChange(500));
    }
}
