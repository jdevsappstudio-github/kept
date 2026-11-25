package com.jdevsappstudio.kept.core.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jdevsappstudio.kept.core.model.ItemAction.Target
import org.junit.jupiter.api.Test

class ItemActionTest {

    @Test
    fun `movie row searches where to watch`() {
        assertThat(itemAction(SaveType.MOVIE_SHOW, " Prisoners ", null))
            .isEqualTo(ItemAction("Where to watch", Target.WebSearch("Prisoners where to watch")))
    }

    @Test
    fun `place opens maps, app opens play store, product searches to buy`() {
        assertThat(itemAction(SaveType.PLACE, "Thalassa", null)?.target).isEqualTo(Target.Maps("Thalassa"))
        assertThat(itemAction(SaveType.APP, "Notion", null)?.target).isEqualTo(Target.PlayStore("Notion"))
        assertThat(itemAction(SaveType.PRODUCT, "Air Max", null)?.label).isEqualTo("Search to buy")
    }

    @Test
    fun `link types open their own http link, else search`() {
        assertThat(itemAction(SaveType.READ, "Essay", "https://a.b/c")?.target).isEqualTo(Target.OpenLink("https://a.b/c"))
        assertThat(itemAction(SaveType.READ, "Essay", "javascript:alert(1)")?.target).isEqualTo(Target.WebSearch("Essay"))
        assertThat(itemAction(SaveType.RECIPE, "Dal", null)?.target).isEqualTo(Target.WebSearch("Dal recipe"))
    }

    @Test
    fun `idea and blank names have no action`() {
        assertThat(itemAction(SaveType.IDEA, "x", null)).isNull()
        assertThat(itemAction(SaveType.MOVIE_SHOW, "  ", null)).isNull()
    }

    @Test
    fun `an item row never borrows its save's link`() {
        val item = ShelfEntry("dune", "Dune", 1, 3, null, SaveType.READ, link = "https://list.example")
        val save = item.copy(key = "")
        assertThat(item.action()?.target).isEqualTo(Target.WebSearch("Dune"))
        assertThat(save.action()?.target).isEqualTo(Target.OpenLink("https://list.example"))
    }
}
