package com.jdevsappstudio.kept.feature.onboarding

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.ShareApp
import com.jdevsappstudio.kept.core.model.itemAction
import com.jdevsappstudio.kept.core.model.ItemAction
import com.jdevsappstudio.kept.data.ai.DeviceModelDownloads
import com.jdevsappstudio.kept.data.ai.DeviceModelState
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.data.settings.SettingsStore
import com.jdevsappstudio.kept.feature.add.PickedImports
import com.jdevsappstudio.kept.navigation.OpenRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Stable
data class OnboardingState(
    /** Null while the on-device check runs (Get started waits for it). */
    val device: DeviceModelState? = null,
    val cloudHelp: Boolean = false,
    val types: List<SaveType> = emptyList(),
    val apps: List<ShareApp> = emptyList(),
    /** The first save, once one exists (picked here or shared from another app), followed live. */
    val firstSave: Save? = null,
    val pause: QueuePause = QueuePause.NONE,
    /** A picked screenshot is being copied in. */
    val adding: Boolean = false,
) {
    /** k-promise-cloud / k-unsupported path. */
    val cloudPhone: Boolean get() = device == DeviceModelState.UNAVAILABLE
}

sealed interface OnboardingAction {
    data object OnGetStarted : OnboardingAction
    data object OnHowKeptUsesAi : OnboardingAction
    data class OnToggleType(val type: SaveType) : OnboardingAction
    data object OnTypesNext : OnboardingAction
    data class OnToggleApp(val app: ShareApp) : OnboardingAction
    data object OnAppsNext : OnboardingAction
    data object OnSkip : OnboardingAction
    data object OnBack : OnboardingAction
    data object OnPick : OnboardingAction
    /** Picker result; null = cancelled (stays on k-ob-first). */
    data class OnPicked(val ref: String?) : OnboardingAction
    data class OnOpenApp(val app: ShareApp) : OnboardingAction
    data object OnGoToKept : OnboardingAction
    /** The card's second link: Open the recipe / See all 5 / Open it (Open in Maps is launched by the Root). */
    data object OnOpenSave : OnboardingAction
    data object OnOpenInMaps : OnboardingAction
    data object OnEditTags : OnboardingAction
}

sealed interface OnboardingEvent {
    data object ToUnsupported : OnboardingEvent
    data object ToTypes : OnboardingEvent
    data object ToApps : OnboardingEvent
    data object ToFirst : OnboardingEvent
    data object LaunchPicker : OnboardingEvent
    data class Launch(val target: ItemAction.Target) : OnboardingEvent
    /** Onboarding is over; [open] = a save to show (and maybe edit) on top of Home. */
    data class Finish(val open: OpenRequest?) : OnboardingEvent
}

/** One ViewModel for the whole first run (k-promise → B1 → B2 → k-ob-first → first card), scoped to its NavHost. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModel(
    private val settings: SettingsStore,
    library: LibraryLocalDataSource,
    private val imports: PickedImports,
    private val downloads: DeviceModelDownloads,
    deviceModel: suspend () -> DeviceModelState,
    pause: Flow<QueuePause>,
    clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val picks = MutableStateFlow(Picks())

    /** Re-checked when the download moves on, so 'getting ready' turns into 'ready'. */
    private val device: Flow<DeviceModelState?> = downloads.state
        .mapLatest<_, DeviceModelState?> { withTimeoutOrNull(DEVICE_CHECK_MS) { deviceModel() } ?: DeviceModelState.UNAVAILABLE }
        .onStart { emit(null) }

    private val openedAt = clock()

    /**
     * The first save kept since onboarding opened (a debug replay over a full library ignores the old ones):
     * its id is pinned, then the row is followed (reading → read).
     */
    private val firstSave: Flow<Save?> = library.observeNewest()
        .map { s -> s?.takeIf { it.createdAt >= openedAt }?.id }
        .filterNotNull()
        .take(1)
        .flatMapLatest { library.observeSave(it) }
        .onStart { emit(null) }

    val state: StateFlow<OnboardingState> = combine(
        device, settings.cloudHelp, combine(picks, imports.progress) { p, adding -> p to (adding != null) }, firstSave, pause,
    ) { d, cloud, (p, adding), first, q ->
        // A pick that added nothing (a duplicate, an unreadable file) ends the progress too, so the button comes back.
        OnboardingState(d, cloud, p.types, p.apps, first, q, adding = adding && first == null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingState())

    private val _events = Channel<OnboardingEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: OnboardingAction) {
        when (action) {
            OnboardingAction.OnGetStarted -> when (state.value.device) {
                null -> Unit
                DeviceModelState.UNAVAILABLE -> send(OnboardingEvent.ToUnsupported)
                else -> {
                    downloads.startIfNeeded() // flows: Get started → 'Model download starts in the background'
                    send(OnboardingEvent.ToTypes)
                }
            }
            is OnboardingAction.OnToggleType -> picks.update { it.copy(types = OnboardingRules.toggleType(it.types, action.type)) }
            OnboardingAction.OnTypesNext -> viewModelScope.launch {
                settings.setPreferredTypes(picks.value.types)
                _events.send(OnboardingEvent.ToApps)
            }
            is OnboardingAction.OnToggleApp -> picks.update { it.copy(apps = OnboardingRules.toggleApp(it.apps, action.app)) }
            OnboardingAction.OnAppsNext -> viewModelScope.launch {
                settings.setShareApps(picks.value.apps)
                _events.send(OnboardingEvent.ToFirst)
            }
            OnboardingAction.OnPick -> send(OnboardingEvent.LaunchPicker)
            is OnboardingAction.OnPicked -> action.ref?.let { imports.start(listOf(it)) }
            // H35 Skip setup / Not now: defaults for whatever wasn't answered; lands on k-ob-ready.
            OnboardingAction.OnSkip, OnboardingAction.OnGoToKept -> finish(null)
            OnboardingAction.OnOpenSave -> state.value.firstSave?.let { finish(OpenRequest(it.id)) }
            OnboardingAction.OnEditTags -> state.value.firstSave?.let { finish(OpenRequest(it.id, edit = true)) }
            OnboardingAction.OnOpenInMaps -> state.value.firstSave
                ?.takeIf { it.status == SaveStatus.READ }
                ?.let { itemAction(it.type, it.place.ifBlank { it.title }, null) }
                ?.let { send(OnboardingEvent.Launch(it.target)) }
            // Navigation-only taps are handled by the Roots.
            OnboardingAction.OnHowKeptUsesAi, OnboardingAction.OnBack, is OnboardingAction.OnOpenApp -> Unit
        }
    }

    /** The event goes first (rendezvous: the Root has it before the gate flips), then the flag. */
    private fun finish(open: OpenRequest?) {
        viewModelScope.launch {
            _events.send(OnboardingEvent.Finish(open))
            settings.setOnboardingDone(true)
        }
    }

    private fun send(event: OnboardingEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private data class Picks(
        val types: List<SaveType> = emptyList(),
        val apps: List<ShareApp> = emptyList(),
    )

    private companion object {
        /** AICore answers in milliseconds; a hung check counts as 'no on-device AI' rather than a stuck button. */
        const val DEVICE_CHECK_MS = 3_000L
    }
}
