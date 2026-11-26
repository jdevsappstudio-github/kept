package com.jdevsappstudio.kept.feature.sent

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.designsystem.Kept
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.designsystem.KeptTopBar
import com.jdevsappstudio.kept.core.model.SentKind
import com.jdevsappstudio.kept.core.model.SentRequest
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.ZoneId

/** k-sent 'What was sent' (H2, H58, H67): the proof behind every Cloud chip. */
@Composable
fun SentRoot(onBack: () -> Unit, viewModel: SentViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SentScreen(state, onBack = onBack, onAction = viewModel::onAction)
}

/** Header items above the rows: the top line. */
private const val HEAD_ITEMS = 1

@Composable
fun SentScreen(
    state: SentState,
    onBack: () -> Unit,
    onAction: (SentAction) -> Unit,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val list = rememberLazyListState()
    // From a Cloud chip: scroll to that request once (not again on coming back to this screen).
    var scrolled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.loading, state.openId) {
        if (scrolled || state.loading) return@LaunchedEffect
        val i = state.rows.indexOfFirst { it.id == state.openId }
        if (i >= 0) list.scrollToItem(HEAD_ITEMS + i)
        scrolled = true
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            KeptTopBar(SentCopy.TITLE, onBack = onBack)
        }
        if (state.loading) return@Column
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
            item(key = "top") {
                Text(
                    if (state.empty) SentCopy.EMPTY else SentCopy.topLine(state.rows.size, state.anyImages),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            itemsIndexed(state.rows, key = { _, r -> r.id }) { i, row ->
                SentRow(
                    row = row,
                    first = i == 0,
                    last = i == state.rows.lastIndex,
                    expanded = row.id in state.expanded,
                    today = today,
                    zone = zone,
                    onClick = { onAction(SentAction.OnToggle(row.id)) },
                )
            }
            item(key = "foot") {
                Text(
                    SentCopy.FOOT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp).navigationBarsPadding(),
                )
            }
        }
    }
}

/** One request; rows share one card (rounded at the ends, dividers between), as on the mockup. */
@Composable
private fun SentRow(
    row: SentRequest,
    first: Boolean,
    last: Boolean,
    expanded: Boolean,
    today: LocalDate,
    zone: ZoneId,
    onClick: () -> Unit,
) {
    val r = 18.dp
    val shape = RoundedCornerShape(
        topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
        bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
    )
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .animateContentSize(),
    ) {
        if (!first) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide the text" else "Show exactly what went", onClick = onClick)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KindIcon(row)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    SentCopy.title(row, today, zone),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    SentCopy.summary(row),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(turn),
            )
        }
        if (expanded) ExactText(row)
    }
}

@Composable
private fun KindIcon(row: SentRequest) {
    val icon: ImageVector = when (row.kind) {
        SentKind.ASK -> Icons.Outlined.ChatBubbleOutline
        SentKind.FILL_IN -> Icons.Outlined.Cloud
        SentKind.READ -> if (row.images > 0) Icons.Outlined.Image else Icons.Outlined.Description
    }
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Kept.colors.cloudBg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Kept.colors.cloud, modifier = Modifier.size(20.dp))
    }
}

/** The whole logged text, selectable (long-press to copy), plus a note when an image went too. */
@Composable
private fun ExactText(row: SentRequest) {
    Column(
        Modifier
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(SentCopy.EXACT, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        SelectionContainer {
            Text(
                row.text.ifBlank { SentCopy.NO_TEXT },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SentCopy.imageNote(row.images)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Kept.colors.text3)
        }
    }
}

// --- previews ---------------------------------------------------------------------------------------------------

private val previewRows = listOf(
    SentRequest(3, System.currentTimeMillis(), SentKind.ASK,
        "You answer questions…\n\nSaves:\n[1] Sunbeam Café (Place)\nPlace: Assagao\n[2] Tide Table (Place)\n\nQuestion: Which Goa cafés did I save?", 0, null),
    SentRequest(2, System.currentTimeMillis() - 86_400_000, SentKind.FILL_IN,
        "Someone saved this link…\n\nLink: https://www.instagram.com/reel/abc123", 0, 7),
    SentRequest(1, System.currentTimeMillis() - 4 * 86_400_000, SentKind.READ, "", 1, 9),
)

@Preview(showBackground = true)
@Composable
private fun SentPreview() = KeptTheme {
    SentScreen(SentState(loading = false, rows = previewRows, expanded = setOf(3)), onBack = {}, onAction = {})
}

@Preview(showBackground = true)
@Composable
private fun SentEmptyPreview() = KeptTheme {
    SentScreen(SentState(loading = false), onBack = {}, onAction = {})
}
