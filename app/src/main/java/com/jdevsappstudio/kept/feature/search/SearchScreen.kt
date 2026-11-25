package com.jdevsappstudio.kept.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.jdevsappstudio.kept.core.designsystem.CardShape
import com.jdevsappstudio.kept.core.designsystem.KeptChip
import com.jdevsappstudio.kept.core.designsystem.KeptMark
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.designsystem.KeptTopBar
import com.jdevsappstudio.kept.core.designsystem.SaveThumb
import com.jdevsappstudio.kept.core.designsystem.SectionLabel
import com.jdevsappstudio.kept.core.designsystem.TagPill
import com.jdevsappstudio.kept.core.designsystem.card
import com.jdevsappstudio.kept.core.designsystem.typeIcon
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.SearchHit
import com.jdevsappstudio.kept.core.model.ShelfEntry
import com.jdevsappstudio.kept.core.model.TagCount
import com.jdevsappstudio.kept.core.model.TypeChip
import com.jdevsappstudio.kept.core.model.action
import com.jdevsappstudio.kept.core.presentation.ItemActionLauncher
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.feature.library.displayTitle
import com.jdevsappstudio.kept.feature.library.metaLine
import kotlinx.coroutines.flow.flowOf
import org.koin.androidx.compose.koinViewModel

