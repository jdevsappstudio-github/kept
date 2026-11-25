package com.jdevsappstudio.kept.data.sent

import com.jdevsappstudio.kept.core.model.SentKind
import com.jdevsappstudio.kept.core.model.SentRequest
import com.jdevsappstudio.kept.data.db.SentDao
import com.jdevsappstudio.kept.data.db.SentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 'What was sent' (k-sent, H2 + H58): one row per cloud request, written just before it leaves the phone.
 * Ask logs its whole prompt (instructions + the saves' text); a cloud read logs the save's own text and its image
 * count (the reader's fixed instructions hold nothing of the person's). Kept [KEEP_DAYS] days.
 */
interface SentLogLocalDataSource {
    fun observe(): Flow<List<SentRequest>>
    suspend fun log(kind: SentKind, text: String, images: Int = 0, saveId: Long? = null, now: Long = System.currentTimeMillis()): Long
    /** Drops rows older than [KEEP_DAYS]; returns how many went. */
    suspend fun prune(now: Long = System.currentTimeMillis()): Int
    suspend fun clear()

    companion object {
        const val KEEP_DAYS = 30
        const val KEEP_MS = KEEP_DAYS * 24L * 60 * 60 * 1000
    }
}

class RoomSentLogDataSource(private val dao: SentDao) : SentLogLocalDataSource {

    override fun observe(): Flow<List<SentRequest>> = dao.observeAll().map { rows ->
        rows.map { SentRequest(it.id, it.sentAt, it.kind, it.text, it.images, it.saveId) }
    }

    override suspend fun log(kind: SentKind, text: String, images: Int, saveId: Long?, now: Long): Long =
        dao.insert(SentEntity(sentAt = now, kind = kind, text = text, images = images, saveId = saveId))

    override suspend fun prune(now: Long): Int = dao.deleteBefore(now - SentLogLocalDataSource.KEEP_MS)

    override suspend fun clear() = dao.deleteAll()
}
