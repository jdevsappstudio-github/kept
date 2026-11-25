package com.jdevsappstudio.kept.data.library

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jdevsappstudio.kept.core.model.CollectionSuggestion
import com.jdevsappstudio.kept.core.model.SaveCollection
import com.jdevsappstudio.kept.core.model.Shelf
import com.jdevsappstudio.kept.core.model.TagCount
import org.junit.jupiter.api.Test

class CollectionRulesTest {

    private fun shelf(kind: String, title: String = kind.replaceFirstChar { it.uppercase() }) =
        Shelf(kind, title, saves = 3, items = 0, entries = 3, hidden = false, position = null)

    private fun col(id: Long, name: String) = SaveCollection(id, name, createdAt = id, saves = 0)

    @Test
    fun `names - trimmed, single spaces, 40 max, taken in any case but not by itself`() {
        assertThat(CollectionNames.clean("  Goa   trip ")).isEqualTo("Goa trip")
        assertThat(CollectionNames.clean("x".repeat(50)).length).isEqualTo(40)
        val all = listOf(col(1, "Goa trip"))
        assertThat(CollectionNames.taken("goa TRIP", all)?.id).isEqualTo(1L)
        assertThat(CollectionNames.taken("Goa trip", all, exceptId = 1)).isNull()
        assertThat(CollectionNames.taken("   ", all)).isNull()
        assertThat(CollectionNames.fromTag("pune")).isEqualTo("Pune")
    }

    @Test
    fun `suggestion - the tag with the most saves, needs 3`() {
        val tags = listOf(TagCount("pune", 4), TagCount("goa", 3), TagCount("dinner", 2))
        assertThat(CollectionSuggestions.pick(tags, emptyList(), emptyList(), emptySet()))
            .isEqualTo(CollectionSuggestion("pune", "Pune", 4))
        assertThat(CollectionSuggestions.pick(listOf(TagCount("dinner", 2)), emptyList(), emptyList(), emptySet())).isNull()
    }

    @Test
    fun `suggestion skips shelf kinds, type names, named collections and dismissed tags`() {
        val tags = listOf(
            TagCount("movies", 9), // Movies shelf (plural of its kind)
            TagCount("home decor", 8), // shelf renamed "Home decor"
            TagCount("recipes", 7), // type name
            TagCount("show", 6), // part of "Movie · Show"
            TagCount("goa", 5), // a collection is called "Goa"
            TagCount("pune", 4), // dismissed
            TagCount("air-fryer", 3),
        )
        val pick = CollectionSuggestions.pick(
            tags,
            shelves = listOf(shelf("movie"), shelf("decor", title = "Home decor")),
            collections = listOf(col(1, "GOA")),
            dismissed = setOf("pune"),
        )
        assertThat(pick).isEqualTo(CollectionSuggestion("air-fryer", "Air-fryer", 3))
    }
}
