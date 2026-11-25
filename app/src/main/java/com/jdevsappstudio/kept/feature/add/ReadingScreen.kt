package com.jdevsappstudio.kept.feature.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.designsystem.Kept
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.designsystem.KeptTopBar
import com.jdevsappstudio.kept.core.designsystem.PillShape
import com.jdevsappstudio.kept.core.designsystem.RouteBadge
import com.jdevsappstudio.kept.core.designsystem.SaveThumb
import com.jdevsappstudio.kept.core.designsystem.TagPill
import com.jdevsappstudio.kept.core.designsystem.card
import com.jdevsappstudio.kept.core.designsystem.typeIcon
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.feature.home.HomeLines
import com.jdevsappstudio.kept.feature.home.ReadsWith
import com.jdevsappstudio.kept.feature.library.displayTitle
import com.jdevsappstudio.kept.feature.library.metaLine
import org.koin.androidx.compose.koinViewModel

@Composable
fun ReadingRoot(
    onBack: () -> Unit,
    onOpenSave: (Long) -> Unit,
    onOpenEdit: (Long) -> Unit,
    onOpenNeedLook: () -> Unit,
    viewModel: ReadingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ReadingEvent.OpenEdit -> onOpenEdit(event.id)
        }
    }
    ReadingScreen(state) { action ->
        when (action) {
            ReadingAction.OnBack -> onBack()
            is ReadingAction.OnSaveClick -> onOpenSave(action.id)
            is ReadingAction.OnEditClick -> onOpenEdit(action.id)
            ReadingAction.OnNeedLookClick -> onOpenNeedLook()
        }
    }
}

/** k-reading "Just kept" (H56, H63). */
@Composable
fun ReadingScreen(state: ReadingState, onAction: (ReadingAction) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        KeptTopBar("Just kept", onBack = { onAction(ReadingAction.OnBack) })
        state.line?.let { LineText(it) }
        if (state.line == ReadingLine.NothingNew) {
            NothingNew(onAction)
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.saves, key = { it.id }) { save ->
                when (save.status) {
                    SaveStatus.QUEUED, SaveStatus.READING -> PendingCard(save, onAction)
                    SaveStatus.READ -> ReadCard(save, onAction)
                    SaveStatus.NEEDS_LOOK -> NeedLookCard(save, onAction)
                }
            }
        }
    }
}

@Composable
private fun LineText(line: ReadingLine) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (text, color) = when (line) {
        is ReadingLine.Adding -> "Adding ${line.done} of ${line.total}" to muted
        is ReadingLine.Reading -> "Reading ${line.current} of ${line.total}" + when (line.with) {
            ReadsWith.DEVICE -> " on this phone · one at a time, so it stays cool"
            ReadsWith.CLOUD -> " with Gemini in the cloud"
            ReadsWith.UNKNOWN -> ""
        } to muted
        is ReadingLine.Waiting -> "${line.count} waiting · ${HomeLines.waitReason(line.pause)}" to Kept.colors.wait
        is ReadingLine.Done -> when {
            line.needLook == 0 -> if (line.read == 1) "Read and tagged" else "All ${line.read} read and tagged"
            else -> "${line.read} read · ${line.needLook} ${if (line.needLook == 1) "needs" else "need"} a look"
        } to muted
        ReadingLine.NothingNew -> "Nothing new" to muted
    }
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = color,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun NothingNew(onAction: (ReadingAction) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Those were all already in Kept.",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground,
        )
        TextButton(onClick = { onAction(ReadingAction.OnBack) }) { Text("Back") }
    }
}

@Composable
private fun CardRow(save: Save, onClick: () -> Unit, label: String, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .card(MaterialTheme.colorScheme.surface, 1.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SaveThumb(save.thumbPath ?: save.imagePath, save.type.takeIf { save.status == SaveStatus.READ }, Modifier.size(84.dp), corner = 12.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun PendingCard(save: Save, onAction: (ReadingAction) -> Unit) {
    val reading = save.status == SaveStatus.READING
    CardRow(save, { onAction(ReadingAction.OnSaveClick(save.id)) }, if (reading) "Reading" else "Waiting to be read") {
        Bar(0.8f)
        Bar(0.5f)
        StatusPill(if (reading) "Reading…" else "Waiting", Kept.colors.wait, Kept.colors.waitBg)
    }
}

@Composable
private fun Bar(fraction: Float) {
    Box(
        Modifier.fillMaxWidth(fraction).height(12.dp).clip(PillShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

@Composable
private fun StatusPill(text: String, fg: Color, bg: Color) {
    Row(
        Modifier.clip(PillShape).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

@Composable
private fun ReadCard(save: Save, onAction: (ReadingAction) -> Unit) {
    CardRow(save, { onAction(ReadingAction.OnSaveClick(save.id)) }, save.displayTitle()) {
        Text(
            save.displayTitle(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(
            save.metaLine(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TagPill(save.type.label, icon = typeIcon(save.type))
            RouteBadge(save.readBy)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ReadingRules.editPrompt(save.type) + " ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Edit tags",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(role = Role.Button) { onAction(ReadingAction.OnEditClick(save.id)) }
                    .heightIn(min = 32.dp).padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun NeedLookCard(save: Save, onAction: (ReadingAction) -> Unit) {
    CardRow(save, { onAction(ReadingAction.OnNeedLookClick) }, "Needs a look") {
        Text("Needs a look", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Kept couldn’t read this one. Tap to see why.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ReadingScreenPreview() {
    fun save(id: Long, status: SaveStatus, title: String = "", type: SaveType = SaveType.OTHER, readBy: ReadBy? = null) = Save(
        id = id, createdAt = id, source = SaveSource.PICKER, sourceApp = null, sharedText = null, url = null, imagePath = null,
        thumbPath = null, status = status, failReason = null, readBy = readBy, title = title, summary = "", type = type,
        kind = "", place = "", eventDate = "", link = "", tags = listOf("air-fryer"), items = emptyList(), readAt = null,
    )
    KeptTheme(dark = false) {
        ReadingScreen(
            ReadingState(
                saves = listOf(
                    save(3, SaveStatus.READING),
                    save(2, SaveStatus.READ, "Paneer tikka, air-fryer", SaveType.RECIPE, ReadBy.DEVICE),
                    save(1, SaveStatus.NEEDS_LOOK),
                ),
                line = ReadingLine.Reading(2, 3, ReadsWith.DEVICE),
                loaded = true,
            ),
        ) {}
    }
}
