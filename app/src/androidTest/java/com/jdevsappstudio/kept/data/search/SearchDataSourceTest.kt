package com.jdevsappstudio.kept.data.search

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.jdevsappstudio.kept.core.model.NewSave
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.SearchHit
import com.jdevsappstudio.kept.core.model.TypeChip
import com.jdevsappstudio.kept.data.ai.CardDraft
import com.jdevsappstudio.kept.data.db.KeptDatabase
import com.jdevsappstudio.kept.data.db.SearchHitRow
import com.jdevsappstudio.kept.data.library.RoomLibraryDataSource
import com.jdevsappstudio.kept.data.saves.RoomSaveDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The search SQL on a real SQLite: prefix + plural terms, unicode61 folding, snippet, order, chips, soft delete. */
@RunWith(AndroidJUnit4::class)
class SearchDataSourceTest {
    private lateinit var db: KeptDatabase
    private lateinit var saves: RoomSaveDataSource
    private lateinit var search: RoomSearchDataSource
    private var now = 1_000_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, KeptDatabase::class.java).build()
        saves = RoomSaveDataSource(db.saveDao(), Dispatchers.IO) { now }
        search = RoomSearchDataSource(db.searchDao())
    }

    @After fun tearDown() = db.close()

    private suspend fun save(title: String, summary: String = "", type: String = "place", kind: String = "", items: List<String> = emptyList()): Long {
        now += 10
        val id = saves.add(listOf(NewSave(SaveSource.SHARE_IMAGE, null, null, null, "/x/$title.jpg"))).first()
        saves.saveCard(id, CardDraft(title, type, kind, emptyList(), items, "", "", "", summary), ReadBy.CLOUD, "m", now)
        return id
    }

    private suspend fun hits(text: String, type: SaveType? = null): List<SearchHitRow> {
        val q = SearchQuery.parse(text)
        val source = db.searchDao().paging(q.match, q.titleWord, type?.wire, "[", "]")
        val page = source.load(PagingSource.LoadParams.Refresh(null, 40, false)) as PagingSource.LoadResult.Page
        return page.data
    }

    private suspend fun titles(text: String, type: SaveType? = null) = hits(text, type).map { it.save.title }

    @Test fun prefixAndPluralTerms() = runTest {
        save("Sunbeam Café", "banana pancakes, North Goa")
        save("Five thriller movies", type = "movie_show", items = listOf("Zodiac"))
        save("Party stories", type = "read")
        assertThat(titles("pan")).containsExactly("Sunbeam Café")
        assertThat(titles("movie")).containsExactly("Five thriller movies")
        assertThat(titles("story")).containsExactly("Party stories")
        assertThat(titles("stories")).containsExactly("Party stories")
        assertThat(titles("zodi")).containsExactly("Five thriller movies")
        assertThat(titles("goa pancakes")).containsExactly("Sunbeam Café")
        assertThat(titles("goa zodiac")).isEmpty()
    }

    @Test fun accentsAndCaseFold() = runTest {
        save("Crème brûlée at CAFÉ Ålesund")
        assertThat(titles("creme brulee")).containsExactly("Crème brûlée at CAFÉ Ålesund")
        assertThat(titles("café")).containsExactly("Crème brûlée at CAFÉ Ålesund")
        assertThat(titles("alesund")).containsExactly("Crème brûlée at CAFÉ Ålesund")
    }

    /** unicode61 splits at Indic vowel signs in the index and the query alike, so whole words still match. */
    @Test fun hindiWordsMatch() = runTest {
        save("पनीर टिक्का रेसिपी")
        assertThat(titles("पनीर")).containsExactly("पनीर टिक्का रेसिपी")
        assertThat(titles("टिक्का")).containsExactly("पनीर टिक्का रेसिपी")
    }

    @Test fun syntaxCantBreakTheQuery() = runTest {
        save("Goa fort")
        assertThat(titles("\"goa\" OR -fort* (")).isEmpty() // 'or' is a plain word here, and no save has it
        assertThat(titles("goa: fort!")).containsExactly("Goa fort")
    }

    @Test fun titleHitsFirstThenNewest() = runTest {
        save("Old Goa café list", "places")
        save("Chapora Fort", "sunset spot in goa")
        save("Prawn curry", "from a goa trip", type = "recipe")
        assertThat(titles("goa")).containsExactly("Old Goa café list", "Prawn curry", "Chapora Fort")
    }

    @Test fun snippetMarksTheMatch() = runTest {
        save("Sunbeam Café", "banana pancakes, North Goa")
        assertThat(hits("goa").single().snippet).contains("[Goa]")
        assertThat(SearchHit.HIT_START.code).isEqualTo(2)
    }

    @Test fun typeChipsAndTypeFilter() = runTest {
        save("Goa fort")
        save("Goa beach")
        save("Goa curry", type = "recipe")
        assertThat(search.observeTypeChips(SearchQuery.parse("goa")).first())
            .containsExactly(TypeChip(SaveType.PLACE, 2), TypeChip(SaveType.RECIPE, 1))
        assertThat(titles("goa", SaveType.RECIPE)).containsExactly("Goa curry")
    }

    @Test fun deletedSavesDontShow() = runTest {
        val gone = save("Goa fort")
        save("Goa beach")
        RoomLibraryDataSource(db, db.libraryDao(), saves) { now }.delete(listOf(gone))
        assertThat(titles("goa")).containsExactly("Goa beach")
        assertThat(search.observeTypeChips(SearchQuery.parse("goa")).first()).containsExactly(TypeChip(SaveType.PLACE, 1))
    }
}
