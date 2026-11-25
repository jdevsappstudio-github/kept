package com.jdevsappstudio.kept.data.fill

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.jdevsappstudio.kept.core.model.SentKind
import com.jdevsappstudio.kept.data.ai.CardDraft
import com.jdevsappstudio.kept.data.ai.FillResult
import com.jdevsappstudio.kept.data.ai.PageFiller
import com.jdevsappstudio.kept.data.ai.ReadFailure
import com.jdevsappstudio.kept.data.ask.FakeSentLog
import com.jdevsappstudio.kept.data.capture.FakeSaveStore
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.testQuota
import com.jdevsappstudio.kept.data.saves.FillTarget
import com.jdevsappstudio.kept.feature.library.FakeSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CloudFillRepositoryTest {
    private val store = FakeSaveStore().apply {
        fillTargets[1] = FillTarget(1, "https://example.com/dal", "Best dal recipe 🔥 #food", "Best dal recipe", canFill = true)
    }
    private val sent = FakeSentLog()
    private val quota = testQuota()
    private val settings = FakeSettings().apply { cloudHelp.value = true }
    private var online = true

    private class FakeFiller(vararg results: FillResult) : PageFiller {
        val results = ArrayDeque(results.toList())
        val prompts = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun fill(prompt: String): FillResult {
            prompts += prompt
            gate?.await()
            return results.removeFirst()
        }
    }

    private val card = CardDraft(
        title = "Dal tadka", type = "recipe", kind = "dals", tags = listOf("dal", "indian"), items = emptyList(),
        place = "", date = "", link = "", summary = "A lentil curry.",
    )

    private fun TestScope.repo(filler: PageFiller) =
        CloudFillRepository(store, filler, quota, sent, settings, { online }, backgroundScope, clock = { 5_000L })

    @Test
    fun `filled card is saved, the exact prompt is logged, one answer counted`() = runTest {
        val filler = FakeFiller(FillResult.Success(card, "gemini-test", 10))
        val outcome = repo(filler).fill(1)

        assertThat(outcome).isEqualTo(FillOutcome.Filled)
        assertThat(store.filled).containsExactly(Triple(1L, card, "gemini-test"))
        val row = sent.rows.value.single()
        assertThat(row.kind).isEqualTo(SentKind.FILL_IN)
        assertThat(row.text).isEqualTo(filler.prompts.single())
        assertThat(row.text).contains("Link: https://example.com/dal")
        assertThat(quota.now(CloudMeter.ANSWERS).used).isEqualTo(1)
    }

    @Test
    fun `page that couldn't be opened changes nothing and costs nothing (H59)`() = runTest {
        val outcome = repo(FakeFiller(FillResult.PageUnreachable("ERROR"))).fill(1)

        assertThat(outcome).isEqualTo(FillOutcome.PageUnreachable)
        assertThat(store.filled).isEmpty()
        assertThat(quota.now(CloudMeter.ANSWERS).used).isEqualTo(0)
        assertThat(sent.rows.value.size).isEqualTo(1) // it did leave the phone
    }

    @Test
    fun `a failed call is not counted`() = runTest {
        val outcome = repo(FakeFiller(FillResult.Failure(ReadFailure.BUSY, null))).fill(1)
        assertThat(outcome).isEqualTo(FillOutcome.Failed(ReadFailure.BUSY))
        assertThat(store.filled).isEmpty()
        assertThat(quota.now(CloudMeter.ANSWERS).used).isEqualTo(0)
    }

    @Test
    fun `gates stop before anything is sent`() = runTest {
        val filler = FakeFiller()
        val repo = repo(filler)

        settings.cloudHelp.value = false
        assertThat(repo.fill(1)).isEqualTo(FillOutcome.NeedsConsent)
        settings.cloudHelp.value = true
        online = false
        assertThat(repo.fill(1)).isEqualTo(FillOutcome.Offline)
        online = true
        repeat(quota.left(CloudMeter.ANSWERS)) { quota.use(CloudMeter.ANSWERS) }
        assertThat(repo.fill(1)).isEqualTo(FillOutcome.OutOfAnswers)

        assertThat(filler.prompts).isEmpty()
        assertThat(sent.rows.value).isEmpty()
    }

    @Test
    fun `only a caption-only save that wasn't filled yet can go`() = runTest {
        store.fillTargets[2] = FillTarget(2, "https://x.com/a", null, "", canFill = false)
        val filler = FakeFiller()
        val repo = repo(filler)
        assertThat(repo.fill(2)).isEqualTo(FillOutcome.NotAllowed)
        assertThat(repo.fill(99)).isEqualTo(FillOutcome.NotAllowed)
        assertThat(filler.prompts).isEmpty()
    }

    @Test
    fun `filling shows while the page is read, and a second tap doesn't send twice`() = runTest {
        val filler = FakeFiller(FillResult.Success(card, "gemini-test", 10)).apply { gate = CompletableDeferred() }
        val repo = repo(filler)
        val first = async { repo.fill(1) }
        runCurrent()
        assertThat(repo.filling.value.toList()).containsExactly(1L)
        assertThat(repo.fill(1)).isEqualTo(FillOutcome.NotAllowed)

        filler.gate!!.complete(Unit)
        assertThat(first.await()).isEqualTo(FillOutcome.Filled)
        assertThat(repo.filling.value).isEmpty()
        assertThat(filler.prompts.size).isEqualTo(1)
    }

    @Test
    fun `prompt carries the link and caption, skips a caption that is just the link`() {
        val p = FillPrompt.build(" https://a.com/x ", "  Look at this  ")
        assertThat(p).contains("Link: https://a.com/x")
        assertThat(p).contains("Caption:\nLook at this")
        assertThat(FillPrompt.build("https://a.com/x", "https://a.com/x")).doesNotContain("Caption")
        assertThat(FillPrompt.build("https://a.com/x", "y".repeat(5000)).length).isEqualTo(
            FillPrompt.build("https://a.com/x", null).length + "\n\nCaption:\n".length + FillPrompt.MAX_CAPTION_CHARS,
        )
    }
}
