package com.jdevsappstudio.kept.feature.settings

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.ai.DeviceModelState
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import com.jdevsappstudio.kept.data.quota.MeterView
import com.jdevsappstudio.kept.data.sent.SentLogLocalDataSource
import com.jdevsappstudio.kept.data.settings.SettingsStore
import com.jdevsappstudio.kept.data.wipe.ContentWiper
import com.jdevsappstudio.kept.feature.needlook.NeedLookRow
import com.jdevsappstudio.kept.feature.needlook.NeedLookRules
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which dialog is up on k-privacy. */
enum class PrivacyDialog { NONE, CLOUD_CONSENT, DELETE }

@Stable
data class PrivacyState(
    /** Null while the on-device check runs. */
    val device: DeviceModelState? = null,
    val cloudHelp: Boolean = false,
    val sentCount: Int = 0,
    val saveCount: Int = 0,
    val reads: MeterView? = null,
    val answers: MeterView? = null,
    val dialog: PrivacyDialog = PrivacyDialog.NONE,
    val deleting: Boolean = false,
) {
    val readingRow: ReadingRowCopy get() = SettingsCopy.readingRow(device, cloudHelp)
}

sealed interface PrivacyAction {
    data object OnBack : PrivacyAction
    data object OnSentClick : PrivacyAction
    data object OnReadingRowClick : PrivacyAction
    data object OnPrivacyPolicyClick : PrivacyAction
    data class OnCloudHelpChange(val on: Boolean) : PrivacyAction
    /** The image-naming consent's yes (switch turned on on a phone without Nano). */
    data object OnConsentYes : PrivacyAction
    data object OnDismissDialog : PrivacyAction
    data object OnDeleteClick : PrivacyAction
    data object OnDeleteConfirm : PrivacyAction
    /** k-unsupported: its own page is the consent (it names images), so this turns Cloud help on at once. */
    data object OnReadWithCloud : PrivacyAction
    /** k-unsupported: nothing leaves the phone. */
    data object OnTagByHand : PrivacyAction
}

sealed interface PrivacyEvent {
    data object Deleted : PrivacyEvent
    data object Close : PrivacyEvent
}

/** k-privacy + k-unsupported (H68, H69). */
class PrivacyViewModel(
    private val settings: SettingsStore,
    private val library: LibraryLocalDataSource,
    private val wiper: ContentWiper,
    private val snackbars: Snackbars,
    sentLog: SentLogLocalDataSource,
    counts: Flow<QueueCounts>,
    deviceModel: suspend () -> DeviceModelState,
    reads: Flow<MeterView?> = flowOf(null),
    answers: Flow<MeterView?> = flowOf(null),
) : ViewModel() {

    private val dialog = MutableStateFlow(PrivacyDialog.NONE)
    private val deleting = MutableStateFlow(false)
    private val device: Flow<DeviceModelState?> = flow<DeviceModelState?> { emit(deviceModel()) }.onStart { emit(null) }
    private val meters = combine(reads, answers) { r, a -> r to a }
    private val ui = combine(dialog, deleting) { d, busy -> d to busy }

    private val _events = Channel<PrivacyEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<PrivacyState> = combine(
        device,
        settings.cloudHelp,
        combine(sentLog.observe().map { it.size }, counts) { sent, c -> sent to (c.pending + c.needsLook + c.read) },
        meters,
        ui,
    ) { model, cloudHelp, (sent, saves), (r, a), (d, busy) ->
        PrivacyState(model, cloudHelp, sent, saves, r, a, d, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyState())

    /** Navigation (back, k-sent, k-unsupported, the policy link) is handled by the Root. */
    fun onAction(action: PrivacyAction) {
        when (action) {
            is PrivacyAction.OnCloudHelpChange -> when {
                // Without Nano, Cloud help sends images too (H68): ask first, naming them.
                action.on && state.value.device == DeviceModelState.UNAVAILABLE -> dialog.value = PrivacyDialog.CLOUD_CONSENT
                else -> viewModelScope.launch { setCloudHelp(action.on) }
            }
            PrivacyAction.OnConsentYes -> {
                dialog.value = PrivacyDialog.NONE
                viewModelScope.launch { setCloudHelp(true) }
            }
            PrivacyAction.OnDismissDialog -> dialog.value = PrivacyDialog.NONE
            PrivacyAction.OnDeleteClick -> dialog.value = PrivacyDialog.DELETE
            PrivacyAction.OnDeleteConfirm -> {
                if (deleting.value) return
                deleting.value = true
                dialog.update { PrivacyDialog.NONE }
                viewModelScope.launch {
                    wiper.wipeContent()
                    snackbars.show(UiText.Dynamic(SettingsCopy.DELETE_DONE))
                    _events.send(PrivacyEvent.Deleted)
                }
            }
            PrivacyAction.OnReadWithCloud -> viewModelScope.launch {
                setCloudHelp(true)
                _events.send(PrivacyEvent.Close)
            }
            PrivacyAction.OnTagByHand -> viewModelScope.launch {
                setCloudHelp(false)
                _events.send(PrivacyEvent.Close)
            }
            else -> Unit
        }
    }

    /** Turning it on also puts the saves that gave up for want of Nano back in line, now for the cloud (as Needs a look does). */
    private suspend fun setCloudHelp(on: Boolean) {
        if (!on) return settings.setCloudHelp(false)
        val noNano = NeedLookRules.rows(library.observeUnread(NeedLookRules.LIMIT).first(), cloudHelp = false)
            .filterIsInstance<NeedLookRow.Failed>().filter { it.copy.readWithCloud }.map { it.save.id }
        settings.setCloudHelp(true)
        noNano.forEach { library.readAgain(it) }
    }
}
