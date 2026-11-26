package com.jdevsappstudio.kept.feature.ask

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.designsystem.ExampleBadge
import com.jdevsappstudio.kept.core.designsystem.ButtonShape
import com.jdevsappstudio.kept.core.designsystem.CardShape
import com.jdevsappstudio.kept.core.designsystem.Kept
import com.jdevsappstudio.kept.core.designsystem.KeptIconButton
import com.jdevsappstudio.kept.core.designsystem.KeptMark
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.designsystem.KeptTopBar
import com.jdevsappstudio.kept.core.designsystem.PillShape
import com.jdevsappstudio.kept.core.designsystem.SaveThumb
import com.jdevsappstudio.kept.core.designsystem.VSpace
import com.jdevsappstudio.kept.core.designsystem.card
import com.jdevsappstudio.kept.core.model.AskRef
import com.jdevsappstudio.kept.core.model.AskTurn
import com.jdevsappstudio.kept.core.model.AskTurnStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.presentation.openEmail
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import org.koin.androidx.compose.koinViewModel

/**
 * k-ask, k-ask-first and k-consent (H29, H58, UI7). The chat comes from Room; consent and Report are sheets
 * over the screen, driven by [AskState.cloudHelp] and [AskState.reportingTurnId].
 */
@Composable
fun AskRoot(
    onOpenSave: (Long) -> Unit,
    onOpenSent: (Long) -> Unit,
    onOpenCloudOut: () -> Unit,
    /** k-cloud-out paid out (set on this back-stack entry by the NavHost); [onGrantConsumed] clears it. */
    answersGranted: Boolean = false,
    onGrantConsumed: () -> Unit = {},
    onClose: () -> Unit,
    viewModel: AskViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(answersGranted) {
        if (answersGranted) {
            onGrantConsumed()
            viewModel.onAction(AskAction.OnAnswersGranted)
        }
    }
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is AskEvent.OpenSave -> onOpenSave(event.saveId)
            is AskEvent.OpenSent -> onOpenSent(event.sentId)
            AskEvent.OpenCloudOut -> onOpenCloudOut()
            is AskEvent.SendReport -> context.openEmail(event.email)
            AskEvent.Close -> onClose()
        }
    }
    AskScreen(state, viewModel::onAction)
}

@Composable
fun AskScreen(state: AskState, onAction: (AskAction) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        KeptTopBar(AskCopy.TITLE, onBack = null) {
            if (state.turns.isNotEmpty()) ChatMenu(enabled = !state.sending, onClear = { onAction(AskAction.OnClearChat) })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> Unit
                state.firstRun -> FirstRun(state, onAction)
                else -> Chat(state, onAction)
            }
        }
        if (!state.online) OfflineBanner()
        if (state.showCounter && !state.firstRun) {
            Text(
                AskCopy.counterLine(state.answersLeft, state.answersTotal),
                style = MaterialTheme.typography.labelMedium,
                color = Kept.colors.text3,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
        InputRow(state, onAction)
    }
    if (!state.loading && !state.cloudHelp) ConsentSheet(onAction)
    state.reportingTurnId?.let { ReportSheet(onAction) }
}

@Composable
private fun ChatMenu(enabled: Boolean, onClear: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        KeptIconButton(Icons.Outlined.MoreVert, "More", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(AskCopy.CLEAR_CHAT) },
                enabled = enabled,
                onClick = { open = false; onClear() },
            )
        }
    }
}

