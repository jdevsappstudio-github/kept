package com.jdevsappstudio.kept.feature.library.list

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
import com.jdevsappstudio.kept.core.model.SourceCounts
import com.jdevsappstudio.kept.core.model.SourceFilter
import com.jdevsappstudio.kept.core.model.TagCount
import com.jdevsappstudio.kept.core.presentation.SnackbarAction
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import com.jdevsappstudio.kept.data.settings.SettingsStore
import com.jdevsappstudio.kept.feature.library.TAG_CHIPS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** k-list: one type (Recipe) or, with no type, All. */
data class SaveListState(
    val type: SaveType? = null,
    val source: SourceFilter? = null,
    val tag: String? = null,
    val sourceCounts: SourceCounts = SourceCounts.EMPTY,
    val tags: List<TagCount> = emptyList(),
    val layout: ListLayout = ListLayout.ROWS,
    val selected: Set<Long> = emptySet(),
) {
    val selecting: Boolean get() = selected.isNotEmpty()
    val filter: SaveFilter get() = SaveFilter(type = type, tag = tag, source = source)
}

sealed interface SaveListAction {
    /** Tapping the chip that is on turns it off. */
    data class OnSourceClick(val source: SourceFilter) : SaveListAction
    data class OnTagClick(val tag: String) : SaveListAction
    data object OnLayoutToggle : SaveListAction
    data class OnSaveClick(val id: Long) : SaveListAction
    /** Long-press, ⋮ Select, or the TalkBack action. */
    data class OnSaveLongPress(val id: Long) : SaveListAction
    data object OnClearSelection : SaveListAction
    data object OnEditSelected : SaveListAction
    data object OnDeleteSelected : SaveListAction
    data object OnSearchClick : SaveListAction
    data object OnBackClick : SaveListAction
}

sealed interface SaveListEvent {
    data class OpenSave(val id: Long) : SaveListEvent
    data class OpenEdit(val ids: List<Long>) : SaveListEvent
}

private data class Picks(val source: SourceFilter?, val tag: String?, val selected: Set<Long>)

@OptIn(ExperimentalCoroutinesApi::class)
class SaveListViewModel(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryLocalDataSource,
    private val settings: SettingsStore,
    private val snackbars: Snackbars,
) : ViewModel() {

    private val type: SaveType? = savedStateHandle.get<String>(ARG_TYPE)?.let { SaveType.fromWire(it) }
    private val picks = MutableStateFlow(Picks(source = null, tag = null, selected = emptySet()))

    private val filter: Flow<SaveFilter> =
        picks.map { SaveFilter(type = type, tag = it.tag, source = it.source) }.distinctUntilChanged()

    val saves: Flow<PagingData<Save>> = filter.flatMapLatest { library.pagedSaves(it) }.cachedIn(viewModelScope)

    val state: StateFlow<SaveListState> = combine(
        picks,
        filter.flatMapLatest { library.observeSourceCounts(it.copy(source = null)) },
        library.observeTags(SaveFilter(type = type), TAG_CHIPS),
        settings.listLayout,
    ) { p, counts, tags, layout ->
        SaveListState(type, p.source, p.tag, counts, tags, layout, p.selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SaveListState(type = type))

    private val _events = Channel<SaveListEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: SaveListAction) {
        when (action) {
            is SaveListAction.OnSourceClick ->
                picks.update { it.copy(source = action.source.takeUnless { s -> s == it.source }) }
            is SaveListAction.OnTagClick ->
                picks.update { it.copy(tag = action.tag.takeUnless { t -> t == it.tag }) }
            SaveListAction.OnLayoutToggle -> viewModelScope.launch {
                settings.setListLayout(if (state.value.layout == ListLayout.ROWS) ListLayout.GRID else ListLayout.ROWS)
            }
            is SaveListAction.OnSaveClick ->
                if (picks.value.selected.isNotEmpty()) toggle(action.id)
                else viewModelScope.launch { _events.send(SaveListEvent.OpenSave(action.id)) }
            is SaveListAction.OnSaveLongPress -> toggle(action.id)
            SaveListAction.OnClearSelection -> picks.update { it.copy(selected = emptySet()) }
            SaveListAction.OnEditSelected -> {
                val ids = picks.value.selected.toList()
                if (ids.isEmpty()) return
                picks.update { it.copy(selected = emptySet()) }
                viewModelScope.launch { _events.send(SaveListEvent.OpenEdit(ids)) }
            }
            SaveListAction.OnDeleteSelected -> deleteSelected()
            SaveListAction.OnSearchClick, SaveListAction.OnBackClick -> Unit
        }
    }

    private fun toggle(id: Long) = picks.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    private fun deleteSelected() {
        val ids = picks.value.selected.toList()
        if (ids.isEmpty()) return
        picks.update { it.copy(selected = emptySet()) }
        viewModelScope.launch {
            library.delete(ids)
            snackbars.show(
                UiText.Plural(R.plurals.snack_deleted, ids.size, ids.size),
                SnackbarAction(UiText.Res(R.string.snack_undo)) { library.undoDelete(ids) },
            )
        }
    }

    companion object {
        /** [SaveType.wire], or absent for All. */
        const val ARG_TYPE = "type"
    }
}
