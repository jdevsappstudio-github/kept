package com.jdevsappstudio.kept.feature.needlook

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.designsystem.ButtonShape
import com.jdevsappstudio.kept.core.designsystem.Kept
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.designsystem.KeptTopBar
import com.jdevsappstudio.kept.core.designsystem.PillShape
import com.jdevsappstudio.kept.core.designsystem.SaveThumb
import com.jdevsappstudio.kept.core.designsystem.card
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.data.queue.QueuePause
import org.koin.androidx.compose.koinViewModel

@Composable
fun NeedLookRoot(
    onBack: () -> Unit,
    onOpenSave: (Long) -> Unit,
    onByHand: (Long) -> Unit,
    viewModel: NeedLookViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NeedLookScreen(state) { action ->
        when (action) {
            NeedLookAction.OnBack -> onBack()
            is NeedLookAction.OnSaveClick -> onOpenSave(action.id)
            is NeedLookAction.OnByHandClick -> onByHand(action.id)
            else -> viewModel.onAction(action)
        }
    }
}

/** k-failed / k-failed-cloud (H68). A save leaves this screen once it is read or fixed by hand. */
@Composable
fun NeedLookScreen(state: NeedLookState, onAction: (NeedLookAction) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        KeptTopBar("Needs a look", onBack = { onAction(NeedLookAction.OnBack) })
        if (state.loaded && state.rows.isEmpty()) {
            AllRead()
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.banner?.let { banner -> item(key = "banner") { Banner(banner, state.pause, onAction) } }
            items(state.rows, key = { it.save.id }) { row ->
                when (row) {
                    is NeedLookRow.Pending -> PendingRow(row, state.pause, onAction)
                    is NeedLookRow.Failed -> FailedRow(row, onAction)
                }
            }
            state.readsLine?.let { line ->
                item(key = "reads") {
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (state.consent) ConsentDialog(onAction)
}

@Composable
private fun AllRead() {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Nothing needs a look", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Every save is read.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Banner(banner: NeedLookBanner, pause: QueuePause, onAction: (NeedLookAction) -> Unit) {
    val cloud = pause == QueuePause.OFFLINE
    val fg = if (cloud) Kept.colors.cloud else Kept.colors.wait
    val bg = if (cloud) Kept.colors.cloudBg else Kept.colors.waitBg
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(14.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (cloud) Icons.Outlined.CloudOff else Icons.Outlined.Schedule, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(banner.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(banner.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (banner.readWithCloud) {
            Button(
                onClick = { onAction(NeedLookAction.OnReadWithCloudClick) }, shape = ButtonShape,
                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
            ) { Text("Read with cloud") }
        }
    }
}

@Composable
private fun RowCard(save: Save, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .card(MaterialTheme.colorScheme.surface, 1.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SaveThumb(save.thumbPath ?: save.imagePath, null, Modifier.size(84.dp), corner = 12.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                NeedLookRules.title(save), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            content()
        }
    }
}

@Composable
private fun PendingRow(row: NeedLookRow.Pending, pause: QueuePause, onAction: (NeedLookAction) -> Unit) {
    RowCard(row.save, { onAction(NeedLookAction.OnSaveClick(row.save.id)) }) {
        Row(
            Modifier.clip(PillShape).background(Kept.colors.waitBg).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = Kept.colors.wait, modifier = Modifier.size(12.dp))
            Text(NeedLookRules.pendingLine(row, pause), style = MaterialTheme.typography.labelMedium, color = Kept.colors.wait)
        }
    }
}

@Composable
private fun FailedRow(row: NeedLookRow.Failed, onAction: (NeedLookAction) -> Unit) {
    val id = row.save.id
    RowCard(row.save, { onAction(NeedLookAction.OnSaveClick(id)) }) {
        Text(row.copy.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                row.copy.readWithCloud -> FilledTonalButton(
                    onClick = { onAction(NeedLookAction.OnReadWithCloudClick) }, shape = ButtonShape, modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Read with cloud", maxLines = 1) }
                row.copy.canRetry -> FilledTonalButton(
                    onClick = { onAction(NeedLookAction.OnTryAgainClick(id)) }, shape = ButtonShape, modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Try again", maxLines = 1) }
            }
            OutlinedButton(
                onClick = { onAction(NeedLookAction.OnByHandClick(id)) }, shape = ButtonShape, modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("By hand", maxLines = 1) }
        }
    }
}

/** Cloud help covers image reads on a phone without Nano, so this consent names images (unlike Ask's k-consent). */
@Composable
private fun ConsentDialog(onAction: (NeedLookAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(NeedLookAction.OnNotNow) },
        title = { Text(NeedLookRules.CONSENT_TITLE) },
        text = { Text(NeedLookRules.CONSENT_BODY) },
        confirmButton = { TextButton(onClick = { onAction(NeedLookAction.OnTurnOnCloudHelp) }) { Text(NeedLookRules.CONSENT_YES) } },
        dismissButton = { TextButton(onClick = { onAction(NeedLookAction.OnNotNow) }) { Text(NeedLookRules.CONSENT_NO) } },
    )
}

@Preview(showBackground = true, heightDp = 760)
@Composable
private fun NeedLookPreview() {
    fun save(id: Long, status: SaveStatus, reason: String? = null) = Save(
        id = id, createdAt = 1_764_000_000_000 + id, source = SaveSource.PICKER, sourceApp = null, sharedText = null, url = null,
        imagePath = null, thumbPath = null, status = status, failReason = reason, readBy = null, title = "", summary = "",
        type = SaveType.OTHER, kind = "", place = "", eventDate = "", link = "", tags = emptyList(), items = emptyList(), readAt = null,
    )
    val saves = listOf(save(1, SaveStatus.NEEDS_LOOK, "UNREADABLE"), save(2, SaveStatus.NEEDS_LOOK, "NO_MODEL"), save(3, SaveStatus.QUEUED), save(4, SaveStatus.QUEUED))
    KeptTheme(dark = false) {
        NeedLookScreen(
            NeedLookState(
                rows = NeedLookRules.rows(saves, cloudHelp = false),
                banner = NeedLookRules.banner(QueuePause.CLOUD_CAP, 2),
                pause = QueuePause.CLOUD_CAP,
                readsLine = "30 of 30 read today · resets at midnight",
                loaded = true,
            ),
        ) {}
    }
}