/** k-ask-first: what goes to the cloud, then 3 starters from the person's own saves (or the few-saves line). */
@Composable
private fun FirstRun(state: AskState, onAction: (AskAction) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KeptMark(size = 72.dp)
        VSpace(20.dp)
        Text(
            AskCopy.FIRST_TITLE,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        VSpace(8.dp)
        Text(
            AskCopy.FIRST_BODY,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        VSpace(28.dp)
        if (state.fewSaves) {
            Text(AskCopy.FEW_SAVES, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        } else if (state.starters.isNotEmpty()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(AskCopy.STARTERS_LABEL, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
                state.starters.forEach { s ->
                    OutlinedButton(
                        onClick = { onAction(AskAction.OnStarter(s)) },
                        enabled = !state.sending && state.online,
                        shape = ButtonShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) { Text(s, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        if (state.answersTotal > 0) {
            VSpace(20.dp)
            Text(AskCopy.dailyLine(state.answersTotal), style = MaterialTheme.typography.bodyMedium, color = Kept.colors.text3)
        }
    }
}

@Composable
private fun Chat(state: AskState, onAction: (AskAction) -> Unit) {
    val list = rememberLazyListState()
    val last = state.turns.lastOrNull()
    // A new question or an answer landing: bring the newest turn up (its question at the top, so a long answer reads down).
    // [seen] survives a trip to detail / k-sent, so coming back keeps the reader's place instead of jumping down.
    var seen by rememberSaveable { mutableStateOf("") }
    val key = "${state.turns.size}:${last?.status}"
    LaunchedEffect(key) {
        if (key == seen || state.turns.isEmpty()) return@LaunchedEffect
        if (seen.isEmpty()) list.scrollToItem(state.turns.lastIndex) else list.animateScrollToItem(state.turns.lastIndex)
        seen = key
    }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.turns, key = { it.id }) { turn -> TurnItem(turn, retryEnabled = !state.sending && state.online, onAction) }
    }
}

@Composable
private fun TurnItem(turn: AskTurn, retryEnabled: Boolean, onAction: (AskAction) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            turn.question,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.End).widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Column(
            Modifier.fillMaxWidth(0.94f).card(MaterialTheme.colorScheme.surface).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                turn.status == AskTurnStatus.PENDING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(AskCopy.THINKING, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
                }
                turn.status == AskTurnStatus.FAILED -> InlineNote(AskCopy.FAILED, AskCopy.TRY_AGAIN, retryEnabled) { onAction(AskAction.OnRetry(turn.id)) }
                turn.hidden -> InlineNote(AskCopy.REPORTED, AskCopy.SHOW, enabled = true) { onAction(AskAction.OnShowAnswer(turn.id)) }
                else -> Answer(turn, onAction)
            }
        }
    }
}

@Composable
private fun InlineNote(text: String, action: String, enabled: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
        TextButton(onClick = onClick, enabled = enabled) { Text(action, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun Answer(turn: AskTurn, onAction: (AskAction) -> Unit) {
    if (turn.answer.isNotBlank()) {
        Text(turn.answer, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
    turn.refs.forEach { ref -> RefRow(ref) { onAction(AskAction.OnRefClick(ref.saveId)) } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // The chip gives way (ellipsis) so Report never wraps.
        CloudChip(turn.sourceCount, Modifier.weight(1f, fill = false), onClick = turn.sentId?.let { { onAction(AskAction.OnCloudChip(turn.id)) } })
        TextButton(onClick = { onAction(AskAction.OnReport(turn.id)) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(AskCopy.REPORT, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun RefRow(ref: AskRef, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(ButtonShape).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaveThumb(ref.thumbPath, ref.type, Modifier.size(44.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Text(ref.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (ref.example) ExampleBadge(Modifier.padding(vertical = 2.dp))
            if (ref.line.isNotBlank()) {
                Text(ref.line, style = MaterialTheme.typography.labelMedium, color = Kept.colors.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 'Cloud · sent text of 9 saves'; tap → k-sent at that request (no request id → a plain label). */
@Composable
private fun CloudChip(sourceCount: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)?) {
    val c = Kept.colors
    Row(
        modifier.clip(PillShape).background(c.cloudBg)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "See what was sent", role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 32.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Outlined.Cloud, contentDescription = null, tint = c.cloud, modifier = Modifier.size(14.dp))
        Text(AskCopy.cloudChip(sourceCount), style = MaterialTheme.typography.labelMedium, color = c.cloud, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OfflineBanner() {
    val c = Kept.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(ButtonShape).background(c.waitBg).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = c.wait, modifier = Modifier.size(18.dp))
        Text(AskCopy.OFFLINE, style = MaterialTheme.typography.bodyMedium, color = c.wait, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun InputRow(state: AskState, onAction: (AskAction) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.input,
            onValueChange = { onAction(AskAction.OnInputChange(it)) },
            placeholder = { Text(AskCopy.INPUT_HINT) },
            maxLines = 4,
            shape = ButtonShape,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (state.canSend) onAction(AskAction.OnSend) }),
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(
            onClick = { onAction(AskAction.OnSend) },
            enabled = state.canSend,
            shape = ButtonShape,
            modifier = Modifier.padding(start = 10.dp).size(52.dp),
        ) { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send") }
    }
}

/** k-consent (H29). Back, a tap outside or a swipe down = Not now. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConsentSheet(onAction: (AskAction) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onAction(AskAction.OnNotNow) },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Kept.colors.cloudBg), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Cloud, contentDescription = null, tint = Kept.colors.cloud, modifier = Modifier.size(28.dp))
            }
            VSpace(10.dp)
            Text(AskCopy.CONSENT_TITLE, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
            VSpace(6.dp)
            Text(AskCopy.CONSENT_BODY, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VSpace(16.dp)
            Button(onClick = { onAction(AskAction.OnTurnOnCloudHelp) }, shape = ButtonShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(AskCopy.CONSENT_YES, style = MaterialTheme.typography.labelLarge)
            }
            VSpace(8.dp)
            TextButton(onClick = { onAction(AskAction.OnNotNow) }, shape = ButtonShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(AskCopy.CONSENT_NO, style = MaterialTheme.typography.labelLarge)
            }
            VSpace(6.dp)
            Text(AskCopy.CONSENT_FOOT, style = MaterialTheme.typography.labelMedium, color = Kept.colors.text3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Report (Play AI-generated content policy): a reason, an optional note for Other, then the mail app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(onAction: (AskAction) -> Unit) {
    var reason by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = { onAction(AskAction.OnReportDismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Text(AskCopy.REPORT_TITLE, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
            VSpace(6.dp)
            Text(AskCopy.REPORT_BODY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VSpace(8.dp)
            Column(Modifier.selectableGroup()) {
                ReportReason.entries.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(ButtonShape)
                            .selectable(selected = reason == r, onClick = { reason = r }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = null)
                        Text(r.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            if (AskCopy.showsNote(reason)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text(AskCopy.REPORT_NOTE) },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onAction(AskAction.OnReportDismiss) }, shape = ButtonShape, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Text(AskCopy.CANCEL, style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = { reason?.let { onAction(AskAction.OnReportSend(it, if (AskCopy.showsNote(it)) note.trim() else "")) } },
                    enabled = AskCopy.canSendReport(reason),
                    shape = ButtonShape,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text(AskCopy.REPORT_SEND, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

// ---- Previews ----

private val previewTurns = listOf(
    AskTurn(
        id = 1, createdAt = 0, question = "Which Goa cafés did I save?",
        answer = "You saved 3: Sunbeam Café for banana pancakes, Tide Table for sunset seating, and Little Fig (closed Tuesdays).",
        refs = listOf(
            AskRef(1, "Sunbeam Café", SaveType.PLACE, line = "Assagao · banana pancakes"),
            AskRef(2, "Tide Table", SaveType.PLACE, line = "Morjim · sunset seating"),
            AskRef(3, "Little Fig", SaveType.PLACE, line = "Panjim · closed Tuesdays"),
        ),
        status = AskTurnStatus.DONE, hidden = false, sentId = 7, sourceCount = 9,
    ),
    AskTurn(2, 0, "Only ones open Tuesday", "", emptyList(), AskTurnStatus.FAILED, hidden = false, sentId = null),
    AskTurn(3, 0, "Which is closest to Anjuna?", "", emptyList(), AskTurnStatus.PENDING, hidden = false, sentId = null),
)

@Preview(showBackground = true, heightDp = 760)
@Composable
private fun AskChatPreview() = KeptTheme {
    AskScreen(AskState(loading = false, turns = previewTurns, cloudHelp = true, sending = true, answersLeft = 2, answersTotal = 5, showCounter = true)) {}
}

@Preview(showBackground = true, heightDp = 760)
@Composable
private fun AskFirstPreview() = KeptTheme {
    AskScreen(
        AskState(
            loading = false, cloudHelp = true, answersLeft = 5, answersTotal = 5,
            starters = listOf("What can I cook tonight?", "Which Goa cafés did I save?", "Which movies did I save?"),
        ),
    ) {}
}

@Preview(showBackground = true, heightDp = 760)
@Composable
private fun AskOfflinePreview() = KeptTheme {
    AskScreen(AskState(loading = false, turns = previewTurns.take(1), cloudHelp = true, online = false, input = "Any brunch spots?")) {}
}
