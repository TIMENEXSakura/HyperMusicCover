package com.os4.musiccover.lyrics

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import java.util.IdentityHashMap

/** Resolve voices before the parser's inherited paragraph alignment can hide a span's agent. */
internal object TtmlVoices {
    private val backing = setOf("x-bg", "bg", "background", "harmony", "x-harmony", "backing", "x-backing", "和声", "和聲", "和音", "伴唱")
    private val second = setOf("duet", "x-duet", "对唱", "對唱", "二重唱")
    private val annotations = setOf("x-translation", "translation", "x-roman", "x-romanization", "romanization")
    private val roleAttribute = Regex("((?:[\\w-]+:)?role\\s*=\\s*)([\"'])(.*?)(\\2)")

    /** Only span roles: a paragraph carrying 'harmony' is still a visible independent line. */
    fun parserBody(body: String, flexible: Boolean): String {
        if (!flexible || !body.contains('<')) return body
        return Regex("<span\\b[^>]*>").replace(body) { tag ->
            roleAttribute.replace(tag.value) { a ->
                val roles = a.groupValues[3].lowercase().split(Regex("\\s+"))
                if (roles.any { it in backing }) a.groupValues[1] + a.groupValues[2] + "x-bg" + a.groupValues[2]
                else a.value
            }
        }
    }

    fun resolve(body: String, lines: List<ISyncedLine>, flexible: Boolean): Map<ISyncedLine, Boolean> {
        if (!body.contains('<')) return emptyMap()
        return try { resolve(TtmlXml.parse(body), lines, flexible) } catch (_: Exception) {
            // Malformed remote lyrics must never take the host down.
            emptyMap()
        }
    }

    private fun attr(n: TtmlXml.Node, key: String): String? =
        n.attr("ttm:$key") ?: n.attr(key)
    private fun roles(n: TtmlXml.Node) = attr(n, "role").orEmpty().lowercase().split(Regex("\\s+"))
    private fun name(n: TtmlXml.Node) = n.name().substringAfter(':')
    private fun key(text: String): String = text.filterNot { it.isWhitespace() || it in "()（）" }
    private fun text(n: TtmlXml.Node, lead: Boolean): String {
        if (roles(n).any { it in annotations || lead && it in backing }) return ""
        return n.text().orEmpty() + n.children().joinToString("") { text(it, lead) }
    }
    private fun time(raw: String?): Int? {
        val s = raw ?: return null
        val ms = when {
            s.endsWith("ms") -> s.dropLast(2).toDoubleOrNull()
            s.endsWith('s') -> s.dropLast(1).toDoubleOrNull()?.times(1000)
            else -> {
                val parts = s.split(':')
                if (parts.size !in 1..3) return null
                var sec = 0.0
                for (part in parts) sec = sec * 60 + (part.toDoubleOrNull() ?: return null)
                sec * 1000
            }
        } ?: return null
        return if (ms.isFinite() && ms in 0.0..Int.MAX_VALUE.toDouble()) kotlin.math.round(ms).toInt() else null
    }
    private data class Voice(val text: String, val start: Int?, val side: Boolean?)
    private data class Paragraph(val lead: Voice, val backs: List<Voice>)

    private fun resolve(root: TtmlXml.Node, lines: List<ISyncedLine>, flexible: Boolean): Map<ISyncedLine, Boolean> {
        val groups = HashSet<String>()
        val agents = LinkedHashSet<String>()
        fun scan(n: TtmlXml.Node) {
            if (name(n) == "agent") {
                val id = n.attr("xml:id") ?: n.attr("id")
                if (id != null) {
                    if (n.attr("type") == "group") groups.add(id) else agents.add(id)
                }
            }
            attr(n, "agent")?.split(Regex("\\s+"))?.filter { it !in groups }?.let { agents.addAll(it) }
            n.children().forEach(::scan)
        }
        scan(root)
        agents.removeAll(groups)
        val agentOrder = agents.toList()
        fun side(n: TtmlXml.Node, inherited: Boolean?, bg: Boolean): Boolean? {
            val agent = attr(n, "agent")?.trim()?.takeIf { it.isNotEmpty() }
            if (agent != null) {
                if (agent in groups || agent.any { it.isWhitespace() }) return false
                val numbered = Regex("(?i)v([0-9]+)").matchEntire(agent)?.groupValues?.get(1)
                return if (numbered != null) (numbered.toIntOrNull() ?: 1) % 2 == 0
                    else agentOrder.indexOf(agent) % 2 == 1
            }
            val role = roles(n)
            // A distinct explicit singer wins; an unlabelled x-bg is the secondary voice in
            // tolerant mode, while strict mode keeps the author's inherited placement.
            if (flexible && (bg || role.any { it in backing || it in second })) return true
            return inherited
        }
        val paragraphs = ArrayList<Paragraph>()
        fun collect(n: TtmlXml.Node, inherited: Boolean?) {
            val own = side(n, inherited, false)
            if (name(n) == "p") {
                val backs = ArrayList<Voice>()
                val leadSides = LinkedHashSet<Boolean>()
                fun spans(child: TtmlXml.Node, parent: Boolean?) {
                    val bg = roles(child).any { it == "x-bg" || flexible && it in backing }
                    val placement = side(child, parent, bg)
                    if (bg) backs.add(Voice(key(text(child, false)), time(child.attr("begin")), placement))
                    else if (roles(child).none { it in annotations }) {
                        if (!child.text().isNullOrBlank() && placement != null) leadSides.add(placement)
                        child.children().forEach { spans(it, placement) }
                    }
                }
                n.children().forEach { spans(it, own) }
                val leadText = n.text().orEmpty() + n.children().joinToString("") { text(it, true) }
                // Some exporters put the singer on the timed spans instead of the paragraph.
                // Only a single unambiguous side overrides the paragraph's inherited side.
                val leadSide = leadSides.singleOrNull() ?: own
                paragraphs.add(Paragraph(Voice(key(leadText), time(n.attr("begin")), leadSide), backs))
            } else n.children().forEach { collect(it, own) }
        }
        collect(root, null)
        val unused = paragraphs.toMutableList()
        val result = IdentityHashMap<ISyncedLine, Boolean>()
        fun content(l: ISyncedLine): String = key(when (l) {
            is KaraokeLine -> l.syllables.joinToString("") { it.content }
            is SyncedLine -> l.content
            else -> ""
        })
        for (line in lines) {
            if (line is KaraokeLine.AccompanimentKaraokeLine) continue
            val words = content(line)
            // Match content as well as time; equal timestamps are common in duets. Never
            // assign the next paragraph's singer just because it happened to sort first.
            val matching = unused.filter { it.lead.text == words }
            val paragraph = matching.firstOrNull { it.lead.start == line.start }
                ?: matching.singleOrNull()
                ?: unused.filter { it.lead.start == line.start }.singleOrNull() ?: continue
            unused.remove(paragraph)
            paragraph.lead.side?.let { result[line] = it }
            val available = paragraph.backs.toMutableList()
            for (bg in (line as? KaraokeLine.MainKaraokeLine)?.accompanimentLines.orEmpty()) {
                val choices = available.filter { it.text == content(bg) }
                val voice = choices.firstOrNull { it.start == bg.start } ?: choices.singleOrNull()
                    ?: available.filter { it.start == bg.start }.singleOrNull() ?: continue
                available.remove(voice)
                voice.side?.let { result[bg] = it }
            }
        }
        return result
    }
}
