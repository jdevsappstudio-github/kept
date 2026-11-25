package com.jdevsappstudio.kept.feature.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.BuildConfig
import com.jdevsappstudio.kept.core.designsystem.ButtonShape
import com.jdevsappstudio.kept.core.designsystem.CardShape
import com.jdevsappstudio.kept.core.designsystem.HintCard
import com.jdevsappstudio.kept.core.designsystem.Kept
import com.jdevsappstudio.kept.core.designsystem.KeptChip
import com.jdevsappstudio.kept.core.designsystem.KeptIconButton
import com.jdevsappstudio.kept.core.designsystem.KeptMark
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.designsystem.PreviewRow
import com.jdevsappstudio.kept.core.designsystem.RouteBadge
import com.jdevsappstudio.kept.core.designsystem.SaveThumb
import com.jdevsappstudio.kept.core.designsystem.SectionLabel
import com.jdevsappstudio.kept.core.designsystem.TagPill
import com.jdevsappstudio.kept.core.designsystem.VSpace
import com.jdevsappstudio.kept.core.designsystem.card
import com.jdevsappstudio.kept.core.designsystem.shelfGround
import com.jdevsappstudio.kept.core.designsystem.typeIcon
import com.jdevsappstudio.kept.core.designsystem.typeTint
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveCollection
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.Shelf
import com.jdevsappstudio.kept.core.model.TypeChip
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.feature.add.AddDebug
import com.jdevsappstudio.kept.feature.ask.AskDebug
import com.jdevsappstudio.kept.feature.library.detail.FillDebug
import com.jdevsappstudio.kept.feature.quota.QuotaDebug
import com.jdevsappstudio.kept.feature.search.SearchDebug
import com.jdevsappstudio.kept.feature.spike.ReaderSpikeRoot
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeRoot(
    onOpenList: (SaveType?) -> Unit,
    onOpenSave: (Long) -> Unit,
    onOpenShelf: (String) -> Unit,
    onOpenShelves: () -> Unit,
    onOpenCollection: (Long) -> Unit,
    onNewCollection: () -> Unit,
    onAdd: () -> Unit,
    onPick: () -> Unit,
    onSettings: () -> Unit,
    onNeedLook: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is HomeEvent.OpenCollection -> onOpenCollection(event.id)
        }
    }
    HomeScreen(state) { action ->
        when (action) {
            HomeAction.OnAllClick -> onOpenList(null)
            is HomeAction.OnTypeClick -> onOpenList(action.type)
            is HomeAction.OnSaveClick -> onOpenSave(action.id)
            is HomeAction.OnShelfClick -> onOpenShelf(action.kind)
            HomeAction.OnSeeAllShelvesClick -> onOpenShelves()
            is HomeAction.OnCollectionClick -> onOpenCollection(action.id)
            HomeAction.OnNewCollectionClick -> onNewCollection()
            HomeAction.OnAddClick -> onAdd()
            HomeAction.OnPickClick -> onPick()
            HomeAction.OnSettingsClick -> onSettings()
            HomeAction.OnNeedLookClick -> onNeedLook()
            else -> viewModel.onAction(action)
        }
    }
}

@Composable
fun HomeScreen(state: HomeState, onAction: (HomeAction) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "bar") { HomeBar(showAdd = !state.isEmpty, onAction = onAction) }
        when {
            !state.loaded -> Unit
            state.isEmpty -> item(key = "empty") { EmptyHome(onAction) }
            else -> {
                item(key = "header") { Header(state) }
                if (state.lines.isNotEmpty()) item(key = "lines") { StatusLines(state.lines, onAction) }
                item(key = "chips") { TypeChips(state.chips, state.total, onAction) }
                state.hero?.let { hero -> item(key = "hero") { HeroCard(hero) { onAction(HomeAction.OnSaveClick(hero.id)) } } }
                item(key = "shelves") {
                    if (state.isEarly) EarlyShelves() else Shelves(state.shelves, state.allShelves, onAction)
                }
                item(key = "collections") { Collections(state.collections, onAction) }
                state.suggestion?.let { s -> item(key = "suggestion") { SuggestionCard(s.name, s.saves, onAction) } }
            }
        }
        if (BuildConfig.DEBUG) item(key = "debug") { DebugPanel(state, onAction) }
    }
}

