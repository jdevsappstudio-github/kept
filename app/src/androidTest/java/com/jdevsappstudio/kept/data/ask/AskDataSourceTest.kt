package com.jdevsappstudio.kept.data.ask

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.jdevsappstudio.kept.core.model.AskTurnStatus
import com.jdevsappstudio.kept.core.model.NewSave
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SentKind
import com.jdevsappstudio.kept.data.ai.CardDraft
import com.jdevsappstudio.kept.data.db.KeptDatabase
import com.jdevsappstudio.kept.data.library.RoomLibraryDataSource
import com.jdevsappstudio.kept.data.saves.RoomSaveDataSource
import com.jdevsappstudio.kept.data.sent.RoomSentLogDataSource
import com.jdevsappstudio.kept.data.sent.SentLogLocalDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Ask SQL on a real SQLite (H58): OR-match candidates, read-only, refs hide deleted saves, history, clear, sent log. */
@RunWith(AndroidJUnit4::class)
class AskDataSourceTest {
    private lateinit var db: KeptDatabase
    private lateinit var saves: RoomSaveDataSource
    private lateinit var ask: RoomAskDataSource
    private var now = 1_000_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, KeptDatabase::class.java).build()
        saves = RoomSaveDataSource(db.saveDao(), Dispatchers.IO) { now }
        ask = RoomAskDataSource(db.askDao())
    }

    @After fun tearDown() = db.close()

    private suspend fun save(title: String, tags: List<String> = emptyList(), read: Boolean = true): Long {
        now += 10
        val id = saves.add(listOf(NewSave(SaveSource.SHARE_IMAGE, null, null, null, "/x/$title.jpg"))).first()
        if (read) saves.saveCard(id, CardDraft(title, "movie_show", "movies", tags, emptyList(), "", "", "", "A film"), ReadBy.CLOUD, "m", now)
        return id
    }

    @Test fun candidatesMatchAnyWordOnlyReadSavesNewestFirst() = runTest {
        val gone = save("Gone Girl", listOf("thriller"))
        val pasta = save("Pasta night")
        save("Unread thriller", read = false)
        val prisoners = save("Prisoners", listOf("thriller"))
        val got = ask.candidates(AskQuery.parse("which thriller or pasta first?"), 50).map { it.id }
        assertThat(got).containsExactly(prisoners, pasta, gone)
        assertThat(ask.candidates(AskQuery.parse("which one?"), 50)).isEmpty()
        assertThat(ask.newest(2).map { it.id }).containsExactly(prisoners, pasta)
        assertThat(ask.observeReadCount().first()).isEqualTo(3)
    }

    @Test fun turnsRefsHistoryAndClear() = runTest {
        val gone = save("Gone Girl", listOf("thriller"))
        val prisoners = save("Prisoners", listOf("thriller"))
        val t1 = ask.insertTurn("thriller?", now)
        ask.markSent(t1, listOf(prisoners, gone), sentId = 7)
        ask.finish(t1, "Prisoners first.", "m", listOf(prisoners, gone))
        val t2 = ask.insertTurn("longer one?", now)
        assertThat(ask.previousSources(t2).map { it.id }).containsExactly(prisoners, gone)
        assertThat(ask.history(t2, 6).single().answer).isEqualTo("Prisoners first.")
        ask.failStale(listOf(t2))
        assertThat(ask.observeTurns().first().last().status).isEqualTo(AskTurnStatus.PENDING)
        ask.failStale(emptyList())
        assertThat(ask.observeTurns().first().last().status).isEqualTo(AskTurnStatus.FAILED)

        RoomLibraryDataSource(db, db.libraryDao(), saves) { now }.delete(listOf(gone))
        val turn = ask.observeTurns().first().first()
        assertThat(turn.refs.map { it.title }).containsExactly("Prisoners")
        assertThat(turn.sentId).isEqualTo(7L)

        ask.clear()
        assertThat(ask.observeTurns().first()).isEmpty()
    }

    @Test fun sentLogNewestFirstAndPrunedAfter30Days() = runTest {
        val log = RoomSentLogDataSource(db.sentDao())
        log.log(SentKind.READ, "old", images = 1, saveId = 1, now = 0)
        log.log(SentKind.ASK, "new", now = SentLogLocalDataSource.KEEP_MS + 5)
        assertThat(log.observe().first().map { it.text }).containsExactly("new", "old")
        assertThat(log.prune(SentLogLocalDataSource.KEEP_MS + 10)).isEqualTo(1)
        assertThat(log.observe().first().map { it.text }).containsExactly("new")
    }
}
