package com.jdevsappstudio.kept.feature.library.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.jdevsappstudio.kept.core.designsystem.KeptChip
import com.jdevsappstudio.kept.core.designsystem.KeptIconButton
import com.jdevsappstudio.kept.core.designsystem.KeptTopBar
import com.jdevsappstudio.kept.core.designsystem.PillShape
import com.jdevsappstudio.kept.core.designsystem.RouteBadge
import com.jdevsappstudio.kept.core.designsystem.SaveThumb
import com.jdevsappstudio.kept.core.designsystem.card
import com.jdevsappstudio.kept.core.model.ListLayout
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.SourceFilter
import com.jdevsappstudio.kept.core.model.TagCount
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.feature.library.chipLabel
import com.jdevsappstudio.kept.feature.library.displayTitle
import com.jdevsappstudio.kept.feature.library.metaLine
import org.koin.androidx.compose.koinViewModel

@Composable
fun SaveListRoot(
    onBack: () -> Unit,
    onSearch: (SaveType?) -> Unit,
    onOpenSave: (Long) -> Unit,
    onOpenEdit: (List<Long>) -> Unit,
    viewModel: SaveListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saves = viewModel.saves.collectAsLazyPagingItems()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SaveListEvent.OpenSave -> onOpenSave(event.id)
            is SaveListEvent.OpenEdit -> onOpenEdit(event.ids)
        }
    }
    SaveListScreen(state, saves) { action ->
        when (action) {
            SaveListAction.OnBackClick -> onBack()
            SaveListAction.OnSearchClick -> onSearch(state.type)
            else -> viewModel.onAction(action)
        }
    }
}

/** First tags shown before '+N more' (doc: air-fryer · breakfast · Goa · +3 more). */
private const val TAGS_FOLDED = 3

