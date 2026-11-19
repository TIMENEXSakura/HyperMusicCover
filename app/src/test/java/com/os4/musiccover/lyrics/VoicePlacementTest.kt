package com.os4.musiccover.lyrics

import com.os4.musiccover.Settings
import org.junit.Assert.*
import org.junit.Test

class VoicePlacementTest {
    private fun parse(paragraphs: String, tolerant: Boolean = true,
                      metadata: String = """<ttm:agent xml:id="v1" type="person"/><ttm:agent xml:id="v2" type="person"/>"""): List<LyricLine> {
        val old = Settings.LYRIC_FLEX_DUET.on()
        Settings.LYRIC_FLEX_DUET.set(tolerant)
        try {
            return LyricParse.parse("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><head><metadata>$metadata</metadata></head><body><div>$paragraphs</div></body></tt>""")
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
        assertEquals(LyricLine.Voice.BACKING, lines.first { it.text == "Harmony" }.voice(false))
        assertEquals(LyricLine.Voice.DUET, lines.first { it.text == "Duet" }.voice(true))
    }

    @Test fun concurrentParagraphsKeepEachSingerAndTheExplicitTranslation() {
        val rows = """<p ttm:agent="v2" begin="00:01.000" end="00:05.000"><span begin="00:01.000" end="00:05.000">回应</span><span ttm:role="x-translation">Answer</span></p>""" +
            """<p ttm:agent="v1" begin="00:01.000" end="00:04.000">Lead</p>""" +
            """<p ttm:role="x-bg" ttm:agent="v2" begin="00:01.000" end="00:03.000">(Echo)</p>"""
        val lines = LyricParse.splitInlineTranslations(parse(rows, tolerant = false, metadata = ""))
        assertEquals(listOf("回应", "Lead", "Echo"), lines.map { it.text })
        assertEquals(listOf(LyricLine.Voice.DUET, LyricLine.Voice.LEAD, LyricLine.Voice.BACKING),
            lines.map { it.voice(true) })
        assertEquals(listOf("Answer", null, null), lines.map { it.translation })
        assertEquals(listOf(false, false, true), lines.map { it.oppositeSide(false) })
    }

    @Test fun overlappingTimedAgentsInOneParagraphAreSungLinesEvenWithAThinSpace() {
        val thin = ' '
        val rows = """<p ttm:agent="v1" begin="00:01.000" end="00:06.000">""" +
            """<span begin="00:01.000" end="00:02.000">Call$thin</span><span ttm:agent="v2" begin="00:01.000" end="00:03.000">回</span>""" +
            """<span begin="00:02.000" end="00:04.000">again</span><span ttm:agent="v2" begin="00:03.000" end="00:06.000">应</span>""" +
            """<span ttm:role="x-translation">呼唤</span><span ttm:role="x-bg"><span begin="00:02.000" end="00:03.000">(echo)</span></span></p>"""
        val lines = LyricParse.splitInlineTranslations(parse(rows, metadata = ""))
        assertEquals(2, lines.size)
        val lead = lines.first()
        val duet = lines.last()
        assertEquals("Call${thin}again", lead.text)
        assertEquals("呼唤", lead.translation)
        assertEquals("回应", duet.text)
        assertNull(duet.translation)
        assertEquals(LyricLine.Voice.LEAD, lead.voice(true))
        assertEquals(LyricLine.Voice.DUET, duet.voice(true))
        assertEquals(LyricLine.Voice.BACKING, lead.backgrounds().single().voice(false))
        assertTrue(duet.backgrounds().isEmpty())
        assertArrayEquals(intArrayOf(1000, 3000), duet.sylStart)
        assertArrayEquals(intArrayOf(3000, 6000), duet.sylEnd)
    }

    @Test fun groupOtherAndThirdPersonKeepStableLanesWithoutRenumberingPeople() {
        val metadata = """<ttm:agent xml:id="choir" type="group"/><ttm:agent xml:id="effect" type="other"/>""" +
            """<ttm:agent xml:id="alex" type="person"/><ttm:agent xml:id="sam" type="person"/>"""
        val ids = listOf("effect", "alex", "sam", "choir", "v3", "v2", "v1", "v1 v2")
        val rows = ids.mapIndexed { i, id ->
            """<p begin="00:0${i + 1}.000" end="00:0${i + 2}.000" ttm:agent="$id">$id</p>"""
        }.joinToString("")
        assertEquals(listOf(true, false, true, false, false, true, false, false),
            parse(rows, metadata = metadata).map { it.opposite })
    }

    @Test fun englishHarmonyAndDuetPrefixesDoNotChangeStructuredSingersBesideThem() {
        fun row(text: String, start: Int, right: Boolean = false) =
            LyricLine(text, null, start, start + 900, right, null, null, null)
        val structured = row("Singer B", 1000, true)
        val oldLead = row("Lead again", 4000)
        val parsed = LyricParse.flexibleSpeakers(listOf(structured, row("Harmony: (echo)", 2000),
            row("Duet: answer", 3000), oldLead), true)
        assertSame(structured, parsed.first())
        assertSame(oldLead, parsed.last())
        assertEquals(listOf("Singer B", "echo", "answer", "Lead again"), parsed.map { it.text })
        assertEquals(listOf(LyricLine.Voice.DUET, LyricLine.Voice.BACKING,
            LyricLine.Voice.DUET, LyricLine.Voice.LEAD), parsed.map { it.voice(true) })
    }

}
