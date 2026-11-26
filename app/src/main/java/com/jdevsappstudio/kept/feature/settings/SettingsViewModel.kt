package com.jdevsappstudio.kept.feature.settings

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.core.model.ThemeMode
import com.jdevsappstudio.kept.data.ai.DeviceModelState
import com.jdevsappstudio.kept.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Stable
data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** The saved choice; the screen shows it on only while Android lets Kept post too. */
    val readingUpdates: Boolean = true,
    val privacyLine: String = "",
    val loaded: Boolean = false,
)

sealed interface SettingsAction {
    data object OnBack : SettingsAction
    data object OnAppearanceClick : SettingsAction
    data object OnPrivacyClick : SettingsAction
    data object OnFeedbackClick : SettingsAction
    data object OnAboutClick : SettingsAction
    data class OnThemeModeClick(val mode: ThemeMode) : SettingsAction
    /** Sent only once Android allows notifications (the Root asks first). */
    data class OnReadingUpdatesChange(val on: Boolean) : SettingsAction
    /** Debug builds only (H70): clears the done flag, so MainActivity shows onboarding again. */
    data object OnReplayOnboarding : SettingsAction
}

/** k-settings hub + k-appearance (H68). */
class SettingsViewModel(
    private val settings: SettingsStore,
    deviceModel: suspend () -> DeviceModelState,
) : ViewModel() {

    private val device: Flow<DeviceModelState?> = flow<DeviceModelState?> { emit(deviceModel()) }.onStart { emit(null) }

    val state: StateFlow<SettingsState> = combine(
        settings.themeMode, settings.readingUpdates, settings.cloudHelp, device,
    ) { mode, updates, cloudHelp, model ->
        SettingsState(mode, updates, SettingsCopy.privacyLine(model, cloudHelp), loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    /** Navigation, the email and the permission ask are handled by the Root. */
    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.OnThemeModeClick -> viewModelScope.launch { settings.setThemeMode(action.mode) }
            is SettingsAction.OnReadingUpdatesChange -> viewModelScope.launch { settings.setReadingUpdates(action.on) }
            SettingsAction.OnReplayOnboarding -> viewModelScope.launch { settings.setOnboardingDone(false) }
            else -> Unit
        }
    }
}
