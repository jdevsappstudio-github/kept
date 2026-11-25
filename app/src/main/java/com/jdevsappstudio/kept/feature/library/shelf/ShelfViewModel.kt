package com.jdevsappstudio.kept.feature.library.shelf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.Shelf
import com.jdevsappstudio.kept.core.model.ShelfEntry
import com.jdevsappstudio.kept.core.model.SaveFilter
import com.jdevsappstudio.kept.core.model.TagCount
import com.jdevsappstudio.kept.core.presentation.SnackbarAction
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import com.jdevsappstudio.kept.feature.library.TAG_CHIPS
import com.jdevsappstudio.kept.feature.library.withoutKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** k-shelf: the items (or saves) of one kind, e.g. every movie from every save. */
data class ShelfState(
    val kind: String,
    /** Null while loading. */
    val shelf: Shelf? = null,
    val tag: String? = null,
    val tags: List<TagCount> = emptyList(),
    val entries: List<ShelfEntry> = emptyList(),
    /** ⋮ Merge into…: every other shelf. */
    val mergeTargets: List<Shelf> = emptyList(),
)

sealed interface ShelfAction {
    data class OnTagClick(val tag: String) : ShelfAction
    data class OnEntryClick(val entry: ShelfEntry) : ShelfAction
    /** Long-press "Not a movie": the row leaves this shelf, Undo puts it back. */
    data class OnRemoveEntry(val entry: ShelfEntry) : ShelfAction
    data object OnHideClick : ShelfAction
    data object OnShowClick : ShelfAction
    /** Blank = back to the name Kept gave it. */
    data class OnRename(val name: String) : ShelfAction
    data class OnMergeInto(val kind: String) : ShelfAction
    data object OnBackClick : ShelfAction
}

sealed interface ShelfEvent {
    /** The shelf is gone (merged away, or under 3 saves): back to home. */
    data object Close : ShelfEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShelfViewModel(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryLocalDataSource,
    private val snackbars: Snackbars,
) : ViewModel() {

    private val kind: String = checkNotNull(savedStateHandle.get<String>(ARG_KIND))
    private val tag = MutableStateFlow<String?>(null)
    private val shelves = library.observeShelves()

    val state: StateFlow<ShelfState> = combine(
        tag,
        shelves,
        library.observeTags(SaveFilter(shelf = kind), TAG_CHIPS + 1).map { it.withoutKind(kind).take(TAG_CHIPS) },
        tag.flatMapLatest { library.observeShelfEntries(kind, it) },
    ) { t, all, tags, entries ->
        ShelfState(
            kind = kind,
            shelf = all.firstOrNull { it.kind == kind },
            tag = t,
            tags = tags,
            entries = entries,
            mergeTargets = all.filter { it.kind != kind },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShelfState(kind))

    private val _events = Channel<ShelfEvent>()
    val events = _events.receiveAsFlow()

    init {
        // Close once the shelf drops out of the list (it was there when the screen opened).
        shelves.map { list -> list.any { it.kind == kind } }
            .distinctUntilChanged()
            .drop(1)
            .filter { present -> !present }
            .onEach { _events.send(ShelfEvent.Close) }
            .launchIn(viewModelScope)
    }

    fun onAction(action: ShelfAction) {
        when (action) {
            is ShelfAction.OnTagClick -> tag.update { action.tag.takeUnless { t -> t == it } }
            is ShelfAction.OnRemoveEntry -> viewModelScope.launch {
                val removal = library.removeFromShelf(kind, action.entry)
                snackbars.show(
                    UiText.Res(R.string.snack_off_shelf, title()),
                    SnackbarAction(UiText.Res(R.string.snack_undo)) { library.undoRemoveFromShelf(removal) },
                )
            }
            ShelfAction.OnHideClick -> viewModelScope.launch {
                library.setShelfHidden(kind, hidden = true)
                snackbars.show(
                    UiText.Res(R.string.snack_hidden),
                    SnackbarAction(UiText.Res(R.string.snack_undo)) { library.setShelfHidden(kind, hidden = false) },
                )
            }
            ShelfAction.OnShowClick -> viewModelScope.launch { library.setShelfHidden(kind, hidden = false) }
            is ShelfAction.OnRename -> viewModelScope.launch {
                library.renameShelf(kind, action.name.trim().ifEmpty { null })
            }
            is ShelfAction.OnMergeInto -> viewModelScope.launch {
                val target = state.value.mergeTargets.firstOrNull { it.kind == action.kind }?.title ?: action.kind
                val merge = library.mergeShelf(kind, action.kind) ?: return@launch
                snackbars.show(
                    UiText.Res(R.string.snack_merged, target),
                    SnackbarAction(UiText.Res(R.string.snack_undo)) { library.undoMerge(merge) },
                )
            }
            is ShelfAction.OnEntryClick, ShelfAction.OnBackClick -> Unit
        }
    }

    private fun title(): String = state.value.shelf?.title ?: kind

    companion object {
        const val ARG_KIND = "kind"
    }
}
