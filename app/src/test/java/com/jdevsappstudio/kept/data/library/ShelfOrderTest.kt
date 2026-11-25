package com.jdevsappstudio.kept.data.library

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.Shelf
import org.junit.jupiter.api.Test

class ShelfOrderTest {
    private fun shelf(kind: String, entries: Int, position: Int? = null, hidden: Boolean = false) =
        Shelf(kind, ShelfOrder.title(kind, null), saves = 3, items = 0, entries = entries, hidden = hidden, position = position)

    @Test fun `dragged order first, then biggest, then by name`() {
        val sorted = ShelfOrder.sort(
            listOf(shelf("shoes", 6), shelf("movies", 23), shelf("books", 6), shelf("decor", 7, position = 1), shelf("apps", 5, position = 0)),
        )
        assertThat(sorted.map { it.kind }).containsExactly("apps", "decor", "movies", "books", "shoes")
    }

    @Test fun `home shows the first 4 that are on`() {
        val sorted = ShelfOrder.sort(
            listOf(shelf("a", 9), shelf("b", 8, hidden = true), shelf("c", 7), shelf("d", 6), shelf("e", 5), shelf("f", 4)),
        )
        assertThat(ShelfOrder.onHome(sorted).map { it.kind }).containsExactly("a", "c", "d", "e")
    }

    @Test fun `title is the rename or the capitalised kind`() {
        assertThat(ShelfOrder.title("movies", null)).isEqualTo("Movies")
        assertThat(ShelfOrder.title("movies", " ")).isEqualTo("Movies")
        assertThat(ShelfOrder.title("movies", "Films")).isEqualTo("Films")
    }

    @Test fun `type chips fold unknown types, drop zeros, preferred first then biggest`() {
        val chips = TypeChips.from(
            mapOf("recipe" to 12, "place" to 9, "product" to 14, "movie_show" to 9, "other" to 2, "recipes" to 1, "video" to 0),
            preferred = listOf(SaveType.PLACE),
        )
        assertThat(chips.map { it.type to it.count }).containsExactly(
            SaveType.PLACE to 9, SaveType.PRODUCT to 14, SaveType.RECIPE to 12, SaveType.MOVIE_SHOW to 9, SaveType.OTHER to 3,
        )
    }
}
