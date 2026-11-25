package com.jdevsappstudio.kept.data.saves

import com.jdevsappstudio.kept.data.ai.CardDraft
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class CardMappingTest {
    private fun card(tags: List<String> = emptyList(), items: List<String> = emptyList(), kind: String = "Movies") =
        CardDraft("Title", "movie_show", kind, tags, items, "", "", "", "Worth a watch.")

    @Test fun tagsAreCleanedDedupedAndCapped() {
        val tags = CardMapping.tags(card(tags = listOf(" #Thriller", "thriller", "", "Korean", "a", "b", "c", "d")))
        assertThat(tags).isEqualTo(listOf("thriller", "korean", "a", "b", "c"))
    }

    @Test fun itemsKeepCaseButDedupeIgnoringIt() {
        val items = CardMapping.items(card(items = listOf("Parasite", "parasite ", " Oldboy", "") + (1..20).map { "M$it" }))
        assertThat(items).hasSize(CardMapping.MAX_ITEMS)
        assertThat(items.take(2)).isEqualTo(listOf("Parasite", "Oldboy"))
    }

    @Test fun kindIsLowerCase() = assertThat(CardMapping.kind(card(kind = " Movies "))).isEqualTo("movies")

    @Test fun searchBodyHasTypeLabelTagsItemsAndSharedText() {
        val c = card(tags = listOf("thriller"), items = listOf("Parasite"))
        val body = CardMapping.searchBody(c, listOf("thriller"), listOf("Parasite"), "from a reel", null)
        listOf("Title", "Movie · Show", "thriller", "Parasite", "from a reel").forEach { assertThat(body).contains(it) }
    }
}
