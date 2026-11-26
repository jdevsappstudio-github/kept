package com.jdevsappstudio.kept.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.core.presentation.SnackbarAction
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.Shelf
import com.jdevsappstudio.kept.core.model.TypeChip
import com.jdevsappstudio.kept.core.model.CollectionSuggestion
import com.jdevsappstudio.kept.core.model.SaveCollection
import com.jdevsappstudio.kept.data.examples.ExampleSaves
import com.jdevsappstudio.kept.data.library.CollectionSuggestions
import com.jdevsappstudio.kept.data.library.CollectionsLocalDataSource
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import com.jdevsappstudio.kept.data.library.ShelfOrder
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.data.queue.ReadRoute
import com.jdevsappstudio.kept.data.settings.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import com.jdevsappstudio.kept.feature.onboarding.PhoneLine
import kotlinx.coroutines.flow.scan
import com.jdevsappstudio.kept.data.quota.MeterView
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val counts: QueueCounts = QueueCounts.EMPTY,
    val route: ReadRoute? = null,
    val pause: QueuePause = QueuePause.NONE,
    val cloudHelp: Boolean = false,
    /** Type chips; 'All' is drawn in front of them by the screen. */
    val chips: List<TypeChip> = emptyList(),
    /** "Just kept": the newest save, read or not. */
    val hero: Save? = null,
    /** Up to 4 shown shelves, in display order. */
    val shelves: List<Shelf> = emptyList(),
    /** "See all 6" counts every shelf, hidden ones too. */
    val allShelves: Int = 0,
    /** H55: every collection, newest made first ('New collection' tile drawn after them). */
    val collections: List<SaveCollection> = emptyList(),
    /** "4 saves mention Pune" card, at most one (H55). */
    val suggestion: CollectionSuggestion? = null,
    /** Subtitle "12 new this week" (saves kept in the last 7 days). */
    val newThisWeek: Int = 0,
    /** Subtitle tail: 'all read on this phone' / 'read with Gemini in the cloud'. */
    val readsWith: ReadsWith = ReadsWith.UNKNOWN,
    /** Status lines under the header, only those that apply ([HomeLines]). */
    val lines: List<HomeLine> = emptyList(),
    /** False until the first emission from Room, so the empty state doesn't flash on open. */
    val loaded: Boolean = false,
    /** k-ob-ready instead of k-empty: set until the first save has shown (H70). */
    val ready: HomeReady? = null,
    /** H71: live 'Try 3 examples' saves; Home shows '3 examples · Clear' while there are any. */
    val examples: List<Long> = emptyList(),
) {
    /** Header "84 saves" = the sum of the chips (k-home note). */
    val total: Int get() = chips.sumOf { it.count }

    /** k-empty: nothing kept at all (a queued save already shows as the hero). */
    val isEmpty: Boolean get() = loaded && hero == null

    /** k-home-early: saves, but no kind has 3 yet, so the shelves block is one hint. */
    val isEarly: Boolean get() = hero != null && allShelves == 0
}

/** Taps that only open another screen are handled by HomeRoot; the ViewModel ignores them. */
sealed interface HomeAction {
    data class SetCloudHelp(val on: Boolean) : HomeAction
    data object OnAllClick : HomeAction
    data class OnTypeClick(val type: SaveType) : HomeAction
    data class OnSaveClick(val id: Long) : HomeAction
    data class OnShelfClick(val kind: String) : HomeAction
    data object OnSeeAllShelvesClick : HomeAction
    data class OnCollectionClick(val id: Long) : HomeAction
    data object OnNewCollectionClick : HomeAction
    /** Make it: collection made with those saves, snackbar 'Made Pune · 4 saves · Undo', then k-collection. */
    data object OnMakeSuggestion : HomeAction
    /** Not now: that tag is never suggested again. */
    data object OnDismissSuggestion : HomeAction
    data object OnAddClick : HomeAction
    data object OnSettingsClick : HomeAction
    /** '2 need a look' line → k-failed. */
    data object OnNeedLookClick : HomeAction
    /** k-empty Pick screenshots: opens the photo picker straight away. */
    data object OnPickClick : HomeAction
    /** k-ob-ready / k-empty 'Try 3 examples' (H71). */
    data object OnTryExamples : HomeAction
    /** '3 examples · Clear': soft delete, snackbar Undo. */
    data object OnClearExamples : HomeAction
    /** k-ob-ready's phone-AI line → k-model or k-unsupported. */
    data class OnPhoneLineClick(val target: PhoneLine.Target) : HomeAction
    /** Debug panel only: checks delete + the app-wide Undo snackbar on a real library. */
    data object DebugDeleteHero : HomeAction
}

sealed interface HomeEvent {
    data class OpenCollection(val id: Long) : HomeEvent
}

