package com.jdevsappstudio.kept.feature.ask

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.model.AskTurnStatus
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.data.sent.SentLogLocalDataSource
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** Debug only (H51): drives AskViewModel from Home until the k-ask screen exists (UI sweep, 27 Nov). */
@Composable
fun AskDebug(viewModel: AskViewModel = koinViewModel(), sentLog: SentLogLocalDataSource = koinInject()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sent by remember { sentLog.observe() }.collectAsState(emptyList())
    var last by remember { mutableStateOf("") }
    var shownSent by remember { mutableStateOf<Long?>(null) }
    ObserveAsEvents(viewModel.events) { e ->
        last = e.toString().take(160)
        if (e is AskEvent.OpenSent) shownSent = if (shownSent == e.sentId) null else e.sentId
    }
    val style = MaterialTheme.typography.bodySmall
    val a: (AskAction) -> Unit = viewModel::onAction

    Text(
        "Ask · ${state.turns.size} turns · answers ${state.answersLeft} of ${state.answersTotal}" +
            (if (state.showCounter) " (low)" else "") + (if (!state.online) " · offline" else "") +
            (if (state.sending) " · Thinking…" else "") + " · sent log ${sent.size}",
        style = style,
    )
    if (!state.cloudHelp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("k-consent:", style = style)
            TextButton(onClick = { a(AskAction.OnTurnOnCloudHelp) }) { Text("Turn on Cloud help") }
            TextButton(onClick = { a(AskAction.OnNotNow) }) { Text("Not now") }
        }
    }
    if (state.firstRun) {
        if (state.fewSaves) Text("Keep a few saves first, then ask about them", style = style)
        state.starters.forEach { s -> Text("› $s", style = style, modifier = Modifier.clickable { a(AskAction.OnStarter(s)) }) }
    }
    state.turns.takeLast(3).forEach { t ->
        Text("Q: ${t.question}", style = style)
        when {
            t.status == AskTurnStatus.PENDING -> Text("A: Thinking…", style = style)
            t.status == AskTurnStatus.FAILED -> Text(
                "A: Cloud didn't answer. Try again", style = style,
                modifier = Modifier.clickable { a(AskAction.OnRetry(t.id)) },
            )
            t.hidden -> Text("A: (reported) Show", style = style, modifier = Modifier.clickable { a(AskAction.OnShowAnswer(t.id)) })
            else -> {
                Text("A: ${t.answer}", style = style)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    t.refs.forEach { r ->
                        Text("[${r.title.take(18)}]", style = style, modifier = Modifier.clickable { a(AskAction.OnRefClick(r.saveId)) })
                    }
                    Text("Cloud", style = style, modifier = Modifier.clickable { a(AskAction.OnCloudChip(t.id)) })
                    Text("Report", style = style, modifier = Modifier.clickable { a(AskAction.OnReport(t.id)) })
                }
                if (state.reportingTurnId == t.id) {
                    Row {
                        ReportReason.entries.forEach { r -> TextButton(onClick = { a(AskAction.OnReportSend(r, "")) }) { Text(r.label, style = style) } }
                    }
                }
            }
        }
        if (shownSent != null && shownSent == t.sentId) {
            Text(sent.firstOrNull { it.id == t.sentId }?.text.orEmpty().take(1_200), style = style)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.input, onValueChange = { a(AskAction.OnInputChange(it)) },
            label = { Text("Ask your saves") }, modifier = Modifier.weight(1f), singleLine = true,
        )
        TextButton(onClick = { a(AskAction.OnSend) }, enabled = state.canSend) { Text("Send") }
    }
    Row {
        TextButton(onClick = { a(AskAction.OnClearChat) }, enabled = !state.sending) { Text("Clear chat") }
        sent.firstOrNull()?.let { s -> Text("Last sent: ${s.kind} ${s.text.length} chars, ${s.images} img", style = style) }
    }
    if (last.isNotEmpty()) Text("Last event: $last", style = style, modifier = Modifier.fillMaxWidth())
}
