package com.jdevsappstudio.kept.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.jdevsappstudio.kept.core.model.ListLayout
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.core.model.SaveFilter
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.SearchHit
import com.jdevsappstudio.kept.core.model.Shelf
import com.jdevsappstudio.kept.core.model.ShelfEntry
import com.jdevsappstudio.kept.core.model.TagCount
import com.jdevsappstudio.kept.core.model.TypeChip
import androidx.paging.map
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.library.CollectionsLocalDataSource
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import com.jdevsappstudio.kept.data.search.SearchLocalDataSource
import com.jdevsappstudio.kept.data.search.SearchQuery
import com.jdevsappstudio.kept.data.settings.SettingsStore
import com.jdevsappstudio.kept.feature.library.TAG_CHIPS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** k-search-tag: the shelf the query names, with its first few rows ("See all 19"). */
data class ShelfMatch(val shelf: Shelf, val entries: List<ShelfEntry>)

/**
 * Pick mode (Add saves on k-collection, H55): taps tick instead of open; an empty query lists every save, newest first;
 * saves already in the collection show ticked and locked ('Already in Goa trip').
 */
data class PickState(val collectionName: String, val already: Set<Long>, val picked: Set<Long>) {
    fun isTicked(id: Long): Boolean = id in already || id in picked
    fun isLocked(id: Long): Boolean = id in already
    val canAdd: Boolean get() = picked.isNotEmpty()
}

/** k-search, k-search-tag and k-search-none. */
data class SearchState(
    val query: String = "",
    /** On type chip; null = All. */
    val type: SaveType? = null,
    val typeChips: List<TypeChip> = emptyList(),
    /** False while the person is still typing (debounce): don't flash "Nothing found" then. */
    val settled: Boolean = false,
    val shelf: ShelfMatch? = null,
    /** Queued or being read: not searchable yet (status line). */
    val stillReading: Int = 0,
    /** Needs a look: never read, so not searchable. */
    val notRead: Int = 0,
    val recent: List<String> = emptyList(),
    val tags: List<TagCount> = emptyList(),
    val layout: ListLayout = ListLayout.ROWS,
    val pick: PickState? = null,
) {
    /** Pick mode lists every save before anything is typed. */
    val showsList: Boolean get() = pick != null || !isEmptyQuery
    val isEmptyQuery: Boolean get() = SearchQuery.parse(query).isBlank
    /** "6 saves" over the list: the on chip's count, or every hit. */
    val count: Int get() = type?.let { t -> typeChips.firstOrNull { it.type == t }?.count ?: 0 } ?: typeChips.sumOf { it.count }
    val nothingFound: Boolean get() = !isEmptyQuery && settled && count == 0 && shelf == null
    val showStatusLine: Boolean get() = !isEmptyQuery && stillReading + notRead > 0
    val canAsk: Boolean get() = pick == null && !isEmptyQuery && settled && !nothingFound
}

sealed interface SearchAction {
    data class OnQueryChange(val text: String) : SearchAction
    data object OnClearQuery : SearchAction
    /** Search key on the keyboard: keeps the query in recent searches (H54). */
    data object OnSubmit : SearchAction
    /** Tapping the type that is on turns it off. Null = the All chip. */
    data class OnTypeClick(val type: SaveType?) : SearchAction
    data class OnHitClick(val id: Long) : SearchAction
    data object OnShelfClick : SearchAction
    data class OnEntryClick(val entry: ShelfEntry) : SearchAction
    data class OnRecentClick(val text: String) : SearchAction
    data class OnTagClick(val tag: String) : SearchAction
    data object OnStatusSeeClick : SearchAction
    data object OnAskClick : SearchAction
    data object OnLayoutToggle : SearchAction
    /** Pick mode: Add -> the ticked saves go in, snackbar 'Added 3', back to the collection. */
    data object OnPickAdd : SearchAction
}

