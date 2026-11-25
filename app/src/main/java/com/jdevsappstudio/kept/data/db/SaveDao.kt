package com.jdevsappstudio.kept.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.data.saves.ItemKey
import kotlinx.coroutines.flow.Flow

/** All suspend / Flow / Paging: Room runs them on its own executors, never on the main thread (BUILD-SPEC §4). */
@Dao
abstract class SaveDao {

    @Insert
    abstract suspend fun insert(saves: List<SaveEntity>): List<Long>

    @Query("SELECT * FROM saves WHERE id = :id")
    abstract suspend fun get(id: Long): SaveEntity?

    @Query("SELECT id FROM saves WHERE contentHash = :hash LIMIT 1")
    abstract suspend fun findByHash(hash: String): Long?

    /** Link duplicates only for saves without an image (a screenshot that shows a link is its own save). */
    @Query("SELECT id FROM saves WHERE url = :url AND imagePath IS NULL LIMIT 1")
    abstract suspend fun findByUrl(url: String): Long?

    @Query("SELECT status, COUNT(*) AS n FROM saves WHERE deletedAt IS NULL GROUP BY status")
    abstract fun observeStatusCounts(): Flow<List<StatusCount>>

    // ---- Read queue ----

    @Query("SELECT * FROM saves WHERE status = 'QUEUED' AND deletedAt IS NULL AND nextAttemptAt <= :now ORDER BY createdAt LIMIT 1")
    protected abstract suspend fun nextDue(now: Long): SaveEntity?

    @Query("UPDATE saves SET status = 'READING' WHERE id = :id AND status = 'QUEUED'")
    protected abstract suspend fun markReading(id: Long): Int

    /** Takes the oldest due row and marks it READING in one transaction, so the loop and the worker never read the same save. */
    @Transaction
    open suspend fun claimNext(now: Long): SaveEntity? {
        val next = nextDue(now) ?: return null
        return if (markReading(next.id) == 1) next.copy(status = SaveStatus.READING) else null
    }

    /** Earliest time a waiting row becomes due, or null if nothing is queued. */
    @Query("SELECT MIN(nextAttemptAt) FROM saves WHERE status = 'QUEUED' AND deletedAt IS NULL")
    abstract suspend fun earliestDue(): Long?

    /** A read cut short (app left the screen, scope cancelled): back to the queue, no try counted. */
    @Query("UPDATE saves SET status = 'QUEUED' WHERE id = :id AND status = 'READING'")
    abstract suspend fun requeue(id: Long)

    /** Process death: rows caught mid-read go back to the queue. */
    @Query("UPDATE saves SET status = 'QUEUED' WHERE status = 'READING'")
    abstract suspend fun requeueInterrupted(): Int

    @Query(
        "UPDATE saves SET status = :status, failReason = :reason, attempts = :attempts, nextAttemptAt = :nextAttemptAt " +
            "WHERE id = :id",
    )
    abstract suspend fun markFailure(id: Long, status: String, reason: String, attempts: Int, nextAttemptAt: Long)

    @Query("UPDATE saves SET thumbPath = :path WHERE id = :id")
    abstract suspend fun setThumb(id: Long, path: String)

    // ---- Card ----

    @Query(
        "UPDATE saves SET status = 'READ', failReason = NULL, readBy = :readBy, model = :model, title = :title, " +
            "summary = :summary, type = :type, kind = :kind, place = :place, eventDate = :eventDate, link = :link, " +
            "readAt = :readAt WHERE id = :id",
    )
    protected abstract suspend fun updateCard(
        id: Long, readBy: String, model: String?, title: String, summary: String, type: String, kind: String,
        place: String, eventDate: String, link: String, readAt: Long,
    )

    @Query("DELETE FROM save_tags WHERE saveId = :saveId")
    protected abstract suspend fun deleteTags(saveId: Long)

    @Insert
    protected abstract suspend fun insertTags(tags: List<SaveTagEntity>)

    @Query("DELETE FROM save_items WHERE saveId = :saveId")
    protected abstract suspend fun deleteItems(saveId: Long)

    @Insert
    protected abstract suspend fun insertItems(items: List<SaveItemEntity>)

    @Query("DELETE FROM search_fts WHERE rowid = :saveId")
    protected abstract suspend fun deleteFts(saveId: Long)

    @Insert
    protected abstract suspend fun insertFts(row: SearchFtsEntity)

    @Query("SELECT tag FROM save_tags WHERE saveId = :saveId ORDER BY tag")
    abstract suspend fun tags(saveId: Long): List<String>

    @Query("SELECT name FROM save_items WHERE saveId = :saveId ORDER BY id")
    abstract suspend fun items(saveId: Long): List<String>

    /** Card fields, tags, items and the search row change together or not at all. */
    @Transaction
    open suspend fun applyCard(card: CardRow, tags: List<String>, items: List<String>, searchBody: String) {
        updateCard(
            card.id, card.readBy, card.model, card.title, card.summary, card.type, card.kind,
            card.place, card.eventDate, card.link, card.readAt,
        )
        deleteTags(card.id)
        if (tags.isNotEmpty()) insertTags(tags.map { SaveTagEntity(card.id, it) })
        deleteItems(card.id)
        if (items.isNotEmpty()) insertItems(items.map { SaveItemEntity(saveId = card.id, name = it, itemKey = ItemKey.of(it)) })
        deleteFts(card.id)
        insertFts(SearchFtsEntity(card.id, searchBody))
    }

    @Query("DELETE FROM saves WHERE id = :id")
    protected abstract suspend fun deleteSaveRow(id: Long)

    /** Tags, items and collection links cascade; the FTS table has no foreign keys, so it goes by hand. */
    @Transaction
    open suspend fun delete(id: Long) {
        deleteFts(id)
        deleteSaveRow(id)
    }
}

/** Arguments of [SaveDao.applyCard], kept apart so the call site stays readable. */
data class CardRow(
    val id: Long,
    val readBy: String,
    val model: String?,
    val title: String,
    val summary: String,
    val type: String,
    val kind: String,
    val place: String,
    val eventDate: String,
    val link: String,
    val readAt: Long,
)
