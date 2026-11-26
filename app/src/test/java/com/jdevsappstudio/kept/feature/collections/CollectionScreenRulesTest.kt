package com.jdevsappstudio.kept.feature.collections

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.TypeChip
import org.junit.jupiter.api.Test

class CollectionScreenRulesTest {

    @Test
    fun `empty collection says only the count`() {
        assertThat(CollectionCopy.line(0, emptyList())).isEqualTo("0 saves · you made this")
    }

    @Test
    fun `one save is singular`() {
        assertThat(CollectionCopy.line(1, listOf(TypeChip(SaveType.RECIPE, 1))))
            .isEqualTo("1 save · you made this · a recipe")
    }

    @Test
    fun `kinds go most used first, joined with and`() {
        val chips = listOf(TypeChip(SaveType.EVENT, 1), TypeChip(SaveType.PLACE, 4), TypeChip(SaveType.RECIPE, 1))
        assertThat(CollectionCopy.line(6, chips)).isEqualTo("6 saves · you made this · places, an event and a recipe")
    }

    @Test
    fun `two kinds have no comma`() {
        val chips = listOf(TypeChip(SaveType.APP, 1), TypeChip(SaveType.MOVIE_SHOW, 3))
        assertThat(CollectionCopy.kinds(chips)).isEqualTo("movies and shows and an app")
    }

    @Test
    fun `more than three kinds end in and more`() {
        val chips = listOf(
            TypeChip(SaveType.PLACE, 4), TypeChip(SaveType.EVENT, 2), TypeChip(SaveType.RECIPE, 2), TypeChip(SaveType.TIP, 1),
        )
        assertThat(CollectionCopy.kinds(chips)).isEqualTo("places, events, recipes and more")
    }

    @Test
    fun `zero-count chips are not named`() {
        assertThat(CollectionCopy.kinds(listOf(TypeChip(SaveType.IDEA, 0), TypeChip(SaveType.IDEA, 2)))).isEqualTo("ideas")
    }
}
