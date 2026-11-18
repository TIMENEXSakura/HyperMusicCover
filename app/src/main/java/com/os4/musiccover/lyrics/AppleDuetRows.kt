package com.os4.musiccover.lyrics

import org.json.JSONObject

/** Keeps the selected provider's catalogue id with its rows when they enter the track cache. */
internal class AppleDuetRows(
    val songId: String,
    private val primary: List<LyricLine>,
    private val shown: List<LyricLine> = primary,
) : AbstractList<LyricLine>() {
    override val size get() = shown.size
    override fun get(index: Int) = shown[index]

    // Owned by the main thread. An unchanged reference needs at most one request and one parse.
    @JvmField var requestedRef: String? = null
    @JvmField var pendingRef: String? = null
    @JvmField var checkedRef: String? = null

    fun splitTranslations(): AppleDuetRows {
        val split = LyricParse.splitInlineTranslations(primary)
        return if (split === primary) this else AppleDuetRows(songId, split)
    }

    fun enriched(ref: String, payload: String): AppleDuetRows {
        if (ref != LyricSource.lyricRef(songId, payload)) return this
        return try {
            val json = JSONObject(payload)
            if (json.opt("songId") != songId) return this
            val ttml = json.opt("ttml") as? String ?: return this
            val next = mergeExplicitVoices(shown, ttml)
            if (next === shown) this else AppleDuetRows(songId, primary, next)
        } catch (_: Exception) {
            this
        }
    }

    companion object {
        const val PLAYER = "com.apple.android.music"

        private data class Cue(val start: Int, val text: String)

        private fun explicitSide(paragraph: TtmlXml.Node): Boolean? {
            if (paragraph.attr("ttm:role") != null) return null
            val proof = paragraph.attr("hmc:leadVoice")
            if (proof != null && proof != "v1" && proof != "v2") return null
            val agents = mutableSetOf<String?>()
            fun sung(node: TtmlXml.Node, inherited: String?) {
                if (node.attr("ttm:role") != null) return
                val agent = node.attr("ttm:agent") ?: inherited
                if (!node.text().isNullOrBlank()) agents.add(agent)
                node.children().forEach { sung(it, agent) }
            }
            sung(paragraph, null)
            val agent = agents.singleOrNull() ?: return null
            // Older payloads used v1 for both the first singer and unassigned/group rows.
            // Only v2 is positive evidence from that writer.
            if (proof != null && proof != agent) return null
            return when (agent) {
                "v1" -> if (proof == "v1") false else null
                "v2" -> true
                else -> null
            }
        }

        /** Read the explicit paragraphs written by AppleLyrics.ttml(). No guessing from lyric text. */
        fun mergeExplicitVoices(primary: List<LyricLine>, ttml: String): List<LyricLine> {
            val voices = HashMap<Cue, Boolean?>()
            fun collect(node: TtmlXml.Node) {
                if (node.name() == "p") {
                    val start = TtmlVoices.time(node.attr("begin")) ?: return
                    val text = TtmlVoices.text(node, true, false).trim()
                    if (text.isEmpty()) return
                    val cue = Cue(start, text)
                    // Two paragraphs with the same start and words cannot identify a voice.
                    voices[cue] = if (voices.containsKey(cue)) null else explicitSide(node)
                } else {
                    for (child in node.children()) collect(child)
                }
            }
            collect(TtmlXml.parse(ttml))
            if (voices.values.all { it == null }) return primary
            val cues = primary.map { Cue(it.start, it.text.trim()) }
            val occurrences = cues.groupingBy { it }.eachCount()
            var out: MutableList<LyricLine>? = null
            for ((index, line) in primary.withIndex()) {
                val cue = cues[index]
                val side = voices[cue] ?: continue
                if (line.opposite == side || occurrences[cue] != 1 || line.voice(true) == LyricLine.Voice.BACKING) continue
                val updated = LyricLine(line.text, line.translation, line.roma, line.start, line.end,
                    side, line.sylStart, line.sylEnd, line.charEnd)
                updated.copyBackgrounds(line)
                if (out == null) out = primary.toMutableList()
                out[index] = updated
            }
            return out ?: primary
        }
    }
}
