package com.jdevsappstudio.kept.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jdevsappstudio.kept.core.designsystem.ButtonShape
import com.jdevsappstudio.kept.core.designsystem.CardShape
import com.jdevsappstudio.kept.core.designsystem.Kept
import com.jdevsappstudio.kept.core.designsystem.KeptIconButton
import com.jdevsappstudio.kept.core.designsystem.KeptMark
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.designsystem.PillShape
import com.jdevsappstudio.kept.core.designsystem.RouteBadge
import com.jdevsappstudio.kept.core.designsystem.TagPill
import com.jdevsappstudio.kept.core.designsystem.VSpace
import com.jdevsappstudio.kept.core.designsystem.card
import com.jdevsappstudio.kept.core.designsystem.typeIcon
import com.jdevsappstudio.kept.core.designsystem.typeTint
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.ShareApp
import com.jdevsappstudio.kept.data.ai.DeviceModelState
import com.jdevsappstudio.kept.feature.home.HeroCard

// ---------------------------------------------------------------- k-promise / k-promise-cloud

@Composable
fun PromiseScreen(state: OnboardingState, onAction: (OnboardingAction) -> Unit) {
    Page {
        VSpace(24.dp)
        KeptMark(56.dp)
        VSpace(8.dp)
        Text(
            OnboardingRules.PROMISE_TITLE, style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() },
        )
        Body(OnboardingRules.PROMISE_BODY)
        if (state.device != null) {
            val icons = if (state.cloudPhone) {
                listOf(Icons.Outlined.Cloud, Icons.Outlined.GridView, Icons.Outlined.ChatBubbleOutline)
            } else {
                listOf(Icons.Outlined.PhoneAndroid, Icons.Outlined.GridView, Icons.Outlined.Cloud)
            }
            OnboardingRules.promiseRows(state.cloudPhone).forEachIndexed { i, row -> PromiseRow(icons[i], row.title, row.body) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Body("Checking this phone for built-in AI…")
            }
        }
        VSpace(8.dp)
        PrimaryButton("Get started", enabled = state.device != null) { onAction(OnboardingAction.OnGetStarted) }
        LinkButton("How Kept uses AI") { onAction(OnboardingAction.OnHowKeptUsesAi) }
        Text(
            OnboardingRules.promiseFoot(state.cloudPhone), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PromiseRow(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Body(body)
        }
    }
}

// ---------------------------------------------------------------- B1 What do you save most?

@Composable
fun TypesScreen(state: OnboardingState, onAction: (OnboardingAction) -> Unit) {
    Page {
        StepHeader(1, onBack = null, onSkip = { onAction(OnboardingAction.OnSkip) })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeptMark(44.dp)
            Body(OnboardingRules.B1_HELLO)
        }
        Title(OnboardingRules.B1_TITLE)
        Body(OnboardingRules.B1_BODY)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OnboardingRules.B1_TYPES.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { type ->
                        TypeTile(type, checked = type in state.types, Modifier.weight(1f)) { onAction(OnboardingAction.OnToggleType(type)) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        VSpace(4.dp)
        PrimaryButton("Next") { onAction(OnboardingAction.OnTypesNext) }
    }
}

@Composable
private fun TypeTile(type: SaveType, checked: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (checked) scheme.primary.copy(alpha = 0.06f) else scheme.surface)
            .border(if (checked) 2.dp else 1.dp, if (checked) scheme.primary else scheme.outlineVariant, RoundedCornerShape(14.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(typeTint(type)), contentAlignment = Alignment.Center) {
            Icon(typeIcon(type), contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(18.dp))
        }
        Text(type.label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 2, modifier = Modifier.weight(1f).padding(start = 8.dp))
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 10.dp))
    }
}

// ---------------------------------------------------------------- B2 Where do you save from?

@Composable
fun AppsScreen(state: OnboardingState, onAction: (OnboardingAction) -> Unit) {
    Page {
        StepHeader(2, onBack = { onAction(OnboardingAction.OnBack) }, onSkip = { onAction(OnboardingAction.OnSkip) })
        Title(OnboardingRules.B2_TITLE)
        Body(OnboardingRules.B2_BODY)
        Column(Modifier.fillMaxWidth().card(MaterialTheme.colorScheme.surface, 1.dp)) {
            ShareApp.entries.forEachIndexed { i, app ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
                AppRow(app, checked = app in state.apps) { onAction(OnboardingAction.OnToggleApp(app)) }
            }
        }
        VSpace(4.dp)
        PrimaryButton("Next") { onAction(OnboardingAction.OnAppsNext) }
    }
}

