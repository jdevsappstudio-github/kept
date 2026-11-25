package com.jdevsappstudio.kept.data.library

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.core.model.NewSave
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveType
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

/** Collections SQL on a real SQLite: counts skip soft deletes, links cascade, Make it files read saves only. */
@RunWith(AndroidJUnit4::class)
class CollectionsDataSourceTest {
    private lateinit var db: KeptDatabase
    private lateinit var saves: RoomSaveDataSource
    private lateinit var lib: RoomLibraryDataSource
    private lateinit var cols: RoomCollectionsDataSource
    private var now = 1_000_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, KeptDatabase::class.java).build()
        saves = RoomSaveDataSource(db.saveDao(), Dispatchers.IO) { now }
        lib = RoomLibraryDataSource(db, db.libraryDao(), saves) { now }
        cols = RoomCollectionsDataSource(db, db.collectionDao()) { now }
    }

    @After fun tearDown() = db.close()

    private suspend fun save(title: String, type: String = "place", tags: List<String> = emptyList(), read: Boolean = true): Long {
        now += 10
        val id = saves.add(listOf(NewSave(SaveSource.SHARE_IMAGE, "app", null, null, "/x/$title.jpg"))).first()
        if (read) saves.saveCard(id, CardDraft(title, type, "", tags, emptyList(), "", "", "", ""), ReadBy.CLOUD, "m", now)
        return id
    }

    private suspend fun titlesIn(collection: Long, type: String? = null): List<String> {
        val page = db.libraryDao().paging(type, null, null, null, collection)
            .load(PagingSource.LoadParams.Refresh(null, 40, false)) as PagingSource.LoadResult.Page
        return page.data.map { it.save.title }
    }

    @Test fun createRenameAndNames() = runTest {
        val goa = cols.create("  Goa   trip ")
        assertThat(goa).isNotNull()
        now += 10
        val gifts = cols.create("Gift ideas")!!
        assertThat(cols.create("goa TRIP")).isNull()
        assertThat(cols.create("   ")).isNull()
        assertThat(cols.observeCollections().first().map { it.name }).containsExactly("Gift ideas", "Goa trip")
        assertThat(cols.rename(gifts, "GOA trip")).isFalse()
        assertThat(cols.rename(gifts, "Gifts")).isTrue()
        assertThat(cols.rename(goa!!, "Goa trip")).isTrue() // keeping its own name is fine
        assertThat(cols.observeCollection(gifts).first()?.name).isEqualTo("Gifts")
    }

    @Test fun addRemoveCountsAndSoftDelete() = runTest {
        val cafe = save("Café")
        val fort = save("Fort")
        val curry = save("Curry", type = "recipe")
        val id = cols.create("Goa")!!

        assertThat(cols.addSaves(id, listOf(cafe, fort, curry))).isEqualTo(3)
        assertThat(cols.addSaves(id, listOf(cafe, 9_999))).isEqualTo(0) // already in, and a save that doesn't exist
        assertThat(cols.observeCollection(id).first()?.saves).isEqualTo(3)
        assertThat(titlesIn(id)).containsExactly("Curry", "Fort", "Café")
        assertThat(titlesIn(id, "place")).containsExactly("Fort", "Café")
        assertThat(cols.observeTypeChips(id).first().map { it.type to it.count })
            .containsExactly(SaveType.PLACE to 2, SaveType.RECIPE to 1)
        assertThat(cols.membership(listOf(cafe, fort))).isEqualTo(mapOf(id to 2))

        lib.delete(listOf(fort))
        assertThat(cols.observeCollection(id).first()?.saves).isEqualTo(2)
        assertThat(cols.observeSaveIds(id).first()).isEqualTo(setOf(cafe, curry))
        lib.undoDelete(listOf(fort))
        assertThat(cols.observeCollection(id).first()?.saves).isEqualTo(3) // Undo puts it back in

        cols.removeSaves(id, listOf(curry))
        assertThat(titlesIn(id)).containsExactly("Fort", "Café")

        saves.delete(cafe) // purge: the link cascades
        assertThat(cols.observeSaveIds(id).first()).isEqualTo(setOf(fort))

        cols.delete(id)
        assertThat(cols.observeCollection(id).first()).isNull()
        assertThat(lib.observeSave(fort).first()).isNotNull() // saves stay
        assertThat(cols.addSaves(id, listOf(fort))).isEqualTo(0) // gone collection: nothing happens
    }

    @Test fun suggestibleTagsAndMakeFromTag() = runTest {
        save("A", tags = listOf("pune", "cafe"))
        save("B", tags = listOf("pune"))
        save("C", tags = listOf("pune", "cafe"))
        save("D", tags = listOf("cafe"))
        save("E", tags = listOf("pune"))
        save("unread", read = false)
        assertThat(cols.observeSuggestibleTags().first().map { it.tag to it.count }).containsExactly("pune" to 4, "cafe" to 3)

        val made = cols.makeFromTag("pune")!!
        assertThat(made.name).isEqualTo("Pune")
        assertThat(made.saves).isEqualTo(4)
        assertThat(titlesIn(made.id)).containsExactly("E", "C", "B", "A")
        assertThat(cols.makeFromTag("pune")).isNull() // the name is taken now
    }
}
