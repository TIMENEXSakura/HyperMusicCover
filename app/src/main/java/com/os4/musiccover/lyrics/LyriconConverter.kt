package com.os4.musiccover.lyrics

import com.os4.musiccover.Settings
import io.github.proify.lyricon.lyric.model.RichLyricLine

/** Android-independent conversion of the main and secondary tracks received over Lyricon. */
internal object LyriconConverter {
    fun convert(line: RichLyricLine, nextStart: Int, flexible: Boolean = Settings.LYRIC_FLEX_DUET.on()): LyricLine? {
        val words = line.words.orEmpty().filter { !it.text.isNullOrBlank() }
        val published = line.text?.trim().orEmpty()
        val joined = line.words.orEmpty().joinToString("") { it.text.orEmpty() }.trim()
        val text = published.ifEmpty { joined }
        val begin = line.begin.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val end = line.end.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        // The line and its timed words travel separately. Some providers time only a
        // prefix. Keep the published lyric intact, and use timing only when it covers it.
        val offsets = if (words.isNotEmpty() && words.all {
            it.begin in 0L..Int.MAX_VALUE.toLong() && it.end in 0L..Int.MAX_VALUE.toLong()
        }) LyriconBackground.mapWords(text, words.map { it.text.orEmpty() }) else null
        val built = if (text.isEmpty()) null else {
            val starts = offsets?.let { IntArray(words.size) { words[it].begin.toInt() } }
            val ends = offsets?.let { IntArray(words.size) { maxOf(words[it].begin, words[it].end).toInt() } }
            if (starts != null && ends != null) LyricParse.closeUntimedTail(starts, ends, end, nextStart)
            LyricLine(text, line.translation, LyricParse.romaOf(line.roma, text), begin, end,
                line.isAlignedRight, starts, ends, offsets)
        }
        val backing = LyriconBackground.convert(line, nextStart, flexible)
        if (built == null) {
            if (backing == null) return null
            return LyricLine(backing.text, line.translation, backing.roma, backing.start,
                backing.end, backing.opposite, backing.sylStart, backing.sylEnd, backing.charEnd).asBacking()
        }
        // The background vocal, which the renderer hangs under the line rather than beside it.
        if (backing != null) built.addBackground(backing)
        return built
    }

}