@Composable
private fun AppRow(app: ShareApp, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) { Icon(app.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
        Text(app.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

internal fun ShareApp.icon(): ImageVector = when (this) {
    ShareApp.INSTAGRAM -> Icons.Outlined.CameraAlt
    ShareApp.YOUTUBE -> Icons.Outlined.SmartDisplay
    ShareApp.SCREENSHOTS -> Icons.Outlined.Image
    ShareApp.WHATSAPP -> Icons.Outlined.ChatBubbleOutline
    ShareApp.OTHER -> Icons.Outlined.Share
}

// ---------------------------------------------------------------- k-ob-first (step 3)

@Composable
fun FirstSaveScreen(state: OnboardingState, openable: ShareApp?, onAction: (OnboardingAction) -> Unit) {
    Page {
        StepHeader(3, onBack = { onAction(OnboardingAction.OnBack) }, onSkip = { onAction(OnboardingAction.OnSkip) })
        Title("That’s how it works")
        ExampleCard(state)
        Text("Now your first real save", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Body(OnboardingRules.firstSaveBody(state.apps, openable))
        PrimaryButton("Pick a screenshot", icon = Icons.Outlined.AddPhotoAlternate, enabled = !state.adding) { onAction(OnboardingAction.OnPick) }
        if (openable != null) {
            OutlinedButton(
                onClick = { onAction(OnboardingAction.OnOpenApp(openable)) }, shape = ButtonShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Open ${openable.label}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                if (state.adding) "Adding your screenshot…" else "Waiting for your first share…",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        LinkButton("Not now, go to Kept") { onAction(OnboardingAction.OnSkip) }
    }
}

/** 'Example · from a screenshot · not saved': drawn, not a real save (H35 merged practice into this step). */
@Composable
private fun ExampleCard(state: OnboardingState) {
    val readBy = when {
        !state.cloudPhone -> ReadBy.DEVICE
        state.cloudHelp -> ReadBy.CLOUD
        else -> null
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.5f).clip(CardShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFE7A35A), Color(0xFFB4632F), Color(0xFF4A2618)))),
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("EXAMPLE · FROM A SCREENSHOT · NOT SAVED", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            Text("Paneer tikka, air-fryer", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TagPill(SaveType.RECIPE.label, icon = typeIcon(SaveType.RECIPE), onDark = true)
                RouteBadge(readBy)
            }
        }
    }
}

// ---------------------------------------------------------------- First card (k-ob-got, -got-shot, -got-post)

@Composable
fun FirstCardScreen(state: OnboardingState, onAction: (OnboardingAction) -> Unit) {
    val save = state.firstSave ?: return
    val read = save.status == SaveStatus.READ
    Page {
        VSpace(8.dp)
        Title("Your first save")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeptMark(44.dp)
            Body(
                (if (read) OnboardingRules.readLine(save) else null)
                    ?: OnboardingRules.pendingLine(save, state.device, state.pause, state.cloudHelp),
            )
        }
        HeroCard(save, label = "KEPT")
        if (read) {
            if (OnboardingRules.cardKind(save) == FirstCardKind.ITEMS) ItemsCard(save)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    OnboardingRules.cardFoot(save), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { onAction(OnboardingAction.OnEditTags) }, shape = ButtonShape, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Edit tags", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        PrimaryButton("Go to Kept") { onAction(OnboardingAction.OnGoToKept) }
        when {
            read -> LinkButton(OnboardingRules.secondaryLabel(save)) {
                onAction(if (OnboardingRules.cardKind(save) == FirstCardKind.PLACE) OnboardingAction.OnOpenInMaps else OnboardingAction.OnOpenSave)
            }
            OnboardingRules.canTagByHand(save, state.device, state.pause) -> LinkButton("Tag it by hand") { onAction(OnboardingAction.OnEditTags) }
        }
    }
}

/** k-ob-got-post: the first item, then '+ 4 more' with the rest named. */
@Composable
private fun ItemsCard(save: Save) {
    Column(Modifier.fillMaxWidth().card(MaterialTheme.colorScheme.surface, 1.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(save.items.first(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        val rest = save.items.drop(1)
        if (rest.isNotEmpty()) {
            Text("+ ${rest.size} more", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(rest.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

// ---------------------------------------------------------------- shared bits

/** '‹ ━━ ━━ ── ──  2 of 4  Skip setup'. */
@Composable
private fun StepHeader(step: Int, onBack: (() -> Unit)?, onSkip: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            KeptIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onClick = onBack)
            Spacer(Modifier.width(12.dp))
        } else {
            Spacer(Modifier.weight(0.3f))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(OnboardingRules.STEPS) { i ->
                Box(
                    Modifier.weight(1f).height(5.dp).clip(PillShape)
                        .background(if (i < step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Text(
            "$step of ${OnboardingRules.STEPS}", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp),
        )
        TextButton(onClick = onSkip) { Text("Skip setup", style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun PrimaryButton(label: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, shape = ButtonShape, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp).padding(end = 0.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = if (icon != null) 8.dp else 0.dp))
    }
}

@Composable
private fun ColumnScope.LinkButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, textDecoration = TextDecoration.Underline)
    }
}

// ---------------------------------------------------------------- previews

private val previewSave = Save(
    id = 1, createdAt = 0, source = SaveSource.PICKER, sourceApp = null, sharedText = null, url = null,
    imagePath = null, thumbPath = null, status = SaveStatus.READ, failReason = null, readBy = ReadBy.DEVICE,
    title = "5 thrillers to watch", summary = "", type = SaveType.MOVIE_SHOW, kind = "movies", place = "", eventDate = "",
    link = "", tags = listOf("movie"), items = listOf("Prisoners", "Gone Girl", "Se7en", "Shutter Island", "Nightcrawler"), readAt = 6_000,
)

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun PromisePreview() {
    KeptTheme(dark = false) { PromiseScreen(OnboardingState(device = DeviceModelState.UNAVAILABLE)) {} }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun TypesPreview() {
    KeptTheme(dark = false) { TypesScreen(OnboardingState(types = listOf(SaveType.RECIPE, SaveType.PLACE))) {} }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun AppsPreview() {
    KeptTheme(dark = false) { AppsScreen(OnboardingState(apps = listOf(ShareApp.INSTAGRAM, ShareApp.SCREENSHOTS))) {} }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun FirstSavePreview() {
    KeptTheme(dark = false) {
        FirstSaveScreen(OnboardingState(device = DeviceModelState.AVAILABLE, apps = listOf(ShareApp.INSTAGRAM)), ShareApp.INSTAGRAM) {}
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun FirstCardPreview() {
    KeptTheme(dark = false) { FirstCardScreen(OnboardingState(device = DeviceModelState.AVAILABLE, firstSave = previewSave)) {} }
}
