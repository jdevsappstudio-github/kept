package com.jdevsappstudio.kept.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.SaveFilter
import com.jdevsappstudio.kept.feature.library.next
import com.jdevsappstudio.kept.feature.library.resId
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.SearchHit
import com.jdevsappstudio.kept.core.model.ShelfEntry
import com.jdevsappstudio.kept.core.model.TypeChip
import com.jdevsappstudio.kept.data.search.SearchLocalDataSource
import com.jdevsappstudio.kept.data.search.SearchQuery
import com.jdevsappstudio.kept.feature.library.FakeLibrary
import com.jdevsappstudio.kept.feature.library.FakeCollections
import com.jdevsappstudio.kept.feature.library.FakeSettings
import com.jdevsappstudio.kept.feature.library.testSnackbars
import com.jdevsappstudio.kept.feature.library.MainDispatcherExtension
import com.jdevsappstudio.kept.feature.library.keepActive
import com.jdevsappstudio.kept.feature.library.shelf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/** Hits are never inspected here (Paging); the fake records what was asked and serves chips per query. */
class FakeSearch : SearchLocalDataSource {
    val hitQueries = mutableListOf<Pair<String, SaveType?>>()
    val chips = MutableStateFlow<Map<String, List<TypeChip>>>(emptyMap())

    override fun pagedHits(query: SearchQuery, type: SaveType?): Flow<PagingData<SearchHit>> {
        hitQueries += query.match to type
        return flowOf(PagingData.empty())
    }

    override fun observeTypeChips(query: SearchQuery): Flow<List<TypeChip>> = chips.map { it[query.match].orEmpty() }
}

@ExtendWith(MainDispatcherExtension::class)
class SearchViewModelTest {
    private val search = FakeSearch()
    private val library = FakeLibrary()
    private val settings = FakeSettings()
    private val collections = FakeCollections()
    private val snackbars = testSnackbars()
    private val queue = MutableStateFlow(QueueCounts.EMPTY)

    private fun TestScope.vm(args: Map<String, Any?> = emptyMap()) =
        SearchViewModel(SavedStateHandle(args), queue, search, library, settings, collections, snackbars).also {
            keepActive(it.hits)
            keepActive(it.state)
        }

    private fun TestScope.type(vm: SearchViewModel, text: String) {
        vm.onAction(SearchAction.OnQueryChange(text))
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        runCurrent()
    }

    @Test
    fun `fast typing runs one query, 250 ms after the last key`() = runTest {
        val vm = vm()
        for (t in listOf("g", "go", "goa")) {
            vm.onAction(SearchAction.OnQueryChange(t))
            advanceTimeBy(100)
        }
        assertThat(search.hitQueries.filter { it.first.isNotEmpty() }).isEmpty()
        assertThat(vm.state.value.settled).isFalse()

        advanceTimeBy(SearchViewModel.DEBOUNCE_MS)
        runCurrent()
        assertThat(search.hitQueries.filter { it.first.isNotEmpty() }).containsExactly("goa*" to null)
        assertThat(vm.state.value.settled).isTrue()
    }

    @Test
    fun `nothing found only once the query has settled`() = runTest {
        val vm = vm()
        vm.onAction(SearchAction.OnQueryChange("sarees"))
        assertThat(vm.state.value.nothingFound).isFalse()
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        runCurrent()
        assertThat(vm.state.value.nothingFound).isTrue()
        assertThat(vm.state.value.canAsk).isFalse()
    }

    @Test
    fun `chips count hits, the on chip narrows and stays at 0`() = runTest {
        search.chips.value = mapOf("goa*" to listOf(TypeChip(SaveType.PLACE, 4), TypeChip(SaveType.RECIPE, 1)))
        val vm = vm()
        type(vm, "goa")
        assertThat(vm.state.value.count).isEqualTo(5)

        vm.onAction(SearchAction.OnTypeClick(SaveType.PLACE))
        assertThat(search.hitQueries.last()).isEqualTo("goa*" to SaveType.PLACE)
        assertThat(vm.state.value.count).isEqualTo(4)

        type(vm, "goa fort")
        assertThat(vm.state.value.typeChips).containsExactly(TypeChip(SaveType.PLACE, 0))
        assertThat(vm.state.value.nothingFound).isTrue()

        vm.onAction(SearchAction.OnTypeClick(SaveType.PLACE))
        assertThat(vm.state.value.type).isNull()
        assertThat(search.hitQueries.last()).isEqualTo("goa* fort*" to null)
    }

    @Test
    fun `k-list's type argument starts the search inside that type`() = runTest {
        val vm = vm(mapOf(SearchViewModel.ARG_TYPE to SaveType.RECIPE.wire))
        type(vm, "paneer")
        assertThat(search.hitQueries.last()).isEqualTo("paneer*" to SaveType.RECIPE)
    }

    @Test
    fun `a search is remembered on tap or Enter, never while typing`() = runTest {
        search.chips.value = mapOf("goa*" to listOf(TypeChip(SaveType.PLACE, 1)))
        val vm = vm()
        type(vm, "go")
        type(vm, "goa")
        assertThat(settings.recentSearches.value).isEmpty()

        vm.events.test {
            vm.onAction(SearchAction.OnHitClick(7))
            assertThat(awaitItem()).isEqualTo(SearchEvent.OpenSave(7))
        }
        type(vm, "  Paneer   tikka ")
        vm.onAction(SearchAction.OnSubmit)
        type(vm, "GOA")
        vm.onAction(SearchAction.OnSubmit)
        assertThat(settings.recentSearches.value).containsExactly("GOA", "Paneer tikka")
    }

