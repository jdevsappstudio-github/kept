package com.jdevsappstudio.kept.feature.library.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.designsystem.ButtonShape
import com.jdevsappstudio.kept.core.designsystem.CardShape
import com.jdevsappstudio.kept.core.designsystem.KeptChip
import com.jdevsappstudio.kept.core.designsystem.PillShape
import com.jdevsappstudio.kept.core.designsystem.VSpace
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import org.koin.androidx.compose.koinViewModel

/** k-organize: the 5 most used types show, the rest behind '+6 more' (H28). */
private const val TYPES_SHOWN = 5
private const val TITLE_MAX = 120

/**
 * k-organize "Edit save", drawn as a bottom sheet in a dialog destination (the screen below stays visible).
 * [createdCollection]: an id k-newcol handed back; it shows ticked.
 */
@Composable
fun EditSaveRoot(
    onClose: () -> Unit,
    onNewCollection: () -> Unit,
    createdCollection: Long?,
    onCreatedConsumed: () -> Unit,
    viewModel: EditSaveViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            EditSaveEvent.Close -> onClose()
        }
    }
    LaunchedEffect(createdCollection) {
        if (createdCollection != null) {
            viewModel.onAction(EditSaveAction.OnCollectionCreated(createdCollection))
            onCreatedConsumed()
        }
    }
    BackHandler { viewModel.onAction(EditSaveAction.OnBack) }
    EditSaveSheet(state) { action ->
        when (action) {
            EditSaveAction.OnNewCollectionClick -> onNewCollection()
            else -> viewModel.onAction(action)
        }
    }
}

@Composable
fun EditSaveSheet(state: EditSaveState, onAction: (EditSaveAction) -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            // A tap outside closes, changes kept (doc: Back → closes, changes kept).
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close") { onAction(EditSaveAction.OnBack) }
            .statusBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.background)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {} // taps inside stay inside
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp)
                    .clip(PillShape).background(MaterialTheme.colorScheme.outlineVariant),
            )
            Text(
                if (state.many) "Edit ${state.count} saves" else "Edit save",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() },
            )
            if (state.loading) return@Column
            if (!state.many) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { onAction(EditSaveAction.OnTitleChange(it.take(TITLE_MAX))) },
                    singleLine = true,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
            TypeSection(state, onAction)
            TagSection(state, onAction)
            CollectionSection(state, onAction)
            VSpace(16.dp)
            Button(
                onClick = { onAction(EditSaveAction.OnDone) },
                shape = ButtonShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("Done", style = MaterialTheme.typography.titleSmall) }
        }
    }
}

@Composable
private fun TypeSection(state: EditSaveState, onAction: (EditSaveAction) -> Unit) {
    var all by rememberSaveable { mutableStateOf(false) }
    // The picked type always shows, even when it isn't among the 5 most used.
    val shown = if (all) state.typeOrder else state.typeOrder.take(TYPES_SHOWN).let { top ->
        if (state.type != null && state.type !in top) top + state.type else top
    }
    Label(if (state.many && state.type == null) "Type · mixed, pick one for all" else "Type · sets the action button")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { t -> KeptChip(t.label, selected = t == state.type, onClick = { onAction(EditSaveAction.OnTypeClick(t)) }) }
        val more = SaveType.entries.size - shown.size
        if (!all && more > 0) KeptChip("+$more more", selected = false, onClick = { all = true })
    }
}

@Composable
private fun TagSection(state: EditSaveState, onAction: (EditSaveAction) -> Unit) {
    var typing by rememberSaveable { mutableStateOf(false) }
    Label("Tags")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.tags.forEach { tag ->
            Row(
                Modifier.clip(PillShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PillShape)
                    .clickable(role = Role.Button, onClickLabel = "Remove tag $tag") { onAction(EditSaveAction.OnRemoveTag(tag)) }
                    .heightIn(min = 36.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(14.dp))
            }
        }
        if (!typing) {
            Row(
                Modifier.clip(PillShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PillShape)
                    .clickable(role = Role.Button) { typing = true }
                    .heightIn(min = 36.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Text("Add tag", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
    if (typing) {
        OutlinedTextField(
            value = state.tagQuery,
            onValueChange = { onAction(EditSaveAction.OnTagQueryChange(it.take(40))) },
            singleLine = true,
            placeholder = { Text("New tag") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (state.tagQuery.isBlank()) typing = false else onAction(EditSaveAction.OnAddTag(state.tagQuery))
            }),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        if (state.suggestions.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.suggestions.forEach { s -> KeptChip(s, selected = false, onClick = { onAction(EditSaveAction.OnAddTag(s)) }) }
            }
        }
    }
}

@Composable
private fun CollectionSection(state: EditSaveState, onAction: (EditSaveAction) -> Unit) {
    Label("Collections")
    Column(Modifier.fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surface)) {
        state.collections.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Checkbox, onClickLabel = if (c.ticked) "Remove from ${c.name}" else "Add to ${c.name}") {
                        onAction(EditSaveAction.OnCollectionToggle(c.id))
                    }
                    .heightIn(min = 64.dp)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconSquare(Icons.Outlined.Folder)
                Text(c.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 12.dp).weight(1f))
                Checkbox(checked = c.ticked, onCheckedChange = null, modifier = Modifier.padding(end = 10.dp))
            }
        }
        if (state.collections.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button) { onAction(EditSaveAction.OnNewCollectionClick) }
                .heightIn(min = 64.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconSquare(Icons.Outlined.Add)
            Column(Modifier.padding(start = 12.dp)) {
                Text("New collection", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("Free, as many as you like", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IconSquare(icon: ImageVector) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}