@Composable
private fun HomeBar(showAdd: Boolean, onAction: (HomeAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KeptMark()
        Text(
            "Kept",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        if (showAdd) {
            KeptIconButton(Icons.Outlined.Add, "Add to Kept", onClick = { onAction(HomeAction.OnAddClick) })
            Spacer(Modifier.size(10.dp))
        }
        KeptIconButton(Icons.Outlined.Settings, "Settings", onClick = { onAction(HomeAction.OnSettingsClick) })
    }
}

@Composable
private fun Header(state: HomeState) {
    Column {
        Text(
            buildAnnotatedString {
                append(if (state.total == 1) "1 save,\n" else "${state.total} saves,\n")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.primary)) { append("tagged.") }
            },
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        val tail = when (state.readsWith) {
            ReadsWith.DEVICE -> "all read on this phone"
            ReadsWith.CLOUD -> "read with Gemini in the cloud"
            ReadsWith.UNKNOWN -> null
        }
        val week = if (state.newThisWeek > 0) "${state.newThisWeek} new this week" else null
        val subtitle = listOfNotNull(week, tail).joinToString(" · ")
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusLines(lines: List<HomeLine>, onAction: (HomeAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val (text, color) = when (line) {
                is HomeLine.Reading -> "Reading ${line.current} of ${line.total}" + when (line.with) {
                    ReadsWith.DEVICE -> " · On this phone"
                    ReadsWith.CLOUD -> " · Gemini in the cloud"
                    ReadsWith.UNKNOWN -> ""
                } to MaterialTheme.colorScheme.onSurfaceVariant
                is HomeLine.Waiting -> "${line.count} waiting · ${HomeLines.waitReason(line.pause)}" to Kept.colors.wait
                is HomeLine.NeedLook -> (if (line.count == 1) "1 needs a look" else "${line.count} need a look") to Kept.colors.wait
                is HomeLine.ReadsLeft -> "${line.left} of ${line.total} cloud reads left today" to Kept.colors.cloud
            }
            val tap = if (line is HomeLine.NeedLook) Modifier.clickable(role = Role.Button) { onAction(HomeAction.OnNeedLookClick) } else Modifier
            Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = tap.heightIn(min = 24.dp))
        }
    }
}

@Composable
private fun TypeChips(chips: List<TypeChip>, total: Int, onAction: (HomeAction) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") { KeptChip("All $total", selected = true, onClick = { onAction(HomeAction.OnAllClick) }) }
        items(chips, key = { it.type.wire }) { chip ->
            KeptChip("${chip.type.label} ${chip.count}", selected = false, onClick = { onAction(HomeAction.OnTypeClick(chip.type)) })
        }
    }
}

@Composable
private fun HeroCard(save: Save, onClick: () -> Unit) {
    val ago = DateUtils.getRelativeTimeSpanString(save.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    val source = when (save.source) {
        SaveSource.PICKER, SaveSource.SHARE_IMAGE -> if (save.imagePath != null) "YOUR SCREENSHOT" else null
        else -> null
    }
    val picture = save.thumbPath ?: save.imagePath
    val title = when (save.status) {
        SaveStatus.QUEUED, SaveStatus.READING -> save.title.ifEmpty { "Reading it…" }
        SaveStatus.NEEDS_LOOK -> save.title.ifEmpty { "Needs a look" }
        SaveStatus.READ -> save.title.ifEmpty { "Untitled" }
    }
    Box(
        Modifier.fillMaxWidth()
            // A link or note has no picture: a shorter tinted card, not a big empty square.
            .then(if (picture != null) Modifier.aspectRatio(1.07f) else Modifier.height(190.dp))
            .card(MaterialTheme.colorScheme.surfaceVariant, 3.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Just kept: $title" },
    ) {
        if (picture != null) {
            SaveThumb(picture, save.type, Modifier.fillMaxSize(), corner = 18.dp)
        } else {
            Box(Modifier.fillMaxSize().background(typeTint(save.type)))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f)),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                listOfNotNull("JUST KEPT", source ?: ago.uppercase()).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (save.status == SaveStatus.READ) {
                    TagPill(save.type.label, icon = typeIcon(save.type), onDark = true)
                    save.tags.firstOrNull()?.let { TagPill(it, onDark = true) }
                    RouteBadge(save.readBy)
                }
            }
        }
    }
}

@Composable
private fun Shelves(shelves: List<Shelf>, all: Int, onAction: (HomeAction) -> Unit) {
    if (shelves.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(
            "Shelves",
            action = "See all $all",
            onAction = { onAction(HomeAction.OnSeeAllShelvesClick) },
        )
        TwoColumns(shelves) { shelf, mod ->
            val count = if (shelf.countsItems) shelf.entries else shelf.saves
            Tile(
                title = shelf.title, count = count, icon = Icons.Outlined.Layers, previews = shelf.previews,
                ground = shelfGround(shelf.kind),
                description = "${shelf.title} shelf, $count ${if (shelf.countsItems) "items" else "saves"}",
                modifier = mod, onClick = { onAction(HomeAction.OnShelfClick(shelf.kind)) },
            )
        }
    }
}

