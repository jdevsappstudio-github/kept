package com.jdevsappstudio.kept.feature.sent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.core.model.SentRequest
import com.jdevsappstudio.kept.data.sent.SentLogLocalDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * k-sent 'What was sent' (H2, H58): every cloud request, newest first, last 30 days. Opened from k-privacy (list) or
 * a Cloud chip ([ARG_OPEN_ID]: that row starts expanded and the screen scrolls to it).
 */
data class SentState(
    val loading: Boolean = true,
    val rows: List<SentRequest> = emptyList(),
    val expanded: Set<Long> = emptySet(),
    /** Scroll target from a Cloud chip; null from k-privacy. */
    val openId: Long? = null,
) {
    /** 'Nothing sent yet. Kept has only used the AI on this phone.' */
    val empty: Boolean get() = !loading && rows.isEmpty()
    /** The top line names images only if any row has one (k-unsupported 'Read with cloud'). */
    val anyImages: Boolean get() = rows.any { it.images > 0 }
}

sealed interface SentAction {
    data class OnToggle(val id: Long) : SentAction
}

class SentViewModel(
    savedStateHandle: SavedStateHandle,
    private val log: SentLogLocalDataSource,
) : ViewModel() {

    private val openId: Long? = savedStateHandle.get<Long>(ARG_OPEN_ID)?.takeIf { it > 0 }
    private val expanded = MutableStateFlow(setOfNotNull(openId))

    val state: StateFlow<SentState> = combine(log.observe(), expanded) { rows, open ->
        SentState(loading = false, rows = rows, expanded = open, openId = openId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SentState(openId = openId))

    init {
        viewModelScope.launch { log.prune() }
    }

    fun onAction(action: SentAction) {
        when (action) {
            is SentAction.OnToggle -> expanded.update { if (action.id in it) it - action.id else it + action.id }
        }
    }

    companion object {
        const val ARG_OPEN_ID = "sentId"
    }
}