@Composable
fun SearchRoot(
    onBack: () -> Unit,
    onOpenSave: (Long) -> Unit,
    onOpenShelf: (String) -> Unit,
    onOpenNeedsLook: () -> Unit,
    onOpenAsk: (String) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hits = viewModel.hits.collectAsLazyPagingItems()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SearchEvent.OpenSave -> onOpenSave(event.id)
            is SearchEvent.OpenShelf -> onOpenShelf(event.kind)
            SearchEvent.OpenNeedsLook -> onOpenNeedsLook()
            is SearchEvent.OpenAsk -> onOpenAsk(event.question)
            SearchEvent.PickDone -> onBack()
        }
    }
    SearchScreen(
        state,
        hits,
        onRunAction = { entry -> entry.action()?.let { ItemActionLauncher.launch(context, it.target) } },
        onBack = onBack,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    state: SearchState,
    hits: LazyPagingItems<SearchHit>,
    onRunAction: (ShelfEntry) -> Unit,
    onBack: () -> Unit,
    onAction: (SearchAction) -> Unit,
) {
    val pick = state.pick
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            if (pick != null) PickBar(pick, onBack, onAction)
            SearchField(state, onAction)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.showsList) {
                startItems(state, onAction)
                return@LazyColumn
            }
            if (state.typeChips.isNotEmpty() && !state.nothingFound) item(key = "types") { TypeChips(state, onAction) }
            if (state.nothingFound) {
                item(key = "none") { NothingFound(state, onAction) }
                return@LazyColumn
            }
            state.shelf?.let { match ->
                item(key = "shelf") { ShelfMatchCard(match, onAction) }
                item(key = "shelf-label") {
                    SectionLabel(match.shelf.title, action = "See all ${match.shelf.entries}", onAction = { onAction(SearchAction.OnShelfClick) })
                }
                item(key = "shelf-rows") { ShelfRows(match.entries, onRunAction, onAction) }
            }
            if (state.count > 0) item(key = "count") {
                Column {
                    SectionLabel(if (state.shelf != null) "Saves with these words" else plural(state.count, "save"))
                    if (state.showStatusLine) StatusLine(state, onAction)
                }
            }
            items(count = hits.itemCount, key = hits.itemKey { it.save.id }) { i ->
                val hit = hits[i] ?: return@items
                HitRow(hit, pick, onAction)
            }
            if (pick == null && state.settled) item(key = "foot") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Search runs on this phone, also offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    if (state.canAsk) {
                        OutlinedButton(
                            onClick = { onAction(SearchAction.OnAskClick) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text("Ask about these", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Empty query, not picking: recent searches, then the person's most used tags (k-search-none's 'empty' state). */
@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.startItems(state: SearchState, onAction: (SearchAction) -> Unit) {
    if (state.recent.isNotEmpty()) {
        item(key = "recent-label") { SectionLabel("Recent") }
        items(state.recent, key = { "r:$it" }) { text ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "Search") { onAction(SearchAction.OnRecentClick(text)) }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (state.tags.isNotEmpty()) item(key = "tags") { TagChips(state.tags, onAction) }
    if (state.recent.isEmpty() && state.tags.isEmpty()) item(key = "hint") {
        Text(
            "Search looks at every word Kept read, plus tags and items. It runs on this phone, also offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PickBar(pick: PickState, onBack: () -> Unit, onAction: (SearchAction) -> Unit) {
    KeptTopBar("Add to ${pick.collectionName}", onBack = onBack) {
        Button(onClick = { onAction(SearchAction.OnPickAdd) }, enabled = pick.canAdd) {
            Text(if (pick.canAdd) "Add ${pick.picked.size}" else "Add")
        }
    }
}

@Composable
private fun SearchField(state: SearchState, onAction: (SearchAction) -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // H62: only on first open, read once (a query typed later must not re-trigger it).
    val autoFocus = remember { autoFocusSearch(state) }
    LaunchedEffect(Unit) { if (autoFocus) focus.requestFocus() }
    OutlinedTextField(
        value = state.query,
        onValueChange = { onAction(SearchAction.OnQueryChange(it)) },
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        placeholder = { Text(if (state.pick != null) "Search to narrow down" else "Search your saves") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = if (state.query.isEmpty()) null else {
            { IconButton(onClick = { onAction(SearchAction.OnClearQuery) }) { Icon(Icons.Outlined.Close, "Clear search") } }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onAction(SearchAction.OnSubmit)
            keyboard?.hide()
        }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun TypeChips(state: SearchState, onAction: (SearchAction) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") { KeptChip("All", selected = state.type == null, onClick = { onAction(SearchAction.OnTypeClick(null)) }) }
        items(state.typeChips, key = { it.type.wire }) { chip ->
            KeptChip("${chip.type.label} ${chip.count}", selected = state.type == chip.type, onClick = { onAction(SearchAction.OnTypeClick(chip.type)) })
        }
    }
}

@Composable
private fun StatusLine(state: SearchState, onAction: (SearchAction) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            statusLine(state.stillReading, state.notRead),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = { onAction(SearchAction.OnStatusSeeClick) }) { Text("See", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun HitRow(hit: SearchHit, pick: PickState?, onAction: (SearchAction) -> Unit) {
    val save = hit.save
    val ticked = pick?.isTicked(save.id) == true
    val locked = pick?.isLocked(save.id) == true
    Row(
        Modifier.fillMaxWidth()
            .card(MaterialTheme.colorScheme.surface, 1.dp)
            .then(if (ticked) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CardShape) else Modifier)
            .clickable(
                enabled = !locked,
                role = if (pick != null) Role.Checkbox else Role.Button,
                onClickLabel = if (pick == null) "Open" else if (ticked) "Untick" else "Tick",
            ) { onAction(SearchAction.OnHitClick(save.id)) }
            .semantics { if (pick != null) stateDescription = if (locked) "Already in ${pick.collectionName}" else if (ticked) "Ticked" else "Not ticked" }
            .then(if (locked) Modifier.alpha(0.6f) else Modifier)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box {
            SaveThumb(save.thumbPath ?: save.imagePath, save.type, Modifier.size(84.dp), corner = 12.dp)
            if (ticked) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(24.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary).padding(2.dp),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(save.displayTitle(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val line = if (hit.snippet.isNotBlank()) snippetText(hit.snippet) else androidx.compose.ui.text.AnnotatedString(save.metaLine())
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (locked) {
                Text("Already in ${pick?.collectionName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            } else save.type?.let { TagPill(it.label, icon = typeIcon(it), modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

@Composable
private fun ShelfMatchCard(match: ShelfMatch, onAction: (SearchAction) -> Unit) {
    val shelf = match.shelf
    Column(
        Modifier.fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .clickable(role = Role.Button, onClickLabel = "Open shelf") { onAction(SearchAction.OnShelfClick) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(shelf.matchHeadline(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Matched the ${shelf.kind} tag, not just the words",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The shelf's first rows (k-search-tag), with the same §6 action as on k-shelf. No long-press menu here. */
@Composable
private fun ShelfRows(entries: List<ShelfEntry>, onRunAction: (ShelfEntry) -> Unit, onAction: (SearchAction) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)) {
        entries.forEachIndexed { i, entry ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val action = entry.action()
            Row(
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Open") { onAction(SearchAction.OnEntryClick(entry)) }
                    .semantics {
                        action?.let { a -> customActions = listOf(CustomAccessibilityAction("${a.label}: ${entry.name}") { onRunAction(entry); true }) }
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
                    TextButton(onClick = { onRunAction(entry) }) {
                        Text(action.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun NothingFound(state: SearchState, onAction: (SearchAction) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KeptMark(size = 72.dp)
        Text(
            "Nothing found for “${state.query.trim()}”",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            nothingFoundBody(state.stillReading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (state.pick == null && state.stillReading + state.notRead > 0) {
            OutlinedButton(onClick = { onAction(SearchAction.OnStatusSeeClick) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("See what's reading", style = MaterialTheme.typography.titleSmall)
            }
        }
        if (state.tags.isNotEmpty()) TagChips(state.tags, onAction)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChips(tags: List<TagCount>, onAction: (SearchAction) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Try a tag you have")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { t -> KeptChip(t.tag, selected = false, onClick = { onAction(SearchAction.OnTagClick(t.tag)) }) }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SearchScreenPreview() {
    fun save(id: Long, title: String, type: SaveType) = Save(
        id = id, createdAt = 0, source = SaveSource.SHARE_TEXT, sourceApp = "Instagram", sharedText = null, url = null,
        imagePath = null, thumbPath = null, status = SaveStatus.READ, failReason = null, readBy = ReadBy.CLOUD, title = title,
        summary = "", type = type, kind = "", place = "", eventDate = "", link = "", tags = emptyList(), items = emptyList(), readAt = null,
    )
    val hits = flowOf(
        PagingData.from(
            listOf(
                SearchHit(save(1, "Sunbeam Café, Assagao", SaveType.PLACE), "…banana pancakes, North \u0002Goa\u0003…"),
                SearchHit(save(2, "Goan prawn curry", SaveType.RECIPE), "…recipe from a \u0002Goa\u0003 trip…"),
            ),
        ),
    ).collectAsLazyPagingItems()
    KeptTheme(dark = false) {
        SearchScreen(
            SearchState(
                query = "goa", settled = true, stillReading = 2, notRead = 1,
                typeChips = listOf(TypeChip(SaveType.PLACE, 4), TypeChip(SaveType.EVENT, 1), TypeChip(SaveType.RECIPE, 1)),
            ),
            hits, onRunAction = {}, onBack = {}, onAction = {},
        )
    }
}
