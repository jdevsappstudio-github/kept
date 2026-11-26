package com.jdevsappstudio.kept.feature.collections

import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.TypeChip

/** k-collection words in one place (H64; UX open to the review panel). */
internal object CollectionCopy {
    /** Kinds named in the line before 'and more'. */
    const val KINDS_SHOWN = 3

    const val EMPTY_TITLE = "Nothing here yet"
    const val EMPTY_BODY = "Add saves you already kept, or tick this collection on any save's Edit sheet."
    const val EMPTY_TYPE = "Nothing here yet"
    const val NEW_LINE = "Free, as many as you like. Add saves next, or from any save's Edit sheet."

    /** '6 saves · you made this · places, an event and a recipe'; empty → '0 saves · you made this'. */
    fun line(count: Int, chips: List<TypeChip>): String {
        val head = "$count ${if (count == 1) "save" else "saves"} · you made this"
        val kinds = kinds(chips)
        return if (kinds.isEmpty()) head else "$head · $kinds"
    }

    /** Most-used kinds first: 'places, an event and a recipe'; more than [KINDS_SHOWN] → 'places, events, recipes and more'. */
    fun kinds(chips: List<TypeChip>): String {
        val used = chips.filter { it.count > 0 }.sortedByDescending { it.count }
        if (used.isEmpty()) return ""
        val words = used.take(KINDS_SHOWN).map { noun(it.type, it.count) }
        return when {
            used.size > KINDS_SHOWN -> words.joinToString(", ") + " and more"
            words.size == 1 -> words[0]
            else -> words.dropLast(1).joinToString(", ") + " and " + words.last()
        }
    }

    /** 'a recipe' / 'recipes'. */
    fun noun(type: SaveType, count: Int): String {
        val (one, many) = when (type) {
            SaveType.RECIPE -> "a recipe" to "recipes"
            SaveType.PLACE -> "a place" to "places"
            SaveType.PRODUCT -> "a product" to "products"
            SaveType.MOVIE_SHOW -> "a movie or show" to "movies and shows"
            SaveType.VIDEO -> "a video" to "videos"
            SaveType.READ -> "a read" to "reads"
            SaveType.EVENT -> "an event" to "events"
            SaveType.TIP -> "a tip" to "tips"
            SaveType.APP -> "an app" to "apps"
            SaveType.IDEA -> "an idea" to "ideas"
            SaveType.OTHER -> "something else" to "other saves"
        }
        return if (count == 1) one else many
    }
}
