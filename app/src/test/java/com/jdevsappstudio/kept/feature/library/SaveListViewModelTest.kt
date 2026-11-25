package com.jdevsappstudio.kept.feature.library

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.ListLayout
import com.jdevsappstudio.kept.core.model.SaveFilter
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.SourceFilter
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.feature.library.list.SaveListAction
import com.jdevsappstudio.kept.feature.library.list.SaveListEvent
import com.jdevsappstudio.kept.feature.library.list.SaveListViewModel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class SaveListViewModelTest {
    private val library = FakeLibrary()
    private val settings = FakeSettings()
    private val snackbars = testSnackbars()

    private fun vm(type: SaveType? = SaveType.RECIPE) = SaveListViewModel(
        SavedStateHandle(type?.let { mapOf(SaveListViewModel.ARG_TYPE to it.wire) } ?: emptyMap()),
        library, settings, snackbars,
    )

    @Test
    fun `chips narrow the paged list, a second tap clears them`() = runTest {
        val vm = vm()
        keepActive(vm.saves)
        keepActive(vm.state)

        vm.onAction(SaveListAction.OnSourceClick(SourceFilter.SCREENSHOTS))
        vm.onAction(SaveListAction.OnTagClick("air-fryer"))
        vm.onAction(SaveListAction.OnSourceClick(SourceFilter.SCREENSHOTS))

        val recipe = SaveFilter(type = SaveType.RECIPE)
        assertThat(library.pagedFilters).containsExactly(
            recipe,
            recipe.copy(source = SourceFilter.SCREENSHOTS),
            recipe.copy(source = SourceFilter.SCREENSHOTS, tag = "air-fryer"),
            recipe.copy(tag = "air-fryer"),
        )
        assertThat(vm.state.value.tag).isEqualTo("air-fryer")
        assertThat(library.tagFilters.last()).isEqualTo(recipe to TAG_CHIPS)
    }

    @Test
    fun `no type argument means All`() = runTest {
        val vm = vm(type = null)
        keepActive(vm.saves)
        assertThat(library.pagedFilters).containsExactly(SaveFilter.ALL)
    }

    @Test
    fun `tap opens, long-press selects, then taps toggle`() = runTest {
        val vm = vm()
        keepActive(vm.state)
        vm.events.test {
            vm.onAction(SaveListAction.OnSaveClick(1))
            assertThat(awaitItem()).isEqualTo(SaveListEvent.OpenSave(1))

            vm.onAction(SaveListAction.OnSaveLongPress(2))
            vm.onAction(SaveListAction.OnSaveClick(3))
            vm.onAction(SaveListAction.OnSaveClick(2))
            assertThat(vm.state.value.selected).isEqualTo(setOf(3L))

            vm.onAction(SaveListAction.OnEditSelected)
            assertThat(awaitItem()).isEqualTo(SaveListEvent.OpenEdit(listOf(3L)))
            assertThat(vm.state.value.selecting).isEqualTo(false)
        }
    }

    @Test
    fun `delete selected shows Deleted N with an Undo that restores`() = runTest {
        library.put(save(1), save(2), save(3))
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(SaveListAction.OnSaveLongPress(1))
        vm.onAction(SaveListAction.OnSaveLongPress(2))
        vm.onAction(SaveListAction.OnDeleteSelected)

        assertThat(library.saves.value.keys).isEqualTo(setOf(3L))
        assertThat(vm.state.value.selected).isEmpty()
        val msg = snackbars.next()
        assertThat((msg.text as UiText.Plural).count).isEqualTo(2)
        assertThat(msg.text.resId()).isEqualTo(R.plurals.snack_deleted)

        msg.action!!.onAction()
        assertThat(library.saves.value.keys).isEqualTo(setOf(1L, 2L, 3L))
    }

    @Test
    fun `layout toggle is remembered in settings`() = runTest {
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(SaveListAction.OnLayoutToggle)
        assertThat(settings.listLayout.value).isEqualTo(ListLayout.GRID)
        assertThat(vm.state.value.layout == ListLayout.GRID).isTrue()
    }
}
