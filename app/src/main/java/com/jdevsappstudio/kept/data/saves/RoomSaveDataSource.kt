package com.jdevsappstudio.kept.data.saves

import com.jdevsappstudio.kept.core.model.NewSave
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.data.ai.CardDraft
import com.jdevsappstudio.kept.data.ai.ReadFailure
import com.jdevsappstudio.kept.data.db.CardRow
import com.jdevsappstudio.kept.data.db.SaveDao
import com.jdevsappstudio.kept.data.db.SaveEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/** Room-backed saves. DAO calls run on Room's executors; file deletes on [fileDispatcher]. */
class RoomSaveDataSource(
    private val dao: SaveDao,
    private val fileDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) : SaveLocalDataSource {

    override suspend fun add(saves: List<NewSave>): List<Long> {
        val now = clock()
        return dao.insert(
            saves.mapIndexed { i, s ->
                // +i keeps the share order stable when 30 screenshots land in the same millisecond.
                SaveEntity(
                    createdAt = now + i, source = s.source, sourceApp = s.sourceApp,
                    sharedText = s.sharedText, url = s.url, imagePath = s.imagePath, contentHash = s.contentHash,
                )
            },
        )
    }

    override suspend fun findDuplicate(contentHash: String?, url: String?): Long? =
        contentHash?.let { dao.findByHash(it) } ?: url?.let { dao.findByUrl(it) }

    override suspend fun addNote(save: NewSave, title: String): Long {
        val id = add(listOf(save)).first()
        val note = CardDraft(
            title = title, type = "other", kind = "notes", tags = emptyList(), items = emptyList(),
            place = "", date = "", link = save.url.orEmpty(), summary = "",
        )
        saveCard(id, note, ReadBy.HAND, model = null, now = clock())
        return id
    }

    override fun observeCounts(): Flow<QueueCounts> =
        dao.observeStatusCounts().map { rows ->
            val by = rows.associate { it.status to it.n }
            QueueCounts(
                queued = by[SaveStatus.QUEUED] ?: 0,
                reading = by[SaveStatus.READING] ?: 0,
                needsLook = by[SaveStatus.NEEDS_LOOK] ?: 0,
                read = by[SaveStatus.READ] ?: 0,
            )
        }.distinctUntilChanged()

    override suspend fun claimNext(now: Long): QueuedSave? =
        dao.claimNext(now)?.let { QueuedSave(it.id, it.imagePath, it.thumbPath, it.sharedText, it.url, it.attempts) }

    override suspend fun earliestDue(): Long? = dao.earliestDue()

    override suspend fun requeueInterrupted() {
        dao.requeueInterrupted()
    }

    override suspend fun requeue(id: Long) = dao.requeue(id)

    override suspend fun saveThumb(id: Long, path: String) = dao.setThumb(id, path)

    override suspend fun saveCard(id: Long, card: CardDraft, readBy: ReadBy, model: String?, now: Long) {
        val save = dao.get(id) ?: return // deleted while it was being read
        val tags = CardMapping.tags(card)
        val items = CardMapping.items(card)
        dao.applyCard(
            CardRow(
                id = id, readBy = readBy.name, model = model, title = card.title.trim(), summary = card.summary.trim(),
                type = card.type.trim().lowercase(), kind = CardMapping.kind(card), place = card.place.trim(),
                eventDate = card.date.trim(), link = card.link.trim(), readAt = now,
            ),
            tags = tags,
            items = items,
            searchBody = CardMapping.searchBody(card, tags, items, save.sharedText, save.url),
        )
    }

    override suspend fun saveFailure(id: Long, reason: ReadFailure, attempts: Int, retryAt: Long?) {
        val status = if (retryAt == null) SaveStatus.NEEDS_LOOK else SaveStatus.QUEUED
        dao.markFailure(id, status.name, reason.name, attempts, retryAt ?: 0)
    }

    override suspend fun delete(id: Long) {
        val save = dao.get(id) ?: return
        dao.delete(id)
        withContext(fileDispatcher) {
            listOfNotNull(save.imagePath, save.thumbPath).forEach { runCatching { File(it).delete() } }
        }
    }
}
