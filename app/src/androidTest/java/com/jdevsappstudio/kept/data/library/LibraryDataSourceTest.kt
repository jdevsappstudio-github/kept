package com.jdevsappstudio.kept.data.library

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.core.model.NearShelf
import com.jdevsappstudio.kept.core.model.NewSave
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.SaveFilter
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.SourceFilter
import com.jdevsappstudio.kept.data.ai.CardDraft
import com.jdevsappstudio.kept.data.db.KeptDatabase
import com.jdevsappstudio.kept.data.saves.RoomSaveDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The library SQL on a real SQLite (view, merges, soft delete, FTS). */
@RunWith(AndroidJUnit4::class)
class LibraryDataSourceTest {
    private lateinit var db: KeptDatabase
    private lateinit var saves: RoomSaveDataSource
    private lateinit var lib: RoomLibraryDataSource
    private var now = 1_000_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, KeptDatabase::class.java).build()
        saves = RoomSaveDataSource(db.saveDao(), Dispatchers.IO) { now }
        lib = RoomLibraryDataSource(db, db.libraryDao(), saves) { now }
    }

    @After fun tearDown() = db.close()

    private suspend fun save(
        title: String, kind: String, type: String = "movie_show", items: List<String> = emptyList(),
        tags: List<String> = emptyList(), image: Boolean = true,
    ): Long {
        now += 10
        val id = saves.add(
            listOf(NewSave(SaveSource.SHARE_IMAGE, "com.instagram.android", null, null, if (image) "/x/$title.jpg" else null)),
        ).first()
        saves.saveCard(id, CardDraft(title, type, kind, tags, items, "", "", "", ""), ReadBy.CLOUD, "m", now)
        return id
    }

    private fun ftsHits(term: String): List<Long> =
        db.openHelper.readableDatabase.query("SELECT rowid FROM search_fts WHERE search_fts MATCH ?", arrayOf("$term*")).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }

    @Test fun nearShelfIsTheKindClosestToThree() = runTest {
        assertThat(lib.observeNearShelf().first()).isNull()
        save("Shoes 1", "shoes", type = "product")
        save("Goa beach", "places in goa", type = "place")
        save("Goa fort", "places in goa", type = "place")
        assertThat(lib.observeNearShelf().first()).isEqualTo(NearShelf("Places in goa", 2))
        save("Goa market", "places in goa", type = "place") // now a shelf: the hint names the next closest
        assertThat(lib.observeNearShelf().first()).isEqualTo(NearShelf("Shoes", 1))
    }

    @Test fun shelfNeedsThreeSavesAndMergesItems() = runTest {
        save("5 thrillers", "movies", items = listOf("Prisoners", "Gone Girl (2014)"))
        save("Fincher list", "movies", items = listOf("gone girl"))
        save("Shoes 1", "shoes", type = "product")
        save("Shoes 2", "shoes", type = "product")
        assertThat(lib.observeShelves().first()).isEmpty()

        val zodiac = save("Zodiac", "movies")
        val shelf = lib.observeShelves().first().single()
        assertThat(shelf.kind).isEqualTo("movies")
        assertThat(shelf.title).isEqualTo("Movies")
        assertThat(listOf(shelf.saves, shelf.items, shelf.entries)).containsExactly(3, 2, 3)
        assertThat(shelf.countsItems).isTrue()

        val entries = lib.observeShelfEntries("movies").first()
        assertThat(entries.map { it.name to it.saves }).containsExactly("Zodiac" to 1, "gone girl" to 2, "Prisoners" to 1)
        assertThat(entries.first().isSave).isTrue()
        assertThat(entries.first().saveId).isEqualTo(zodiac)
    }

    @Test fun recipeIngredientsStayOffTheShelf() = runTest {
        save("Paneer tikka", "recipes", type = "recipe", items = listOf("200 g paneer", "1 onion"))
        save("Lemon rice", "recipes", type = "recipe", items = listOf("1 cup rice", "1 onion"))
        save("Dal", "recipes", type = "recipe")
        val shelf = lib.observeShelves().first().single()
        assertThat(listOf(shelf.saves, shelf.items, shelf.entries)).containsExactly(3, 0, 3)
        assertThat(lib.observeShelfEntries("recipes").first().map { it.name }).containsExactly("Dal", "Lemon rice", "Paneer tikka")
    }

    @Test fun notAMovieAndUndo() = runTest {
        save("A", "movies", items = listOf("Prisoners", "Heat"))
        save("B", "movies", items = listOf("Prisoners"))
        val bare = save("Zodiac", "movies")
        val prisoners = lib.observeShelfEntries("movies").first().first { it.key == "prisoners" }

        val r1 = lib.removeFromShelf("movies", prisoners)
        assertThat(lib.observeShelfEntries("movies").first().map { it.name }).containsExactly("Zodiac", "Heat")
        lib.undoRemoveFromShelf(r1)
        assertThat(lib.observeShelfEntries("movies").first().map { it.name }).containsExactly("Zodiac", "Prisoners", "Heat")

        val zodiac = lib.observeShelfEntries("movies").first().first { it.isSave }
        val r2 = lib.removeFromShelf("movies", zodiac)
        assertThat(lib.observeSave(bare).first()!!.kind).isEqualTo("")
        assertThat(lib.observeShelves().first()).isEmpty() // 2 saves left
        lib.undoRemoveFromShelf(r2)
        assertThat(lib.observeSave(bare).first()!!.kind).isEqualTo("movies")
        assertThat(lib.observeShelves().first().single().saves).isEqualTo(3)
    }

    @Test fun mergeIntoAndUndoKeepsCarriedKinds() = runTest {
        repeat(3) { save("m$it", "movies") }
        repeat(3) { save("f$it", "films") }
        repeat(3) { save("c$it", "cinema") }
        lib.mergeShelf("cinema", "films")
        assertThat(lib.observeShelves().first().map { it.kind to it.saves })
            .containsExactlyInAnyOrder("movies" to 3, "films" to 6)

        val merge = lib.mergeShelf("films", "movies")!!
        assertThat(merge.carried).containsExactly("cinema")
        assertThat(lib.observeShelves().first().map { it.kind to it.saves }).containsExactly("movies" to 9)

        lib.undoMerge(merge)
        assertThat(lib.observeShelves().first().map { it.kind to it.saves })
            .containsExactlyInAnyOrder("movies" to 3, "films" to 6)
        assertThat(lib.mergeShelf("films", "films")).isNull()
    }

    @Test fun mergingBackNeverLoops() = runTest {
        repeat(3) { save("m$it", "movies") }
        repeat(3) { save("f$it", "films") }
        lib.mergeShelf("films", "movies")
        lib.mergeShelf("movies", "films")
        assertThat(lib.observeShelves().first().map { it.kind to it.saves }).containsExactly("films" to 6)
    }

    @Test fun hideRenameReorder() = runTest {
        repeat(3) { save("m$it", "movies") }
        repeat(4) { save("s$it", "shoes", type = "product") }
        assertThat(lib.observeShelves().first().map { it.kind }).containsExactly("shoes", "movies")

        lib.reorderShelves(listOf("movies", "shoes"))
        lib.renameShelf("movies", "Films")
        lib.setShelfHidden("shoes", true)
        val shelves = lib.observeShelves().first()
        assertThat(shelves.map { it.title }).containsExactly("Films", "Shoes")
        assertThat(ShelfOrder.onHome(shelves).map { it.kind }).containsExactly("movies")
        lib.renameShelf("movies", " ")
        assertThat(lib.observeShelves().first().first().title).isEqualTo("Movies")
    }

    @Test fun deleteUndoAndPurge() = runTest {
        val a = save("Paneer tikka", "dishes", type = "recipe")
        val b = save("Lemon rice", "dishes", type = "recipe")
        lib.delete(listOf(b))
        assertThat(lib.observeNewest().first()!!.id).isEqualTo(a)
        assertThat(lib.observeTypeChips().first().single().count).isEqualTo(1)
        assertThat(lib.observeSave(b).first()).isNull()
        assertThat(saves.observeCounts().first().read).isEqualTo(1)

        lib.undoDelete(listOf(b))
        assertThat(lib.observeNewest().first()!!.id).isEqualTo(b)

        lib.delete(listOf(b))
        now += 1_000
        lib.purgeStale(graceMs = 5_000) // still in its Undo window
        assertThat(db.saveDao().get(b)).isNotNull()
        now += 10_000
        lib.purgeStale(graceMs = 5_000)
        assertThat(db.saveDao().get(b)).isNull()
        assertThat(ftsHits("lemon")).isEmpty()
        assertThat(ftsHits("paneer")).containsExactly(a)
    }

    @Test fun readAgainOnlyFromReadOrNeedsLook() = runTest {
        val id = save("X", "notes", type = "other")
        assertThat(lib.readAgain(id)).isTrue()
        assertThat(lib.observeSave(id).first()!!.status).isEqualTo(SaveStatus.QUEUED)
        assertThat(lib.observeSave(id).first()!!.title).isEqualTo("X") // old card stays until the new read
        assertThat(lib.readAgain(id)).isFalse()
    }

    @Test fun editRebuildsSearchAndMarksHandRead() = runTest {
        val id = save("Paneer tikka", "dishes", type = "recipe", tags = listOf("dinner"))
        lib.edit(id, " Paneer tikka, air-fryer ", SaveType.TIP, listOf("#Airfryer", "dinner", "", "airfryer"))
        val s = lib.observeSave(id).first()!!
        assertThat(s.title).isEqualTo("Paneer tikka, air-fryer")
        assertThat(s.type).isEqualTo(SaveType.TIP)
        assertThat(s.tags).containsExactly("airfryer", "dinner")
        assertThat(ftsHits("airfry")).containsExactly(id)

        // A queued save edited by hand becomes the user's card.
        val queued = saves.add(listOf(NewSave(SaveSource.SHARE_TEXT, null, "hello", null, null))).first()
        lib.edit(queued, "By hand", SaveType.IDEA, emptyList())
        val hand = lib.observeSave(queued).first()!!
        assertThat(hand.status).isEqualTo(SaveStatus.READ)
        assertThat(hand.readBy).isEqualTo(ReadBy.HAND)

        lib.editMany(listOf(id, queued), type = null, addTags = listOf("Goa"), removeTags = listOf("dinner"))
        assertThat(lib.observeSave(id).first()!!.tags).containsExactly("airfryer", "goa")
        assertThat(ftsHits("goa")).containsExactlyInAnyOrder(id, queued)
    }

    @Test fun itemsAddRenameDelete() = runTest {
        val a = save("A", "movies", items = listOf("Prisoners"))
        val b = save("B", "movies", items = listOf("Heat"))
        val res = lib.addItem(b, "Prisoners (2013)")
        assertThat(res.added).isTrue()
        assertThat(res.alreadyOnShelf).isNull() // 2 saves: no shelf yet
        assertThat(lib.addItem(b, "prisoners").added).isFalse() // already on this save
        assertThat(lib.addItem(b, " ").added).isFalse()

        val items = lib.observeItems(b).first()
        assertThat(items.map { it.name to it.otherSaves }).containsExactly("Heat" to 0, "Prisoners (2013)" to 1)
        val c = save("C", "movies", items = listOf("Heat")) // 3rd save: the shelf exists now
        assertThat(lib.addItem(c, "Prisoners").alreadyOnShelf).isEqualTo("Movies")

        lib.renameItem(items[0].id, "Heat (1995)")
        lib.deleteItem(items[1].id)
        assertThat(lib.observeItems(b).first().map { it.name }).containsExactly("Heat (1995)")
        assertThat(ftsHits("prisoners")).containsExactlyInAnyOrder(a, c)
    }

    @Test fun countsAndPagingFilters() = runTest {
        save("R1", "dishes", type = "recipe", tags = listOf("dinner", "goa"))
        save("R2", "dishes", type = "recipe", tags = listOf("dinner"), image = false)
        save("R3", "dishes", type = "recipe", tags = listOf("breakfast"))
        save("P1", "cafes", type = "place", tags = listOf("goa"))
        now += 10
        saves.add(listOf(NewSave(SaveSource.SHARE_IMAGE, null, null, null, "/x/q.jpg"))) // queued: no chip

        assertThat(lib.observeTypeChips().first().map { it.type to it.count })
            .containsExactly(SaveType.RECIPE to 3, SaveType.PLACE to 1)
        val recipes = SaveFilter(type = SaveType.RECIPE)
        val src = lib.observeSourceCounts(recipes).first()
        assertThat(src.screenshots to src.links).isEqualTo(2 to 1)
        assertThat(lib.observeTags(recipes).first().map { it.tag to it.count })
            .containsExactly("dinner" to 2, "breakfast" to 1, "goa" to 1)

        suspend fun titles(f: SaveFilter): List<String> {
            val page = db.libraryDao().paging(f.type?.wire, f.shelf, f.tag, f.source?.let { it == SourceFilter.SCREENSHOTS }, f.collection)
                .load(PagingSource.LoadParams.Refresh(null, 40, false)) as PagingSource.LoadResult.Page
            return page.data.map { it.save.title }
        }
        assertThat(titles(SaveFilter.ALL)).containsExactly("", "P1", "R3", "R2", "R1")
        assertThat(titles(recipes.copy(tag = "dinner"))).containsExactly("R2", "R1")
        assertThat(titles(recipes.copy(source = SourceFilter.SCREENSHOTS))).containsExactly("R3", "R1")
        assertThat(titles(SaveFilter(shelf = "dishes", source = SourceFilter.LINKS))).containsExactly("R2")
    }
}
