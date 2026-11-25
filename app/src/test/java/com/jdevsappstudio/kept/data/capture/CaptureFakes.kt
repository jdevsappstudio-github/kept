package com.jdevsappstudio.kept.data.capture

import com.jdevsappstudio.kept.core.model.NewSave
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.data.ai.CardDraft
import com.jdevsappstudio.kept.data.ai.ReadFailure
import com.jdevsappstudio.kept.data.files.CopyResult
import com.jdevsappstudio.kept.data.saves.QueuedSave
import com.jdevsappstudio.kept.data.saves.FillTarget
import com.jdevsappstudio.kept.data.saves.SaveLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Saves table for capture tests: only add / findDuplicate / addNote. */
class FakeSaveStore : SaveLocalDataSource {
    val saves = mutableListOf<NewSave>()
    val notes = mutableListOf<Pair<NewSave, String>>()
    private var nextId = 1L
    private val byHash = mutableMapOf<String, Long>()
    private val byUrl = mutableMapOf<String, Long>()

    override suspend fun add(saves: List<NewSave>): List<Long> = saves.map { s ->
        val id = nextId++
        this.saves += s
        s.contentHash?.let { byHash[it] = id }
        if (s.imagePath == null) s.url?.let { byUrl[it] = id }
        id
    }

    override suspend fun findDuplicate(contentHash: String?, url: String?): Long? =
        contentHash?.let { byHash[it] } ?: url?.let { byUrl[it] }

    override suspend fun addNote(save: NewSave, title: String): Long {
        notes += save to title
        return nextId++
    }

    override fun observeCounts(): Flow<QueueCounts> = emptyFlow()
    override suspend fun claimNext(now: Long): QueuedSave? = null
    override suspend fun earliestDue(): Long? = null
    override suspend fun requeueInterrupted() = Unit
    override suspend fun requeue(id: Long) = Unit
    override suspend fun saveThumb(id: Long, path: String) = Unit
    override suspend fun saveCard(id: Long, card: CardDraft, readBy: ReadBy, model: String?, now: Long) = Unit
    override suspend fun saveFailure(id: Long, reason: ReadFailure, attempts: Int, retryAt: Long?) = Unit
    override suspend fun delete(id: Long) = Unit

    // Fill in (H59) + Add a screenshot
    val fillTargets = mutableMapOf<Long, FillTarget>()
    val filled = mutableListOf<Triple<Long, CardDraft, String>>()
    val attached = mutableMapOf<Long, String>()
    /** Ids that already have an image (attach refuses them). */
    val withImage = mutableSetOf<Long>()

    override suspend fun fillTarget(id: Long): FillTarget? = fillTargets[id]
    override suspend fun saveFill(id: Long, card: CardDraft, model: String, now: Long) {
        filled += Triple(id, card, model)
    }
    override suspend fun attachImage(id: Long, path: String, sha256: String): Boolean {
        if (id in withImage) return false
        attached[id] = path
        withImage += id
        return true
    }
}

/** ref "img:<hash>[#n]" copies with that hash (#n = another URI, same bytes); "big:…" is too big; "bad:…" fails. */
class FakeCopier : ImageCopier {
    val deleted = mutableListOf<String>()
    var copies = 0

    override suspend fun copyIn(ref: String): CopyResult {
        copies++
        val (kind, rest) = ref.split(":", limit = 2)
        return when (kind) {
            "img" -> CopyResult.Copied(path = "files/$copies.jpg", sha256 = rest.substringBefore('#'))
            "big" -> CopyResult.TooBig
            else -> CopyResult.Failed
        }
    }

    override suspend fun delete(path: String) {
        deleted += path
    }
}