@Composable
private fun EarlyShelves() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Shelves")
        HintCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Shelves build themselves", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "When 3 saves share a kind, like Movies or Shoes, a shelf appears here.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Collections(collections: List<SaveCollection>, onAction: (HomeAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Collections")
        val tiles: List<SaveCollection?> = collections + null // null = 'New collection' tile, always last (H55)
        TwoColumns(tiles) { c, mod ->
            if (c == null) {
                NewCollectionTile(mod) { onAction(HomeAction.OnNewCollectionClick) }
            } else {
                Tile(
                    title = c.name, count = c.saves, icon = Icons.Outlined.Folder, previews = c.previews,
                    ground = MaterialTheme.colorScheme.surface,
                    description = "${c.name} collection, ${c.saves} saves",
                    modifier = mod, onClick = { onAction(HomeAction.OnCollectionClick(c.id)) },
                )
            }
        }
    }
}

/** Tiles two to a row, equal heights per row. */
@Composable
private fun <T> TwoColumns(items: List<T>, tile: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicTileHeight), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tile(it, Modifier.weight(1f).fillMaxSize()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private val IntrinsicTileHeight = 132.dp

@Composable
private fun Tile(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    previews: List<String>,
    ground: Color,
    description: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.card(ground, 1.dp).clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                Spacer(Modifier.weight(1f))
                Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
        PreviewRow(previews, count)
    }
}

@Composable
private fun NewCollectionTile(modifier: Modifier, onClick: () -> Unit) {
    HintCard(modifier, onClick = onClick) {
        Column {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Text("New collection", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
            Text("Free, as many as you like", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SuggestionCard(name: String, saves: Int, onAction: (HomeAction) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("$saves saves mention $name", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            "Make a \"$name\" collection? Kept only suggests, it never files for you.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            Button(
                onClick = { onAction(HomeAction.OnMakeSuggestion) },
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text("Make it", style = MaterialTheme.typography.labelLarge) }
            OutlinedButton(
                onClick = { onAction(HomeAction.OnDismissSuggestion) },
                shape = ButtonShape,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text("Not now", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun EmptyHome(onAction: (HomeAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        VSpace(8.dp)
        Text("Nothing kept yet", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text("Two ways to start:", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier.fillMaxWidth().card(MaterialTheme.colorScheme.surface, 1.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Step(1, "Share from any app", "On a reel, post or page, tap Share, then Kept.")
            Step(2, "Pick screenshots", "Up to 100 you meant to come back to. Start with 3, read and tagged in under a minute.")
        }
        Button(
            onClick = { onAction(HomeAction.OnPickClick) },
            shape = ButtonShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Text("Pick screenshots", style = MaterialTheme.typography.labelLarge)
        }
        Text(
            "No gallery permission: you pick, Kept only sees those.",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Step(n: Int, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Debug only: the H51 panels, below the real home until each screen has its own UI. */
@Composable
private fun DebugPanel(state: HomeState, onAction: (HomeAction) -> Unit) {
    Column(Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Debug")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cloud help (debug)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = state.cloudHelp, onCheckedChange = { onAction(HomeAction.SetCloudHelp(it)) })
        }
        AddDebug()
        QuotaDebug()
        SearchDebug()
        AskDebug()
        FillDebug()
        ReaderSpikeRoot()
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun HomeScreenPreview() {
    val save = Save(
        id = 1, createdAt = System.currentTimeMillis() - 120_000, source = SaveSource.SHARE_TEXT, sourceApp = "Instagram",
        sharedText = null, url = "https://example.com", imagePath = null, thumbPath = null, status = SaveStatus.READ,
        failReason = null, readBy = ReadBy.CLOUD, title = "Paneer tikka, air-fryer", summary = "", type = SaveType.RECIPE,
        kind = "dishes", place = "", eventDate = "", link = "", tags = listOf("air-fryer"), items = emptyList(), readAt = null,
    )
    KeptTheme(dark = false) {
        HomeScreen(
            HomeState(
                counts = QueueCounts(0, 0, 0, 84), chips = listOf(TypeChip(SaveType.RECIPE, 12), TypeChip(SaveType.PLACE, 9)),
                hero = save, newThisWeek = 12, readsWith = ReadsWith.CLOUD, loaded = true, allShelves = 6,
                shelves = listOf(
                    Shelf("movies", "Movies", 20, 23, 23, false, null),
                    Shelf("shoes", "Shoes", 6, 0, 6, false, null),
                ),
                collections = listOf(SaveCollection(1, "Goa trip", 0, 6)),
            ),
        ) {}
    }
}
