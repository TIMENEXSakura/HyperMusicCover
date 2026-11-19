package com.os4.musiccover.lyrics

import org.junit.Assert.*
import org.junit.Test

class AppleDuetRowsTest {
    private fun row(text: String, start: Int, right: Boolean = false) =
        LyricLine(text, "local translation", "romanisation", start, start + 1000, right,
            intArrayOf(start), intArrayOf(start + 520), intArrayOf(text.length))

    private fun document(vararg p: String) =
        """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:hmc="urn:hyper-music-cover:lyrics"><body><div>""" +
            p.joinToString("") + "</div></body></tt>"

    private fun cue(ms: Int, agent: String, body: String, confirmed: String? = null): String {
        val credit = confirmed?.let { " hmc:leadVoice=\"$it\"" }.orEmpty()
        return """<p begin="${ms}ms" end="${ms + 900}ms" ttm:agent="$agent"$credit>$body</p>"""
    }

    @Test fun exactCuesKeepAllProviderFieldsAndSongOrder() {
        val first = row("again", 1000)
        val second = row("again", 5500)
        val echo = row("echo", 6100)
        second.addBackground(echo)
        val sameTime = row("listen", 5500)
        val gap = row("···", 7300)
        val rows = listOf(first, second, sameTime, gap)
        val ttml = document(cue(1000, "v1", "again"), cue(5500, "v1", "listen"),
            cue(5500, "v2", """<span begin="5500ms" end="5700ms">again</span><span ttm:role="x-bg">echo</span><span ttm:role="x-translation">elsewhere</span>"""))
        val merged = AppleDuetRows.mergeExplicitVoices(rows, ttml)
        assertEquals(4, merged.size)
        assertSame(first, merged[0])
        assertSame(sameTime, merged[2])
        assertSame(gap, merged[3])
        val updated = merged[1]
        assertTrue(updated.opposite)
        assertEquals(second.text, updated.text)
        assertEquals(second.translation, updated.translation)
        assertEquals(second.roma, updated.roma)
        assertEquals(second.start, updated.start)
        assertEquals(second.end, updated.end)
        assertArrayEquals(second.sylStart, updated.sylStart)
        assertArrayEquals(second.sylEnd, updated.sylEnd)
        assertArrayEquals(second.charEnd, updated.charEnd)
        assertSame(echo, updated.backgrounds().single())
    }

    @Test fun ambiguousUnmatchedAndBackingCuesStayOwnedByProvider() {
        val rows = listOf(row("two",1000), row("two",1000), row("three",1800),
            row("four",3000), row("five",4000), row("back",5000).asBacking())
        val doc = document(cue(1000,"v2","two"), cue(1800,"v1","three"), cue(1800,"v2","three"),
            cue(3001,"v2","four"), cue(4000,"v2","five!"), cue(5000,"v2","back"))
        assertSame(rows, AppleDuetRows.mergeExplicitVoices(rows, doc))
    }

    @Test fun explicitWordVoicesAreUsedOnlyWhenAllSungWordsAgree() {
        val rows = listOf(row("AB",1000), row("CD",2000))
        val doc = document(cue(1000,"v1","""<span ttm:agent="v2">A</span><span ttm:agent="v2">B</span>"""),
            cue(2000,"v2","""<span>C</span><span ttm:agent="v1">D</span>"""))
        val merged = AppleDuetRows.mergeExplicitVoices(rows, doc)
        assertTrue(merged.first().opposite)
        assertSame(rows.last(), merged.last())
    }

    @Test fun aDifferentSongCanCorrectBothSidesWhileKeepingTheProviderForAmbiguousCues() {
        val id = "921357864"
        val rows = listOf(row("refrain", 1200), row("refrain", 4200), row("answer", 5700, true),
            row("first", 6100, true), row("choir", 8000, true), row("legacy", 8500, true),
            row("ambiguous", 9000, true), row("unmatched", 9500, true), row("overlap", 9900, true))
        val xml = document(cue(1200, "v1", "refrain", "v1"), cue(4200, "v2", "refrain", "v2"),
            cue(5700, "v2", "answer", "v2"), cue(6100, "v1", "first", "v1"),
            cue(8000, "v1", "choir", "unknown"), cue(8500, "v1", "legacy"),
            cue(9000, "v1", "ambiguous", "v1"), cue(9000, "v2", "ambiguous", "v2"),
            cue(9900, "v1", """over<span ttm:agent="v2">lap</span>""", "v1"))
        val payload = org.json.JSONObject().put("songId", id).put("ttml", xml).toString()
        val original = AppleDuetRows(id, rows)
        val merged = original.enriched(LyricSource.lyricRef(id, payload), payload)
        assertTrue(merged[1].opposite)
        assertFalse(merged[3].opposite)
        for (i in listOf(0, 2, 4, 5, 6, 7, 8)) assertSame(rows[i], merged[i])
        val partial = org.json.JSONObject().put("songId", id)
            .put("ttml", document(cue(5700, "v2", "answer", "v2"))).toString()
        assertSame(merged, merged.enriched(LyricSource.lyricRef(id, partial), partial))
    }

    @Test fun foreignSongAndStaleReferenceStayRejected() {
        val doc = document(cue(1000, "v2", "left"))
        val primary = AppleDuetRows("321", listOf(row("left",1000)))
        val wrongSong = org.json.JSONObject().put("songId","123").put("ttml",doc).toString()
        assertSame(primary, primary.enriched(LyricSource.lyricRef("321", wrongSong), wrongSong))
        val payload = org.json.JSONObject().put("songId","321").put("ttml",doc).toString()
        assertSame(primary, primary.enriched("stale-ref",payload))
        assertTrue(primary.enriched(LyricSource.lyricRef("321",payload),payload).single().opposite)
    }
}
