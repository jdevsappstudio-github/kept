package com.jdevsappstudio.kept.data.ask

import com.jdevsappstudio.kept.core.model.SaveType

/** A read save as Ask sees it: everything the prompt may quote, plus the search body for ranking. */
data class SourceSave(
    val id: Long,
    val createdAt: Long,
    val title: String,
    val type: String,
    val kind: String = "",
    val tags: List<String> = emptyList(),
    val items: List<String> = emptyList(),
    val place: String = "",
    val date: String = "",
    val link: String = "",
    val summary: String = "",
    val fullText: String = "",
    /** The FTS body (title, summary, kind, type, tags, items, text): what [AskQuery.hits] scores. */
    val body: String = "",
)

/** One past turn the model remembers. */
data class AskHistory(val question: String, val answer: String)

/** The prompt and which save each [n] stands for ([saveIds] index 0 = [1]). The text is exactly what k-sent shows. */
data class BuiltPrompt(val text: String, val saveIds: List<Long>)

/**
 * Ask source choice + prompt (BUILD-SPEC §3: top 20 matches, about 6k characters; H58).
 * Pure functions so ranking, the budget and ref mapping are unit-tested.
 */
object AskPrompt {
    const val MAX_SOURCES = 20
    const val SOURCE_BUDGET = 6_000
    /** Each source gets an even share of the budget, never less than this (fewer, fuller sources beat 20 stubs). */
    private const val MIN_PER_SOURCE = 280
    const val HISTORY_TURNS = 6
    private const val HISTORY_ANSWER_CHARS = 600
    const val MAX_QUESTION_CHARS = 500

    /**
     * Best first: words hit in the title/kind/tags/items count 3, in the body 1; ties go to the newest.
     * [carryOver] (the previous turn's sources, H58) fill the free slots after the fresh hits, so a follow-up like
     * 'only ones open Tuesday' keeps talking about the same saves.
     */
    fun rank(query: AskQuery, hits: List<SourceSave>, carryOver: List<SourceSave> = emptyList()): List<SourceSave> {
        val fresh = hits
            .map { it to score(query, it) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<SourceSave, Int>> { it.second }.thenByDescending { it.first.createdAt })
            .map { it.first }
        val seen = fresh.mapTo(HashSet()) { it.id }
        return (fresh + carryOver.filter { seen.add(it.id) }).take(MAX_SOURCES)
    }

    private fun score(query: AskQuery, s: SourceSave): Int {
        val head = buildString {
            append(s.title).append(' ').append(s.kind).append(' ')
            s.tags.joinTo(this, " ").append(' ')
            s.items.joinTo(this, " ")
        }
        return 3 * query.hits(head) + query.hits(s.body)
    }

    fun build(question: String, history: List<AskHistory>, sources: List<SourceSave>): BuiltPrompt {
        val used = sources.take(MAX_SOURCES)
        val per = if (used.isEmpty()) 0 else maxOf(MIN_PER_SOURCE, SOURCE_BUDGET / used.size)
        val blocks = ArrayList<String>(used.size)
        var total = 0
        for ((i, s) in used.withIndex()) {
            val block = sourceBlock(i + 1, s).clip(per)
            if (total + block.length > SOURCE_BUDGET && blocks.isNotEmpty()) break
            blocks += block
            total += block.length
        }
        val text = buildString {
            appendLine("You answer questions about the person's own saved screenshots and links, kept in the Kept app.")
            appendLine("Use only the numbered saves below; don't add facts that aren't in them. If they don't answer the question, say so.")
            appendLine("Keep it short and friendly. Refer to saves by title.")
            if (history.isNotEmpty()) {
                appendLine()
                appendLine("Earlier in this chat:")
                history.takeLast(HISTORY_TURNS).forEach {
                    appendLine("Q: ${it.question.oneLine()}")
                    appendLine("A: ${it.answer.oneLine().clip(HISTORY_ANSWER_CHARS)}")
                }
            }
            appendLine()
            if (blocks.isEmpty()) appendLine("Saves: none match.") else {
                appendLine("Saves:")
                blocks.forEach { appendLine(it) }
            }
            appendLine()
            append("Question: ").append(question.trim().take(MAX_QUESTION_CHARS))
        }
        return BuiltPrompt(text, used.take(blocks.size).map { it.id })
    }

    /** The model's ref numbers → save ids: in its order, no repeats, out-of-range numbers dropped. */
    fun refIds(refs: List<Int>, saveIds: List<Long>): List<Long> =
        refs.mapNotNull { saveIds.getOrNull(it - 1) }.distinct()

    private fun sourceBlock(n: Int, s: SourceSave): String = buildString {
        append('[').append(n).append("] ").append(s.title.ifBlank { "Untitled" }.oneLine())
        val type = SaveType.entries.firstOrNull { it.wire == s.type }?.label
        listOfNotNull(type, s.kind.ifBlank { null }).takeIf { it.isNotEmpty() }?.let { append(" (").append(it.joinToString(", ")).append(')') }
        if (s.tags.isNotEmpty()) append("\nTags: ").append(s.tags.joinToString(", "))
        if (s.items.isNotEmpty()) append("\nItems: ").append(s.items.joinToString(", ") { it.oneLine() })
        if (s.place.isNotBlank()) append("\nPlace: ").append(s.place.oneLine())
        if (s.date.isNotBlank()) append("\nDate: ").append(s.date.oneLine())
        if (s.link.isNotBlank()) append("\nLink: ").append(s.link.trim())
        if (s.summary.isNotBlank()) append("\n").append(s.summary.oneLine())
        val extra = s.fullText.oneLine()
        if (extra.isNotBlank() && extra != s.summary.oneLine()) append("\nText: ").append(extra)
    }

    private fun String.oneLine() = replace(Regex("\\s+"), " ").trim()

    private fun String.clip(max: Int) = if (length <= max) this else take(max - 1).trimEnd() + "…"
}
