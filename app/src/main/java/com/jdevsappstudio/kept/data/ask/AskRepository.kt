package com.jdevsappstudio.kept.data.ask

import com.jdevsappstudio.kept.core.model.AskTurn
import com.jdevsappstudio.kept.core.model.SentKind
import com.jdevsappstudio.kept.data.ai.AskResult
import com.jdevsappstudio.kept.data.ai.CloudAsker
import com.jdevsappstudio.kept.data.ai.ReadFailure
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.CloudQuota
import com.jdevsappstudio.kept.data.sent.SentLogLocalDataSource
import com.jdevsappstudio.kept.data.settings.SettingsStore
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow

/** Why a question didn't go, or how it ended. The screen maps each to a sheet, a banner or a bubble. */
sealed interface AskOutcome {
    data object Answered : AskOutcome
    /** The call failed; the turn shows 'Cloud didn't answer. Try again' and nothing was counted (H57). */
    data class Failed(val reason: ReadFailure) : AskOutcome
    /** Cloud help is off: k-consent first (H29). Nothing was stored or sent. */
    data object NeedsConsent : AskOutcome
    /** No network: send stays disabled (k-offline). Nothing was stored or sent. */
    data object Offline : AskOutcome
    /** 0 answers left today: k-cloud-out (H45/H57). Nothing was stored or sent. */
    data object OutOfAnswers : AskOutcome
    data object Blank : AskOutcome
}

/** Ask (H58): chat over the person's saves in the cloud. Combines the chat store, the model, the meter and the sent log. */
interface AskRepository {
    val turns: Flow<List<AskTurn>>
    /** Read saves, for 'Keep a few saves first' (k-ask-first). */
    val readSaves: Flow<Int>

    suspend fun starters(): List<String>
    /** Consent, network, today's answers: what stops a question now, or null if it can go. [send] checks again. */
    suspend fun check(): AskOutcome?
    suspend fun send(question: String): AskOutcome
    /** Try again on a failed turn: same question, sources picked again. */
    suspend fun retry(turnId: Long): AskOutcome
    /** On opening Ask: turns a dead process left waiting become failed; old sent-log rows go. */
    suspend fun recover()
    suspend fun setHidden(turnId: Long, hidden: Boolean)
    suspend fun clear()
}

class CloudAskRepository(
    private val store: AskLocalDataSource,
    private val asker: CloudAsker,
    private val quota: CloudQuota,
    private val sentLog: SentLogLocalDataSource,
    private val settings: SettingsStore,
    private val isOnline: () -> Boolean,
    /** App scope: an answer still lands in the chat if the person leaves the screen while it's coming. */
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : AskRepository {

    private val live: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    override val turns: Flow<List<AskTurn>> = store.observeTurns()
    override val readSaves: Flow<Int> = store.observeReadCount()

    override suspend fun starters(): List<String> {
        val counts = store.typeCounts()
        return AskStarters.build(counts.values.sum(), counts, store.topTags(TAG_POOL))
    }

    override suspend fun send(question: String): AskOutcome {
        val q = question.trim().take(AskPrompt.MAX_QUESTION_CHARS)
        if (q.isEmpty()) return AskOutcome.Blank
        check()?.let { return it }
        val turnId = store.insertTurn(q, clock())
        return run(turnId, q)
    }

    override suspend fun retry(turnId: Long): AskOutcome {
        if (turnId in live) return AskOutcome.Blank // already going
        val q = store.question(turnId) ?: return AskOutcome.Blank
        check()?.let { return it }
        return run(turnId, q)
    }

    override suspend fun check(): AskOutcome? = when {
        !settings.cloudHelpNow() -> AskOutcome.NeedsConsent
        !isOnline() -> AskOutcome.Offline
        quota.left(CloudMeter.ANSWERS) <= 0 -> AskOutcome.OutOfAnswers
        else -> null
    }

    private suspend fun run(turnId: Long, question: String): AskOutcome {
        live += turnId
        return scope.async {
            try {
                val prompt = buildPrompt(turnId, question)
                val sentId = sentLog.log(SentKind.ASK, prompt.text, now = clock())
                store.markSent(turnId, prompt.saveIds, sentId)
                when (val result = asker.ask(prompt.text)) {
                    is AskResult.Success -> {
                        val raw = result.answer.answer
                        val refs = result.answer.refs.ifEmpty { AskPrompt.markers(raw) }
                        store.finish(turnId, AskPrompt.cleanAnswer(raw), result.model, AskPrompt.refIds(refs, prompt.saveIds))
                        quota.use(CloudMeter.ANSWERS) // only successes count (H57)
                        AskOutcome.Answered
                    }
                    is AskResult.Failure -> {
                        store.fail(turnId, result.reason.name)
                        AskOutcome.Failed(result.reason)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                withContext(NonCancellable) { store.fail(turnId, ReadFailure.UNKNOWN.name) }
                AskOutcome.Failed(ReadFailure.UNKNOWN)
            } finally {
                live -= turnId
            }
        }.await()
    }

    private suspend fun buildPrompt(turnId: Long, question: String): BuiltPrompt {
        val query = AskQuery.parse(question)
        val ranked = AskPrompt.rank(query, store.candidates(query, CANDIDATES), store.previousSources(turnId))
        val sources = ranked.ifEmpty { store.newest(AskPrompt.MAX_SOURCES) }
        return AskPrompt.build(question, store.history(turnId, AskPrompt.HISTORY_TURNS), sources)
    }

    override suspend fun recover() {
        store.failStale(live.toList())
        sentLog.prune(clock())
    }

    override suspend fun setHidden(turnId: Long, hidden: Boolean) = store.setHidden(turnId, hidden)

    override suspend fun clear() = store.clear()

    private companion object {
        /** FTS rows ranked in Kotlin; newest first, so a huge library still ranks its recent saves. */
        const val CANDIDATES = 200
        const val TAG_POOL = 8
    }
}
