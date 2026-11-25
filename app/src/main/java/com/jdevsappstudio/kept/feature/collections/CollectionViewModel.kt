package com.jdevsappstudio.kept.feature.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.ListLayout
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveFilter
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.TypeChip
import com.jdevsappstudio.kept.core.presentation.SnackbarAction
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.library.CollectionsLocalDataSource
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import com.jdevsappstudio.kept.data.settings.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** k-collection and k-collection-empty. */
data class CollectionState(
    val loading: Boolean = true,
    val name: String = "",
    /** "6 saves · you made this"; 0 → 'Nothing here yet' + Add saves. */
    val count: Int = 0,
    val typeChips: List<TypeChip> = emptyList(),
    /** On type chip; null = All. */
    val type: SaveType? = null,
    val layout: ListLayout = ListLayout.ROWS,
    /** Dialog 'Delete Goa trip? The 6 saves stay.' (only when it has saves). */
    val confirmDelete: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && count == 0
}

/** Taps that only open another screen (a save, Add saves → pick mode, Rename → k-newcol, Back) are handled by the Root. */
sealed interface CollectionAction {
    data class OnTypeClick(val type: SaveType?) : CollectionAction
    data class OnSaveClick(val id: Long) : CollectionAction
    data object OnAddSavesClick : CollectionAction
    data object OnRenameClick : CollectionAction
    data object OnDeleteClick : CollectionAction
    data object OnConfirmDelete : CollectionAction
    data object OnDismissDelete : CollectionAction
    /** Long-press → Remove from collection (or the TalkBack action), snackbar Undo. */
    data class OnRemove(val id: Long) : CollectionAction
    data object OnLayoutToggle : CollectionAction
    data object OnBack : CollectionAction
}

sealed interface CollectionEvent {
    /** Deleted here, or gone (Undo of Make it): back to home. */
    data object Close : CollectionEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val collections: CollectionsLocalDataSource,
    library: LibraryLocalDataSource,
    private val settings: SettingsStore,
    private val snackbars: Snackbars,
) : ViewModel() {

    private val id: Long = checkNotNull(savedStateHandle.get<Long>(ARG_ID))
    private val typePick = MutableStateFlow(savedStateHandle.get<String>(ARG_TYPE)?.let { SaveType.fromWire(it) })
    private val confirm = MutableStateFlow(false)
    private var closed = false

    private val _events = Channel<CollectionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val collection = collections.observeCollection(id).onEach { if (it == null) close() }

    val saves: Flow<PagingData<Save>> = typePick
        .flatMapLatest { library.pagedSaves(SaveFilter(type = it, collection = id)) }
        .cachedIn(viewModelScope)

    val state: StateFlow<CollectionState> = combine(
        collection, collections.observeTypeChips(id), typePick, settings.listLayout, confirm,
    ) { c, chips, type, layout, confirming ->
        if (c == null) return@combine CollectionState(type = type, layout = layout)
        CollectionState(
            loading = false,
            name = c.name,
            count = c.saves,
            typeChips = if (type == null || chips.any { it.type == type }) chips else chips + TypeChip(type, 0),
            type = type,
            layout = layout,
            confirmDelete = confirming && c.saves > 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionState(type = typePick.value))

    fun onAction(action: CollectionAction) {
        when (action) {
            is CollectionAction.OnTypeClick -> {
                val type = action.type?.takeUnless { it == typePick.value }
                savedStateHandle[ARG_TYPE] = type?.wire
                typePick.value = type
            }
            CollectionAction.OnDeleteClick -> {
                // H55: empty → gone at once; with saves → ask first (the saves stay either way).
                if (state.value.count == 0) delete() else confirm.value = true
            }
            CollectionAction.OnConfirmDelete -> delete()
            CollectionAction.OnDismissDelete -> confirm.value = false
            is CollectionAction.OnRemove -> remove(action.id)
            CollectionAction.OnLayoutToggle -> viewModelScope.launch {
                settings.setListLayout(if (state.value.layout == ListLayout.ROWS) ListLayout.GRID else ListLayout.ROWS)
            }
            else -> Unit
        }
    }

    private fun delete() {
        confirm.value = false
        viewModelScope.launch {
            collections.delete(id)
            close()
        }
    }

    private fun remove(saveId: Long) {
        val name = state.value.name
        viewModelScope.launch {
            collections.removeSaves(id, listOf(saveId))
            snackbars.show(
                UiText.Res(R.string.snack_removed_from, name),
                SnackbarAction(UiText.Res(R.string.snack_undo)) { collections.addSaves(id, listOf(saveId)) },
            )
        }
    }

    private fun close() {
        if (closed) return
        closed = true
        _events.trySend(CollectionEvent.Close)
    }

    companion object {
        const val ARG_ID = "collectionId"
        private const val ARG_TYPE = "collection_type"
    }
}
