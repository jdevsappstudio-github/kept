package com.jdevsappstudio.kept.feature.spike

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import com.jdevsappstudio.kept.data.ai.CloudPreset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.data.ai.DeviceModelState
import com.jdevsappstudio.kept.data.ai.ReadEngine
import com.jdevsappstudio.kept.data.ai.ReadResult
import org.koin.androidx.compose.koinViewModel

@Composable
fun ReaderSpikeRoot(viewModel: ReaderSpikeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onAction(ReaderSpikeAction.Picked(uri))
    }
    ReaderSpikeScreen(
        state = state,
        onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onAction = viewModel::onAction,
    )
}

@Composable
private fun ReaderSpikeScreen(state: ReaderSpikeState, onPick: () -> Unit, onAction: (ReaderSpikeAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Reader spike (debug)", style = MaterialTheme.typography.titleLarge)
        Text("On-device model: ${state.deviceModel ?: "checking…"}", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = state.preset == null,
                onClick = { onAction(ReaderSpikeAction.SetPreset(null)) },
                label = { Text("From config", style = MaterialTheme.typography.labelSmall) },
            )
            CloudPreset.entries.forEach { p ->
                FilterChip(
                    selected = p == state.preset,
                    onClick = { onAction(ReaderSpikeAction.SetPreset(p)) },
                    label = { Text(p.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPick) { Text("Pick") }
            OutlinedButton(onClick = { onAction(ReaderSpikeAction.NextTestFile) }) { Text("Test file") }
            Button(
                onClick = { onAction(ReaderSpikeAction.Read(ReadEngine.CLOUD)) },
                enabled = state.image != null && !state.reading,
            ) { Text("Read in cloud") }
        }
        if (state.deviceModel == DeviceModelState.AVAILABLE) {
            Button(
                onClick = { onAction(ReaderSpikeAction.Read(ReadEngine.DEVICE)) },
                enabled = state.image != null && !state.reading,
            ) { Text("Read on this phone") }
        }
        state.fileName?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        state.image?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Picked screenshot",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
            )
            Text("${it.width} × ${it.height} px", style = MaterialTheme.typography.labelSmall)
        }
        if (state.reading) CircularProgressIndicator()
        when (val r = state.result) {
            is ReadResult.Success -> Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${r.readBy} · ${r.model} · ${r.millis} ms", style = MaterialTheme.typography.labelLarge)
                Text(r.card.title, style = MaterialTheme.typography.headlineSmall)
                Text("Type: ${SaveType.fromWire(r.card.type).label} · Kind: ${r.card.kind}")
                Text(r.card.summary)
                if (r.card.tags.isNotEmpty()) Text("Tags: ${r.card.tags.joinToString()}")
                if (r.card.items.isNotEmpty()) Text("Items: ${r.card.items.joinToString(" · ")}")
                if (r.card.place.isNotEmpty()) Text("Place: ${r.card.place}")
                if (r.card.date.isNotEmpty()) Text("Date: ${r.card.date}")
                if (r.card.link.isNotEmpty()) Text("Link: ${r.card.link}")
            }
            is ReadResult.Failure -> Text(
                "Failed: ${r.reason}\n${r.detail.orEmpty()}",
                color = MaterialTheme.colorScheme.error,
            )
            null -> Unit
        }
    }
}
