package com.jdevsappstudio.kept.data.search

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.core.model.Shelf
import org.junit.jupiter.api.Test

class SearchQueryTest {

    private fun shelf(kind: String) = Shelf(kind, kind, saves = 3, items = 0, entries = 3, hidden = false, position = null)

    @Test
    fun `FTS syntax in the input is dropped`() {
        val q = SearchQuery.parse("  \"goa\" -cafe*: NEAR/2 (x) ")
        assertThat(q.words).containsExactly("goa", "cafe", "near", "2", "x")
        assertThat(q.match).isEqualTo("goa* cafe* near* 2* x*")
    }

    @Test
    fun `blank and punctuation-only input is blank`() {
        assertThat(SearchQuery.parse("").isBlank).isTrue()
        assertThat(SearchQuery.parse(" -- ** \" ").isBlank).isTrue()
        assertThat(SearchQuery.parse(" ").titleWord).isEqualTo("")
    }

    @Test
    fun `plurals also match the singular`() {
        assertThat(SearchQuery.parse("movies").match).isEqualTo("movie*")
        assertThat(SearchQuery.parse("dishes boxes").match).isEqualTo("dish* box*")
        assertThat(SearchQuery.parse("stories").match).isEqualTo("stories* OR story*")
        assertThat(SearchQuery.parse("goa stories").match).isEqualTo("goa* stories* OR story*")
        assertThat(SearchQuery.parse("story").match).isEqualTo("story* OR stories*")
        assertThat(SearchQuery.parse("day toys").match).isEqualTo("day* toy*")
    }

    @Test
    fun `words that only look plural stay`() {
        assertThat(SearchQuery.parse("glass series bus news").match).isEqualTo("glass* series* bus* news*")
    }

    @Test
    fun `any script, lowercased`() {
        val q = SearchQuery.parse("Café KĀFĪ पनीर")
        assertThat(q.words).containsExactly("café", "kāfī", "पनीर")
    }

    @Test
    fun `at most 8 words`() {
        assertThat(SearchQuery.parse((1..20).joinToString(" ") { "w$it" }).words).hasSize(8)
    }

    @Test
    fun `title word is what the first word's forms share`() {
        assertThat(SearchQuery.parse("Stories of Goa").titleWord).isEqualTo("stor")
        assertThat(SearchQuery.parse("movies").titleWord).isEqualTo("movie")
    }

    @Test
    fun `same words means the same query`() {
        assertThat(SearchQuery.parse(" Goa  cafe ")).isEqualTo(SearchQuery.parse("goa, cafe"))
    }

    @Test
    fun `a kind word, plural or synonym, names its shelf`() {
        val shelves = listOf(shelf("shoes"), shelf("movies"), shelf("cafés"), shelf("skincare products"))
        assertThat(SearchQuery.parse("movies to watch").matchShelf(shelves)?.kind).isEqualTo("movies")
        assertThat(SearchQuery.parse("a film").matchShelf(shelves)?.kind).isEqualTo("movies")
        assertThat(SearchQuery.parse("sneakers").matchShelf(shelves)?.kind).isEqualTo("shoes")
        assertThat(SearchQuery.parse("goa cafes").matchShelf(shelves)?.kind).isEqualTo("cafés")
        assertThat(SearchQuery.parse("best skincare product").matchShelf(shelves)?.kind).isEqualTo("skincare products")
        assertThat(SearchQuery.parse("skincare").matchShelf(shelves)).isNull()
        assertThat(SearchQuery.parse("goa").matchShelf(shelves)).isNull()
    }
}
