package com.jdevsappstudio.kept.feature.library

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.ShelfEntry
import com.jdevsappstudio.kept.core.model.TagCount
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.feature.library.shelf.ShelfAction
import com.jdevsappstudio.kept.feature.library.shelf.ShelfEvent
import com.jdevsappstudio.kept.feature.library.shelf.ShelfViewModel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class ShelfViewModelTest {
    private val library = FakeLibrary()
    private val snackbars = testSnackbars()
    private val prisoners = ShelfEntry("prisoners", "Prisoners", saves = 2, saveId = 4, sourceApp = null)
    private val goneGirl = ShelfEntry("gone girl", "Gone Girl", saves = 1, saveId = 5, sourceApp = null)

    private fun vm() = ShelfViewModel(SavedStateHandle(mapOf(ShelfViewModel.ARG_KIND to "movies")), library, snackbars)

    @Test
    fun `sub-kind chips leave out the shelf's own kind, a chip narrows the rows`() = runTest {
        library.shelves.value = listOf(shelf("movies"), shelf("shoes"))
        library.tags.value = listOf(TagCount("movies", 9), TagCount("thriller", 5), TagCount("drama", 1))
        library.entries.value = mapOf(null to listOf(prisoners, goneGirl), "thriller" to listOf(prisoners))
        val vm = vm()
        keepActive(vm.state)

        assertThat(vm.state.value.tags.map { it.tag }).containsExactly("thriller", "drama")
        assertThat(vm.state.value.mergeTargets.map { it.kind }).containsExactly("shoes")
        vm.onAction(ShelfAction.OnTagClick("thriller"))
        assertThat(vm.state.value.entries).containsExactly(prisoners)
        vm.onAction(ShelfAction.OnTagClick("thriller"))
        assertThat(vm.state.value.entries).containsExactly(prisoners, goneGirl)
    }

    @Test
    fun `Not a movie takes the row off with an Undo`() = runTest {
        library.shelves.value = listOf(shelf("movies"))
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(ShelfAction.OnRemoveEntry(goneGirl))

        val msg = snackbars.next()
        assertThat(msg.text.resId()).isEqualTo(R.string.snack_off_shelf)
        assertThat((msg.text as UiText.Res).args.toList()).containsExactly("Movies")
        msg.action!!.onAction()
        assertThat(library.log).containsExactly("remove movies Gone Girl", "undoRemove Gone Girl")
    }

    @Test
    fun `hide, rename and merge go through with Undo where the design has it`() = runTest {
        library.shelves.value = listOf(shelf("movies"), shelf("films"))
        val vm = vm()
        keepActive(vm.state)

        vm.onAction(ShelfAction.OnHideClick)
        snackbars.next().action!!.onAction()
        vm.onAction(ShelfAction.OnRename("  "))
        vm.onAction(ShelfAction.OnMergeInto("films"))
        val merged = snackbars.next()
        assertThat((merged.text as UiText.Res).args.toList()).containsExactly("Films")
        merged.action!!.onAction()

        assertThat(library.log).containsExactly(
            "hidden movies true", "hidden movies false", "rename movies null", "merge movies films", "undoMerge movies",
        )
    }

    @Test
    fun `the screen closes once the shelf is gone`() = runTest {
        library.shelves.value = listOf(shelf("movies"), shelf("films"))
        val vm = vm()
        vm.events.test {
            library.shelves.value = listOf(shelf("films"))
            assertThat(awaitItem()).isEqualTo(ShelfEvent.Close)
        }
    }
}
