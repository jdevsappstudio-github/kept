package com.jdevsappstudio.kept.feature.home

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.data.examples.ExampleContent
import com.jdevsappstudio.kept.data.examples.ExampleSaves
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.data.queue.ReadRoute
import com.jdevsappstudio.kept.feature.library.FakeCollections
import com.jdevsappstudio.kept.feature.library.FakeLibrary
import com.jdevsappstudio.kept.feature.library.FakeSettings
import com.jdevsappstudio.kept.feature.library.MainDispatcherExtension
import com.jdevsappstudio.kept.feature.library.keepActive
import com.jdevsappstudio.kept.feature.library.next
import com.jdevsappstudio.kept.feature.library.resId
import com.jdevsappstudio.kept.feature.library.save
import com.jdevsappstudio.kept.feature.library.testSnackbars
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/** The examples as saves in the fake library, the way RoomExampleSaves writes them to Room. */
private class FakeExampleSaves(private val library: FakeLibrary) : ExampleSaves {
    override fun observeIds(): Flow<List<Long>> =
        library.saves.map { m -> m.values.filter { it.example }.sortedBy { it.createdAt }.map { it.id } }

    override suspend fun add(): List<Long> {
        val ids = ExampleContent.ALL.indices.map { 100L + it }
        ExampleContent.ALL.forEachIndexed { i, ex ->
            library.put(save(ids[i], title = ex.card.title).copy(createdAt = 100L + i, example = true))
        }
        return ids
    }
}

@ExtendWith(MainDispatcherExtension::class)
class HomeExamplesTest {
    private val library = FakeLibrary()
    private val snackbars = testSnackbars()

    private fun vm() = HomeViewModel(
        flowOf(QueueView(QueueCounts.EMPTY, ReadRoute.DEVICE, QueuePause.NONE)),
        FakeSettings(), library, FakeCollections(), snackbars,
        examples = FakeExampleSaves(library),
    )

    @Test
    fun `Try 3 examples puts three marked saves on Home, the recipe on top`() = runTest {
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(HomeAction.OnTryExamples)
        assertThat(vm.state.value.examples).containsExactly(100L, 101L, 102L)
        val hero = vm.state.value.hero
        assertThat(hero).isNotNull()
        assertThat(hero!!.title).isEqualTo("Shakshuka")
        assertThat(hero.example).isTrue()
    }

    @Test
    fun `Clear removes them with an Undo that brings them back`() = runTest {
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(HomeAction.OnTryExamples)
        vm.onAction(HomeAction.OnClearExamples)
        assertThat(vm.state.value.examples).isEmpty()
        val msg = snackbars.next()
        assertThat(msg.text.resId()).isEqualTo(R.string.snack_examples_cleared)
        msg.action!!.onAction()
        assertThat(vm.state.value.examples).containsExactly(100L, 101L, 102L)
    }

    @Test
    fun `the examples cover a recipe, a place and a multi-item save`() {
        assertThat(ExampleContent.ALL.map { it.card.type }).containsExactly("movie_show", "place", "recipe")
        assertThat(ExampleContent.ALL.first().card.items.size).isEqualTo(5)
    }
}
