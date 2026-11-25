package com.jdevsappstudio.kept.feature.home

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.jdevsappstudio.kept.data.library.CollectionsLocalDataSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jdevsappstudio.kept.BuildConfig
import com.jdevsappstudio.kept.core.designsystem.Kept
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.data.queue.ReadRoute
import com.jdevsappstudio.kept.feature.search.SearchDebug
import com.jdevsappstudio.kept.feature.ask.AskDebug
import com.jdevsappstudio.kept.feature.quota.QuotaDebug
import com.jdevsappstudio.kept.feature.add.AddDebug
import com.jdevsappstudio.kept.feature.spike.ReaderSpikeRoot

@Composable
fun HomeRoot(viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, viewModel::onAction)
}

@Composable
fun HomeScreen(state: HomeState, onAction: (HomeAction) -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Kept", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            Text("Your saves, read and searchable.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("ON THIS PHONE", style = MaterialTheme.typography.labelSmall, color = Kept.colors.device)
            QueueStrip(state)
            if (BuildConfig.DEBUG) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cloud help (debug)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = state.cloudHelp, onCheckedChange = { onAction(HomeAction.SetCloudHelp(it)) })
                }
                AddDebug()
                QuotaDebug()
                LibraryDebug(state, onAction)
                SearchDebug()
                AskDebug()
                com.jdevsappstudio.kept.feature.library.detail.FillDebug()
                ReaderSpikeRoot()
            }
        }
    }
}

/** Home queue strip ("Reading 3 of 12 · On this phone"); full design comes with the home grid. */
@Composable
private fun QueueStrip(state: HomeState) {
    val c = state.counts
    val text = when {
        c.pending == 0 && c.needsLook == 0 -> "${c.read} read"
        c.pending == 0 -> "${c.read} read · ${c.needsLook} need a look"
        else -> when (state.pause) {
            QueuePause.NEEDS_CONSENT -> "${c.pending} waiting · turn on cloud help or tag by hand"
            QueuePause.OFFLINE -> "${c.pending} waiting · read when you're back online"
            QueuePause.IN_BACKGROUND -> "${c.pending} waiting"
            QueuePause.CLOUD_CAP -> "${c.pending} waiting · today's cloud reads are used up"
            QueuePause.NOT_SET_UP -> "${c.pending} waiting · reading isn't set up"
            QueuePause.NONE -> "Reading ${c.read + 1} of ${c.read + c.pending}" + when (state.route) {
                ReadRoute.DEVICE -> " · On this phone"
                ReadRoute.CLOUD -> " · Gemini in the cloud"
                else -> ""
            }
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Debug only (H51): the library as HomeViewModel sees it, until the real home lands on 27 Nov. */
@Composable
private fun LibraryDebug(state: HomeState, onAction: (HomeAction) -> Unit) {
    val style = MaterialTheme.typography.bodySmall
    Text("Library (debug): ${state.total} saves", style = MaterialTheme.typography.labelLarge)
    Text(state.chips.joinToString(" · ") { "${it.type.label} ${it.count}" }.ifEmpty { "no chips" }, style = style)
    Text("Just kept: " + (state.hero?.let { "${it.title.ifEmpty { "(untitled)" }} · ${it.status}" } ?: "nothing"), style = style)
    Text(
        "Shelves: " + state.shelves.joinToString(" · ") { "${it.title} ${if (it.countsItems) it.entries else it.saves}" }
            .ifEmpty { "none" } + " (see all ${state.allShelves})",
        style = style,
    )
    TextButton(onClick = { onAction(HomeAction.DebugDeleteHero) }, enabled = state.hero != null) { Text("Delete newest (Undo)") }
    CollectionsDebug(state, onAction)
}

/** Debug only (H51 step 3): collections + the suggestion card as home sees them; buttons hit the real data source. */
@Composable
private fun CollectionsDebug(
    state: HomeState,
    onAction: (HomeAction) -> Unit,
    collections: CollectionsLocalDataSource = koinInject(),
) {
    val style = MaterialTheme.typography.bodySmall
    val scope = rememberCoroutineScope()
    val first = state.collections.firstOrNull()
    Text(
        "Collections: " + state.collections.joinToString(" · ") { "${it.name} ${it.saves}" }.ifEmpty { "none" },
        style = style,
    )
    state.suggestion?.let { s ->
        Text("Suggestion: ${s.saves} saves mention ${s.name}", style = style)
        Row {
            TextButton(onClick = { onAction(HomeAction.OnMakeSuggestion) }) { Text("Make it") }
            TextButton(onClick = { onAction(HomeAction.OnDismissSuggestion) }) { Text("Not now") }
        }
    } ?: Text("Suggestion: none", style = style)
    Row {
        TextButton(onClick = { scope.launch { collections.create("Test ${state.collections.size + 1}") } }) { Text("New") }
        TextButton(
            onClick = { scope.launch { collections.addSaves(first!!.id, listOfNotNull(state.hero?.id)) } },
            enabled = first != null && state.hero != null,
        ) { Text("Add newest") }
        TextButton(
            onClick = { scope.launch { collections.removeSaves(first!!.id, listOfNotNull(state.hero?.id)) } },
            enabled = first != null && state.hero != null,
        ) { Text("Remove") }
        TextButton(onClick = { scope.launch { collections.delete(first!!.id) } }, enabled = first != null) { Text("Delete") }
    }
}
