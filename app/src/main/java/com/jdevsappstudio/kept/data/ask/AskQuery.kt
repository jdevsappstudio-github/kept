package com.jdevsappstudio.kept.data.ask

import com.jdevsappstudio.kept.data.search.SearchQuery
import java.text.Normalizer

/**
 * Turns a chat question into an FTS4 query for Ask sources (H58). Unlike search (every word must match), a question
 * is mostly filler ("which thriller should I watch first?"), so filler words go and the rest are OR-ed; ranking
 * ([hits]) then puts saves that match more of the words on top. Plurals and kind synonyms as in search (H54).
 */
class AskQuery private constructor(val words: List<String>) {

    val isBlank: Boolean get() = words.isEmpty()

    private val forms: List<List<String>> = words.map { word ->
        val one = SearchQuery.singular(word)
        val kind = SearchQuery.canonical(one)
        (SearchQuery.prefixForms(word) + if (kind != one) SearchQuery.prefixForms(kind) else emptyList()).distinct()
    }

    /** The FTS4 `MATCH` argument: any form of any word, as a prefix. Empty when [isBlank]. */
    val match: String get() = forms.flatten().distinct().joinToString(" OR ") { "$it*" }

    /** How many of the question's words appear in [text] (as word prefixes, accents folded). */
    fun hits(text: String): Int {
        if (words.isEmpty() || text.isEmpty()) return 0
        val tokens = tokens(text)
        return forms.count { wordForms -> wordForms.any { f -> val ff = fold(f); tokens.any { it.startsWith(ff) } } }
    }

    override fun equals(other: Any?) = other is AskQuery && other.words == words
    override fun hashCode() = words.hashCode()
    override fun toString() = "AskQuery($match)"

    companion object {
        private const val MAX_WORDS = 8

        fun parse(question: String): AskQuery =
            AskQuery(split(question).filter { it.length > 1 && it !in STOP }.distinct().take(MAX_WORDS))

        private fun split(text: String): List<String> =
            text.lowercase().split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }

        private fun tokens(text: String): Set<String> = split(fold(text)).toSet()

        /** Same folding as unicode61 for Latin text: 'café' and 'cafe' are one word. */
        private fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

        /** Question filler. Content words (cook, watch, goa, thriller) stay. */
        private val STOP = setOf(
            "a", "an", "the", "i", "me", "my", "mine", "we", "us", "our", "you", "your", "it", "its", "they", "them",
            "is", "are", "was", "were", "be", "been", "am", "do", "does", "did", "done", "have", "has", "had",
            "what", "which", "who", "whom", "whose", "when", "where", "why", "how", "that", "this", "these", "those",
            "there", "here", "of", "in", "on", "at", "to", "for", "from", "with", "about", "by", "as", "into", "over",
            "and", "or", "but", "not", "no", "so", "if", "then", "than", "too", "very", "just", "only", "also",
            "can", "could", "should", "would", "will", "shall", "may", "might", "must",
            "any", "some", "all", "more", "most", "much", "many", "few", "one", "ones", "other", "else",
            "first", "next", "best", "good", "great", "like", "get", "got", "give", "show", "tell", "find", "list",
            "saved", "save", "saves", "kept", "keep", "please", "want", "need", "know", "remind", "something", "anything",
            "thing", "things", "stuff", "pick", "choose", "suggest", "recommend", "worth",
        )
    }
}
