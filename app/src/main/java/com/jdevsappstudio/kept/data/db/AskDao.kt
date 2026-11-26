package com.jdevsappstudio.kept.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.jdevsappstudio.kept.core.model.AskTurnStatus
import kotlinx.coroutines.flow.Flow

/**
 * Ask (H58): the chat, its refs, and source retrieval over the search index.
 * [match] comes from [com.jdevsappstudio.kept.data.ask.AskQuery.match] only: never raw user text.
 */
@Dao
abstract class AskDao {

    @Query("SELECT * FROM ask_turns ORDER BY id")
    abstract fun observeTurns(): Flow<List<AskTurnEntity>>

    /** Refs to saves that still show (soft-deleted ones hide at once, before the purge removes the row). */
    @Query(
        "SELECT ask_refs.turnId, ask_refs.position, saves.id AS saveId, saves.title, saves.type, saves.thumbPath, " +
            "saves.summary, saves.place, saves.example FROM ask_refs " +
            "JOIN saves ON saves.id = ask_refs.saveId WHERE saves.deletedAt IS NULL ORDER BY ask_refs.turnId, ask_refs.position",
    )
    abstract fun observeRefs(): Flow<List<AskRefRow>>

    @Query("SELECT * FROM ask_turns WHERE id = :id")
    abstract suspend fun turn(id: Long): AskTurnEntity?

    /** The last [limit] answered turns before [beforeId], newest first (the model's chat memory). */
    @Query("SELECT * FROM ask_turns WHERE status = 'DONE' AND id < :beforeId ORDER BY id DESC LIMIT :limit")
    abstract suspend fun doneBefore(beforeId: Long, limit: Int): List<AskTurnEntity>

    /** The newest turn before [beforeId] that sent any sources (a follow-up with no hits reuses them). */
    @Query("SELECT sourceIds FROM ask_turns WHERE id < :beforeId AND sourceIds != '' ORDER BY id DESC LIMIT 1")
    abstract suspend fun lastSourcesBefore(beforeId: Long): String?

    @Insert
    abstract suspend fun insertTurn(turn: AskTurnEntity): Long

    @Query("UPDATE ask_turns SET status = 'PENDING', failReason = NULL, sourceIds = :sourceIds, sentId = :sentId WHERE id = :id")
    abstract suspend fun markSent(id: Long, sourceIds: String, sentId: Long?)

    @Query("UPDATE ask_turns SET status = 'DONE', answer = :answer, model = :model, failReason = NULL WHERE id = :id")
    abstract suspend fun markDone(id: Long, answer: String, model: String)

    @Query("UPDATE ask_turns SET status = 'FAILED', failReason = :reason WHERE id = :id")
    abstract suspend fun markFailed(id: Long, reason: String)

    /** Process died mid-call: those turns can never finish. [live] = turns still in flight in this process. */
    @Query("UPDATE ask_turns SET status = 'FAILED', failReason = 'INTERRUPTED' WHERE status = 'PENDING' AND id NOT IN (:live)")
    abstract suspend fun failStale(live: List<Long>)

    @Query("UPDATE ask_turns SET hidden = :hidden WHERE id = :id")
    abstract suspend fun setHidden(id: Long, hidden: Boolean)

    @Insert
    abstract suspend fun insertRefs(refs: List<AskRefEntity>)

    @Query("DELETE FROM ask_refs WHERE turnId = :turnId")
    abstract suspend fun clearRefs(turnId: Long)

    @Transaction
    open suspend fun finish(id: Long, answer: String, model: String, refIds: List<Long>) {
        markDone(id, answer, model)
        clearRefs(id)
        insertRefs(refIds.mapIndexed { i, saveId -> AskRefEntity(id, saveId, i) })
    }

    @Query("DELETE FROM ask_refs")
    abstract suspend fun deleteAllRefs()

    @Query("DELETE FROM ask_turns")
    abstract suspend fun deleteAllTurns()

    /** ⋮ Clear chat. Refs go first so it works whether or not foreign keys are on. */
    @Transaction
    open suspend fun clear() {
        deleteAllRefs()
        deleteAllTurns()
    }

    @Query("DELETE FROM ask_turns WHERE id = :id")
    abstract suspend fun deleteTurnRow(id: Long)

    @Transaction
    open suspend fun deleteTurn(id: Long) {
        clearRefs(id)
        deleteTurnRow(id)
    }

    // ---- Sources ----

    /** Candidate sources: read, not deleted, matching any query word. Ranked in Kotlin (FTS4 has no bm25). */
    @Transaction
    @Query(
        "SELECT saves.*, search_fts.body AS body FROM search_fts JOIN saves ON saves.id = search_fts.rowid " +
            "WHERE search_fts MATCH :match AND saves.deletedAt IS NULL AND saves.status = 'READ' " +
            "ORDER BY saves.createdAt DESC LIMIT :limit",
    )
    abstract suspend fun candidates(match: String, limit: Int): List<AskSourceRow>

    @Transaction
    @Query(
        "SELECT saves.*, search_fts.body AS body FROM saves JOIN search_fts ON search_fts.rowid = saves.id " +
            "WHERE saves.deletedAt IS NULL AND saves.status = 'READ' ORDER BY saves.createdAt DESC LIMIT :limit",
    )
    abstract suspend fun newest(limit: Int): List<AskSourceRow>

    @Transaction
    @Query(
        "SELECT saves.*, search_fts.body AS body FROM saves JOIN search_fts ON search_fts.rowid = saves.id " +
            "WHERE saves.id IN (:ids) AND saves.deletedAt IS NULL AND saves.status = 'READ'",
    )
    abstract suspend fun byIds(ids: List<Long>): List<AskSourceRow>

    @Query("SELECT COUNT(*) FROM saves WHERE deletedAt IS NULL AND status = 'READ'")
    abstract fun observeReadCount(): Flow<Int>

    /** Starters (k-ask-first): read saves per type. */
    @Query("SELECT type, COUNT(*) AS n FROM saves WHERE deletedAt IS NULL AND status = 'READ' GROUP BY type")
    abstract suspend fun typeCounts(): List<TypeCountRow>

    @Query(
        "SELECT save_tags.tag AS tag, COUNT(*) AS n FROM save_tags JOIN saves ON saves.id = save_tags.saveId " +
            "WHERE saves.deletedAt IS NULL AND saves.status = 'READ' GROUP BY save_tags.tag ORDER BY n DESC, tag LIMIT :limit",
    )
    abstract suspend fun topTags(limit: Int): List<TagCountRow>
}

data class AskRefRow(
    val turnId: Long,
    val position: Int,
    val saveId: Long,
    val title: String,
    val type: String,
    val thumbPath: String? = null,
    val summary: String = "",
    val place: String = "",
    val example: Boolean = false,
)

data class AskSourceRow(
    @Embedded val save: SaveEntity,
    val body: String,
    @Relation(parentColumn = "id", entityColumn = "saveId", entity = SaveTagEntity::class, projection = ["tag"])
    val tags: List<String>,
    @Relation(parentColumn = "id", entityColumn = "saveId")
    val items: List<SaveItemEntity>,
)
