package com.jdevsappstudio.kept.data.search

import com.jdevsappstudio.kept.core.model.Shelf

/**
 * What the person typed, cleaned for FTS4 (H51 step 2, H54). Pure Kotlin, so every rule is unit-tested.
 *
 * Words = runs of letters, marks and digits (any script: Hindi vowel signs stay inside the word, and unicode61 then
 * splits it into a phrase exactly as it split the indexed text), lowercased; everything else (quotes, `-`, `*`, `:`) is dropped, so no
 * input can turn into FTS syntax. Each word is a prefix term; a plural also matches its singular ("movies" → `movie*`,
 * "stories" → `stories* OR story*`). Words are ANDed.
 */
class SearchQuery private constructor(val text: String, val words: List<String>) {

    val isBlank: Boolean get() = words.isEmpty()

    /** The FTS4 `MATCH` argument. Standard query syntax: OR binds tighter than the implicit AND. */
    val match: String
        get() = words.joinToString(" ") { word ->
            val forms = prefixForms(word)
            forms.joinToString(" OR ") { "$it*" }
        }

    /** H54 title-first order: saves whose title holds this go on top. What the first word's forms share ("stories" → "stor"). */
    val titleWord: String get() = words.firstOrNull()?.let { prefixForms(it).reduce(String::commonPrefixWith) }.orEmpty()

    /**
     * k-search-tag: the shelf this query names ('movies', 'films', 'shoes'), or null. A shelf's kind can be one word or
     * a few ('skincare products'); it matches when its singular words appear in order in the query.
     */
    fun matchShelf(shelves: List<Shelf>): Shelf? {
        if (words.isEmpty()) return null
        val query = words.map { canonical(singular(it)) }
        return shelves.firstOrNull { shelf ->
            val kind = split(shelf.kind).map { canonical(singular(it)) }
            kind.isNotEmpty() && query.windowed(kind.size).any { it == kind }
        }
    }

    override fun equals(other: Any?) = other is SearchQuery && other.words == words
    override fun hashCode() = words.hashCode()
    override fun toString() = "SearchQuery($match)"

    companion object {
        val EMPTY = SearchQuery("", emptyList())

        /** At most this many words reach SQLite; a pasted paragraph stays cheap. */
        private const val MAX_WORDS = 8

        fun parse(text: String): SearchQuery = SearchQuery(text.trim(), split(text).take(MAX_WORDS))

        private fun split(text: String): List<String> =
            text.lowercase().split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }

        /** Words whose trailing s isn't a plural. */
        private val INVARIANT = setOf("series", "news", "species", "glasses", "jeans", "shorts", "pants", "physics", "maths")

        /** Singulars ending in -ie (else "-ies" → "-y": stories → story). */
        private val IE_WORDS = setOf(
            "movie", "cookie", "selfie", "rookie", "zombie", "smoothie", "brownie", "calorie", "genie", "foodie",
            "indie", "veggie", "techie", "newbie", "freebie", "auntie", "hoodie", "beanie", "goalie", "boogie",
        )

        /** A small word list so 'films' finds the Movies shelf (H28: plurals and synonyms on device). Singular → kind word. */
        private val SYNONYMS = mapOf(
            "film" to "movie", "flick" to "movie", "cinema" to "movie",
            "tv" to "show", "serial" to "show", "series" to "show",
            "dish" to "recipe",
            "cafe" to "café",
            "sneaker" to "shoe", "footwear" to "shoe",
        )

        private fun canonical(word: String): String = SYNONYMS[word] ?: word

        /** English plural → singular, conservative: short words and -ss/-us/-is endings stay as they are. */
        internal fun singular(word: String): String = when {
            word.length < 4 || word in INVARIANT -> word
            word.endsWith("ies") -> if (word.dropLast(1) in IE_WORDS) word.dropLast(1) else word.dropLast(3) + "y"
            word.endsWith("sses") || word.endsWith("shes") || word.endsWith("ches") || word.endsWith("xes") ||
                word.endsWith("zes") -> word.dropLast(2)
            word.endsWith("ss") || word.endsWith("us") || word.endsWith("is") -> word
            word.endsWith("s") -> word.dropLast(1)
            else -> word
        }

        /**
         * Prefix terms for one word. The singular alone when it's a prefix of the word (it matches both); a -y singular
         * also gets its -ies plural, which no prefix of it reaches ("story" finds "stories").
         */
        internal fun prefixForms(word: String): List<String> {
            val one = singular(word)
            val forms = if (word.startsWith(one)) linkedSetOf(one) else linkedSetOf(word, one)
            if (one.length >= 4 && one.endsWith("y") && one[one.length - 2] !in "aeiou") forms += one.dropLast(1) + "ies"
            return forms.toList()
        }
    }
}
