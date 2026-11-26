package com.jdevsappstudio.kept.feature.needlook

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.data.quota.MeterView
import com.jdevsappstudio.kept.data.settings.SettingsStore
import com.jdevsappstudio.kept.feature.home.QueueView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
data class NeedLookState(
    val rows: List<NeedLookRow> = emptyList(),
    val banner: NeedLookBanner? = null,
    val pause: QueuePause = QueuePause.NONE,
    /** '28 of 30 read today · resets at midnight'. */
    val readsLine: String? = null,
    val consent: Boolean = false,
    val loaded: Boolean = false,
)

sealed interface NeedLookAction {
    data object OnBack : NeedLookAction
    data class OnSaveClick(val id: Long) : NeedLookAction
    data class OnTryAgainClick(val id: Long) : NeedLookAction
    /** By hand: the edit sheet; a card change there marks it hand-read and it leaves this screen. */
    data class OnByHandClick(val id: Long) : NeedLookAction
    data object OnReadWithCloudClick : NeedLookAction
    data object OnTurnOnCloudHelp : NeedLookAction
    data object OnNotNow : NeedLookAction
}

/** k-failed / k-failed-cloud: everything not read yet, why, and what to do (H68). */
class NeedLookViewModel(
    private val library: LibraryLocalDataSource,
    queue: Flow<QueueView>,
    private val settings: SettingsStore,
    private val snackbars: Snackbars,
    reads: Flow<MeterView?> = flowOf(null),
) : ViewModel() {

    private val consent = MutableStateFlow(false)

    val state: StateFlow<NeedLookState> = combine(
        library.observeUnread(NeedLookRules.LIMIT),
        queue,
        settings.cloudHelp,
        reads,
        consent,
    ) { unread, q, cloudHelp, meter, asking ->
        val rows = NeedLookRules.rows(unread, cloudHelp)
        NeedLookState(
            rows = rows,
            banner = NeedLookRules.banner(q.pause, rows.count { it is NeedLookRow.Pending }),
            pause = q.pause,
            readsLine = NeedLookRules.readsLine(meter),
            consent = asking,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NeedLookState())

    /** Navigation (back, open, By hand) is handled by the Root. */
    fun onAction(action: NeedLookAction) {
        when (action) {
            is NeedLookAction.OnTryAgainClick -> viewModelScope.launch {
                snackbars.show(UiText.Res(if (library.readAgain(action.id)) R.string.snack_read_again else R.string.snack_read_busy))
            }
            NeedLookAction.OnReadWithCloudClick -> consent.value = true
            NeedLookAction.OnNotNow -> consent.value = false
            NeedLookAction.OnTurnOnCloudHelp -> {
                // Saves that gave up because there was no Nano go back in line, now for the cloud (taken before the switch flips).
                val noNano = state.value.rows.filterIsInstance<NeedLookRow.Failed>().filter { it.copy.readWithCloud }.map { it.save.id }
                consent.update { false }
                viewModelScope.launch {
                    settings.setCloudHelp(true)
                    noNano.forEach { library.readAgain(it) }
                }
            }
            else -> Unit
        }
    }
}
