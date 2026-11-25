package com.jdevsappstudio.kept.data.saves

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
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.data.ai.CardDraft
import com.jdevsappstudio.kept.data.db.KeptDatabase
import com.jdevsappstudio.kept.data.library.RoomLibraryDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Fill in with cloud + Add a screenshot (H59) on a real SQLite. */
@RunWith(AndroidJUnit4::class)
class FillDataSourceTest {
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

    private fun card(title: String, tags: List<String>, items: List<String> = emptyList()) =
        CardDraft(title, "recipe", "dals", tags, items, "", "", "", "From the page")

    private suspend fun link(): Long {
        val id = saves.add(listOf(NewSave(SaveSource.SHARE_TEXT, "com.instagram.android", "Best dal 🔥", "https://ex.com/dal", null))).first()
        saves.saveCard(id, CardDraft("Best dal", "video", "videos", listOf("food"), emptyList(), "", "", "", ""), ReadBy.CLOUD, "m", now)
        return id
    }

    @Test fun fillReplacesTheCardKeepsTagsAndHidesTheButton() = runTest {
        val id = link()
        assertThat(saves.fillTarget(id)!!.canFill).isTrue()

        now += 10
        saves.saveFill(id, card("Dal tadka", listOf("dal", "food"), listOf("toor dal", "ghee")), "gemini-x", now)
        val s = lib.observeSave(id).first()!!
        assertThat(s.title).isEqualTo("Dal tadka")
        assertThat(s.kind).isEqualTo("dals")
        assertThat(s.readBy).isEqualTo(ReadBy.CLOUD)
        assertThat(s.filledAt).isEqualTo(now)
        assertThat(s.tags).containsExactly("dal", "food")
        assertThat(s.items).containsExactly("toor dal", "ghee")
        assertThat(saves.fillTarget(id)!!.canFill).isFalse()
        // the page's words are searchable
        db.openHelper.readableDatabase.query("SELECT rowid FROM search_fts WHERE search_fts MATCH 'tadka'").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getLong(0)).isEqualTo(id)
        }
    }

    @Test fun aLaterReadClearsTheFillMark() = runTest {
        val id = link()
        saves.saveFill(id, card("Dal tadka", listOf("dal")), "gemini-x", now)
        saves.saveCard(id, card("Dal again", listOf("dal")), ReadBy.CLOUD, "m", now + 1)
        assertThat(lib.observeSave(id).first()!!.filledAt).isNull()
    }

    @Test fun attachImageQueuesTheLinkOnce() = runTest {
        val id = link()
        assertThat(saves.attachImage(id, "/x/shot.jpg", "abc")).isTrue()
        val s = lib.observeSave(id).first()!!
        assertThat(s.status).isEqualTo(SaveStatus.QUEUED)
        assertThat(s.imagePath).isNotNull()
        assertThat(s.title).isEqualTo("Best dal") // old card stays until the new read lands
        assertThat(s.captionOnly).isFalse()
        assertThat(saves.attachImage(id, "/x/other.jpg", "def")).isFalse()
        assertThat(saves.fillTarget(id)!!.canFill).isFalse()
    }
}
