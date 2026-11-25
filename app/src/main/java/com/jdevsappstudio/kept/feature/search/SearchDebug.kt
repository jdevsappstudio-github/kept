package com.jdevsappstudio.kept.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.paging.compose.collectAsLazyPagingItems
import com.jdevsappstudio.kept.core.model.SearchHit
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import org.koin.androidx.compose.koinViewModel

/** Debug only (H51): search as SearchViewModel sees it, until the real k-search lands on 27 Nov. */
@Composable
fun SearchDebug(viewModel: SearchViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hits = viewModel.hits.collectAsLazyPagingItems()
    var last by remember { mutableStateOf("") }
    ObserveAsEvents(viewModel.events) { last = it.toString() }
    val style = MaterialTheme.typography.bodySmall
    val on = viewModel::onAction

    OutlinedTextField(
        value = state.query,
        onValueChange = { on(SearchAction.OnQueryChange(it)) },
        label = { Text("Search (debug)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { on(SearchAction.OnSubmit) }),
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.isEmptyQuery) {
        Text("Recent: " + state.recent.joinToString(" · ").ifEmpty { "none" }, style = style)
        Text("Tags: " + state.tags.take(6).joinToString(" · ") { it.tag }, style = style)
        return
    }
    Text(
        "${if (state.settled) "" else "(typing) "}${state.count} saves · " +
            (listOf("All") + state.typeChips.map { "${it.type.label} ${it.count}" }).joinToString(" · ") +
            (state.type?.let { " [on: ${it.label}]" } ?: ""),
        style = style,
        modifier = Modifier.clickable { on(SearchAction.OnTypeClick(state.typeChips.firstOrNull()?.type)) },
    )
    state.shelf?.let { m ->
        Text("Shelf: ${m.shelf.title} · ${m.entries.joinToString { it.name }}", style = style)
    }
    if (state.showStatusLine) Text("${state.stillReading} still reading, ${state.notRead} not read", style = style)
    if (state.nothingFound) Text("Nothing found for “${state.query.trim()}”", style = style)
    for (i in 0 until minOf(hits.itemCount, 4)) {
        val hit = hits[i] ?: continue
        Text(
            "${hit.save.title} — ${hit.snippet.replace(SearchHit.HIT_START, '[').replace(SearchHit.HIT_END, ']').replace('\n', ' ')}",
            style = style,
            maxLines = 2,
            modifier = Modifier.clickable { on(SearchAction.OnHitClick(hit.save.id)) },
        )
    }
    if (last.isNotEmpty()) Text("Event: $last", style = style)
}
