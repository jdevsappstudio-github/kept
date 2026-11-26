package com.jdevsappstudio.kept.feature.search

import androidx.compose.ui.text.font.FontWeight
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.core.model.Shelf
import org.junit.jupiter.api.Test

class SearchRulesTest {

    @Test
    fun `snippet drops the markers and bolds only the matched words`() {
        val text = snippetText("…North \u0002Goa\u0003 and \u0002goan\u0003 food…")
        assertThat(text.text).isEqualTo("…North Goa and goan food…")
        val bold = text.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { text.text.substring(it.start, it.end) }
        assertThat(bold).containsExactly("Goa", "goan")
    }

    @Test
    fun `snippet with an unclosed marker bolds to the end`() {
        val text = snippetText("a \u0002bc")
        assertThat(text.text).isEqualTo("a bc")
        assertThat(text.spanStyles.single().let { text.text.substring(it.start, it.end) }).isEqualTo("bc")
    }

    @Test
    fun `status line names only what is there`() {
        assertThat(statusLine(2, 1)).isEqualTo("2 still reading, 1 not read: they show up once read.")
        assertThat(statusLine(0, 3)).isEqualTo("3 not read: they show up once read.")
        assertThat(statusLine(0, 0)).isEqualTo("")
    }

    @Test
    fun `nothing found names the saves still reading`() {
        assertThat(nothingFoundBody(0)).isEqualTo("Search looks at every word Kept read, plus tags and items.")
        assertThat(nothingFoundBody(2).endsWith("2 saves are still reading: they show up once read.")).isTrue()
        assertThat(nothingFoundBody(1).endsWith("1 save is still reading: it shows up once read.")).isTrue()
    }

    @Test
    fun `shelf headline counts items when the shelf has them, else saves`() {
        assertThat(Shelf("movies", "Movies", 20, 23, 19, false, null).matchHeadline()).isEqualTo("Movies shelf · 19 items")
        assertThat(Shelf("shoes", "Shoes", 1, 0, 1, false, null).matchHeadline()).isEqualTo("Shoes shelf · 1 save")
    }

    @Test
    fun `keyboard comes up only on an empty open outside pick mode`() {
        assertThat(autoFocusSearch(SearchState())).isTrue()
        assertThat(autoFocusSearch(SearchState(query = "goa"))).isFalse()
        assertThat(autoFocusSearch(SearchState(pick = PickState("Goa trip", emptySet(), emptySet())))).isFalse()
    }
}
