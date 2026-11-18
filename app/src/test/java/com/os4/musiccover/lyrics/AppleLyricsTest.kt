package com.os4.musiccover.lyrics

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

class AppleLyricsTest {
    // Same getter names, native-vector wrappers and Long vector indices as the JNI objects.
    private class Ref(private val item: Any) { fun get() = item }
    private class Vec(vararg values: Any) {
        private val items = values
        fun size() = items.size.toLong()
        fun get(i: Long) = Ref(items[i.toInt()])
    }
    private class Agent(val id: String, private val type: Long) { fun getType_() = type }
    private class LegacyAgent(val id: String, val type: String)
    private class Word(val htmlLineText: String, val begin: Long, val end: Long,
                       val agent: String = "", val duration: Long = 0)
    private class Row(val htmlLineText: String, val begin: Long, val end: Long,
                      val agent: String = "", val words: Vec = Vec(), val bg: String = "",
                      private val backing: Vec = Vec()) {
        fun getHtmlBackgroundVocalsLineText(includeTranslation: Boolean) = if (includeTranslation) "" else bg
        fun getBackgroundWords(includeTranslation: Boolean) = if (includeTranslation) Vec() else backing
    }
    private class Section(val lines: Vec)
    private class Song(val sections: Vec, val agents: Vec)

    private fun extract(agents: Vec, vararg rows: Row): JSONObject {
        val song = Song(Vec(Section(Vec(*rows))), agents)
        val lines = AppleLyrics.readLines(song)
        return JSONObject(AppleLyrics.payload("12345", lines, AppleLyrics.readVoices(song, lines)))
    }
    private fun paragraphs(payload: JSONObject): List<Element> {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(InputSource(StringReader(payload.getString("ttml"))))
        val ps = xml.getElementsByTagName("p")
        return (0 until ps.length).map { ps.item(it) as Element }
    }

    @Test fun onlyAnActualSecondLeadCountsForTheNativeSnapshot() {
        val agents = Vec(Agent("v1", 1), Agent("v2", 5))
        fun isDuet(row: Row): Boolean {
            val song = Song(Vec(Section(Vec(row))), agents)
            val lines = AppleLyrics.readLines(song)
            return AppleLyrics.hasSecondLead(lines, AppleLyrics.readVoices(song, lines))
        }
        assertFalse(isDuet(Row("唱", 1000, 2000, "v1",
            backing = Vec(Word("和", 1200, 1800, "v2")))))
        assertFalse(isDuet(Row("", 1000, 2000, "v2", bg = "和")))
        assertTrue(isDuet(Row("答", 1000, 2000, "v2")))
        assertTrue(isDuet(Row("问答", 1000, 2000,
            words = Vec(Word("问", 1000, 2000, "v1"), Word("答", 1000, 2000, "v2")))))
    }

    @Test fun numericOtherCanSingSecondButNumericGroupCannot() {
        val payload = extract(Vec(Agent("lead", 1), Agent("ensemble", 3), Agent("guest", 5)),
            Row("一起", 1000, 2000, "ensemble"),
            Row("", 2000, 3000, words = Vec(Word("你", 2000, 2400, "guest"), Word("好", 2400, 3000, "guest"))),
            Row("回来", 3000, 4000, "lead", Vec(Word("回来", 3000, 4000, "guest"))))
        val ps = paragraphs(payload)
        assertEquals(listOf("一起", "你好", "回来"), ps.map { it.textContent })
        assertEquals(listOf("v1", "v2", "v1"), ps.map { it.getAttribute("ttm:agent") })
        assertEquals(2, ps[1].getElementsByTagName("span").length)
        assertTrue(payload.getString("lyric").contains("你好"))
        assertFalse(extract(Vec(Agent("lead", 1), Agent("ensemble", 3)),
            Row("唱", 1000, 2000, "lead"), Row("合唱", 2000, 3000, "ensemble")).has("ttml"))
    }

