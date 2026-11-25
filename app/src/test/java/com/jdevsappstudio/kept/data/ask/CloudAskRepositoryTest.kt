package com.jdevsappstudio.kept.data.ask

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.jdevsappstudio.kept.core.model.AskTurnStatus
import com.jdevsappstudio.kept.core.model.SentKind
import com.jdevsappstudio.kept.data.ai.AskResult
import com.jdevsappstudio.kept.data.ai.ReadFailure
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.testQuota
import com.jdevsappstudio.kept.data.sent.SentLogLocalDataSource
import com.jdevsappstudio.kept.feature.library.FakeSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CloudAskRepositoryTest {
    private val saves = listOf(
        SourceSave(1, 100, "Gone Girl", "movie_show", tags = listOf("thriller"), body = "Gone Girl thriller movie"),
        SourceSave(2, 200, "Paneer tikka", "recipe", body = "Paneer tikka recipe dinner"),
        SourceSave(3, 300, "Prisoners", "movie_show", tags = listOf("thriller"), body = "Prisoners thriller movie"),
    )
    private val store = FakeAskStore(saves)
    private val sent = FakeSentLog()
    private val quota = testQuota()
    private val settings = FakeSettings().apply { cloudHelp.value = true }
    private var online = true
    private var now = 1_000L

    private fun TestScope.repo(asker: FakeAsker) =
        CloudAskRepository(store, asker, quota, sent, settings, { online }, backgroundScope, clock = { now })

    @Test
    fun `answer is stored with refs mapped to saves, sent log has the exact prompt, one answer counted`() = runTest {
        val asker = FakeAsker(FakeAsker.ok("Start with Prisoners.", 1, 2))
        val outcome = repo(asker).send("Which thriller first?")
        assertThat(outcome).isEqualTo(AskOutcome.Answered)
        val row = store.rows.value.single()
        assertThat(row.turn.status).isEqualTo(AskTurnStatus.DONE)
        assertThat(row.turn.answer).isEqualTo("Start with Prisoners.")
        // Sources ranked newest-first among equal hits: Prisoners [1], Gone Girl [2]; the recipe isn't sent.
        assertThat(row.turn.refs.map { it.saveId }).containsExactly(3L, 1L)
        assertThat(row.sourceIds).containsExactly(3L, 1L)
        val log = sent.rows.value.single()
        assertThat(log.kind).isEqualTo(SentKind.ASK)
        assertThat(log.text).isEqualTo(asker.prompts.single())
        assertThat(log.text).doesNotContain("Paneer")
        assertThat(row.turn.sentId).isEqualTo(log.id)
        assertThat(quota.left(CloudMeter.ANSWERS)).isEqualTo(4)
    }

    @Test
    fun `markers leave the stored answer and fill in missing refs`() = runTest {
        repo(FakeAsker(FakeAsker.ok("Start with Prisoners [1], then Gone Girl [2]."))).send("thriller")
        val turn = store.rows.value.single().turn
        assertThat(turn.answer).isEqualTo("Start with Prisoners, then Gone Girl.")
        assertThat(turn.refs.map { it.saveId }).containsExactly(3L, 1L)
    }

    @Test
    fun `gates stop before anything is stored or sent`() = runTest {
        val asker = FakeAsker()
        val repo = repo(asker)
        settings.cloudHelp.value = false
        assertThat(repo.send("hi there")).isEqualTo(AskOutcome.NeedsConsent)
        settings.cloudHelp.value = true
        online = false
        assertThat(repo.send("hi there")).isEqualTo(AskOutcome.Offline)
        online = true
        repeat(5) { quota.use(CloudMeter.ANSWERS) }
        assertThat(repo.send("hi there")).isEqualTo(AskOutcome.OutOfAnswers)
        assertThat(repo.send("   ")).isEqualTo(AskOutcome.Blank)
        assertThat(store.rows.value).isEmpty()
        assertThat(sent.rows.value).isEmpty()
        assertThat(asker.prompts).isEmpty()
    }

    @Test
    fun `a failed call is not counted and retry answers the same turn`() = runTest {
        val asker = FakeAsker(AskResult.Failure(ReadFailure.BUSY), FakeAsker.ok("Gone Girl.", 1))
        val repo = repo(asker)
        assertThat(repo.send("thriller")).isEqualTo(AskOutcome.Failed(ReadFailure.BUSY))
        val id = store.rows.value.single().turn.id
        assertThat(store.row(id).turn.status).isEqualTo(AskTurnStatus.FAILED)
        assertThat(quota.left(CloudMeter.ANSWERS)).isEqualTo(5)
        assertThat(repo.retry(id)).isEqualTo(AskOutcome.Answered)
        assertThat(store.rows.value.size).isEqualTo(1)
        assertThat(store.row(id).turn.status).isEqualTo(AskTurnStatus.DONE)
        assertThat(quota.left(CloudMeter.ANSWERS)).isEqualTo(4)
        assertThat(sent.rows.value.size).isEqualTo(2) // each try left the phone
    }

    @Test
    fun `a follow-up with no hits reuses the previous sources and sees the earlier turn`() = runTest {
        val asker = FakeAsker(FakeAsker.ok("Prisoners or Gone Girl.", 1, 2), FakeAsker.ok("Prisoners is longer.", 1))
        val repo = repo(asker)
        repo.send("thriller")
        repo.send("Which one is longer?")
        val second = store.rows.value.last()
        assertThat(second.sourceIds).containsExactly(3L, 1L)
        assertThat(asker.prompts.last()).contains("Q: thriller")
        assertThat(asker.prompts.last()).contains("A: Prisoners or Gone Girl.")
    }

    @Test
    fun `no hits at all sends the newest saves`() = runTest {
        val asker = FakeAsker(FakeAsker.ok("Nothing about zebras."))
        repo(asker).send("zebra")
        assertThat(store.rows.value.single().sourceIds).containsExactly(3L, 2L, 1L)
    }

    @Test
    fun `recover fails turns a dead process left waiting, but not one in flight, and prunes the log`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val asker = FakeAsker().apply { this.gate = gate }
        val repo = repo(asker)
        val orphan = store.insertTurn("old", 1) // left PENDING by an earlier process
        backgroundScope.launch { repo.send("thriller") }
        runCurrent()
        repo.recover()
        assertThat(store.row(orphan).turn.status).isEqualTo(AskTurnStatus.FAILED)
        assertThat(store.rows.value.last().turn.status).isEqualTo(AskTurnStatus.PENDING)
        assertThat(sent.pruned).isEqualTo(1)
        gate.complete(Unit)
        runCurrent()
        assertThat(store.rows.value.last().turn.status).isEqualTo(AskTurnStatus.DONE)
    }

    @Test
    fun `sent log keeps 30 days`() = runTest {
        sent.log(SentKind.READ, "old", images = 1, saveId = 5, now = 0)
        sent.log(SentKind.ASK, "new", now = SentLogLocalDataSource.KEEP_MS + 10)
        now = SentLogLocalDataSource.KEEP_MS + 20
        repo(FakeAsker()).recover()
        assertThat(sent.rows.value.map { it.text }).containsExactly("new")
    }
}
