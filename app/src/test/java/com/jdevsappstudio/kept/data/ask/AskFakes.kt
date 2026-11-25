package com.jdevsappstudio.kept.data.ask

import com.jdevsappstudio.kept.core.model.AskRef
import com.jdevsappstudio.kept.core.model.AskTurn
import com.jdevsappstudio.kept.core.model.AskTurnStatus
import com.jdevsappstudio.kept.core.model.SentKind
import com.jdevsappstudio.kept.core.model.SentRequest
import com.jdevsappstudio.kept.data.ai.AskAnswer
import com.jdevsappstudio.kept.data.ai.AskResult
import com.jdevsappstudio.kept.data.ai.CloudAsker
import com.jdevsappstudio.kept.data.sent.SentLogLocalDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory chat + a fixed set of read saves. Candidates = saves whose body hits any query word. */
class FakeAskStore(var saves: List<SourceSave> = emptyList()) : AskLocalDataSource {
    data class Row(val turn: AskTurn, val sourceIds: List<Long> = emptyList(), val failReason: String? = null)

    val rows = MutableStateFlow<List<Row>>(emptyList())
    private var nextId = 1L
    val readCount = MutableStateFlow(0)

    override fun observeTurns(): Flow<List<AskTurn>> = rows.map { list -> list.map { it.turn } }
    override fun observeReadCount(): Flow<Int> = readCount

    private fun edit(id: Long, f: (Row) -> Row) {
        rows.value = rows.value.map { if (it.turn.id == id) f(it) else it }
    }

    fun row(id: Long) = rows.value.first { it.turn.id == id }

    override suspend fun insertTurn(question: String, now: Long): Long {
        val id = nextId++
        rows.value += Row(AskTurn(id, now, question, "", emptyList(), AskTurnStatus.PENDING, false, null))
        return id
    }

    override suspend fun question(turnId: Long) = rows.value.firstOrNull { it.turn.id == turnId }?.turn?.question

    override suspend fun markSent(turnId: Long, sourceIds: List<Long>, sentId: Long?) =
        edit(turnId) { it.copy(turn = it.turn.copy(status = AskTurnStatus.PENDING, sentId = sentId), sourceIds = sourceIds, failReason = null) }

    override suspend fun finish(turnId: Long, answer: String, model: String, refIds: List<Long>) = edit(turnId) { r ->
        val refs = refIds.mapNotNull { id -> saves.firstOrNull { it.id == id }?.let { AskRef(it.id, it.title, null) } }
        r.copy(turn = r.turn.copy(status = AskTurnStatus.DONE, answer = answer, refs = refs))
    }

    override suspend fun fail(turnId: Long, reason: String) =
        edit(turnId) { it.copy(turn = it.turn.copy(status = AskTurnStatus.FAILED), failReason = reason) }

    override suspend fun failStale(live: Collection<Long>) {
        rows.value.filter { it.turn.status == AskTurnStatus.PENDING && it.turn.id !in live }.forEach { fail(it.turn.id, "INTERRUPTED") }
    }

    override suspend fun setHidden(turnId: Long, hidden: Boolean) = edit(turnId) { it.copy(turn = it.turn.copy(hidden = hidden)) }

    override suspend fun clear() {
        rows.value = emptyList()
    }

    override suspend fun history(beforeId: Long, limit: Int) = rows.value
        .filter { it.turn.id < beforeId && it.turn.status == AskTurnStatus.DONE }
        .takeLast(limit).map { AskHistory(it.turn.question, it.turn.answer) }

    override suspend fun previousSources(beforeId: Long): List<SourceSave> {
        val ids = rows.value.lastOrNull { it.turn.id < beforeId && it.sourceIds.isNotEmpty() }?.sourceIds.orEmpty()
        return ids.mapNotNull { id -> saves.firstOrNull { it.id == id } }
    }

    override suspend fun candidates(query: AskQuery, limit: Int) =
        saves.filter { query.hits(it.body.ifEmpty { it.title }) > 0 }.sortedByDescending { it.createdAt }.take(limit)

    override suspend fun newest(limit: Int) = saves.sortedByDescending { it.createdAt }.take(limit)
    override suspend fun typeCounts() = saves.groupingBy { it.type }.eachCount()
    override suspend fun topTags(limit: Int) =
        saves.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(limit)
}

/** Answers from a queue of results; [gate] (when set) holds the call until completed. */
class FakeAsker(vararg results: AskResult) : CloudAsker {
    val results = ArrayDeque(results.toList())
    val prompts = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun ask(prompt: String): AskResult {
        prompts += prompt
        gate?.await()
        return results.removeFirstOrNull() ?: ok("Fine.")
    }

    companion object {
        fun ok(answer: String, vararg refs: Int) = AskResult.Success(AskAnswer(answer, refs.toList()), "gemini-test", 10)
    }
}

class FakeSentLog : SentLogLocalDataSource {
    val rows = MutableStateFlow<List<SentRequest>>(emptyList())
    var pruned = 0

    override fun observe(): Flow<List<SentRequest>> = rows.map { it.sortedByDescending { r -> r.sentAt } }
    override suspend fun log(kind: SentKind, text: String, images: Int, saveId: Long?, now: Long): Long {
        val id = rows.value.size + 1L
        rows.value += SentRequest(id, now, kind, text, images, saveId)
        return id
    }
    override suspend fun prune(now: Long): Int {
        pruned++
        val before = rows.value.size
        rows.value = rows.value.filter { it.sentAt >= now - SentLogLocalDataSource.KEEP_MS }
        return before - rows.value.size
    }
    override suspend fun clear() {
        rows.value = emptyList()
    }
}