sealed interface SearchEvent {
    data class OpenSave(val id: Long) : SearchEvent
    data class OpenShelf(val kind: String) : SearchEvent
    data object OpenNeedsLook : SearchEvent
    /** k-ask with the question prefilled (consent first is Ask's job, H29). */
    data class OpenAsk(val question: String) : SearchEvent
    /** Pick mode is over (added, or the collection is gone): back to k-collection. */
    data object PickDone : SearchEvent
}

private data class Results(val query: SearchQuery, val chips: List<TypeChip>, val shelf: ShelfMatch?, val type: SaveType?)

private data class Extras(val queue: QueueCounts, val recent: List<String>, val tags: List<TagCount>, val layout: ListLayout)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val savedStateHandle: SavedStateHandle,
    queueCounts: Flow<QueueCounts>,
    private val search: SearchLocalDataSource,
    private val library: LibraryLocalDataSource,
    private val settings: SettingsStore,
    private val collections: CollectionsLocalDataSource,
    private val snackbars: Snackbars,
) : ViewModel() {

    /** Set when opened from Add saves on a collection. */
    private val pickInto: Long? = savedStateHandle.get<Long>(ARG_PICK_INTO)
    private val picked = MutableStateFlow(savedStateHandle.get<LongArray>(KEY_PICKED)?.toSet().orEmpty())
    private var adding = false

    private val _events = Channel<SearchEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val text = MutableStateFlow(savedStateHandle.get<String>(ARG_QUERY).orEmpty())
    private val typePick = MutableStateFlow(savedStateHandle.get<String>(ARG_TYPE)?.let { SaveType.fromWire(it) })

    /** 250 ms after the last key (BUILD-SPEC §4); clearing the field is instant. */
    private val query: Flow<SearchQuery> = text
        .map { SearchQuery.parse(it) }
        .distinctUntilChanged()
        .debounce { if (it.isBlank) 0L else DEBOUNCE_MS }

    val hits: Flow<PagingData<SearchHit>> = combine(query, typePick, ::Pair)
        .flatMapLatest { (q, type) ->
            if (browsing(q)) library.pagedSaves(SaveFilter(type = type)).map { page -> page.map { SearchHit(it, "") } }
            else search.pagedHits(q, type)
        }
        .cachedIn(viewModelScope)

    /** Chips tagged with the query they belong to, so a stale count never passes for the new query's. */
    private val chips: Flow<Pair<SearchQuery, List<TypeChip>>> =
        query.flatMapLatest { q -> (if (browsing(q)) library.observeTypeChips() else search.observeTypeChips(q)).map { q to it } }

    private val pick: Flow<PickState?> = if (pickInto == null) flowOf(null) else combine(
        collections.observeCollection(pickInto), collections.observeSaveIds(pickInto), picked,
    ) { c, already, ticked ->
        if (c == null) {
            _events.trySend(SearchEvent.PickDone)
            null
        } else PickState(c.name, already, ticked - already)
    }

    private val shelf: Flow<ShelfMatch?> = combine(query, library.observeShelves()) { q, shelves ->
        if (pickInto != null) null else q.matchShelf(shelves)
    }
        .distinctUntilChanged()
        .flatMapLatest { s ->
            if (s == null) flowOf(null) else library.observeShelfEntries(s.kind).map { ShelfMatch(s, it.take(SHELF_ROWS)) }
        }

    private val results: Flow<Results> = combine(chips, shelf, typePick) { (q, c), s, t -> Results(q, c, s, t) }

    private val extras: Flow<Extras> = combine(
        queueCounts,
        settings.recentSearches,
        library.observeTags(SaveFilter.ALL, TAG_CHIPS),
        settings.listLayout,
        ::Extras,
    )

    val state: StateFlow<SearchState> = combine(text, results, extras, pick) { t, r, e, p ->
        val settled = SearchQuery.parse(t) == r.query
        SearchState(
            query = t,
            type = r.type,
            typeChips = r.chips.withPicked(r.type),
            settled = settled,
            shelf = r.shelf.takeIf { settled },
            stillReading = e.queue.pending,
            notRead = e.queue.needsLook,
            recent = e.recent,
            tags = e.tags,
            layout = e.layout,
            pick = p,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState(query = text.value, type = typePick.value))


    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.OnQueryChange -> setText(action.text)
            SearchAction.OnClearQuery -> setText("")
            SearchAction.OnSubmit -> remember()
            is SearchAction.OnTypeClick -> setType(action.type?.takeUnless { it == typePick.value })
            is SearchAction.OnHitClick -> if (pickInto != null) togglePick(action.id) else {
                remember()
                send(SearchEvent.OpenSave(action.id))
            }
            SearchAction.OnPickAdd -> addPicked()
            SearchAction.OnShelfClick -> state.value.shelf?.let {
                remember()
                send(SearchEvent.OpenShelf(it.shelf.kind))
            }
            is SearchAction.OnEntryClick -> {
                remember()
                send(SearchEvent.OpenSave(action.entry.saveId))
            }
            is SearchAction.OnRecentClick -> setText(action.text)
            is SearchAction.OnTagClick -> setText(action.tag)
            SearchAction.OnStatusSeeClick -> send(SearchEvent.OpenNeedsLook)
            SearchAction.OnAskClick -> if (state.value.canAsk) {
                remember()
                send(SearchEvent.OpenAsk(text.value.trim()))
            }
            SearchAction.OnLayoutToggle -> viewModelScope.launch {
                settings.setListLayout(if (state.value.layout == ListLayout.ROWS) ListLayout.GRID else ListLayout.ROWS)
            }
        }
    }

    private fun togglePick(id: Long) {
        val p = state.value.pick ?: return
        if (p.isLocked(id)) return
        picked.value = if (id in picked.value) picked.value - id else picked.value + id
        savedStateHandle[KEY_PICKED] = picked.value.toLongArray()
    }

    private fun addPicked() {
        val into = pickInto ?: return
        val p = state.value.pick ?: return
        if (!p.canAdd || adding) return
        adding = true
        viewModelScope.launch {
            val n = collections.addSaves(into, p.picked)
            snackbars.show(
                if (n > 0) UiText.Plural(R.plurals.snack_added, n, n) else UiText.Res(R.string.snack_nothing_added, p.collectionName),
            )
            _events.send(SearchEvent.PickDone)
        }
    }

    /** Pick mode with nothing typed: every save, newest first (H55). */
    private fun browsing(q: SearchQuery): Boolean = pickInto != null && q.isBlank

    private fun setText(value: String) {
        savedStateHandle[ARG_QUERY] = value
        text.value = value
    }

    private fun setType(type: SaveType?) {
        savedStateHandle[ARG_TYPE] = type?.wire
        typePick.value = type
    }

    /** H54: a search counts once the person acts on it, never while half typed. */
    private fun remember() {
        val now = text.value
        if (pickInto != null || SearchQuery.parse(now).isBlank) return
        viewModelScope.launch { settings.addRecentSearch(now) }
    }

    private fun send(event: SearchEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    /** The on chip stays visible even with 0 hits, so it can be turned off. */
    private fun List<TypeChip>.withPicked(type: SaveType?): List<TypeChip> =
        if (type == null || any { it.type == type }) this else this + TypeChip(type, 0)

    companion object {
        /** Text to start with (a tag chip elsewhere), and the query kept across process death. */
        const val ARG_QUERY = "q"
        /** [SaveType.wire]: k-list's search icon searches within its type. */
        const val ARG_TYPE = "type"
        const val DEBOUNCE_MS = 250L
        const val SHELF_ROWS = 3
        /** Collection id: opens search in pick mode. */
        const val ARG_PICK_INTO = "pickInto"
        private const val KEY_PICKED = "picked"
    }
}
