package com.jdevsappstudio.kept.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.core.designsystem.ButtonShape
import com.jdevsappstudio.kept.core.designsystem.CardShape
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.designsystem.KeptTopBar
import com.jdevsappstudio.kept.core.designsystem.card
import com.jdevsappstudio.kept.data.ai.DeviceModelDownloads
import com.jdevsappstudio.kept.data.ai.DeviceModelState
import com.jdevsappstudio.kept.data.ai.ModelDownload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import org.koin.androidx.compose.koinViewModel

data class ModelState(val device: DeviceModelState? = null, val download: ModelDownload = ModelDownload()) {
    val copy: ModelCopy get() = OnboardingRules.modelCopy(device, download)
}

sealed interface ModelAction {
    data object OnBack : ModelAction
    data object OnRetry : ModelAction
    data object OnReadWithCloud : ModelAction
}

/** k-model / k-model-failed (H70): the app-scoped download's state, plus the model's own state re-read on each change. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelViewModel(private val downloads: DeviceModelDownloads, deviceModel: suspend () -> DeviceModelState) : ViewModel() {

    val state: StateFlow<ModelState> = downloads.state
        .mapLatest { ModelState(runCatching { deviceModel() }.getOrDefault(DeviceModelState.UNAVAILABLE), it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelState(download = downloads.state.value))

    init {
        // Opened while nothing is running (e.g. after a restart): ask again, it's a no-op when there's nothing to get.
        if (downloads.state.value.phase == ModelDownload.Phase.IDLE) downloads.startIfNeeded()
    }

    fun onAction(action: ModelAction) {
        if (action == ModelAction.OnRetry) downloads.start()
    }
}

@Composable
fun ModelRoot(onBack: () -> Unit, onReadWithCloud: () -> Unit, viewModel: ModelViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModelScreen(state) { action ->
        when (action) {
            ModelAction.OnBack -> onBack()
            ModelAction.OnReadWithCloud -> onReadWithCloud()
            ModelAction.OnRetry -> viewModel.onAction(action)
        }
    }
}

@Composable
fun ModelScreen(state: ModelState, onAction: (ModelAction) -> Unit) {
    val copy = state.copy
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        KeptTopBar("", onBack = { onAction(ModelAction.OnBack) })
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(copy.title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            if (!copy.ready && !copy.unavailable) {
                Text(OnboardingRules.MODEL_BODY, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(
                Modifier.fillMaxWidth().card(MaterialTheme.colorScheme.surface, 1.dp).padding(16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!copy.ready && !copy.failed && !copy.unavailable) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(copy.status, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(copy.statusBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!copy.ready && !copy.failed && !copy.unavailable) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f), CardShape).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(OnboardingRules.SAVE_NOW_TITLE, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(OnboardingRules.SAVE_NOW_BODY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            when {
                copy.failed -> PrimaryButton("Retry", icon = Icons.Outlined.Refresh) { onAction(ModelAction.OnRetry) }
                copy.unavailable -> Unit
                else -> PrimaryButton("Continue") { onAction(ModelAction.OnBack) }
            }
            if (copy.offerCloud || copy.unavailable) {
                OutlinedButton(
                    onClick = { onAction(ModelAction.OnReadWithCloud) }, shape = ButtonShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Read with cloud instead", modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (copy.failed) {
                Text(
                    "Your saves are kept and wait in line.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ModelPreview() {
    KeptTheme(dark = false) { ModelScreen(ModelState(DeviceModelState.DOWNLOADING, ModelDownload(ModelDownload.Phase.DOWNLOADING))) {} }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ModelFailedPreview() {
    KeptTheme(dark = false) { ModelScreen(ModelState(DeviceModelState.DOWNLOADABLE, ModelDownload(ModelDownload.Phase.FAILED, 2))) {} }
}