    @Test fun wordOnlyAndPlainBackgroundsSurviveWithTheirOwnSinger() {
        val payload = extract(Vec(LegacyAgent("first", "Person"), LegacyAgent("second", "Other")),
            Row("", 0, 0, words = Vec(Word("Hold ", 1000, 1500, "first"),
                Word("on", 1500, 2000, "first")),
                backing = Vec(Word("echo", 1300, 1800, "second"))),
            Row("", 2500, 3200, "second", bg = "again"))
        val ps = paragraphs(payload)
        assertEquals("00:00:01.000", ps[0].getAttribute("begin"))
        assertEquals("00:00:02.000", ps[0].getAttribute("end"))
        assertEquals("Hold onecho", ps[0].textContent)
        val bg = ps[0].getElementsByTagName("span").item(2) as Element
        assertEquals("x-bg", bg.getAttribute("ttm:role"))
        assertEquals("v2", bg.getAttribute("ttm:agent"))
        val solo = ps[1].firstChild as Element
        assertEquals("x-bg", solo.getAttribute("ttm:role"))
        assertEquals("again", solo.textContent)
        assertEquals("v2", solo.getAttribute("ttm:agent"))
        assertEquals("[00:01.000]Hold on\n[00:02.500]again\n", payload.getString("lyric"))
        val rendered = LyricParse.parse(payload.getString("ttml"))
        assertEquals(listOf("Hold on", "again"), rendered.map { it.text })
        assertEquals("echo", rendered.first().backgrounds().single().text)
        assertEquals(LyricLine.Voice.BACKING, rendered.last().voice(true))
    }

    @Test fun partialWordTrackNeverTruncatesAnyPartOfFullText() {
        val payload = extract(Vec(Agent("v1", 1), Agent("v2", 1)),
            Row("before middle after", 1000, 2000, "v1", Vec(Word("middle", 1300, 1600))),
            Row("one missed three", 2000, 3000, "v2", Vec(Word("one", 2000, 2500), Word("three", 2500, 3000))),
            Row("start tail", 3000, 4000, "v1", Vec(Word("start", 3000, 3400))),
            Row("“Ready, go!”", 4000, 5000, "v2", Vec(Word("Ready", 4000, 4500), Word("go", 4500, 0, duration = 500)),
                bg = "complete echo", backing = Vec(Word("echo", 4300, 4900))))
        val ps = paragraphs(payload)
        assertEquals(listOf("before middle after", "one missed three", "start tail"),
            ps.take(3).map { it.textContent })
        assertTrue(ps.take(3).all { it.getElementsByTagName("span").length == 0 })
        assertEquals("“Ready, go!”complete echo", ps.last().textContent)
        assertEquals("00:00:05.000", (ps.last().getElementsByTagName("span").item(1) as Element).getAttribute("end"))
        assertTrue(payload.getString("rawLyric").contains("[00:03.000]start tail\n"))
    }

    @Test fun partialLeadWithPlainBackingKeepsBothInTheReader() {
        val payload = extract(Vec(Agent("v1", 1)), Row("begin middle last", 1000, 2000, "v1",
            Vec(Word("begin", 1000, 1400)), bg = "echo"))
        val line = LyricParse.parse(payload.getString("ttml")).single()
        assertEquals("begin middle last", line.text)
        assertEquals("echo", line.backgrounds().single().text)
    }

    @Test fun simultaneousWordAgentsReachTheReaderWithoutARowCredit() {
        val payload = extract(Vec(Agent("first", 1), Agent("other", 5)), Row("呼应", 1000, 2500,
            words = Vec(Word("呼", 1000, 2500, "first"), Word("应", 1000, 2200, "other"))))
        val lines = LyricParse.parse(payload.getString("ttml"))
        assertEquals(listOf("呼", "应"), lines.map { it.text })
        assertEquals(listOf(LyricLine.Voice.LEAD, LyricLine.Voice.DUET), lines.map { it.voice(true) })
    }

    @Test fun noDeclarationUsesConsistentWordCreditsAndKeepsGroupCreditsNeutral() {
        val payload = extract(Vec(),
            Row("次", 1000, 2000, words = Vec(Word("次", 1000, 2000, "v2"))),
            Row("主", 2000, 3000, words = Vec(Word("主", 2000, 3000, "v1"))),
            Row("同唱", 3000, 4000, words = Vec(Word("同", 3000, 3500, "v1"), Word("唱", 3500, 4000, "v2"))),
            Row("大家", 4000, 5000, "v1 v2"))
        assertEquals(listOf("v2", "v1", "v1", "v1"), paragraphs(payload).map { it.getAttribute("ttm:agent") })
    }
}
