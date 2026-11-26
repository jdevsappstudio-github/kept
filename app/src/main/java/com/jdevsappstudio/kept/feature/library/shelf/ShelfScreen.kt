package com.jdevsappstudio.kept.feature.library.shelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.designsystem.KeptChip
import com.jdevsappstudio.kept.core.designsystem.KeptIconButton
import com.jdevsappstudio.kept.core.designsystem.KeptTopBar
import com.jdevsappstudio.kept.core.model.ShelfEntry
import com.jdevsappstudio.kept.core.model.action
import com.jdevsappstudio.kept.core.presentation.ItemActionLauncher
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.feature.library.metaLine
import org.koin.androidx.compose.koinViewModel

/** Rename field cap (same as collection names, H55). */
private const val NAME_MAX = 40

@Composable
fun ShelfRoot(
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenSave: (Long) -> Unit,
    viewModel: ShelfViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ShelfEvent.Close -> onBack()
        }
    }
    ShelfScreen(
        state,
        onRunAction = { entry -> entry.action()?.let { ItemActionLauncher.launch(context, it.target) } },
        onSearch = { onSearch(state.shelf?.title ?: state.kind) },
    ) { action ->
        when (action) {
            ShelfAction.OnBackClick -> onBack()
            is ShelfAction.OnEntryClick -> onOpenSave(action.entry.saveId)
            ShelfAction.OnHideClick -> {
                viewModel.onAction(action)
                onBack() // flows: Hide shelf from home → k-home (snackbar there, with Undo)
            }
            else -> viewModel.onAction(action)
        }
    }
}

@Composable
fun ShelfScreen(
    state: ShelfState,
    onRunAction: (ShelfEntry) -> Unit,
    onSearch: () -> Unit,
    onAction: (ShelfAction) -> Unit,
) {
    val shelf = state.shelf
    val title = shelf?.title ?: ""
    var renaming by rememberSaveable { mutableStateOf(false) }
    var merging by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item(key = "bar") {
            KeptTopBar(title, onBack = { onAction(ShelfAction.OnBackClick) }) {
                KeptIconButton(Icons.Outlined.Search, "Search", onClick = onSearch)
                ShelfMenu(
                    hidden = shelf?.hidden == true,
                    canMerge = state.mergeTargets.isNotEmpty(),
                    onHide = { onAction(ShelfAction.OnHideClick) },
                    onShow = { onAction(ShelfAction.OnShowClick) },
                    onRename = { renaming = true },
                    onMerge = { merging = true },
                )
            }
        }
        if (shelf == null) return@LazyColumn
        item(key = "sub") {
            val count = if (shelf.countsItems) "${shelf.entries} items from ${shelf.saves} saves" else "${shelf.saves} saves"
            Text(
                "$count · a shelf: Kept fills it from the ${state.kind} tag",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        }
        if (state.tags.isNotEmpty()) item(key = "chips") {
            LazyRow(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "all") {
                    KeptChip("All ${shelf.entries}", selected = state.tag == null, onClick = {
                        state.tag?.let { onAction(ShelfAction.OnTagClick(it)) }
                    })
                }
                items(state.tags, key = { it.tag }) { t ->
                    KeptChip("${t.tag.replaceFirstChar { it.titlecase() }} ${t.count}", selected = state.tag == t.tag, onClick = {
                        onAction(ShelfAction.OnTagClick(t.tag))
                    })
                }
            }
        }
        // One lazy item per row (a shelf can hold hundreds); the first and last round off the card.
        itemsIndexed(state.entries, key = { _, e -> if (e.isSave) "s:${e.saveId}" else "i:${e.key}" }) { i, entry ->
            val last = state.entries.lastIndex
            val shape = RoundedCornerShape(
                topStart = if (i == 0) 18.dp else 0.dp, topEnd = if (i == 0) 18.dp else 0.dp,
                bottomStart = if (i == last) 18.dp else 0.dp, bottomEnd = if (i == last) 18.dp else 0.dp,
            )
            Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)) {
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                EntryRow(entry, onRunAction, onAction)
            }
        }
        item(key = "hint") {
            Text(
                "Wrong one? Long-press it → Not on this shelf. It leaves the shelf, nothing else changes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp),
            )
        }
    }

    if (renaming && shelf != null) {
        RenameDialog(shelf.title, onDismiss = { renaming = false }) { name ->
            renaming = false
            onAction(ShelfAction.OnRename(name))
        }
    }
    if (merging) {
        AlertDialog(
            onDismissRequest = { merging = false },
            title = { Text("Merge $title into…") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(state.mergeTargets, key = { _, s -> s.kind }) { _, target ->
                        Text(
                            target.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                                .clickable(role = Role.Button) { merging = false; onAction(ShelfAction.OnMergeInto(target.kind)) }
                                .heightIn(min = 48.dp)
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { merging = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ShelfMenu(
    hidden: Boolean,
    canMerge: Boolean,
    onHide: () -> Unit,
    onShow: () -> Unit,
    onRename: () -> Unit,
    onMerge: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        KeptIconButton(Icons.Outlined.MoreVert, "More options", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (hidden) DropdownMenuItem(text = { Text("Show on home") }, onClick = { open = false; onShow() })
            else DropdownMenuItem(text = { Text("Hide shelf from home") }, onClick = { open = false; onHide() })
            DropdownMenuItem(text = { Text("Rename") }, onClick = { open = false; onRename() })
            if (canMerge) DropdownMenuItem(text = { Text("Merge into…") }, onClick = { open = false; onMerge() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(entry: ShelfEntry, onRunAction: (ShelfEntry) -> Unit, onAction: (ShelfAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val action = entry.action()
    val remove = { onAction(ShelfAction.OnRemoveEntry(entry)) }
    Box {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = "Open",
                    onLongClickLabel = "More options",
                    onClick = { onAction(ShelfAction.OnEntryClick(entry)) },
                    onLongClick = { menu = true },
                )
                .semantics {
                    customActions = listOfNotNull(
                        action?.let { a -> CustomAccessibilityAction("${a.label}: ${entry.name}") { onRunAction(entry); true } },
                        CustomAccessibilityAction("Not on this shelf") { remove(); true },
                    )
                }
                .heightIn(min = 64.dp)
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = entry.metaLine()
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (action != null) {
                // The row's own custom action covers TalkBack; this button is for touch.
                TextButton(onClick = { onRunAction(entry) }) {
                    Text(action.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Not on this shelf") }, onClick = { menu = false; remove() })
        }
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename shelf") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(NAME_MAX) },
                singleLine = true,
                supportingText = { Text("Leave blank to go back to the name Kept gave it.") },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
