package com.jdevsappstudio.kept.feature.add

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.presentation.SnackbarAction
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import com.jdevsappstudio.kept.data.settings.SettingsStore
import com.jdevsappstudio.kept.feature.home.HomeLines
import com.jdevsappstudio.kept.feature.home.QueueView
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

@Stable
data class ReadingState(
    /** This batch, newest first (H63). */
    val saves: List<Save> = emptyList(),
    val line: ReadingLine? = null,
    val loaded: Boolean = false,
)

sealed interface ReadingAction {
    data object OnBack : ReadingAction
    data class OnSaveClick(val id: Long) : ReadingAction
    /** 'Not a recipe? Edit tags' on a read card. */
    data class OnEditClick(val id: Long) : ReadingAction
    data object OnNeedLookClick : ReadingAction
}

sealed interface ReadingEvent {
    /** The snackbar's Edit, which fires from the app-wide host. */
    data class OpenEdit(val id: Long) : ReadingEvent
}

/** k-reading "Just kept": what was just picked or pasted, being read one at a time (H56, H63). */
class ReadingViewModel(
    savedStateHandle: SavedStateHandle,
    library: LibraryLocalDataSource,
    queue: Flow<QueueView>,
    settings: SettingsStore,
    adding: Flow<AddProgress?>,
    private val snackbars: Snackbars,
) : ViewModel() {

    private val since: Long = checkNotNull(savedStateHandle.get<Long>(ARG_SINCE))
    private var seen: Map<Long, SaveStatus>? = null

    // Buffered + trySend: the snackbar's Edit runs on the app scope and must never wait on a gone screen.
    private val _events = Channel<ReadingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<ReadingState> = combine(
        library.observeSince(since, ReadingRules.BATCH_LIMIT).onEach(::tellJustRead),
        adding,
        queue,
        settings.cloudHelp,
    ) { saves, add, q, cloudHelp ->
        ReadingState(
            saves = saves,
            line = ReadingRules.line(saves, add, q.pause, HomeLines.readsWith(q.route, cloudHelp)),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingState())

    /** Navigation taps are handled by the Root; nothing here changes state. */
    fun onAction(action: ReadingAction) = Unit

    private fun tellJustRead(saves: List<Save>) {
        val before = seen
        seen = saves.associate { it.id to it.status }
        if (before == null || saves.size > ReadingRules.TAG_SNACK_MAX) return
        ReadingRules.justRead(before, saves).forEach { save ->
            snackbars.show(
                UiText.Dynamic(ReadingRules.taggedText(save)),
                SnackbarAction(UiText.Res(R.string.snack_edit)) { _events.trySend(ReadingEvent.OpenEdit(save.id)) },
            )
        }
    }

    companion object {
        const val ARG_SINCE = "since"
    }
}
