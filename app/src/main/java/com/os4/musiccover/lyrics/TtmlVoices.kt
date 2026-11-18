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

    data class Placement(val side: Boolean?, val backing: Boolean)
    class Resolved(val lines: List<ISyncedLine>, val placements: Map<ISyncedLine, Placement> = emptyMap())

    fun resolve(body: String, lines: List<ISyncedLine>, flexible: Boolean): Resolved {
        if (!body.contains('<')) return Resolved(lines)
        return try { resolve(TtmlXml.parse(body), lines, flexible) } catch (_: Exception) {
            // Malformed remote lyrics must never take the host down.
            Resolved(lines)
        }
    }

    private fun attr(n: TtmlXml.Node, key: String): String? =
        n.attr("ttm:$key") ?: n.attr(key)
    private fun roles(n: TtmlXml.Node) = attr(n, "role").orEmpty().lowercase().split(Regex("\\s+"))
    private fun name(n: TtmlXml.Node) = n.name().substringAfter(':')
    private fun key(text: String): String = text.filterNot { it.isWhitespace() || it in "()（）" }
    internal fun text(n: TtmlXml.Node, lead: Boolean, flexible: Boolean): String {
        if (roles(n).any { it in annotations } || lead && isBacking(n, flexible)) return ""
        return n.text().orEmpty() + n.children().joinToString("") { text(it, lead, flexible) }
    }
    internal fun time(raw: String?): Int? {
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
    private data class Word(val agent: String?, val side: Boolean?, val start: Int, val end: Int, val text: String)
    private data class Paragraph(val lead: Voice, val backing: Boolean, val backs: List<Voice>, val words: List<Word>)
    private fun agent(n: TtmlXml.Node) = attr(n, "agent")?.trim()?.takeIf { it.isNotEmpty() }
    private fun isBacking(n: TtmlXml.Node, flexible: Boolean) = roles(n).any { it == "x-bg" || flexible && it in backing }

    private fun resolve(root: TtmlXml.Node, lines: List<ISyncedLine>, flexible: Boolean): Resolved {
        val groups = HashSet<String>()
        val other = HashSet<String>()
        val agents = LinkedHashSet<String>()
        fun scan(n: TtmlXml.Node) {
            if (name(n) == "agent") {
                val id = n.attr("xml:id") ?: n.attr("id")
                if (id != null) {
                    when (n.attr("type")) {
                        "group" -> groups.add(id)
                        "other" -> other.add(id)
                        else -> agents.add(id)
                    }
                }
            }
            attr(n, "agent")?.split(Regex("\\s+"))?.filter { it !in groups }?.let { agents.addAll(it) }
            n.children().forEach(::scan)
        }
        scan(root)
        agents.removeAll(groups)
        agents.removeAll(other)
        val agentOrder = agents.toList()
        fun side(n: TtmlXml.Node, inherited: Boolean?, bg: Boolean): Boolean? {
            val agent = agent(n)
            if (agent != null) {
                if (agent in groups || agent.any { it.isWhitespace() }) return false
                if (agent in other) return true
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
        fun collect(n: TtmlXml.Node, inherited: Boolean?, inheritedAgent: String?, inheritedBacking: Boolean) {
            val own = side(n, inherited, false)
            val ownAgent = agent(n) ?: inheritedAgent
            val ownBacking = inheritedBacking || isBacking(n, flexible)
            if (name(n) == "p") {
                val backs = ArrayList<Voice>()
                val leadSides = LinkedHashSet<Boolean>()
                fun spans(child: TtmlXml.Node, parent: Boolean?) {
                    val bg = isBacking(child, flexible)
                    val placement = side(child, parent, bg)
                    if (bg) backs.add(Voice(key(text(child, false, flexible)), time(child.attr("begin")), placement))
                    else if (roles(child).none { it in annotations }) {
                        if (!child.text().isNullOrBlank() && placement != null) leadSides.add(placement)
                        child.children().forEach { spans(it, placement) }
                    }
                }
                val children = n.children()
                children.forEach { spans(it, own) }
                val leadText = n.text().orEmpty() + children.joinToString("") { text(it, true, flexible) }
                // Some exporters put the singer on the timed spans instead of the paragraph.
                // Only a single unambiguous side overrides the paragraph's inherited side.
                val leadSide = leadSides.singleOrNull() ?: own
                val spanAgents = children.any { name(it) == "span" && agent(it) != null }
                val words = if (!spanAgents) emptyList() else children.mapNotNull { span ->
                    if (name(span) != "span" || isBacking(span, flexible) || roles(span).any { it in annotations }) return@mapNotNull null
                    val start = time(span.attr("begin")) ?: return@mapNotNull null
                    val end = time(span.attr("end")) ?: return@mapNotNull null
                    Word(agent(span) ?: ownAgent, side(span, own, false), start, end, key(span.text().orEmpty()))
                }
                paragraphs.add(Paragraph(Voice(key(leadText), time(n.attr("begin")), leadSide), ownBacking, backs, words))
            } else n.children().forEach { collect(it, own, ownAgent, ownBacking) }
        }
        collect(root, null, null, false)
        val unused = paragraphs.toMutableList()
        val placements = IdentityHashMap<ISyncedLine, Placement>()
        val expanded = IdentityHashMap<ISyncedLine, List<KaraokeLine.MainKaraokeLine>>()
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
            placements[line] = Placement(paragraph.lead.side, paragraph.backing)
            if (line is KaraokeLine.MainKaraokeLine && !paragraph.backing) {
                val parts = simultaneousSingers(line, paragraph.words)
                if (parts.isNotEmpty()) {
                    expanded[line] = parts.map { it.first }
                    for ((part, side) in parts) placements[part] = Placement(side, false)
                }
            }
            val available = paragraph.backs.toMutableList()
            for (bg in (line as? KaraokeLine.MainKaraokeLine)?.accompanimentLines.orEmpty()) {
                val choices = available.filter { it.text == content(bg) }
                val voice = choices.firstOrNull { it.start == bg.start } ?: choices.singleOrNull()
                    ?: available.filter { it.start == bg.start }.singleOrNull() ?: continue
                available.remove(voice)
                placements[bg] = Placement(voice.side, true)
            }
        }
        return Resolved(if (expanded.isEmpty()) lines else lines.flatMap { expanded[it] ?: listOf(it) }, placements)
    }

    // lyrics-core combines every sung span of a paragraph into one line. Split only explicit
    // singers whose timed words overlap and match the parser's words exactly. The paragraph's
    // translation, romanisation and backgrounds stay on its first singer, each exactly once.
    private fun simultaneousSingers(line: KaraokeLine.MainKaraokeLine, words: List<Word>):
        List<Pair<KaraokeLine.MainKaraokeLine, Boolean>> {
        if (words.size < 2 || words.size != line.syllables.size) return emptyList()
        if (words.indices.any { i ->
                val w = words[i]
                val s = line.syllables[i]
                w.agent == null || w.side == null || w.start != s.start || w.end != s.end || w.text != key(s.content)
            }) return emptyList()
        val groups = words.indices.groupBy { words[it].agent }
        if (groups.size < 2) return emptyList()
        val ends = HashMap<String?, Int>()
        val overlaps = words.sortedBy { it.start }.any { w ->
            val overlapsOther = ends.any { (singer, end) -> singer != w.agent && end > w.start }
            ends[w.agent] = maxOf(ends[w.agent] ?: w.end, w.end)
            overlapsOther
        }
        if (!overlaps) return emptyList()
        return groups.values.mapIndexed { index, indices ->
            val syllables = indices.map { line.syllables[it] }
            val first = index == 0
            val row = line.copy(syllables = syllables, start = syllables.minOf { it.start }, end = syllables.maxOf { it.end },
                translation = if (first) line.translation else null, phonetic = if (first) line.phonetic else null,
                accompanimentLines = if (first) line.accompanimentLines else null)
            row to requireNotNull(words[indices.first()].side)
        }
    }
}
