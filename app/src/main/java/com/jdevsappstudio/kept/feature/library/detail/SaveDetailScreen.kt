package com.jdevsappstudio.kept.feature.library.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.designsystem.ButtonShape
import com.jdevsappstudio.kept.core.designsystem.CardShape
import com.jdevsappstudio.kept.core.designsystem.Kept
import com.jdevsappstudio.kept.core.designsystem.KeptIconButton
import com.jdevsappstudio.kept.core.designsystem.PillShape
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.designsystem.RouteBadge
import com.jdevsappstudio.kept.core.designsystem.SaveThumb
import com.jdevsappstudio.kept.core.designsystem.SectionLabel
import com.jdevsappstudio.kept.core.designsystem.TagPill
import com.jdevsappstudio.kept.core.designsystem.VSpace
import com.jdevsappstudio.kept.core.designsystem.typeIcon
import com.jdevsappstudio.kept.core.model.EventDates
import com.jdevsappstudio.kept.core.model.ItemAction
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveItem
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.itemAction
import com.jdevsappstudio.kept.core.presentation.ItemActionLauncher
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.feature.library.displayTitle
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.koin.androidx.compose.koinViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

/** Item name cap in the fix / add dialogs. */
private const val ITEM_MAX = 80

@Composable
fun SaveDetailRoot(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenCloudOut: () -> Unit,
    onOpenSent: (Long?) -> Unit = {},
    /** k-cloud-out paid out (set on this back-stack entry by the NavHost); [onGrantConsumed] clears it. */
    answersGranted: Boolean = false,
    onGrantConsumed: () -> Unit = {},
    viewModel: SaveDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(answersGranted) {
        if (answersGranted) {
            onGrantConsumed()
            viewModel.onAction(SaveDetailAction.OnAnswersGranted)
        }
    }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onAction(SaveDetailAction.OnScreenshotPicked(uri?.toString()))
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SaveDetailEvent.Close -> onBack()
            SaveDetailEvent.PickScreenshot ->
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            SaveDetailEvent.OpenCloudOut -> onOpenCloudOut()
            is SaveDetailEvent.OpenSent -> onOpenSent(event.sentId)
        }
    }
    SaveDetailScreen(
        state,
        onRun = { action, save ->
            val target = action.target
            if (save.type == SaveType.RECIPE && target is ItemAction.Target.Copy) {
                copy(context, target.text) // Copy ingredients: our own snackbar says how many
                viewModel.onAction(SaveDetailAction.OnCopied(target.text.lines().size))
            } else {
                ItemActionLauncher.launch(context, target)
            }
        },
        onOpenLink = { ItemActionLauncher.launch(context, ItemAction.Target.OpenLink(it)) },
        onShare = { share(context, it) },
    ) { action ->
        when (action) {
            SaveDetailAction.OnBackClick -> onBack()
            SaveDetailAction.OnEditClick -> state.save?.takeIf { !it.example }?.let { onEdit(it.id) }
            else -> viewModel.onAction(action)
        }
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Kept", text))
}

