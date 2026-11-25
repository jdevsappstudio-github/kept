package com.jdevsappstudio.kept.feature.spike

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.data.ai.CardReader
import com.jdevsappstudio.kept.data.ai.CloudPreset
import com.jdevsappstudio.kept.data.ai.DeviceModelState
import com.jdevsappstudio.kept.data.ai.ReadEngine
import com.jdevsappstudio.kept.data.ai.ReadResult
import com.jdevsappstudio.kept.data.files.ImageLoader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Debug-only: one screenshot → one card, to prove the AI path before the queue exists. */
data class ReaderSpikeState(
    val deviceModel: DeviceModelState? = null,
    val image: Bitmap? = null,
    val reading: Boolean = false,
    val result: ReadResult? = null,
    val fileName: String? = null,
    /** null = what Remote Config says (the real app path). */
    val preset: CloudPreset? = null,
)

sealed interface ReaderSpikeAction {
    data class Picked(val uri: Uri) : ReaderSpikeAction
    /** Cycles through files pushed with `adb run-as … files/spike/` (no picker, no personal photos). */
    data object NextTestFile : ReaderSpikeAction
    data class SetPreset(val preset: CloudPreset?) : ReaderSpikeAction
    data class Read(val engine: ReadEngine) : ReaderSpikeAction
}

class ReaderSpikeViewModel(
    private val reader: CardReader,
    private val images: ImageLoader,
    private val testDir: File,
) : ViewModel() {

    private var testIndex = 0

    private val _state = MutableStateFlow(ReaderSpikeState())
    val state: StateFlow<ReaderSpikeState> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.update { it.copy(deviceModel = reader.deviceModelState()) } }
    }

    fun onAction(action: ReaderSpikeAction) {
        when (action) {
            is ReaderSpikeAction.Picked -> viewModelScope.launch {
                val bitmap = images.decodeScaled(action.uri)
                _state.update { it.copy(image = bitmap, result = null, fileName = null) }
            }
            is ReaderSpikeAction.SetPreset -> _state.update { it.copy(preset = action.preset) }
            ReaderSpikeAction.NextTestFile -> viewModelScope.launch {
                val files = withContext(Dispatchers.IO) { testDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty() }
                if (files.isEmpty()) {
                    _state.update { it.copy(fileName = "No files in ${testDir.path}") }
                    return@launch
                }
                val file = files[testIndex++ % files.size]
                val bitmap = images.decodeScaled(Uri.fromFile(file))
                _state.update { it.copy(image = bitmap, result = null, fileName = file.name) }
            }
            is ReaderSpikeAction.Read -> {
                val image = _state.value.image ?: return
                if (_state.value.reading) return
                _state.update { it.copy(reading = true, result = null) }
                viewModelScope.launch {
                    val result = reader.read(image, null, action.engine, _state.value.preset?.choice)
                    _state.update { it.copy(reading = false, result = result) }
                }
            }
        }
    }
}
