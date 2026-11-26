package com.jdevsappstudio.kept.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Kept knows about its own ask for the on-device model ([failures] counts failed tries since start). */
data class ModelDownload(val phase: Phase = Phase.IDLE, val failures: Int = 0) {
    enum class Phase { IDLE, DOWNLOADING, DONE, FAILED }
}

/**
 * The on-device model download, on the app scope so leaving k-model never stops it (H70: started by onboarding's
 * Get started, k-model's Retry, and app start once onboarding is done). Android/AICore does the actual download.
 */
class DeviceModelDownloads(private val reader: CardReader, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(ModelDownload())
    val state: StateFlow<ModelDownload> = _state.asStateFlow()
    private var job: Job? = null

    /** Starts only when the model is there to download (or already coming); a running ask is never doubled. */
    fun startIfNeeded() {
        scope.launch {
            when (reader.deviceModelStateSafe()) {
                DeviceModelState.DOWNLOADABLE, DeviceModelState.DOWNLOADING -> start()
                DeviceModelState.AVAILABLE -> _state.update { it.copy(phase = ModelDownload.Phase.DONE) }
                DeviceModelState.UNAVAILABLE -> Unit
            }
        }
    }

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        _state.update { it.copy(phase = ModelDownload.Phase.DOWNLOADING) }
        job = scope.launch {
            var failed = false
            try {
                reader.downloadDeviceModel().collect { step ->
                    when (step) {
                        ModelDownloadStep.Done -> _state.update { it.copy(phase = ModelDownload.Phase.DONE) }
                        is ModelDownloadStep.Failed -> failed = true
                        else -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
            }
            // The flow can end without a Done (already there, or nothing to get): the model's own state decides.
            if (_state.value.phase == ModelDownload.Phase.DOWNLOADING) {
                val ready = !failed && reader.deviceModelStateSafe() == DeviceModelState.AVAILABLE
                _state.update {
                    if (ready) it.copy(phase = ModelDownload.Phase.DONE)
                    else it.copy(phase = ModelDownload.Phase.FAILED, failures = it.failures + 1)
                }
            }
        }
    }
}

private suspend fun CardReader.deviceModelStateSafe(): DeviceModelState = try {
    deviceModelState()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    DeviceModelState.UNAVAILABLE
}
