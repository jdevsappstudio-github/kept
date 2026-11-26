package com.jdevsappstudio.kept.data.ask

import com.jdevsappstudio.kept.core.model.AskRef
import com.jdevsappstudio.kept.core.model.AskTurn
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.data.db.AskDao
import com.jdevsappstudio.kept.data.db.AskSourceRow
import com.jdevsappstudio.kept.data.db.AskTurnEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The Ask chat in Room (H58) and the source queries over the search index. Every call is main-safe. */
interface AskLocalDataSource {
    fun observeTurns(): Flow<List<AskTurn>>
    fun observeReadCount(): Flow<Int>

    suspend fun insertTurn(question: String, now: Long): Long
    /** The question of a turn, or null if it's gone (Clear chat while it was failing). */
    suspend fun question(turnId: Long): String?
    suspend fun markSent(turnId: Long, sourceIds: List<Long>, sentId: Long?)
    suspend fun finish(turnId: Long, answer: String, model: String, refIds: List<Long>)
    suspend fun fail(turnId: Long, reason: String)
    /** Turns left PENDING by a dead process become FAILED; [live] are still in flight. */
    suspend fun failStale(live: Collection<Long>)
    suspend fun setHidden(turnId: Long, hidden: Boolean)
    suspend fun clear()

    /** Answered turns before [beforeId], oldest first, at most [limit]. */
    suspend fun history(beforeId: Long, limit: Int): List<AskHistory>
    /** The newest earlier turn's sources, in source order. */
    suspend fun previousSources(beforeId: Long): List<SourceSave>

    suspend fun candidates(query: AskQuery, limit: Int): List<SourceSave>
    suspend fun newest(limit: Int): List<SourceSave>
    suspend fun typeCounts(): Map<String, Int>
    suspend fun topTags(limit: Int): List<String>
}

class RoomAskDataSource(private val dao: AskDao) : AskLocalDataSource {

    override fun observeTurns(): Flow<List<AskTurn>> = combine(dao.observeTurns(), dao.observeRefs()) { turns, refs ->
        val byTurn = refs.groupBy { it.turnId }
        turns.map { t ->
            t.toAskTurn(byTurn[t.id].orEmpty().map { AskRef(it.saveId, it.title, it.type.toTypeOrNull(), it.thumbPath, AskRef.line(it.summary, it.place)) })
        }
    }

    override fun observeReadCount(): Flow<Int> = dao.observeReadCount()

    override suspend fun insertTurn(question: String, now: Long): Long =
        dao.insertTurn(AskTurnEntity(createdAt = now, question = question))

    override suspend fun question(turnId: Long): String? = dao.turn(turnId)?.question

    override suspend fun markSent(turnId: Long, sourceIds: List<Long>, sentId: Long?) =
        dao.markSent(turnId, sourceIds.joinToString(","), sentId)

    override suspend fun finish(turnId: Long, answer: String, model: String, refIds: List<Long>) =
        dao.finish(turnId, answer, model, refIds)

    override suspend fun fail(turnId: Long, reason: String) = dao.markFailed(turnId, reason)

    // NOT IN () with an empty list is valid SQL in Room's expansion; -1 keeps it explicit.
    override suspend fun failStale(live: Collection<Long>) = dao.failStale(live.ifEmpty { listOf(-1L) }.toList())

    override suspend fun setHidden(turnId: Long, hidden: Boolean) = dao.setHidden(turnId, hidden)

    override suspend fun clear() = dao.clear()

    override suspend fun history(beforeId: Long, limit: Int): List<AskHistory> =
        dao.doneBefore(beforeId, limit).reversed().map { AskHistory(it.question, it.answer) }

    override suspend fun previousSources(beforeId: Long): List<SourceSave> {
        val ids = dao.lastSourcesBefore(beforeId)?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()
        if (ids.isEmpty()) return emptyList()
        val rows = dao.byIds(ids).associateBy { it.save.id }
        return ids.mapNotNull { rows[it]?.toSource() }
    }

    override suspend fun candidates(query: AskQuery, limit: Int): List<SourceSave> =
        if (query.isBlank) emptyList() else dao.candidates(query.match, limit).map { it.toSource() }

    override suspend fun newest(limit: Int): List<SourceSave> = dao.newest(limit).map { it.toSource() }

    override suspend fun typeCounts(): Map<String, Int> = dao.typeCounts().associate { it.type to it.n }

    override suspend fun topTags(limit: Int): List<String> = dao.topTags(limit).map { it.tag }

    private fun AskTurnEntity.toAskTurn(refs: List<AskRef>) = AskTurn(
        id = id, createdAt = createdAt, question = question, answer = answer, refs = refs,
        status = status, hidden = hidden, sentId = sentId,
        sourceCount = sourceIds.split(',').count { it.isNotBlank() },
    )

    private fun AskSourceRow.toSource() = SourceSave(
        id = save.id, createdAt = save.createdAt, title = save.title, type = save.type, kind = save.kind,
        tags = tags, items = items.filterNot { it.hidden }.map { it.name }, place = save.place, date = save.eventDate,
        link = save.link, summary = save.summary, fullText = save.fullText, body = body,
    )

    private fun String.toTypeOrNull(): SaveType? = SaveType.entries.firstOrNull { it.wire == this }
}
