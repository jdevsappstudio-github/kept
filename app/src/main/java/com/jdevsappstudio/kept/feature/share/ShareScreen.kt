package com.jdevsappstudio.kept.feature.share

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.queue.ReadRoute

@Composable
fun ShareRoot(viewModel: ShareViewModel, onOpenApp: (Long?) -> Unit, onClose: () -> Unit, onAskNotifications: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ShareEvent.OpenApp -> onOpenApp(event.saveId)
            ShareEvent.Close -> onClose()
            ShareEvent.AskNotifications -> onAskNotifications()
        }
    }
    ShareScreen(state = state, onAction = viewModel::onAction)
}

/** k-shared / k-shared-cant: a small sheet over the other app. It never closes by itself (review-product #16). */
@Composable
fun ShareScreen(state: ShareState, onAction: (ShareAction) -> Unit) {
    val keeping = state.phase == SharePhase.KEEPING
    // Back and taps outside are ignored only while bytes are being copied (1–2 s); after that they mean Done.
    BackHandler(enabled = keeping) {}
    Box(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null) { onAction(ShareAction.OnDone) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, indication = null) {}, // taps on the sheet stay here
        ) {
            Column(Modifier.navigationBarsPadding().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mark(keeping)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                        Text(state.title().asString(), style = MaterialTheme.typography.titleLarge)
                        state.body()?.let {
                            Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.extras().forEach {
                            Text(it.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (!keeping) {
                    Spacer(Modifier.padding(top = 16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        state.secondaryButton()?.let { (label, action) ->
                            FilledTonalButton(
                                onClick = { onAction(action) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) { Text(stringResource(label)) }
                        }
                        Button(
                            onClick = { onAction(ShareAction.OnDone) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.share_done)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Mark(busy: Boolean) {
    Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(40.dp))
        }
    }
}

// ---- State → copy (pure mapping, no logic beyond picking strings) ----

private fun ShareState.title(): UiText = when (phase) {
    SharePhase.KEEPING -> UiText.Plural(R.plurals.share_keeping, total.coerceAtLeast(1), total)
    SharePhase.KEPT -> UiText.Plural(R.plurals.share_kept, kept, kept)
    SharePhase.ALREADY_KEPT -> UiText.Res(R.string.share_already)
    SharePhase.KEPT_AS_NOTE -> UiText.Res(R.string.share_note)
    SharePhase.FAILED -> UiText.Res(R.string.share_failed)
}

private fun ShareState.body(): UiText? = when (phase) {
    SharePhase.KEEPING -> if (total > 1) UiText.Res(R.string.share_progress, done, total) else null
    SharePhase.KEPT -> when (route) {
        ReadRoute.DEVICE -> UiText.Res(R.string.share_route_device)
        ReadRoute.CLOUD -> UiText.Res(if (readsOut) R.string.share_route_reads_out else R.string.share_route_cloud)
        ReadRoute.OFFLINE -> UiText.Res(R.string.share_route_offline)
        ReadRoute.NEEDS_CONSENT -> UiText.Res(R.string.share_route_consent)
        null -> null
    }
    SharePhase.ALREADY_KEPT -> UiText.Res(R.string.share_already_body)
    SharePhase.KEPT_AS_NOTE ->
        UiText.Res(if (noteReason == NoteReason.TOO_BIG) R.string.share_note_big else R.string.share_note_text)
    SharePhase.FAILED -> UiText.Res(R.string.share_failed_body)
}

private fun ShareState.extras(): List<UiText> = buildList {
    if (phase == SharePhase.KEEPING || phase == SharePhase.ALREADY_KEPT) return@buildList
    if (duplicates > 0) add(UiText.Plural(R.plurals.share_also_dupes, duplicates, duplicates))
    if (unreadable > 0 && phase != SharePhase.FAILED) add(UiText.Plural(R.plurals.share_also_unreadable, unreadable, unreadable))
    if (overCap > 0) add(UiText.Res(R.string.share_also_cap, ShareViewModel.MAX_PER_SHARE, overCap))
}

private fun ShareState.secondaryButton(): Pair<Int, ShareAction>? = when (phase) {
    SharePhase.KEPT -> R.string.share_open_kept to ShareAction.OnOpenKept
    SharePhase.ALREADY_KEPT -> R.string.share_open_it to ShareAction.OnOpenSave
    SharePhase.KEPT_AS_NOTE -> R.string.share_add_title to ShareAction.OnOpenSave
    SharePhase.KEEPING, SharePhase.FAILED -> null
}

@Preview
@Composable
private fun ShareKeptPreview() {
    KeptTheme { ShareScreen(ShareState(phase = SharePhase.KEPT, kept = 3, duplicates = 1, route = ReadRoute.CLOUD), {}) }
}

@Preview
@Composable
private fun ShareKeepingPreview() {
    KeptTheme { ShareScreen(ShareState(phase = SharePhase.KEEPING, done = 7, total = 30), {}) }
}
