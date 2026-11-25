package com.jdevsappstudio.kept.feature.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.data.library.CollectionNames
import com.jdevsappstudio.kept.data.library.CollectionsLocalDataSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * k-newcol: one sheet for a new collection (home, onboarding, the edit sheet) and for Rename (k-collection ⋮).
 * Where Created goes next is the caller's call: home opens the empty collection, the edit sheet ticks it.
 */
data class CollectionNameState(
    val loading: Boolean = false,
    /** Titled Rename, button Save; otherwise New collection, button Create. */
    val rename: Boolean = false,
    val name: String = "",
    /** 'You already have Goa trip' (the existing name as written). */
    val takenBy: String? = null,
) {
    val canSubmit: Boolean get() = !loading && CollectionNames.clean(name).isNotEmpty() && takenBy == null
}

sealed interface CollectionNameAction {
    data class OnNameChange(val text: String) : CollectionNameAction
    data object OnSubmit : CollectionNameAction
    data object OnCancel : CollectionNameAction
}

sealed interface CollectionNameEvent {
    data class Created(val id: Long) : CollectionNameEvent
    /** Renamed, cancelled, or the collection to rename is gone. */
    data object Close : CollectionNameEvent
}

class CollectionNameViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val collections: CollectionsLocalDataSource,
) : ViewModel() {

    private val renameId: Long? = savedStateHandle.get<Long>(ARG_RENAME_ID)
    private val text = MutableStateFlow(savedStateHandle.get<String>(KEY_NAME))
    private var busy = false

    val state: StateFlow<CollectionNameState> = combine(text, collections.observeCollections()) { t, all ->
        if (t == null) return@combine CollectionNameState(loading = true, rename = renameId != null)
        CollectionNameState(
            rename = renameId != null,
            name = t,
            takenBy = CollectionNames.taken(t, all, exceptId = renameId)?.name,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionNameState(loading = true, rename = renameId != null))

    private val _events = Channel<CollectionNameEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        if (text.value == null) {
            if (renameId == null) {
                text.value = ""
            } else viewModelScope.launch {
                val current = collections.observeCollection(renameId).first()
                if (current == null) _events.send(CollectionNameEvent.Close) else text.value = current.name
            }
        }
    }

    fun onAction(action: CollectionNameAction) {
        when (action) {
            is CollectionNameAction.OnNameChange -> {
                // 40 characters max: extra typing is ignored, like a maxLength field.
                val value = action.text.take(CollectionNames.MAX_LENGTH)
                savedStateHandle[KEY_NAME] = value
                text.value = value
            }
            CollectionNameAction.OnSubmit -> submit()
            CollectionNameAction.OnCancel -> viewModelScope.launch { _events.send(CollectionNameEvent.Close) }
        }
    }

    private fun submit() {
        if (busy || !state.value.canSubmit) return
        busy = true
        val name = state.value.name
        viewModelScope.launch {
            if (renameId != null) {
                collections.rename(renameId, name)
                _events.send(CollectionNameEvent.Close)
            } else {
                // Null only if the name was taken a moment ago; the line shows it and Create stays off.
                val id = collections.create(name)
                if (id != null) _events.send(CollectionNameEvent.Created(id)) else busy = false
            }
        }
    }

    companion object {
        /** Set for Rename; absent for a new collection. */
        const val ARG_RENAME_ID = "renameCollectionId"
        private const val KEY_NAME = "collection_name"
    }
}