/** Share → Android share sheet with the title and the link (k-recipe note). */
private fun share(context: Context, save: Save) {
    val text = listOfNotNull(save.title.ifBlank { null }, DetailRules.originalLink(save)).joinToString("\n")
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

@Composable
fun SaveDetailScreen(
    state: SaveDetailState,
    onRun: (ItemAction, Save) -> Unit,
    onOpenLink: (String) -> Unit,
    onShare: (Save) -> Unit,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    onAction: (SaveDetailAction) -> Unit,
) {
    val save = state.save
    var adding by rememberSaveable { mutableStateOf(false) }
    // k-link while there is still something to do on it; after a fill the normal detail shows (H59).
    val linkMode = state.link != null && (state.link.canFill || state.link.filling)

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
    ) {
        item(key = "bar") {
            DetailBar(
                save = save,
                showShare = save != null && !linkMode,
                onBack = { onAction(SaveDetailAction.OnBackClick) },
                onShare = { save?.let(onShare) },
                onAction = onAction,
            )
        }
        if (save == null) return@LazyColumn
        if (linkMode) {
            item(key = "link") { LinkBody(save, state.link!!, onAction) }
            return@LazyColumn
        }
        item(key = "head") { Header(save, today, zone, onOpenLink, onAction) }
        item(key = "status") { StatusNote(save, onAction) }
        if (save.type == SaveType.EVENT) item(key = "event") { EventFacts(save, today) }

        val main = DetailRules.mainAction(save, state.items, today, zone)
        if (DetailRules.showsItems(save.type, state.items)) {
            item(key = "itemsLabel") {
                SectionLabel("${DetailRules.itemsLabel(save.type)}  ${state.items.size}", Modifier.padding(top = 16.dp, bottom = 6.dp))
            }
            itemsIndexed(state.items, key = { _, it -> it.id }) { i, item ->
                val last = state.items.lastIndex
                val shape = RoundedCornerShape(
                    topStart = if (i == 0) 18.dp else 0.dp, topEnd = if (i == 0) 18.dp else 0.dp,
                    bottomStart = if (i == last) 18.dp else 0.dp, bottomEnd = if (i == last) 18.dp else 0.dp,
                )
                Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)) {
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ItemRow(save, item, onRun, onAction)
                }
            }
            DetailRules.addItemLabel(save.type)?.takeIf { !save.example }?.let { label ->
                item(key = "addItem") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Missed one?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { adding = true }) { Text("+ $label", style = MaterialTheme.typography.labelLarge) }
                        Text("· Tap a title to fix it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (save.type != SaveType.EVENT) item(key = "post") { FromThePost(save) }
        item(key = "actions") {
            Actions(save, main, onRun, onOpenLink, onAction)
        }
    }

    state.dialog?.let { DetailDialogs(it, if (save?.type == SaveType.MOVIE_SHOW) "movies" else "items", onAction) }
    state.editingItem?.let { item ->
        ItemDialog(
            title = "Fix this item",
            initial = item.name,
            confirm = "Save",
            onConfirm = { onAction(SaveDetailAction.OnRenameItem(it)) },
            onDismiss = { onAction(SaveDetailAction.OnDismissItem) },
            onDelete = { onAction(SaveDetailAction.OnDeleteItem) },
        )
    }
    if (adding && save != null) {
        ItemDialog(
            title = DetailRules.addItemLabel(save.type) ?: "Add an item",
            initial = "",
            confirm = "Add",
            onConfirm = { adding = false; onAction(SaveDetailAction.OnAddItem(it)) },
            onDismiss = { adding = false },
            onDelete = null,
        )
    }
}

@Composable
private fun DetailBar(
    save: Save?,
    showShare: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onAction: (SaveDetailAction) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        KeptIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onClick = onBack)
        Box(Modifier.weight(1f))
        if (showShare) {
            KeptIconButton(Icons.Outlined.Share, "Share", onClick = onShare)
            Box(Modifier.size(10.dp))
        }
        // H71: an example has no Edit / Read again / Delete; Home's '3 examples · Clear' removes them.
        if (save != null && !save.example) {
            Box {
                KeptIconButton(Icons.Outlined.MoreVert, "More options", onClick = { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onAction(SaveDetailAction.OnEditClick) })
                    if (save.status == SaveStatus.READ || save.status == SaveStatus.NEEDS_LOOK) {
                        DropdownMenuItem(text = { Text("Read again") }, onClick = { menu = false; onAction(SaveDetailAction.OnReadAgainClick) })
                    }
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onAction(SaveDetailAction.OnDeleteClick) })
                }
            }
        }
    }
}

