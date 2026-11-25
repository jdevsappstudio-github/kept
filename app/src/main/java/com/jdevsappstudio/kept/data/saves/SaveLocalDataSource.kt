package com.jdevsappstudio.kept.data.saves

import com.jdevsappstudio.kept.core.model.NewSave
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.data.ai.CardDraft
import com.jdevsappstudio.kept.data.ai.ReadFailure
import kotlinx.coroutines.flow.Flow

/** What the read queue needs from one save. */
data class QueuedSave(
    val id: Long,
    val imagePath: String?,
    val thumbPath: String?,
    val sharedText: String?,
    val url: String?,
    val attempts: Int,
)

/** What Fill in with cloud needs from one save (H59). */
data class FillTarget(
    val id: Long,
    val url: String,
    val sharedText: String?,
    val title: String,
    /** Fill in only runs on a live, caption-only save that hasn't been filled since its last read. */
    val canFill: Boolean,
)

/** The saves table as the share target, queue and screens see it. Every call is main-safe. */
interface SaveLocalDataSource {
    suspend fun add(saves: List<NewSave>): List<Long>

    /** Id of a save with the same image bytes, or (for link-only saves) the same link; null if new. */
    suspend fun findDuplicate(contentHash: String?, url: String?): Long?

    /** A save Kept can't read (plain text, too-big image): stored as a finished note, never sent anywhere. */
    suspend fun addNote(save: NewSave, title: String): Long

    fun observeCounts(): Flow<QueueCounts>

    /** Oldest due QUEUED save, already marked READING; null if none is due. */
    suspend fun claimNext(now: Long): QueuedSave?

    suspend fun earliestDue(): Long?

    /** On start: READING rows left by a killed process go back to QUEUED. */
    suspend fun requeueInterrupted()

    /** One read cut short: back to QUEUED, no try counted. */
    suspend fun requeue(id: Long)

    /** The 320 px list thumbnail, made once before the first read. */
    suspend fun saveThumb(id: Long, path: String)

    suspend fun saveCard(id: Long, card: CardDraft, readBy: ReadBy, model: String?, now: Long)

    /** [retryAt] null = give up: the save goes to "Needs a look" with [reason]; the raw save is kept. */
    suspend fun saveFailure(id: Long, reason: ReadFailure, attempts: Int, retryAt: Long?)

    suspend fun delete(id: Long)

    /** Null if the save is gone or has no link. */
    suspend fun fillTarget(id: Long): FillTarget?

    /**
     * Fill in with cloud (H59): the page's card replaces title, type, kind, summary, place, date, link and items.
     * Tags already on the save stay (Kept doesn't know which ones were typed by hand); the page's tags are added.
     */
    suspend fun saveFill(id: Long, card: CardDraft, model: String, now: Long)

    /** Add a screenshot (k-link): false if the save is gone or already has an image. */
    suspend fun attachImage(id: Long, path: String, sha256: String): Boolean
}