/** The queue's live state, as home needs it (ReadQueue in the app, a fake in tests). */
data class QueueView(val counts: QueueCounts, val route: ReadRoute?, val pause: QueuePause)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    queue: Flow<QueueView>,
    private val settings: SettingsStore,
    private val library: LibraryLocalDataSource,
    private val collections: CollectionsLocalDataSource,
    private val snackbars: Snackbars,
    /** Onboarding B1 picks (settings in the app). */
    preferredTypes: Flow<List<SaveType>> = flowOf(emptyList()),
    /** k-ob-ready's content while no save has shown yet ([homeReady] in the app). */
    ready: Flow<HomeReady?> = flowOf(null),
    /** Today's cloud reads meter ('3 of 100 cloud reads left today'); null = never shown. */
    reads: Flow<MeterView?> = flowOf(null),
    private val examples: ExampleSaves = ExampleSaves.NONE,
    clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val status: Flow<Status> = combine(
        queue.scanBatch(),
        settings.cloudHelp,
        reads,
    ) { (q, batch), cloudHelp, meter ->
        Status(
            q = q, cloudHelp = cloudHelp,
            readsWith = HomeLines.readsWith(q.route, cloudHelp),
            lines = HomeLines.lines(q.counts, batch, q.route, q.pause, cloudHelp, meter),
        )
    }

    private val weekAgo = clock() - WEEK_MS

    private val organised: Flow<Organised> = combine(
        library.observeShelves(),
        collections.observeCollections(),
        collections.observeSuggestibleTags(),
        settings.dismissedSuggestions,
    ) { shelves, all, tags, dismissed ->
        Organised(shelves, all, CollectionSuggestions.pick(tags, shelves, all, dismissed))
    }

    val state: StateFlow<HomeState> = combine(
        combine(status, ready, examples.observeIds()) { s, r, e -> Triple(s, r, e) },
        preferredTypes.flatMapLatest { library.observeTypeChips(it) },
        library.observeNewest(),
        organised,
        library.observeCountSince(weekAgo),
    ) { (st, rdy, ex), chips, hero, o, week ->
        HomeState(
            counts = st.q.counts, route = st.q.route, pause = st.q.pause, cloudHelp = st.cloudHelp,
            chips = chips, hero = hero, shelves = ShelfOrder.onHome(o.shelves), allShelves = o.shelves.size,
            collections = o.collections, suggestion = o.suggestion,
            newThisWeek = week, readsWith = st.readsWith, lines = st.lines, loaded = true, ready = rdy,
            examples = ex,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var making = false

    init {
        // Home open: saves deleted over a minute ago go for good (start-up does the same).
        viewModelScope.launch { library.purgeStale() }
        // H70: once a save has shown, an empty Home is k-empty (after Delete everything), not k-ob-ready.
        viewModelScope.launch {
            library.observeNewest().filterNotNull().first()
            if (!settings.firstSaveSeen.first()) settings.markFirstSaveSeen()
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.SetCloudHelp -> viewModelScope.launch { settings.setCloudHelp(action.on) }
            HomeAction.DebugDeleteHero -> {
                val id = state.value.hero?.id ?: return
                viewModelScope.launch {
                    library.delete(listOf(id))
                    snackbars.show(
                        UiText.Plural(R.plurals.snack_deleted, 1, 1),
                        SnackbarAction(UiText.Res(R.string.snack_undo)) { library.undoDelete(listOf(id)) },
                    )
                }
            }
            HomeAction.OnTryExamples -> viewModelScope.launch { examples.add() }
            HomeAction.OnClearExamples -> {
                val ids = state.value.examples.takeIf { it.isNotEmpty() } ?: return
                viewModelScope.launch {
                    library.delete(ids)
                    snackbars.show(
                        UiText.Res(R.string.snack_examples_cleared),
                        SnackbarAction(UiText.Res(R.string.snack_undo)) { library.undoDelete(ids) },
                    )
                }
            }
            HomeAction.OnMakeSuggestion -> makeSuggestion()
            HomeAction.OnDismissSuggestion -> {
                val tag = state.value.suggestion?.tag ?: return
                viewModelScope.launch { settings.dismissSuggestion(tag) }
            }
            else -> Unit
        }
    }

    private fun makeSuggestion() {
        val tag = state.value.suggestion?.tag ?: return
        if (making) return
        making = true
        viewModelScope.launch {
            val made = collections.makeFromTag(tag)
            making = false
            if (made == null) return@launch
            snackbars.show(
                UiText.Plural(R.plurals.snack_made_collection, made.saves, made.name, made.saves),
                SnackbarAction(UiText.Res(R.string.snack_undo)) { collections.delete(made.id) },
            )
            _events.send(HomeEvent.OpenCollection(made.id))
        }
    }
}

private data class Status(val q: QueueView, val cloudHelp: Boolean, val readsWith: ReadsWith, val lines: List<HomeLine>)

/** Pairs each queue state with where its batch started ([HomeLines.batchStart]). */
private fun Flow<QueueView>.scanBatch(): Flow<Pair<QueueView, Int?>> =
    scan<QueueView, Pair<QueueView, Int?>?>(null) { prev, q -> q to HomeLines.batchStart(prev?.second, q.counts) }
        .filterNotNull()

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

private data class Organised(val shelves: List<Shelf>, val collections: List<SaveCollection>, val suggestion: CollectionSuggestion?)