/** Picture, chips, title, summary and where it came from. */
@Composable
private fun Header(save: Save, today: LocalDate, zone: ZoneId, onOpenLink: (String) -> Unit, onAction: (SaveDetailAction) -> Unit) {
    val link = DetailRules.originalLink(save)
    Column(Modifier.padding(top = 8.dp)) {
        val picture = save.imagePath ?: save.thumbPath
        if (picture != null) {
            Hero(
                path = picture,
                caption = if (link != null && save.source != com.jdevsappstudio.kept.core.model.SaveSource.PICKER &&
                    save.source != com.jdevsappstudio.kept.core.model.SaveSource.SHARE_IMAGE
                ) "${DetailRules.openOriginalLabel(save)} ↗" else if (save.example) "Example screenshot" else "Your screenshot",
                onClick = link?.let { { onOpenLink(it) } },
            )
            VSpace(14.dp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TagPill(save.type.label, icon = typeIcon(save.type))
            save.tags.forEach { TagPill(it) }
            if (!save.example) Text(
                "+ tag",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(PillShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PillShape)
                    .clickable(role = Role.Button, onClickLabel = "Edit tags") { onAction(SaveDetailAction.OnEditClick) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        DetailRouteBadge(save, onAction, Modifier.padding(top = 8.dp))
        Text(
            save.displayTitle(),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp, lineHeight = 33.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 10.dp).semantics { heading() },
        )
        val lines = listOfNotNull(
            save.summary.trim().ifEmpty { null },
            save.place.trim().takeIf { it.isNotEmpty() && save.type == SaveType.PLACE },
            DetailRules.savedLine(save, today, zone),
        )
        Text(
            lines.joinToString("\n"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun Hero(path: String, caption: String, onClick: (() -> Unit)?) {
    Box(
        Modifier.fillMaxWidth().height(190.dp).clip(CardShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = caption, onClick = onClick) else Modifier),
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))),
        )
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}

/** Still reading, or Kept couldn't read it (k-failed words). */
@Composable
private fun StatusNote(save: Save, onAction: (SaveDetailAction) -> Unit) {
    val text = when (save.status) {
        SaveStatus.QUEUED, SaveStatus.READING -> "Reading it… The card fills in when Kept is done."
        SaveStatus.NEEDS_LOOK -> "Kept couldn't read this one. Read it again, or fill it in by hand."
        SaveStatus.READ -> return
    }
    Column(
        Modifier.padding(top = 14.dp).fillMaxWidth().clip(CardShape).background(Kept.colors.waitBg).padding(14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        if (save.status == SaveStatus.NEEDS_LOOK) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                TextButton(onClick = { onAction(SaveDetailAction.OnReadAgainClick) }) { Text("Read again") }
                TextButton(onClick = { onAction(SaveDetailAction.OnEditClick) }) { Text("By hand") }
            }
        }
    }
}

/** k-date: the date as read (and what it was read from), the time, the place. */
@Composable
private fun EventFacts(save: Save, today: LocalDate) {
    val w = DetailRules.eventWhen(save, today)
    val written = save.eventDate.trim()
    val rows = buildList {
        when {
            w.date != null -> add(Triple(Icons.Outlined.CalendarMonth, EventDates.formatDate(w.date), "Read from '$written'"))
            written.isNotEmpty() -> add(Triple(Icons.Outlined.CalendarMonth, written, "As written"))
        }
        w.time?.let { add(Triple(Icons.Outlined.Schedule, EventDates.formatTime(it), null)) }
        save.place.trim().takeIf { it.isNotEmpty() }?.let { add(Triple(Icons.Outlined.Place, it, null)) }
    }
    if (rows.isEmpty()) return
    Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surface)) {
        rows.forEachIndexed { i, (icon, main, sub) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconSquare(icon)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(main, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
private fun ItemRow(save: Save, item: SaveItem, onRun: (ItemAction, Save) -> Unit, onAction: (SaveDetailAction) -> Unit) {
    // Ingredients are a plain list as read (H43); other items open their own action and fix on tap.
    val recipe = save.type == SaveType.RECIPE
    val action = if (recipe) null else itemAction(save.type, item.name, null)
    Row(
        Modifier.fillMaxWidth()
            // H71: an example is never edited, so its items don't open Fix.
            .then(if (recipe || save.example) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = "Fix") { onAction(SaveDetailAction.OnItemClick(item)) })
            .heightIn(min = if (recipe) 56.dp else 64.dp)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!recipe && item.otherSaves > 0) {
                Text(
                    "also in ${item.otherSaves} other save${if (item.otherSaves == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (action != null) {
            TextButton(onClick = { onRun(action, save) }) {
                Text(action.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** The caption or text that came with the share (k-place 'From the post'). */
@Composable
private fun FromThePost(save: Save) {
    val text = save.sharedText?.let { t -> listOfNotNull(save.url).fold(t) { acc, u -> acc.replace(u, "") } }?.trim().orEmpty()
    if (text.isEmpty() || text == save.summary.trim()) return
    Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Text("From the post", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 8, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Actions(
    save: Save,
    main: ItemAction?,
    onRun: (ItemAction, Save) -> Unit,
    onOpenLink: (String) -> Unit,
    onAction: (SaveDetailAction) -> Unit,
) {
    if (save.status != SaveStatus.READ && save.status != SaveStatus.NEEDS_LOOK) return
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            main != null -> PrimaryButton(main.label, actionIcon(main)) { onRun(main, save) }
            save.type == SaveType.IDEA -> PrimaryButton("Add to collection", Icons.Outlined.Add) { onAction(SaveDetailAction.OnEditClick) }
        }
        if (save.type == SaveType.EVENT && main != null) {
            Text(
                "Opens your calendar with this filled in. Nothing is added until you save it there.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val link = DetailRules.originalLink(save)
        if (link != null && DetailRules.showsOpenOriginal(save, main)) {
            SecondaryButton(DetailRules.openOriginalLabel(save), Icons.AutoMirrored.Outlined.OpenInNew) { onOpenLink(link) }
        }
    }
}

private fun actionIcon(action: ItemAction): ImageVector = when (action.target) {
    is ItemAction.Target.Copy -> Icons.Outlined.ContentCopy
    is ItemAction.Target.Maps -> Icons.Outlined.Map
    is ItemAction.Target.Calendar -> Icons.Outlined.CalendarMonth
    else -> Icons.AutoMirrored.Outlined.OpenInNew
}

@Composable
private fun PrimaryButton(label: String, icon: ImageVector?, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = ButtonShape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp).padding(end = 0.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun SecondaryButton(label: String, icon: ImageVector?, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 10.dp))
    }
}

/** k-link (H59): a link that came with only its caption. */
@Composable
private fun LinkBody(save: Save, link: LinkFill, onAction: (SaveDetailAction) -> Unit) {
    val reel = DetailRules.isReel(save.url)
    Column(Modifier.padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surface).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaveThumb(null, save.type, Modifier.size(76.dp), corner = 12.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(save.displayTitle(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${shortLink(save.url.orEmpty())} · ${if (reel) "caption only" else "preview only"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    TagPill(save.type.label, icon = typeIcon(save.type))
                    DetailRouteBadge(save, onAction)
                }
            }
        }
        Row(
            Modifier.padding(top = 14.dp).fillMaxWidth().clip(CardShape).background(Kept.colors.waitBg).padding(14.dp),
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = Kept.colors.wait, modifier = Modifier.size(22.dp))
            Column(Modifier.padding(start = 10.dp)) {
                Text(if (reel) "Kept could only read the caption" else "Kept could only read the link's preview", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (reel) "Links from reels share very little. Add a screenshot of the reel and Kept will read what's on it."
                    else "This link shared very little. Add a screenshot of the page and Kept will read what's on it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        VSpace(16.dp)
        PrimaryButton("Add a screenshot", Icons.Outlined.AddPhotoAlternate) { onAction(SaveDetailAction.OnAddScreenshotClick) }
        VSpace(10.dp)
        OutlinedButton(
            onClick = { onAction(SaveDetailAction.OnFillInClick) },
            enabled = !link.filling,
            shape = ButtonShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            if (link.filling) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Filling in…", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 10.dp))
            } else {
                Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(20.dp))
                val left = if (link.showAnswersLeft) " · ${link.answersLeft} left" else ""
                Text("Fill in with cloud$left", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 10.dp))
            }
        }
        Text(
            "Fill in with cloud sends only the link to Google Gemini. Asks first if Cloud help is off.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 8.dp, end = 8.dp),
        )
    }
}

/** Who read it; a filled-in card counts as Cloud (H59). A Cloud chip opens k-sent at that request (H67). */
@Composable
private fun DetailRouteBadge(save: Save, onAction: (SaveDetailAction) -> Unit, modifier: Modifier = Modifier) {
    val readBy = if (save.filledAt != null) ReadBy.CLOUD else save.readBy
    val tap = if (readBy == ReadBy.CLOUD && !save.example) {
        Modifier.clip(PillShape).clickable(role = Role.Button, onClickLabel = "See what was sent") { onAction(SaveDetailAction.OnCloudChipClick) }
    } else Modifier
    RouteBadge(readBy, save.example, modifier.then(tap))
}

/** 'instagram.com/reel/…' */
internal fun shortLink(url: String): String {
    val u = runCatching { Uri.parse(url) }.getOrNull() ?: return url
    val host = u.host?.removePrefix("www.") ?: return url
    val first = u.pathSegments.firstOrNull()
    return if (first == null) host else "$host/$first/…"
}

@Composable
private fun DetailDialogs(dialog: DetailDialog, noun: String, onAction: (SaveDetailAction) -> Unit) {
    val (title, body, confirm) = when (dialog) {
        is DetailDialog.ConfirmDelete -> Triple("Delete this save?", "${dialog.items} $noun go with it.", "Delete")
        DetailDialog.ConfirmReadAgain -> Triple("Read it again?", "Your edits will be replaced by a fresh read.", "Read again")
        DetailDialog.CloudConsent -> Triple(
            "Fill in uses the cloud",
            "To fill this in, Kept sends only the link to Google Gemini. Never your images. Answers are marked Cloud. " +
                "During the beta, Google may use what is sent to improve its products.\n\nChange it any time in Privacy.",
            "Turn on Cloud help",
        )
    }
    AlertDialog(
        onDismissRequest = { onAction(SaveDetailAction.OnDismissDialog) },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = { onAction(SaveDetailAction.OnConfirmDialog) }) { Text(confirm) } },
        dismissButton = {
            TextButton(onClick = { onAction(SaveDetailAction.OnDismissDialog) }) {
                Text(if (dialog == DetailDialog.CloudConsent) "Not now" else "Cancel")
            }
        },
    )
}

@Composable
private fun ItemDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it.take(ITEM_MAX) }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete item", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
