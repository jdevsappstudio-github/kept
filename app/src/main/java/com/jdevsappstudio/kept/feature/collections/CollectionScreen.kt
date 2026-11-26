package com.jdevsappstudio.kept.feature.collections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.jdevsappstudio.kept.core.designsystem.ButtonShape
import com.jdevsappstudio.kept.core.designsystem.KeptChip
import com.jdevsappstudio.kept.core.designsystem.KeptIconButton
import com.jdevsappstudio.kept.core.designsystem.KeptTopBar
import com.jdevsappstudio.kept.core.designsystem.RouteBadge
import com.jdevsappstudio.kept.core.designsystem.SaveThumb
import com.jdevsappstudio.kept.core.designsystem.TagPill
import com.jdevsappstudio.kept.core.designsystem.card
import com.jdevsappstudio.kept.core.designsystem.typeIcon
import com.jdevsappstudio.kept.core.model.ListLayout
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.feature.library.displayTitle
import com.jdevsappstudio.kept.feature.library.metaLine
import org.koin.androidx.compose.koinViewModel

/** k-collection and k-collection-empty (H55, H64). */
@Composable
fun CollectionRoot(
    onBack: () -> Unit,
    onOpenSave: (Long) -> Unit,
    onAddSaves: (collectionId: Long) -> Unit,
    onRename: (collectionId: Long) -> Unit,
    collectionId: Long,
    viewModel: CollectionViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saves = viewModel.saves.collectAsLazyPagingItems()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            CollectionEvent.Close -> onBack()
        }
    }
    CollectionScreen(state, saves) { action ->
        when (action) {
            CollectionAction.OnBack -> onBack()
            is CollectionAction.OnSaveClick -> onOpenSave(action.id)
            CollectionAction.OnAddSavesClick -> onAddSaves(collectionId)
            CollectionAction.OnRenameClick -> onRename(collectionId)
            else -> viewModel.onAction(action)
        }
    }
}

@Composable
fun CollectionScreen(state: CollectionState, saves: LazyPagingItems<Save>, onAction: (CollectionAction) -> Unit) {
    val columns = if (state.layout == ListLayout.GRID) 2 else 1
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            KeptTopBar(state.name, onBack = { onAction(CollectionAction.OnBack) }) {
                if (!state.isEmpty) {
                    val grid = state.layout == ListLayout.GRID
                    KeptIconButton(
                        if (grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                        if (grid) "Show as a list" else "Show as a grid",
                        onClick = { onAction(CollectionAction.OnLayoutToggle) },
                    )
                }
                CollectionMenu(onAction)
            }
        }
        if (state.loading) return@Column
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "line", span = full) {
                Text(
                    CollectionCopy.line(state.count, state.typeChips),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isEmpty) {
                item(key = "empty", span = full) { EmptyBlock(onAction) }
                return@LazyVerticalGrid
            }
            if (state.typeChips.size > 1 || state.type != null) item(key = "types", span = full) { TypeChips(state, onAction) }
            items(count = saves.itemCount, key = saves.itemKey { it.id }) { i ->
                val save = saves[i] ?: return@items
                SaveCard(save, grid = columns == 2, collectionName = state.name, onAction = onAction)
            }
            if (saves.itemCount == 0 && saves.loadState.refresh is LoadState.NotLoading) {
                item(key = "none", span = full) {
                    Text(
                        CollectionCopy.EMPTY_TYPE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            item(key = "add", span = full) {
                OutlinedButton(
                    onClick = { onAction(CollectionAction.OnAddSavesClick) },
                    shape = ButtonShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Add saves", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    if (state.confirmDelete) DeleteDialog(state, onAction)
}

@Composable
private fun CollectionMenu(onAction: (CollectionAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        KeptIconButton(Icons.Outlined.MoreVert, "More options", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { open = false; onAction(CollectionAction.OnRenameClick) })
            DropdownMenuItem(text = { Text("Delete collection") }, onClick = { open = false; onAction(CollectionAction.OnDeleteClick) })
        }
    }
}

@Composable
private fun TypeChips(state: CollectionState, onAction: (CollectionAction) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") {
            KeptChip("All ${state.count}", selected = state.type == null, onClick = {
                if (state.type != null) onAction(CollectionAction.OnTypeClick(null))
            })
        }
        items(state.typeChips, key = { it.type.wire }) { chip ->
            KeptChip("${chip.type.label} ${chip.count}", selected = state.type == chip.type, onClick = {
                onAction(CollectionAction.OnTypeClick(chip.type))
            })
        }
    }
}

@Composable
private fun EmptyBlock(onAction: (CollectionAction) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Text(
            CollectionCopy.EMPTY_TITLE,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            CollectionCopy.EMPTY_BODY,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = { onAction(CollectionAction.OnAddSavesClick) },
            shape = ButtonShape,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 52.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Add saves", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Tap opens the save; long-press (or the TalkBack action) offers Remove from <collection>, snackbar Undo. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SaveCard(save: Save, grid: Boolean, collectionName: String, onAction: (CollectionAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val removeLabel = "Remove from $collectionName"
    val clicks = Modifier
        .combinedClickable(
            role = Role.Button,
            onClickLabel = "Open",
            onLongClickLabel = "More options",
            onClick = { onAction(CollectionAction.OnSaveClick(save.id)) },
            onLongClick = { menu = true },
        )
        .semantics { customActions = listOf(CustomAccessibilityAction(removeLabel) { onAction(CollectionAction.OnRemove(save.id)); true }) }
    Box {
        if (grid) {
            Column(Modifier.fillMaxWidth().card(MaterialTheme.colorScheme.surface, 1.dp).then(clicks)) {
                SaveThumb(save.thumbPath ?: save.imagePath, save.type, Modifier.fillMaxWidth().aspectRatio(1f), corner = 0.dp)
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Title(save)
                    RouteBadge(save.readBy)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().card(MaterialTheme.colorScheme.surface, 1.dp).then(clicks).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SaveThumb(save.thumbPath ?: save.imagePath, save.type, Modifier.size(84.dp), corner = 12.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Title(save)
                    val meta = save.metaLine()
                    if (meta.isNotEmpty()) {
                        Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // A collection mixes types, so each row says its own (the doc draws Place / Event / Recipe pills).
                    FlowRow(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TagPill(save.type.label, icon = typeIcon(save.type))
                        RouteBadge(save.readBy)
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(removeLabel) }, onClick = { menu = false; onAction(CollectionAction.OnRemove(save.id)) })
        }
    }
}

@Composable
private fun Title(save: Save) {
    Text(save.displayTitle(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun DeleteDialog(state: CollectionState, onAction: (CollectionAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(CollectionAction.OnDismissDelete) },
        title = { Text("Delete ${state.name}?") },
        text = { Text(if (state.count == 1) "The save stays in Kept." else "The ${state.count} saves stay in Kept.") },
        confirmButton = { TextButton(onClick = { onAction(CollectionAction.OnConfirmDelete) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { onAction(CollectionAction.OnDismissDelete) }) { Text("Cancel") } },
    )
}