@Composable
fun SaveListScreen(state: SaveListState, saves: LazyPagingItems<Save>, onAction: (SaveListAction) -> Unit) {
    BackHandler(enabled = state.selecting) { onAction(SaveListAction.OnClearSelection) }
    val title = state.type?.label ?: "All saves"
    val columns = if (state.layout == ListLayout.GRID) 2 else 1
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            if (state.selecting) SelectBar(state.selected.size, onAction) else ListBar(title, state.layout, onAction)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "sources", span = full) { SourceChips(state, onAction) }
            if (state.tags.isNotEmpty()) item(key = "tags", span = full) { TagRow(state.tags, state.tag, onAction) }
            items(count = saves.itemCount, key = saves.itemKey { it.id }) { i ->
                val save = saves[i] ?: return@items
                val picked = save.id in state.selected
                if (columns == 2) GridCard(save, picked, state.selecting, onAction) else RowCard(save, picked, state.selecting, onAction)
            }
            if (saves.itemCount == 0 && saves.loadState.refresh is LoadState.NotLoading) {
                item(key = "empty", span = full) {
                    val narrowed = state.source != null || state.tag != null
                    Text(
                        if (narrowed) "Nothing matches. Tap the lit chip again to see all." else "Nothing here yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListBar(title: String, layout: ListLayout, onAction: (SaveListAction) -> Unit) {
    KeptTopBar(title, onBack = { onAction(SaveListAction.OnBackClick) }) {
        val grid = layout == ListLayout.GRID
        KeptIconButton(
            if (grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
            if (grid) "Show as a list" else "Show as a grid",
            onClick = { onAction(SaveListAction.OnLayoutToggle) },
        )
        KeptIconButton(Icons.Outlined.Search, "Search in $title", onClick = { onAction(SaveListAction.OnSearchClick) })
    }
}

@Composable
private fun SelectBar(count: Int, onAction: (SaveListAction) -> Unit) {
    KeptTopBar(
        "$count selected",
        onBack = { onAction(SaveListAction.OnClearSelection) },
        backLabel = "Clear selection",
    ) {
        KeptIconButton(Icons.Outlined.Edit, "Edit selected", onClick = { onAction(SaveListAction.OnEditSelected) })
        KeptIconButton(Icons.Outlined.Delete, "Delete selected", onClick = { onAction(SaveListAction.OnDeleteSelected) })
    }
}

@Composable
private fun SourceChips(state: SaveListState, onAction: (SaveListAction) -> Unit) {
    val counts = state.sourceCounts
    // UI2: a source chip shows only when it has saves; 'All' always.
    val sources = listOf(SourceFilter.LINKS to counts.links, SourceFilter.SCREENSHOTS to counts.screenshots).filter { it.second > 0 }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") {
            KeptChip("All ${counts.links + counts.screenshots}", selected = state.source == null, onClick = {
                state.source?.let { onAction(SaveListAction.OnSourceClick(it)) }
            })
        }
        items(sources, key = { it.first.name }) { (source, n) ->
            KeptChip("${source.chipLabel()} $n", selected = state.source == source, onClick = { onAction(SaveListAction.OnSourceClick(source)) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagRow(tags: List<TagCount>, on: String?, onAction: (SaveListAction) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    // The lit tag always shows, even when it sits past the fold.
    val shown = if (open || tags.size <= TAGS_FOLDED + 1) tags else tags.take(TAGS_FOLDED).let { first ->
        if (on != null && first.none { it.tag == on }) first + tags.filter { it.tag == on } else first
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { t -> SmallPill(t.tag, lit = t.tag == on) { onAction(SaveListAction.OnTagClick(t.tag)) } }
        if (shown.size < tags.size) SmallPill("+${tags.size - shown.size} more", lit = false) { open = true }
    }
}

/** A small tag pill you can tap; the touch area grows to 48 dp, the pill stays small. */
@Composable
private fun SmallPill(text: String, lit: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = if (lit) scheme.primary.copy(alpha = 0.10f) else scheme.background,
        border = BorderStroke(1.dp, if (lit) scheme.primary else scheme.outlineVariant),
        modifier = Modifier.minimumInteractiveComponentSize().semantics { selected = lit },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (lit) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.saveClicks(save: Save, picked: Boolean, selecting: Boolean, onAction: (SaveListAction) -> Unit): Modifier =
    combinedClickable(
        role = Role.Button,
        onClickLabel = if (selecting) (if (picked) "Unselect" else "Select") else "Open",
        onLongClickLabel = "Select",
        onClick = { onAction(SaveListAction.OnSaveClick(save.id)) },
        onLongClick = { onAction(SaveListAction.OnSaveLongPress(save.id)) },
    ).semantics {
        selected = picked
        if (!selecting) customActions = listOf(CustomAccessibilityAction("Select") { onAction(SaveListAction.OnSaveLongPress(save.id)); true })
    }

@Composable
private fun Modifier.pickedRing(picked: Boolean): Modifier =
    if (picked) border(2.dp, MaterialTheme.colorScheme.primary, com.jdevsappstudio.kept.core.designsystem.CardShape) else this

@Composable
private fun RowCard(save: Save, picked: Boolean, selecting: Boolean, onAction: (SaveListAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .card(MaterialTheme.colorScheme.surface, 1.dp)
            .pickedRing(picked)
            .saveClicks(save, picked, selecting, onAction)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box {
            SaveThumb(save.thumbPath ?: save.imagePath, save.type, Modifier.size(84.dp), corner = 12.dp)
            if (picked) PickedMark(Modifier.align(Alignment.TopStart).padding(4.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(save.displayTitle(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(save.metaLine(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            RouteBadge(save.readBy, Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun GridCard(save: Save, picked: Boolean, selecting: Boolean, onAction: (SaveListAction) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .card(MaterialTheme.colorScheme.surface, 1.dp)
            .pickedRing(picked)
            .saveClicks(save, picked, selecting, onAction),
    ) {
        Box {
            SaveThumb(save.thumbPath ?: save.imagePath, save.type, Modifier.fillMaxWidth().aspectRatio(1f), corner = 0.dp)
            if (picked) PickedMark(Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(save.displayTitle(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            RouteBadge(save.readBy)
        }
    }
}

@Composable
private fun PickedMark(modifier: Modifier) {
    Icon(
        Icons.Outlined.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(2.dp),
    )
}
