package com.jdevsappstudio.kept.data.library

import androidx.room.withTransaction
import com.jdevsappstudio.kept.core.model.MadeCollection
import com.jdevsappstudio.kept.core.model.SaveCollection
import com.jdevsappstudio.kept.core.model.TagCount
import com.jdevsappstudio.kept.core.model.TypeChip
import com.jdevsappstudio.kept.data.db.CollectionDao
import com.jdevsappstudio.kept.data.db.CollectionEntity
import com.jdevsappstudio.kept.data.db.CollectionRow
import com.jdevsappstudio.kept.data.db.CollectionSaveEntity
import com.jdevsappstudio.kept.data.db.KeptDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Collections (H51 step 3): k-collection, k-newcol, the edit sheet's ticks, search pick mode and home's suggestion card.
 * The saves inside one come from [LibraryLocalDataSource.pagedSaves] with `SaveFilter(collection = id)`. Every call is main-safe.
 */
interface CollectionsLocalDataSource {
    /** H55: newest made first. */
    fun observeCollections(): Flow<List<SaveCollection>>
    fun observeCollection(id: Long): Flow<SaveCollection?>
    fun observeTypeChips(id: Long): Flow<List<TypeChip>>
    fun observeSaveIds(id: Long): Flow<Set<Long>>

    /** Tags on [CollectionSuggestions.MIN_SAVES]+ read saves, most first ([CollectionSuggestions.pick] takes it from there). */
    fun observeSuggestibleTags(): Flow<List<TagCount>>

    /** For each collection holding any of [saveIds], how many of them it holds. */
    suspend fun membership(saveIds: List<Long>): Map<Long, Int>

    /** Null when the name is empty or already used ([CollectionNames]). */
    suspend fun create(name: String): Long?
    /** False when the name is empty or used by another collection. */
    suspend fun rename(id: Long, name: String): Boolean
    suspend fun delete(id: Long)

    /** Returns how many went in (saves already in, or gone, don't count). Nothing happens if the collection is gone. */
    suspend fun addSaves(id: Long, saveIds: Collection<Long>): Int
    suspend fun removeSaves(id: Long, saveIds: Collection<Long>)

    /** Make it: a collection named after [tag] with every read save that has it. Null if that name is taken. */
    suspend fun makeFromTag(tag: String): MadeCollection?
}

class RoomCollectionsDataSource(
    private val db: KeptDatabase,
    private val dao: CollectionDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : CollectionsLocalDataSource {

    override fun observeCollections(): Flow<List<SaveCollection>> =
        combine(dao.observeAll(), dao.observePreviews()) { rows, pics ->
            val previews = pics.groupBy({ it.grp }, { it.path })
            rows.map { it.toModel().copy(previews = previews[it.id.toString()].orEmpty()) }
        }.distinctUntilChanged()

    override fun observeCollection(id: Long): Flow<SaveCollection?> =
        dao.observe(id).map { it?.toModel() }.distinctUntilChanged()

    override fun observeTypeChips(id: Long): Flow<List<TypeChip>> =
        dao.observeTypeCounts(id).map { rows ->
            TypeChips.from(rows.filter { it.type.isNotEmpty() }.associate { it.type to it.n }, preferred = emptyList())
        }.distinctUntilChanged()

    override fun observeSaveIds(id: Long): Flow<Set<Long>> = dao.observeSaveIds(id).map { it.toSet() }.distinctUntilChanged()

    override fun observeSuggestibleTags(): Flow<List<TagCount>> =
        dao.observeTagsWithAtLeast(CollectionSuggestions.MIN_SAVES).map { rows -> rows.map { TagCount(it.tag, it.n) } }
            .distinctUntilChanged()

    override suspend fun membership(saveIds: List<Long>): Map<Long, Int> =
        if (saveIds.isEmpty()) emptyMap() else dao.membership(saveIds).associate { it.collectionId to it.n }

    override suspend fun create(name: String): Long? {
        val clean = CollectionNames.clean(name).ifEmpty { return null }
        return db.withTransaction {
            if (CollectionNames.taken(clean, all()) != null) return@withTransaction null
            dao.insert(CollectionEntity(name = clean, createdAt = clock()))
        }
    }

    override suspend fun rename(id: Long, name: String): Boolean {
        val clean = CollectionNames.clean(name).ifEmpty { return false }
        return db.withTransaction {
            if (dao.get(id) == null || CollectionNames.taken(clean, all(), exceptId = id) != null) return@withTransaction false
            dao.rename(id, clean)
            true
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun addSaves(id: Long, saveIds: Collection<Long>): Int {
        if (saveIds.isEmpty()) return 0
        return db.withTransaction {
            if (dao.get(id) == null) return@withTransaction 0
            val live = dao.existing(saveIds.distinct())
            dao.link(live.map { CollectionSaveEntity(id, it) }).count { it != -1L }
        }
    }

    override suspend fun removeSaves(id: Long, saveIds: Collection<Long>) {
        if (saveIds.isNotEmpty()) dao.unlink(id, saveIds.distinct())
    }

    override suspend fun makeFromTag(tag: String): MadeCollection? {
        val name = CollectionNames.fromTag(tag).ifEmpty { return null }
        return db.withTransaction {
            if (CollectionNames.taken(name, all()) != null) return@withTransaction null
            val id = dao.insert(CollectionEntity(name = name, createdAt = clock()))
            val saves = dao.readSavesWithTag(tag)
            dao.link(saves.map { CollectionSaveEntity(id, it) })
            MadeCollection(id, name, saves.size)
        }
    }

    private suspend fun all(): List<SaveCollection> = dao.all().map { it.toModel() }

    private fun CollectionRow.toModel() = SaveCollection(id, name, createdAt, saves)
}
