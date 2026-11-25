package com.jdevsappstudio.kept.data.library

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.jdevsappstudio.kept.core.model.AddItemResult
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveFilter
import com.jdevsappstudio.kept.core.model.SaveItem
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.Shelf
import com.jdevsappstudio.kept.core.model.ShelfEntry
import com.jdevsappstudio.kept.core.model.ShelfMerge
import com.jdevsappstudio.kept.core.model.ShelfRemoval
import com.jdevsappstudio.kept.core.model.SourceCounts
import com.jdevsappstudio.kept.core.model.SourceFilter
import com.jdevsappstudio.kept.core.model.TagCount
import com.jdevsappstudio.kept.core.model.TypeChip
import com.jdevsappstudio.kept.data.db.KeptDatabase
import com.jdevsappstudio.kept.data.db.LibraryDao
import com.jdevsappstudio.kept.data.db.SaveItemEntity
import com.jdevsappstudio.kept.data.db.SaveWithDetails
import com.jdevsappstudio.kept.data.db.SearchFtsEntity
import com.jdevsappstudio.kept.data.saves.CardMapping
import com.jdevsappstudio.kept.data.saves.ItemKey
import com.jdevsappstudio.kept.data.saves.SaveLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Room-backed library. Multi-step edits run in one transaction and rebuild the save's search row. */
class RoomLibraryDataSource(
    private val db: KeptDatabase,
    private val dao: LibraryDao,
    private val saves: SaveLocalDataSource,
    private val clock: () -> Long = System::currentTimeMillis,
) : LibraryLocalDataSource {

    override fun pagedSaves(filter: SaveFilter): Flow<PagingData<Save>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            dao.paging(
                type = filter.type?.wire,
                shelf = filter.shelf,
                tag = filter.tag,
                shots = filter.source?.let { it == SourceFilter.SCREENSHOTS },
                collection = filter.collection,
            )
        }.flow.map { page -> page.map { it.toSave() } }

    override fun observeNewest(): Flow<Save?> = dao.observeNewest().map { it?.toSave() }.distinctUntilChanged()

    override fun observeSave(id: Long): Flow<Save?> = dao.observeSave(id).map { it?.toSave() }.distinctUntilChanged()

    override fun observeItems(saveId: Long): Flow<List<SaveItem>> =
        dao.observeItems(saveId).map { rows -> rows.map { SaveItem(it.id, it.name, it.hidden, it.otherSaves) } }
            .distinctUntilChanged()

    override fun observeTypeChips(preferred: List<SaveType>): Flow<List<TypeChip>> =
        dao.observeTypeCounts().map { rows ->
            TypeChips.from(rows.filter { it.type.isNotEmpty() }.associate { it.type to it.n }, preferred)
        }.distinctUntilChanged()

    override fun observeSourceCounts(filter: SaveFilter): Flow<SourceCounts> =
        dao.observeSourceCounts(filter.type?.wire, filter.shelf).map { SourceCounts(it.screenshots, it.links) }
            .distinctUntilChanged()

    override fun observeTags(filter: SaveFilter, limit: Int): Flow<List<TagCount>> =
        dao.observeTagCounts(filter.type?.wire, filter.shelf, limit).map { rows -> rows.map { TagCount(it.tag, it.n) } }
            .distinctUntilChanged()

    override fun observeAllTags(): Flow<List<String>> = dao.observeAllTags().distinctUntilChanged()

    override fun observeShelves(): Flow<List<Shelf>> =
        dao.observeShelves().map { rows ->
            ShelfOrder.sort(
                rows.map {
                    Shelf(
                        kind = it.kind, title = ShelfOrder.title(it.kind, it.name), saves = it.saves, items = it.items,
                        entries = if (it.items > 0) it.items + it.bareSaves else it.saves,
                        hidden = it.hidden, position = it.position,
                    )
                },
            )
        }.distinctUntilChanged()

    override fun observeShelfEntries(shelf: String, tag: String?): Flow<List<ShelfEntry>> =
        dao.observeShelfEntries(shelf, tag).map { rows ->
            rows.map { ShelfEntry(it.itemKey, it.name, it.saves, it.saveId, it.sourceApp) }
        }.distinctUntilChanged()

    // ---- Edit sheet ----

    override suspend fun edit(id: Long, title: String, type: SaveType, tags: List<String>) {
        db.withTransaction {
            dao.setTitleType(id, title.trim(), type.wire)
            dao.clearTags(id)
            tags.mapNotNull(CardMapping::cleanTag).distinct().forEach { dao.addTag(id, it) }
            dao.markHandRead(id, clock())
            reindex(id)
        }
    }

    override suspend fun editMany(ids: List<Long>, type: SaveType?, addTags: List<String>, removeTags: List<String>) {
        val add = addTags.mapNotNull(CardMapping::cleanTag).distinct()
        val remove = removeTags.mapNotNull(CardMapping::cleanTag).distinct()
        db.withTransaction {
            ids.forEach { id ->
                type?.let { dao.setType(id, it.wire) }
                if (remove.isNotEmpty()) dao.removeTags(id, remove)
                add.forEach { dao.addTag(id, it) }
                if (type != null) dao.markHandRead(id, clock())
                reindex(id)
            }
        }
    }

    // ---- Items ----

    override suspend fun renameItem(itemId: Long, name: String) {
        val clean = name.trim().ifEmpty { return }
        db.withTransaction {
            val item = dao.item(itemId) ?: return@withTransaction
            dao.renameItem(itemId, clean, ItemKey.of(clean))
            reindex(item.saveId)
        }
    }

    override suspend fun deleteItem(itemId: Long) {
        db.withTransaction {
            val item = dao.item(itemId) ?: return@withTransaction
            dao.deleteItem(itemId)
            reindex(item.saveId)
        }
    }

    override suspend fun addItem(saveId: Long, name: String): AddItemResult {
        val clean = name.trim()
        val key = ItemKey.of(clean)
        if (key.isEmpty()) return AddItemResult(added = false, alreadyOnShelf = null)
        return db.withTransaction {
            val save = dao.getWithDetails(saveId) ?: return@withTransaction AddItemResult(false, null)
            if (save.items.any { it.itemKey == key }) return@withTransaction AddItemResult(false, null)
            val elsewhere = dao.shelfWithItem(key, saveId)?.let { kind -> ShelfOrder.title(kind, dao.shelf(kind)?.name) }
            dao.insertItem(SaveItemEntity(saveId = saveId, name = clean, itemKey = key))
            reindex(saveId)
            AddItemResult(added = true, alreadyOnShelf = elsewhere)
        }
    }

    // ---- Delete + Undo, Read again ----

    override suspend fun delete(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.softDelete(ids, clock())
    }

    override suspend fun undoDelete(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.restore(ids)
    }

    override suspend fun purgeStale(graceMs: Long) {
        dao.deletedBefore(clock() - graceMs).forEach { saves.delete(it) }
    }

    override suspend fun readAgain(id: Long): Boolean = dao.readAgain(id) == 1

    // ---- Shelf menu ----

    override suspend fun setShelfHidden(kind: String, hidden: Boolean) {
        db.withTransaction {
            dao.ensureShelf(kind)
            dao.setShelfHidden(kind, hidden)
        }
    }

    override suspend fun renameShelf(kind: String, name: String?) {
        db.withTransaction {
            dao.ensureShelf(kind)
            dao.setShelfName(kind, name?.trim()?.ifEmpty { null })
        }
    }

    override suspend fun mergeShelf(from: String, into: String): ShelfMerge? {
        if (from == into) return null
        return db.withTransaction {
            dao.ensureShelf(from)
            dao.ensureShelf(into)
            // Merging back the other way: the target stops pointing at us first, so there is never a loop.
            if (dao.shelf(into)?.mergedInto == from) dao.unmergeShelf(into)
            val carried = dao.mergedInto(from)
            dao.mergeShelf(from, into)
            ShelfMerge(from, into, carried)
        }
    }

    override suspend fun undoMerge(merge: ShelfMerge) {
        db.withTransaction {
            dao.unmergeShelf(merge.from)
            merge.carried.forEach { dao.mergeShelf(it, merge.from) }
        }
    }

    override suspend fun reorderShelves(kinds: List<String>) {
        db.withTransaction {
            dao.clearPositions()
            kinds.forEachIndexed { i, kind ->
                dao.ensureShelf(kind)
                dao.setPosition(kind, i)
            }
        }
    }

    override suspend fun removeFromShelf(shelf: String, entry: ShelfEntry): ShelfRemoval =
        db.withTransaction {
            if (entry.isSave) {
                val kind = dao.getWithDetails(entry.saveId)?.save?.kind
                dao.setKind(entry.saveId, "")
                reindex(entry.saveId)
                ShelfRemoval(shelf, entry, kind)
            } else {
                dao.setItemHiddenOnShelf(shelf, entry.key, hidden = true)
                ShelfRemoval(shelf, entry, previousKind = null)
            }
        }

    override suspend fun undoRemoveFromShelf(removal: ShelfRemoval) {
        db.withTransaction {
            val entry = removal.entry
            if (entry.isSave) {
                dao.setKind(entry.saveId, removal.previousKind ?: removal.shelf)
                reindex(entry.saveId)
            } else {
                dao.setItemHiddenOnShelf(removal.shelf, entry.key, hidden = false)
            }
        }
    }

    /** Rebuilds the save's one search row from what is stored now. Call inside a transaction. */
    private suspend fun reindex(id: Long) {
        val d = dao.getWithDetails(id) ?: return
        val s = d.save
        dao.deleteFts(id)
        dao.insertFts(
            SearchFtsEntity(
                id,
                CardMapping.searchBody(
                    s.title, s.summary, s.kind, SaveType.fromWire(s.type), d.tags.sorted(),
                    d.items.sortedBy { it.id }.map { it.name }, s.place, s.eventDate, s.link, s.sharedText, s.url,
                ),
            ),
        )
    }

    private companion object {
        const val PAGE_SIZE = 40
    }
}

/** Entity → domain. Tags A–Z, items in reading order. */
internal fun SaveWithDetails.toSave(): Save = Save(
    id = save.id, createdAt = save.createdAt, source = save.source, sourceApp = save.sourceApp,
    sharedText = save.sharedText, url = save.url, imagePath = save.imagePath, thumbPath = save.thumbPath,
    status = save.status, failReason = save.failReason, readBy = save.readBy, title = save.title,
    summary = save.summary, type = SaveType.fromWire(save.type), kind = save.kind, place = save.place,
    eventDate = save.eventDate, link = save.link, tags = tags.sorted(),
    items = items.sortedBy { it.id }.map { it.name }, readAt = save.readAt,
    filledAt = save.filledAt,
)
