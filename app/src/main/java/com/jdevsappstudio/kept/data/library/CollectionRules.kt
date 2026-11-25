package com.jdevsappstudio.kept.data.library

import com.jdevsappstudio.kept.core.model.CollectionSuggestion
import com.jdevsappstudio.kept.core.model.SaveCollection
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.Shelf
import com.jdevsappstudio.kept.core.model.TagCount
import com.jdevsappstudio.kept.data.search.SearchQuery

/** k-newcol rules: one sheet for new and renamed collections. UX open to the review panel (docs/UX-REVIEW.md). */
object CollectionNames {
    const val MAX_LENGTH = 40

    /** Trimmed, single spaces, at most [MAX_LENGTH]. */
    fun clean(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH).trim()

    /** The collection already called [name] (any case), not counting [exceptId] (a rename keeping its own name). */
    fun taken(name: String, existing: List<SaveCollection>, exceptId: Long? = null): SaveCollection? {
        val clean = clean(name)
        if (clean.isEmpty()) return null
        return existing.firstOrNull { it.id != exceptId && it.name.equals(clean, ignoreCase = true) }
    }

    /** Make it on a suggestion uses the tag as the name: "pune" → "Pune". */
    fun fromTag(tag: String): String = clean(tag.replaceFirstChar { it.titlecase() })
}

/**
 * H55: one suggestion card at a time, for the tag with the most read saves ([MIN_SAVES]+). Skipped: tags a shelf or a
 * type already groups ('movies' with a Movies shelf, 'recipe'), tags a collection is named after, and dismissed tags.
 * UX open to the review panel (docs/UX-REVIEW.md).
 */
object CollectionSuggestions {
    const val MIN_SAVES = 3

    /** [tags] must come most saves first (the DAO's order). */
    fun pick(
        tags: List<TagCount>,
        shelves: List<Shelf>,
        collections: List<SaveCollection>,
        dismissed: Set<String>,
    ): CollectionSuggestion? {
        val grouped = buildSet {
            shelves.forEach { add(norm(it.kind)); add(norm(it.title)) }
            SaveType.entries.forEach { t ->
                add(norm(t.wire))
                t.label.split("·").forEach { add(norm(it)) }
            }
            collections.forEach { add(norm(it.name)) }
        }
        return tags.firstOrNull { it.count >= MIN_SAVES && it.tag !in dismissed && norm(it.tag) !in grouped }
            ?.let { CollectionSuggestion(it.tag, CollectionNames.fromTag(it.tag), it.count) }
    }

    /** Lowercase words, each singular, so 'Movies', 'movie' and 'movie_show' parts compare equal. */
    private fun norm(text: String): String =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.joinToString(" ") { SearchQuery.singular(it) }
}
