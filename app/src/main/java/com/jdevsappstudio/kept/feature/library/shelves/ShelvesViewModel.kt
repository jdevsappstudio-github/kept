package com.jdevsappstudio.kept.feature.library.shelves

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.core.model.Shelf
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import com.jdevsappstudio.kept.data.library.ShelfOrder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** k-shelves: every shelf, a Show on home switch each, drag to reorder. */
data class ShelvesState(
    val shelves: List<Shelf> = emptyList(),
    /** The first 4 that are on: the ones home shows. */
    val onHome: Set<String> = emptySet(),
)

sealed interface ShelvesAction {
    data class OnShowOnHome(val kind: String, val show: Boolean) : ShelvesAction
    /** The whole order after a drag ends. */
    data class OnReorder(val kinds: List<String>) : ShelvesAction
    data class OnShelfClick(val kind: String) : ShelvesAction
    data object OnBackClick : ShelvesAction
}

class ShelvesViewModel(private val library: LibraryLocalDataSource) : ViewModel() {

    val state: StateFlow<ShelvesState> = library.observeShelves()
        .map { all -> ShelvesState(all, ShelfOrder.onHome(all).map { it.kind }.toSet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShelvesState())

    fun onAction(action: ShelvesAction) {
        when (action) {
            is ShelvesAction.OnShowOnHome -> viewModelScope.launch { library.setShelfHidden(action.kind, !action.show) }
            is ShelvesAction.OnReorder -> viewModelScope.launch { library.reorderShelves(action.kinds) }
            is ShelvesAction.OnShelfClick, ShelvesAction.OnBackClick -> Unit
        }
    }
}
