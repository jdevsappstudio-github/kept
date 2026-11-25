package com.jdevsappstudio.kept.feature.collections

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.core.model.SaveFilter
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.TagCount
import com.jdevsappstudio.kept.core.model.TypeChip
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.feature.home.HomeAction
import com.jdevsappstudio.kept.feature.home.HomeEvent
import com.jdevsappstudio.kept.feature.home.HomeViewModel
import com.jdevsappstudio.kept.feature.home.QueueView
import com.jdevsappstudio.kept.feature.library.FakeCollections
import com.jdevsappstudio.kept.feature.library.FakeLibrary
import com.jdevsappstudio.kept.feature.library.FakeSettings
import com.jdevsappstudio.kept.feature.library.MainDispatcherExtension
import com.jdevsappstudio.kept.feature.library.keepActive
import com.jdevsappstudio.kept.feature.library.next
import com.jdevsappstudio.kept.feature.library.resId
import com.jdevsappstudio.kept.feature.library.shelf
import com.jdevsappstudio.kept.feature.library.testSnackbars
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class CollectionsViewModelTest {
    private val library = FakeLibrary()
    private val collections = FakeCollections()
    private val settings = FakeSettings()
    private val snackbars = testSnackbars()

    private fun TestScope.home() = HomeViewModel(
        flowOf(QueueView(QueueCounts.EMPTY, null, QueuePause.NONE)), settings, library, collections, snackbars,
    ).also { keepActive(it.state) }

    private fun collection(id: Long) =
        CollectionViewModel(SavedStateHandle(mapOf(CollectionViewModel.ARG_ID to id)), collections, library, settings, snackbars)

    private fun nameSheet(renameId: Long? = null) = CollectionNameViewModel(
        SavedStateHandle(renameId?.let { mapOf(CollectionNameViewModel.ARG_RENAME_ID to it) } ?: emptyMap()), collections,
    )

    // ---- Home ----

    @Test
    fun `home lists collections newest first and one suggestion, skipping a shelf's kind`() = runTest {
        collections.collection(1, "Goa trip", setOf(1, 2))
        collections.collection(2, "Gift ideas")
        library.shelves.value = listOf(shelf("movie"))
        collections.suggestible.value = listOf(TagCount("movies", 9), TagCount("pune", 4))
        val vm = home()
        val s = vm.state.value
        assertThat(s.collections.map { it.name to it.saves }).containsExactly("Gift ideas" to 0, "Goa trip" to 2)
        assertThat(s.suggestion?.name).isEqualTo("Pune")
        assertThat(s.suggestion?.saves).isEqualTo(4)
    }

    @Test
    fun `Make it makes the collection, says Made Pune, opens it, Undo deletes it and the card comes back`() = runTest {
        collections.suggestible.value = listOf(TagCount("pune", 4))
        collections.tagged["pune"] = listOf(1, 2, 3, 4)
        val vm = home()
        vm.events.test {
            vm.onAction(HomeAction.OnMakeSuggestion)
            val open = awaitItem() as HomeEvent.OpenCollection
            assertThat(collections.members.value[open.id]).isEqualTo(setOf(1L, 2L, 3L, 4L))
        }
        assertThat(vm.state.value.suggestion).isNull() // a collection is now called Pune
        val snack = snackbars.next()
        assertThat(snack.text.resId()).isEqualTo(R.plurals.snack_made_collection)
        snack.action!!.onAction()
        assertThat(vm.state.value.collections).isEqualTo(emptyList())
        assertThat(vm.state.value.suggestion?.tag).isEqualTo("pune")
    }

    @Test
    fun `Not now hides that tag for good and shows the next one`() = runTest {
        collections.suggestible.value = listOf(TagCount("pune", 4), TagCount("goa", 3))
        val vm = home()
        vm.onAction(HomeAction.OnDismissSuggestion)
        assertThat(settings.dismissedSuggestions.value).isEqualTo(setOf("pune"))
        assertThat(vm.state.value.suggestion?.tag).isEqualTo("goa")
    }

    // ---- k-collection ----

    @Test
    fun `collection shows name, count, chips and pages its own saves by type`() = runTest {
        collections.collection(5, "Goa trip", setOf(1, 2, 3))
        collections.chips.value = listOf(TypeChip(SaveType.PLACE, 2), TypeChip(SaveType.RECIPE, 1))
        val vm = collection(5)
        keepActive(vm.state)
        keepActive(vm.saves)
        assertThat(vm.state.value.name).isEqualTo("Goa trip")
        assertThat(vm.state.value.count).isEqualTo(3)
        vm.onAction(CollectionAction.OnTypeClick(SaveType.PLACE))
        assertThat(library.pagedFilters).containsExactly(SaveFilter(collection = 5), SaveFilter(type = SaveType.PLACE, collection = 5))
    }

    @Test
    fun `delete asks first when it has saves, then closes`() = runTest {
        collections.collection(5, "Goa trip", setOf(1))
        val vm = collection(5)
        keepActive(vm.state)
        vm.onAction(CollectionAction.OnDeleteClick)
        assertThat(vm.state.value.confirmDelete).isTrue()
        vm.onAction(CollectionAction.OnDismissDelete)
        assertThat(vm.state.value.confirmDelete).isFalse()
        vm.events.test {
            vm.onAction(CollectionAction.OnDeleteClick)
            vm.onAction(CollectionAction.OnConfirmDelete)
            assertThat(awaitItem()).isEqualTo(CollectionEvent.Close)
        }
        assertThat(collections.log).containsExactly("delete 5")
    }

    @Test
    fun `an empty collection goes at once`() = runTest {
        collections.collection(5, "Weekend brunch")
        val vm = collection(5)
        keepActive(vm.state)
        assertThat(vm.state.value.isEmpty).isTrue()
        vm.events.test {
            vm.onAction(CollectionAction.OnDeleteClick)
            assertThat(awaitItem()).isEqualTo(CollectionEvent.Close)
        }
        assertThat(vm.state.value.confirmDelete).isFalse()
    }

    @Test
    fun `remove from collection says Removed from, Undo puts it back`() = runTest {
        collections.collection(5, "Goa trip", setOf(1, 2))
        val vm = collection(5)
        keepActive(vm.state)
        vm.onAction(CollectionAction.OnRemove(2))
        assertThat(vm.state.value.count).isEqualTo(1)
        val snack = snackbars.next()
        assertThat(snack.text.resId()).isEqualTo(R.string.snack_removed_from)
        snack.action!!.onAction()
        assertThat(vm.state.value.count).isEqualTo(2)
    }

    @Test
    fun `collection closes when it disappears (Undo of Make it)`() = runTest {
        collections.collection(5, "Pune")
        val vm = collection(5)
        keepActive(vm.state)
        vm.events.test {
            collections.delete(5)
            assertThat(awaitItem()).isEqualTo(CollectionEvent.Close)
        }
    }

    // ---- k-newcol ----

    @Test
    fun `new collection - empty or taken name keeps Create off, 40 max, Create returns the id`() = runTest {
        collections.collection(1, "Goa trip")
        val vm = nameSheet()
        keepActive(vm.state)
        assertThat(vm.state.value.canSubmit).isFalse()
        vm.onAction(CollectionNameAction.OnNameChange("goa trip "))
        assertThat(vm.state.value.takenBy).isEqualTo("Goa trip")
        assertThat(vm.state.value.canSubmit).isFalse()
        vm.onAction(CollectionNameAction.OnNameChange("x".repeat(45)))
        assertThat(vm.state.value.name.length).isEqualTo(40)
        vm.onAction(CollectionNameAction.OnNameChange("Weekend brunch"))
        vm.events.test {
            vm.onAction(CollectionNameAction.OnSubmit)
            val created = awaitItem() as CollectionNameEvent.Created
            assertThat(collections.all.value.first { it.id == created.id }.name).isEqualTo("Weekend brunch")
        }
    }

    @Test
    fun `rename starts from the current name, may keep it, Save renames and closes`() = runTest {
        collections.collection(1, "Goa trip")
        val vm = nameSheet(renameId = 1)
        keepActive(vm.state)
        assertThat(vm.state.value.rename).isTrue()
        assertThat(vm.state.value.name).isEqualTo("Goa trip")
        assertThat(vm.state.value.canSubmit).isTrue()
        vm.onAction(CollectionNameAction.OnNameChange("Goa 2026"))
        vm.events.test {
            vm.onAction(CollectionNameAction.OnSubmit)
            assertThat(awaitItem()).isEqualTo(CollectionNameEvent.Close)
        }
        assertThat(collections.log).containsExactly("rename 1 Goa 2026")
    }
}
