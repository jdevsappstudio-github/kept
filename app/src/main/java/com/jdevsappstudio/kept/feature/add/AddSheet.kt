package com.jdevsappstudio.kept.feature.add

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Button
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.designsystem.ButtonShape
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.designsystem.PillShape
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.data.capture.ClipPeek
import com.jdevsappstudio.kept.data.capture.Keeper
import org.koin.androidx.compose.koinViewModel

/**
 * k-addmenu "Add to Kept", a bottom sheet in a dialog destination (H33, H56). [pickNow]: k-empty's Pick screenshots
 * opens the photo picker straight away and draws no sheet; cancelling it goes back to Home.
 */
@Composable
fun AddRoot(
    pickNow: Boolean,
    onClose: () -> Unit,
    onOpenReading: (since: Long) -> Unit,
    onOpenSave: (Long) -> Unit,
    onOpenHowTo: () -> Unit,
    viewModel: AddViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(pickerLimit(Keeper.MAX_PER_BATCH)),
    ) { uris -> viewModel.onAction(AddAction.OnPicked(uris.map { it.toString() })) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is AddEvent.LaunchPicker -> picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            is AddEvent.OpenReading -> onOpenReading(event.since)
            is AddEvent.OpenSave -> onOpenSave(event.id)
            AddEvent.OpenHowTo -> onOpenHowTo()
            AddEvent.Close -> onClose()
        }
    }
    // Peek again whenever Kept comes back to the front: the person may have gone to copy a link (H56).
    LifecycleResumeEffect(Unit) {
        viewModel.onAction(AddAction.OnShown)
        onPauseOrDispose { }
    }
    var picked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pickNow) {
        if (pickNow && !picked) {
            picked = true
            viewModel.onAction(AddAction.OnPickScreenshots)
        }
    }
    BackHandler(onBack = onClose)
    if (!pickNow) {
        AddSheet(state, viewModel::onAction)
    }
}

@Composable
fun AddSheet(state: AddState, onAction: (AddAction) -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close") {
                onAction(AddAction.OnDismiss)
            }
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
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp)
                    .clip(PillShape).background(MaterialTheme.colorScheme.outlineVariant),
            )
            Text(
                "Add to Kept",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).semantics { heading() },
            )
            AddRow(
                Icons.Outlined.AddPhotoAlternate, "Pick screenshots", AddCopy.PICK_LINE,
                onClick = { onAction(AddAction.OnPickScreenshots) },
            )
            AddRow(
                Icons.Outlined.ContentPaste, "Paste a link", AddCopy.pasteLine(state.clip),
                enabled = !state.keeping && state.field == null,
                onClick = { onAction(AddAction.OnPasteLink) },
            )
            state.field?.let { PasteFieldBlock(it, state.keeping, onAction) }
            AddRow(
                Icons.Outlined.IosShare, "Share from an app", AddCopy.SHARE_LINE,
                onClick = { onAction(AddAction.OnShareFromApp) },
            )
            Text(
                AddCopy.FOOTER,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
    }
}

/** Sheet words in one place (UX open to the review panel). */
internal object AddCopy {
    const val PICK_LINE = "Up to 100 at a time · Kept sees only what you pick"
    const val SHARE_LINE = "How to send things to Kept from Instagram, YouTube…"
    const val FOOTER = "Sharing is quickest: you never leave the other app."

    /** H56: never the URL itself, only that a link is there. */
    fun pasteLine(clip: ClipPeek): String =
        if (clip == ClipPeek.LINK) "A link is copied" else "Copy a link in any app, or type one"
}

@Composable
private fun AddRow(icon: ImageVector, title: String, line: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** No link on the clipboard: type or paste one here (H56). */
@Composable
private fun PasteFieldBlock(field: PasteField, keeping: Boolean, onAction: (AddAction) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.padding(start = 62.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = field.text,
            onValueChange = { onAction(AddAction.OnPasteTextChange(it)) },
            label = { Text("Paste or type a link") },
            isError = field.error != null,
            supportingText = field.error?.let { e -> { Text(e.asString()) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAction(AddAction.OnAddPasted) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Button(
            onClick = { onAction(AddAction.OnAddPasted) },
            enabled = field.canAdd && !keeping,
            shape = ButtonShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Add", style = MaterialTheme.typography.labelLarge) }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun AddSheetPreview() {
    KeptTheme(dark = false) {
        AddSheet(AddState(clip = ClipPeek.LINK)) {}
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun AddSheetFieldPreview() {
    KeptTheme(dark = false) {
        AddSheet(AddState(field = PasteField("hello"))) {}
    }
}
