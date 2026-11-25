package com.jdevsappstudio.kept.feature.add

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.data.capture.ClipPeek
import com.jdevsappstudio.kept.data.capture.Keeper
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** Debug only (H51): the + sheet as AddViewModel sees it, until the real k-addmenu lands on 27 Nov. */
@Composable
fun AddDebug(viewModel: AddViewModel = koinViewModel(), imports: PickedImports = koinInject()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by imports.progress.collectAsStateWithLifecycle()
    var last by remember { mutableStateOf("") }
    val on = viewModel::onAction
    val style = MaterialTheme.typography.bodySmall
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(pickerLimit(Keeper.MAX_PER_BATCH))) { uris ->
        on(AddAction.OnPicked(uris.map { it.toString() }))
    }
    LifecycleResumeEffect(Unit) {
        on(AddAction.OnShown)
        onPauseOrDispose { }
    }
    ObserveAsEvents(viewModel.events) { event ->
        last = event.toString()
        if (event is AddEvent.LaunchPicker) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Row {
        TextButton(onClick = { on(AddAction.OnPickScreenshots) }) { Text("Pick (debug)") }
        // Test images pushed into files/import (adb + run-as) go in as if picked: same AddViewModel → PickedImports path.
        TextButton(onClick = {
            scope.launch {
                val refs = withContext(Dispatchers.IO) {
                    File(context.filesDir, "import").listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }
                        .map { Uri.fromFile(it).toString() }
                }
                on(AddAction.OnPicked(refs))
            }
        }) { Text("Import") }
        TextButton(onClick = { on(AddAction.OnPasteLink) }, enabled = !state.keeping) {
            Text(if (state.clip == ClipPeek.LINK) "Paste · a link is copied" else "Paste a link")
        }
    }
    progress?.let { Text("Adding ${it.done} of ${it.total}", style = style) }
    state.field?.let { field ->
        OutlinedTextField(
            value = field.text,
            onValueChange = { on(AddAction.OnPasteTextChange(it)) },
            label = { Text("Paste or type a link") },
            isError = field.error != null,
            supportingText = field.error?.let { e -> { Text(e.asString()) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { on(AddAction.OnAddPasted) }, enabled = field.canAdd && !state.keeping) { Text("Add") }
    }
    if (last.isNotEmpty()) Text("Event: $last", style = style)
}

/** The system picker refuses more than its own limit (100 on most phones); older phones fall back to the docs picker. */
@SuppressLint("NewApi")
internal fun pickerLimit(want: Int): Int {
    val hasPicker = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2)
    return if (hasPicker) minOf(want, MediaStore.getPickImagesMaxLimit()) else want
}
