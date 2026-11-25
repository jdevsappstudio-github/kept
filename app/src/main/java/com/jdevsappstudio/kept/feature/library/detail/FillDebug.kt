package com.jdevsappstudio.kept.feature.library.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.data.db.KeptDatabase
import com.jdevsappstudio.kept.data.db.LinkRow
import com.jdevsappstudio.kept.data.sent.SentLogLocalDataSource
import kotlinx.coroutines.launch
import org.koin.compose.getKoin
import org.koin.compose.koinInject

/**
 * Debug only (H51): Fill in with cloud + Add a screenshot (k-link) through SaveDetailViewModel, on the newest
 * caption-only links. The last sent-log row shows the exact request.
 */
@Composable
fun FillDebug(db: KeptDatabase = koinInject(), sent: SentLogLocalDataSource = koinInject()) {
    val scope = rememberCoroutineScope()
    var links by remember { mutableStateOf(emptyList<LinkRow>()) }
    var picked by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { links = db.saveDao().recentLinks(5) }
    val style = MaterialTheme.typography.bodySmall

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Fill in: ${links.size} caption-only links", style = style)
        TextButton(onClick = { scope.launch { links = db.saveDao().recentLinks(5) } }) { Text("Refresh") }
    }
    links.forEach { l ->
        Text(
            "${if (picked == l.id) "▶ " else ""}#${l.id} ${l.title.ifBlank { "(no title)" }} · ${l.url.take(40)}",
            style = style,
            modifier = androidx.compose.ui.Modifier.clickable { picked = l.id },
        )
    }
    picked?.let { id -> FillDebugSave(id, sent) }
}

@Composable
private fun FillDebugSave(id: Long, sent: SentLogLocalDataSource) {
    val koin = getKoin()
    val vm: SaveDetailViewModel = viewModel(key = "fill-debug-$id") {
        SaveDetailViewModel(
            SavedStateHandle(mapOf(SaveDetailViewModel.ARG_ID to id)),
            koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(),
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val log by remember { sent.observe() }.collectAsStateWithLifecycle(emptyList())
    var last by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        vm.onAction(SaveDetailAction.OnScreenshotPicked(uri?.toString()))
    }
    ObserveAsEvents(vm.events) { e ->
        last = e.toString()
        if (e == SaveDetailEvent.PickScreenshot) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    val style = MaterialTheme.typography.bodySmall
    val s = state.save ?: return
    Text("${s.status} · ${s.readBy} · ${s.type.wire}/${s.kind} · filled=${s.filledAt != null}", style = style)
    Text("${s.title} — ${s.summary.take(120)}", style = style)
    Text("tags ${s.tags} · items ${s.items}", style = style)
    val link = state.link
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (link == null) Text("Not a caption-only link", style = style)
        else {
            val label = buildString {
                append(if (link.filling) "Filling…" else "Fill in with cloud")
                if (link.showAnswersLeft) append(" · ${link.answersLeft} left")
            }
            TextButton(onClick = { vm.onAction(SaveDetailAction.OnFillInClick) }, enabled = link.canFill && !link.filling) {
                Text(label)
            }
            TextButton(onClick = { vm.onAction(SaveDetailAction.OnAddScreenshotClick) }) { Text("Add a screenshot") }
        }
    }
    if (state.dialog == DetailDialog.CloudConsent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("k-consent:", style = style)
            TextButton(onClick = { vm.onAction(SaveDetailAction.OnConfirmDialog) }) { Text("Turn on") }
            TextButton(onClick = { vm.onAction(SaveDetailAction.OnDismissDialog) }) { Text("Not now") }
        }
    }
    if (last.isNotEmpty()) Text("Last event: $last", style = style)
    log.firstOrNull()?.let { Text("Sent ${it.kind}: ${it.text.take(160)}", style = style) }
}
