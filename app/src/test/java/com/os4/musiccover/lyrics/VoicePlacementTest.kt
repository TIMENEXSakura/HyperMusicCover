package com.os4.musiccover.lyrics

import com.os4.musiccover.Settings
import org.junit.Assert.*
import org.junit.Test

class VoicePlacementTest {
    private fun parse(paragraphs: String, tolerant: Boolean = true): List<LyricLine> {
        val old = Settings.LYRIC_FLEX_DUET.on()
        Settings.LYRIC_FLEX_DUET.set(tolerant)
        try {
            return LyricParse.parse("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><head><metadata><ttm:agent xml:id="v1" type="person"/><ttm:agent xml:id="v2" type="person"/></metadata></head><body><div>$paragraphs</div></body></tt>""")
        } finally { Settings.LYRIC_FLEX_DUET.set(old) }
    }
    private fun p(agent: String, text: String, bg: String, bgAgent: String = "", role: String = "x-bg") =
        """<p begin="00:01.000" end="00:08.000" ttm:agent="$agent"><span begin="00:01.000" end="00:03.000">$text</span><span ttm:role="$role" $bgAgent begin="00:02.000" end="00:08.000"><span begin="00:02.000" end="00:08.000">($bg)</span></span></p>"""

    @Test fun nestedBackgroundAgentOverridesParagraphOnBothSides() {
        val lines = parse(p("v1", "Left lead", "Right harmony", "ttm:agent=\"v2\"") +
            p("v2", "Right lead", "Left harmony", "ttm:agent=\"v1\""))
        assertEquals(2, lines.size)
        assertFalse(lines[0].opposite)
        assertTrue(lines[0].backgrounds().single().opposite)
        assertTrue(lines[1].opposite)
        assertFalse(lines[1].backgrounds().single().opposite)
    }
    @Test fun toleranceRecognizesHarmonyAliasesWithoutInventingSingersFromWidth() {
        for (role in listOf("harmony", "x-harmony", "backing", "和声")) {
            val line = parse(p("v1", "Lead", "Very long harmony ".repeat(20).trim(), role = role)).single()
            assertFalse(line.opposite)
            assertEquals(1, line.backgrounds().size)
            assertTrue(line.backgrounds().single().opposite)
            assertEquals(8000, line.backgrounds().single().end)
        }
    }
    @Test fun strictModeStillHonoursExplicitAgents() {
        val line = parse(p("v1", "Lead", "Echo", "ttm:agent=\"v2\""), false).single()
        assertTrue(line.backgrounds().single().opposite)
        assertFalse(parse(p("v1", "Lead", "Echo"), false).single().backgrounds().single().opposite)
    }
    @Test fun changingLeadDoesNotFlipNumberedAgentIdentity() {
        val line = parse(p("v2", "Second singer starts", "First responds", "ttm:agent=\"v1\""), false).single()
        assertTrue(line.opposite)
        assertFalse(line.backgrounds().single().opposite)
    }
    @Test fun singerOnTimedSpansIsNotLostWhenParagraphHasNoAgent() {
        val lines = parse("""<p begin="00:01.000" end="00:04.000"><span begin="00:01.000" end="00:04.000" ttm:agent="v2">Right singer</span></p>""")
        assertEquals(1, lines.size)
        assertTrue(lines.single().opposite)
    }
    @Test fun independentHarmonyAndDuetAtSameTimeKeepTheirOwnRole() {
        val lines = parse("""<p begin="00:01.000" end="00:04.000" ttm:role="harmony">Harmony</p><p begin="00:01.000" end="00:04.000" ttm:agent="v1">Lead</p><p begin="00:05.000" end="00:08.000" ttm:role="duet">Duet</p>""")
        assertEquals(3, lines.size)
        assertTrue(lines.single { it.text == "Harmony" }.opposite)
        assertFalse(lines.single { it.text == "Lead" }.opposite)
        assertTrue(lines.single { it.text == "Duet" }.opposite)
    }

}
