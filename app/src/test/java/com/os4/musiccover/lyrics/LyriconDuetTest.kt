package com.os4.musiccover.lyrics

import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import org.junit.Assert.*
import org.junit.Test

class LyriconDuetTest {
    private fun convert(line: RichLyricLine) = requireNotNull(LyriconConverter.convert(line, 9000))

    @Test fun flexiblePrefixesKeepWordTimesTranslationsAndBackgrounds() {
        val female = convert(RichLyricLine(begin = 1000, end = 2000, text = "女：你好",
            words = listOf(LyricWord(text = "女：", begin = 1000, end = 1100),
                LyricWord(text = "你好", begin = 1100, end = 2000)),
            translation = "hello", roma = "ni hao", secondary = "(回声)",
            secondaryWords = listOf(LyricWord(text = "回声", begin = 1200, end = 2500))))
        val male = convert(RichLyricLine(begin = 3000, end = 4000, text = "男：再见"))
        val input = listOf(female, male)
        val disabled = LyricParse.flexibleSpeakers(input, false)
        assertSame(input, disabled)
        assertEquals(listOf("女：你好", "男：再见"), disabled.map { it.text })
        val enabled = LyricParse.flexibleSpeakers(input, true)
        assertEquals(listOf("你好", "再见"), enabled.map { it.text })
        assertEquals(listOf(false, true), enabled.map { it.opposite })
        assertArrayEquals(intArrayOf(1100), enabled[0].sylStart)
        assertArrayEquals(intArrayOf(2000), enabled[0].sylEnd)
        assertArrayEquals(intArrayOf(2), enabled[0].charEnd)
        assertEquals("hello", enabled[0].translation)
        assertEquals("ni hao", enabled[0].roma)
        assertEquals("回声", enabled[0].backgrounds().single().text)
        assertEquals(2500, enabled[0].backgrounds().single().end)
    }

    @Test fun existingStructuredAlignmentWinsAndBackgroundOnlyRowsSurvive() {
        val ordinary = convert(RichLyricLine(begin = 1000, end = 2000, text = "女：你好"))
        val aligned = convert(RichLyricLine(begin = 3000, end = 4000, text = "男：再见", isAlignedRight = true))
        val standard = listOf(ordinary, aligned)
        assertSame(standard, LyricParse.flexibleSpeakers(standard, true))
        assertEquals(listOf(false, true), standard.map { it.opposite })
        val background = convert(RichLyricLine(begin = 2500, end = 2800,
            secondaryWords = listOf(LyricWord(text = "echo", begin = 2500, end = 3500))))
        val lines = LyricParse.flexibleSpeakers(listOf(ordinary, background,
            convert(RichLyricLine(begin = 4000, end = 5000, text = "男：再见"))), true)
        assertEquals(3, lines.size)
        assertEquals("", lines[1].text)
        assertEquals("echo", lines[1].backgrounds().single().text)
        assertEquals(3500, lines[1].backgrounds().single().end)
    }
}
