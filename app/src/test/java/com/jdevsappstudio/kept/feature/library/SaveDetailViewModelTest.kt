package com.jdevsappstudio.kept.feature.library

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.AddItemResult
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.SaveItem
import com.jdevsappstudio.kept.feature.library.detail.DetailDialog
import com.jdevsappstudio.kept.feature.library.detail.SaveDetailAction
import com.jdevsappstudio.kept.feature.library.detail.SaveDetailEvent
import com.jdevsappstudio.kept.feature.library.detail.SaveDetailViewModel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class SaveDetailViewModelTest {
    private val library = FakeLibrary()
    private val snackbars = testSnackbars()

    private fun vm() = SaveDetailViewModel(SavedStateHandle(mapOf(SaveDetailViewModel.ARG_ID to 1L)), library, snackbars)
    private fun item(id: Long, name: String) = SaveItem(id, name, hidden = false, otherSaves = 0)

    @Test
    fun `delete with one item goes at once, closes once, Undo brings it back`() = runTest {
        library.put(save(1))
        library.items.value = mapOf(1L to listOf(item(10, "Prisoners")))
        val vm = vm()
        keepActive(vm.state)
        vm.events.test {
            vm.onAction(SaveDetailAction.OnDeleteClick)
            assertThat(awaitItem()).isEqualTo(SaveDetailEvent.Close)
            expectNoEvents()
        }
        val msg = snackbars.next()
        assertThat(msg.text.resId()).isEqualTo(R.plurals.snack_deleted)
        msg.action!!.onAction()
        assertThat(library.saves.value.keys).isEqualTo(setOf(1L))
    }

    @Test
    fun `delete with 2+ items asks first (H53)`() = runTest {
        library.put(save(1))
        library.items.value = mapOf(1L to listOf(item(10, "Prisoners"), item(11, "Gone Girl")))
        val vm = vm()
        keepActive(vm.state)

        vm.onAction(SaveDetailAction.OnDeleteClick)
        assertThat(vm.state.value.dialog).isEqualTo(DetailDialog.ConfirmDelete(2))
        assertThat(library.log).containsExactly()
        vm.onAction(SaveDetailAction.OnConfirmDialog)
        assertThat(library.log).containsExactly("delete [1]")
    }

    @Test
    fun `read again asks first only when the save was edited by hand`() = runTest {
        library.put(save(1, readBy = ReadBy.HAND))
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(SaveDetailAction.OnReadAgainClick)
        assertThat(vm.state.value.dialog).isEqualTo(DetailDialog.ConfirmReadAgain)
        vm.onAction(SaveDetailAction.OnDismissDialog)
        assertThat(library.log).containsExactly()

        library.put(save(1, readBy = ReadBy.CLOUD))
        library.readAgainResult = false
        vm.onAction(SaveDetailAction.OnReadAgainClick)
        assertThat(vm.state.value.dialog).isNull()
        assertThat(library.log).containsExactly("readAgain 1")
        assertThat(snackbars.next().text.resId()).isEqualTo(R.string.snack_read_busy)
    }

    @Test
    fun `item sheet renames, deletes, and add tells when it is already on a shelf`() = runTest {
        library.put(save(1))
        library.items.value = mapOf(1L to listOf(item(10, "Prisoners")))
        val vm = vm()
        keepActive(vm.state)

        vm.onAction(SaveDetailAction.OnItemClick(item(10, "Prisoners")))
        vm.onAction(SaveDetailAction.OnRenameItem(" Prisoners (2013) "))
        assertThat(vm.state.value.editingItem).isNull()
        vm.onAction(SaveDetailAction.OnItemClick(item(10, "Prisoners")))
        vm.onAction(SaveDetailAction.OnDeleteItem)
        library.addResult = AddItemResult(added = true, alreadyOnShelf = "Movies")
        vm.onAction(SaveDetailAction.OnAddItem("Zodiac"))

        assertThat(library.log).containsExactly("renameItem 10 Prisoners (2013)", "deleteItem 10", "addItem 1 Zodiac")
        assertThat(snackbars.next().text.resId()).isEqualTo(R.string.snack_already_on_shelf)
    }

    @Test
    fun `a save deleted elsewhere closes the screen`() = runTest {
        library.put(save(1))
        val vm = vm()
        keepActive(vm.state)
        vm.events.test {
            library.saves.value = emptyMap()
            assertThat(awaitItem()).isEqualTo(SaveDetailEvent.Close)
        }
    }
}