    @Test
    fun `recent and tag chips fill the field`() = runTest {
        val vm = vm()
        vm.onAction(SearchAction.OnTagClick("air-fryer"))
        assertThat(vm.state.value.query).isEqualTo("air-fryer")
        vm.onAction(SearchAction.OnClearQuery)
        assertThat(vm.state.value.isEmptyQuery).isTrue()
        vm.onAction(SearchAction.OnRecentClick("goa"))
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        runCurrent()
        assertThat(search.hitQueries.last()).isEqualTo("goa*" to null)
    }

    @Test
    fun `a kind word shows its shelf with the first 3 rows`() = runTest {
        library.shelves.value = listOf(shelf("movies"), shelf("shoes"))
        library.entries.value = mapOf(null to (1..5).map { ShelfEntry("m$it", "Movie $it", 1, it.toLong(), null) })
        val vm = vm()
        type(vm, "films to watch")
        val match = vm.state.value.shelf
        assertThat(match).isNotNull()
        assertThat(match!!.shelf.kind).isEqualTo("movies")
        assertThat(match.entries.map { it.name }).containsExactly("Movie 1", "Movie 2", "Movie 3")
        assertThat(vm.state.value.nothingFound).isFalse()

        vm.events.test {
            vm.onAction(SearchAction.OnShelfClick)
            assertThat(awaitItem()).isEqualTo(SearchEvent.OpenShelf("movies"))
            vm.onAction(SearchAction.OnEntryClick(match.entries[1]))
            assertThat(awaitItem()).isEqualTo(SearchEvent.OpenSave(2))
        }
        assertThat(settings.recentSearches.value).containsExactly("films to watch")
    }

    @Test
    fun `status line names saves that aren't searchable yet`() = runTest {
        queue.value = QueueCounts(queued = 1, reading = 1, needsLook = 1, read = 9)
        val vm = vm()
        assertThat(vm.state.value.showStatusLine).isFalse()
        type(vm, "goa")
        with(vm.state.value) {
            assertThat(showStatusLine).isTrue()
            assertThat(stillReading to notRead).isEqualTo(2 to 1)
        }
        vm.events.test {
            vm.onAction(SearchAction.OnStatusSeeClick)
            assertThat(awaitItem()).isEqualTo(SearchEvent.OpenNeedsLook)
        }
    }

    @Test
    fun `Ask about these sends the trimmed question`() = runTest {
        search.chips.value = mapOf("goa*" to listOf(TypeChip(SaveType.PLACE, 2)))
        val vm = vm()
        type(vm, " goa ")
        vm.events.test {
            vm.onAction(SearchAction.OnAskClick)
            assertThat(awaitItem()).isEqualTo(SearchEvent.OpenAsk("goa"))
        }
    }

    @Test
    fun `the query survives process death`() = runTest {
        val handle = SavedStateHandle()
        val first = SearchViewModel(handle, queue, search, library, settings, collections, snackbars)
        first.onAction(SearchAction.OnQueryChange("goa"))
        first.onAction(SearchAction.OnTypeClick(SaveType.PLACE))

        val vm = vm(mapOf(SearchViewModel.ARG_QUERY to handle.get<String>(SearchViewModel.ARG_QUERY), SearchViewModel.ARG_TYPE to handle.get<String>(SearchViewModel.ARG_TYPE)))
        assertThat(vm.state.value.query).isEqualTo("goa")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        runCurrent()
        assertThat(search.hitQueries.last()).isEqualTo("goa*" to SaveType.PLACE)
    }

    // ---- Pick mode (H55) ----

    @Test
    fun `pick mode - empty query lists every save, taps tick, already-in is locked, Add adds and goes back`() = runTest {
        collections.collection(5, "Goa trip", setOf(1))
        val vm = vm(mapOf(SearchViewModel.ARG_PICK_INTO to 5L))
        assertThat(vm.state.value.showsList).isTrue()
        assertThat(vm.state.value.pick?.collectionName).isEqualTo("Goa trip")
        assertThat(library.pagedFilters).containsExactly(SaveFilter.ALL)
        assertThat(search.hitQueries).isEmpty()

        vm.onAction(SearchAction.OnHitClick(1)) // already in: locked
        vm.onAction(SearchAction.OnHitClick(2))
        vm.onAction(SearchAction.OnHitClick(3))
        vm.onAction(SearchAction.OnHitClick(3))
        val pick = vm.state.value.pick!!
        assertThat(pick.picked).isEqualTo(setOf(2L))
        assertThat(pick.isTicked(1) && pick.isLocked(1)).isTrue()
        vm.events.test {
            vm.onAction(SearchAction.OnPickAdd)
            assertThat(awaitItem()).isEqualTo(SearchEvent.PickDone)
        }
        assertThat(collections.log).containsExactly("add 5 [2]")
        assertThat(snackbars.next().text.resId()).isEqualTo(R.plurals.snack_added)
    }

    @Test
    fun `pick mode - typing searches, but no shelf block, no Ask, nothing kept in recent`() = runTest {
        collections.collection(5, "Goa trip")
        library.shelves.value = listOf(shelf("movies"))
        search.chips.value = mapOf("movie*" to listOf(TypeChip(SaveType.MOVIE_SHOW, 3)))
        val vm = vm(mapOf(SearchViewModel.ARG_PICK_INTO to 5L))
        type(vm, "movies")
        assertThat(search.hitQueries.last().first).isEqualTo("movie*")
        assertThat(vm.state.value.shelf).isNull()
        assertThat(vm.state.value.canAsk).isFalse()
        vm.onAction(SearchAction.OnSubmit)
        assertThat(settings.recentSearches.value).isEmpty()
    }
}
