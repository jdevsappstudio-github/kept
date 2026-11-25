package com.jdevsappstudio.kept.feature.library.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveItem
import com.jdevsappstudio.kept.core.presentation.SnackbarAction
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the detail screen is asking before it acts (H53). */
sealed interface DetailDialog {
    /** Delete a save with 2+ items: "5 movies go with it". */
    data class ConfirmDelete(val items: Int) : DetailDialog
    /** Read again on a save edited by hand: "Your edits will be replaced". */
    data object ConfirmReadAgain : DetailDialog
}

/** Detail of any type (k-recipe, k-place, k-product, k-movies...): the type picks the layout and the action button. */
data class SaveDetailState(
    val loading: Boolean = true,
    val save: Save? = null,
    val items: List<SaveItem> = emptyList(),
    val dialog: DetailDialog? = null,
    /** The item whose fix-or-delete sheet is open (k-movies: tap a title). */
    val editingItem: SaveItem? = null,
)

sealed interface SaveDetailAction {
    data object OnDeleteClick : SaveDetailAction
    data object OnReadAgainClick : SaveDetailAction
    data object OnConfirmDialog : SaveDetailAction
    data object OnDismissDialog : SaveDetailAction
    data class OnItemClick(val item: SaveItem) : SaveDetailAction
    data class OnRenameItem(val name: String) : SaveDetailAction
    data object OnDeleteItem : SaveDetailAction
    data object OnDismissItem : SaveDetailAction
    data class OnAddItem(val name: String) : SaveDetailAction
    data object OnEditClick : SaveDetailAction
    data object OnOpenOriginalClick : SaveDetailAction
    data object OnShareClick : SaveDetailAction
    data object OnBackClick : SaveDetailAction
}

sealed interface SaveDetailEvent {
    /** Deleted here or elsewhere: back to where it was opened. */
    data object Close : SaveDetailEvent
}

private data class Ui(val dialog: DetailDialog? = null, val editingItem: SaveItem? = null)

class SaveDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryLocalDataSource,
    private val snackbars: Snackbars,
) : ViewModel() {

    private val id: Long = checkNotNull(savedStateHandle.get<Long>(ARG_ID))
    private val ui = MutableStateFlow(Ui())
    private var closing = false

    val state: StateFlow<SaveDetailState> = combine(
        library.observeSave(id), library.observeItems(id), ui,
    ) { save, items, u ->
        if (save == null) close()
        SaveDetailState(loading = false, save = save, items = items, dialog = u.dialog, editingItem = u.editingItem)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SaveDetailState())

    private val _events = Channel<SaveDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: SaveDetailAction) {
        when (action) {
            SaveDetailAction.OnDeleteClick -> {
                val items = state.value.items.size
                if (items >= 2) ui.update { it.copy(dialog = DetailDialog.ConfirmDelete(items)) } else delete()
            }
            SaveDetailAction.OnReadAgainClick ->
                if (state.value.save?.readBy == ReadBy.HAND) ui.update { it.copy(dialog = DetailDialog.ConfirmReadAgain) }
                else readAgain()
            SaveDetailAction.OnConfirmDialog -> {
                val dialog = ui.value.dialog
                ui.update { it.copy(dialog = null) }
                when (dialog) {
                    is DetailDialog.ConfirmDelete -> delete()
                    DetailDialog.ConfirmReadAgain -> readAgain()
                    null -> Unit
                }
            }
            SaveDetailAction.OnDismissDialog -> ui.update { it.copy(dialog = null) }
            is SaveDetailAction.OnItemClick -> ui.update { it.copy(editingItem = action.item) }
            SaveDetailAction.OnDismissItem -> ui.update { it.copy(editingItem = null) }
            is SaveDetailAction.OnRenameItem -> {
                val item = ui.value.editingItem ?: return
                ui.update { it.copy(editingItem = null) }
                val name = action.name.trim()
                if (name.isNotEmpty() && name != item.name) viewModelScope.launch { library.renameItem(item.id, name) }
            }
            SaveDetailAction.OnDeleteItem -> {
                val item = ui.value.editingItem ?: return
                ui.update { it.copy(editingItem = null) }
                viewModelScope.launch { library.deleteItem(item.id) }
            }
            is SaveDetailAction.OnAddItem -> addItem(action.name.trim())
            SaveDetailAction.OnEditClick, SaveDetailAction.OnOpenOriginalClick,
            SaveDetailAction.OnShareClick, SaveDetailAction.OnBackClick -> Unit
        }
    }

    private fun delete() {
        viewModelScope.launch {
            library.delete(listOf(id))
            snackbars.show(
                UiText.Plural(R.plurals.snack_deleted, 1, 1),
                SnackbarAction(UiText.Res(R.string.snack_undo)) { library.undoDelete(listOf(id)) },
            )
            close()
        }
    }

    private fun readAgain() {
        viewModelScope.launch {
            val queued = library.readAgain(id)
            snackbars.show(UiText.Res(if (queued) R.string.snack_read_again else R.string.snack_read_busy))
        }
    }

    private fun addItem(name: String) {
        if (name.isEmpty()) return
        viewModelScope.launch {
            val result = library.addItem(id, name)
            when {
                result.alreadyOnShelf != null ->
                    snackbars.show(UiText.Res(R.string.snack_already_on_shelf, result.alreadyOnShelf))
                !result.added -> snackbars.show(UiText.Res(R.string.snack_already_here))
            }
        }
    }

    /** Once only: our own delete and the save vanishing from Room would otherwise both close the screen. */
    private fun close() {
        if (closing) return
        closing = true
        _events.trySend(SaveDetailEvent.Close)
    }

    companion object {
        const val ARG_ID = "id"
    }
}
