package com.jdevsappstudio.kept.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Library reads + edits (H51 step 1). Lists, counts and shelves come from SQL, never from loading rows.
 * Every list skips soft-deleted saves. Multi-step edits run in `withTransaction` in [RoomLibraryDataSource].
 */
@Dao
abstract class LibraryDao {

    // ---- Lists ----

    @Transaction
    @Query(
        "SELECT * FROM saves WHERE deletedAt IS NULL $SCOPE " +
            "AND (:tag IS NULL OR id IN (SELECT saveId FROM save_tags WHERE tag = :tag)) " +
            "AND (:shots IS NULL OR (imagePath IS NOT NULL) = :shots) " +
            "ORDER BY createdAt DESC",
    )
    abstract fun paging(type: String?, shelf: String?, tag: String?, shots: Boolean?): PagingSource<Int, SaveWithDetails>

    /** "Just kept" hero: the newest save, read or not. */
    @Transaction
    @Query("SELECT * FROM saves WHERE deletedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    abstract fun observeNewest(): Flow<SaveWithDetails?>

    @Transaction
    @Query("SELECT * FROM saves WHERE id = :id AND deletedAt IS NULL")
    abstract fun observeSave(id: Long): Flow<SaveWithDetails?>

    @Transaction
    @Query("SELECT * FROM saves WHERE id = :id")
    abstract suspend fun getWithDetails(id: Long): SaveWithDetails?

    // ---- Counts ----

    @Query("SELECT type, COUNT(*) AS n FROM saves WHERE deletedAt IS NULL AND status = 'READ' GROUP BY type")
    abstract fun observeTypeCounts(): Flow<List<TypeCountRow>>

    @Query(
        "SELECT COALESCE(SUM(imagePath IS NOT NULL), 0) AS screenshots, COALESCE(SUM(imagePath IS NULL), 0) AS links " +
            "FROM saves WHERE deletedAt IS NULL $SCOPE",
    )
    abstract fun observeSourceCounts(type: String?, shelf: String?): Flow<SourceCountRow>

    @Query(
        "SELECT t.tag AS tag, COUNT(*) AS n FROM save_tags t JOIN saves ON saves.id = t.saveId " +
            "WHERE saves.deletedAt IS NULL $SCOPE GROUP BY t.tag ORDER BY n DESC, t.tag LIMIT :limit",
    )
    abstract fun observeTagCounts(type: String?, shelf: String?, limit: Int): Flow<List<TagCountRow>>

    /** Edit sheet suggestions: every tag in use, most used first. */
    @Query(
        "SELECT t.tag FROM save_tags t JOIN saves s ON s.id = t.saveId WHERE s.deletedAt IS NULL " +
            "GROUP BY t.tag ORDER BY COUNT(*) DESC, t.tag",
    )
    abstract fun observeAllTags(): Flow<List<String>>

    // ---- Shelves ----

    /** Every kind with 3+ read saves, with its settings. Sorted in Kotlin ([com.jdevsappstudio.kept.data.library.ShelfOrder]). */
    @Query(
        "SELECT v.shelf AS kind, COUNT(*) AS saves, " +
            "(SELECT COUNT(DISTINCT i.itemKey) FROM save_items i JOIN shelf_saves v2 ON v2.saveId = i.saveId " +
            "  WHERE v2.shelf = v.shelf AND i.hidden = 0) AS items, " +
            "(SELECT COUNT(*) FROM shelf_saves v3 WHERE v3.shelf = v.shelf " +
            "  AND NOT EXISTS (SELECT 1 FROM save_items i3 WHERE i3.saveId = v3.saveId)) AS bareSaves, " +
            "sh.name AS name, COALESCE(sh.hidden, 0) AS hidden, sh.position AS position " +
            "FROM shelf_saves v LEFT JOIN shelves sh ON sh.kind = v.shelf " +
            "GROUP BY v.shelf, sh.name, sh.hidden, sh.position HAVING COUNT(*) >= 3",
    )
    abstract fun observeShelves(): Flow<List<ShelfRow>>

    /**
     * Rows of one shelf, newest first: items merged by key (name + source from the newest save that has it),
     * then saves with no items at all as their own rows. [tag] narrows to saves with that tag (sub-kind chips).
     */
    @Query(
        "SELECT i.itemKey AS itemKey, i.name AS name, COUNT(DISTINCT i.saveId) AS saves, i.saveId AS saveId, " +
            "s.sourceApp AS sourceApp, MAX(s.createdAt) AS newest " +
            "FROM save_items i JOIN shelf_saves v ON v.saveId = i.saveId JOIN saves s ON s.id = i.saveId " +
            "WHERE v.shelf = :shelf AND i.hidden = 0 AND i.itemKey != '' " +
            "AND (:tag IS NULL OR i.saveId IN (SELECT saveId FROM save_tags WHERE tag = :tag)) " +
            "GROUP BY i.itemKey " +
            "UNION ALL " +
            "SELECT '' AS itemKey, s.title AS name, 1 AS saves, s.id AS saveId, s.sourceApp AS sourceApp, s.createdAt AS newest " +
            "FROM saves s JOIN shelf_saves v ON v.saveId = s.id " +
            "WHERE v.shelf = :shelf AND NOT EXISTS (SELECT 1 FROM save_items i WHERE i.saveId = s.id) " +
            "AND (:tag IS NULL OR s.id IN (SELECT saveId FROM save_tags WHERE tag = :tag)) " +
            "ORDER BY newest DESC",
    )
    abstract fun observeShelfEntries(shelf: String, tag: String?): Flow<List<ShelfEntryRow>>

    @Query("INSERT OR IGNORE INTO shelves (kind, hidden) VALUES (:kind, 0)")
    abstract suspend fun ensureShelf(kind: String)

    @Query("UPDATE shelves SET hidden = :hidden WHERE kind = :kind")
    abstract suspend fun setShelfHidden(kind: String, hidden: Boolean)

    @Query("UPDATE shelves SET name = :name WHERE kind = :kind")
    abstract suspend fun setShelfName(kind: String, name: String?)

    /** Merge [from] into [to], and everything that was merged into [from] with it (no chains). */
    @Query("UPDATE shelves SET mergedInto = :to WHERE kind = :from OR mergedInto = :from")
    abstract suspend fun mergeShelf(from: String, to: String)

    /** Undo of a merge: [from] has its own shelf again. */
    @Query("UPDATE shelves SET mergedInto = NULL WHERE kind = :from")
    abstract suspend fun unmergeShelf(from: String)

    /** The kinds currently merged into [to] (to undo a merge exactly). */
    @Query("SELECT kind FROM shelves WHERE mergedInto = :to")
    abstract suspend fun mergedInto(to: String): List<String>

    @Query("UPDATE shelves SET position = NULL")
    abstract suspend fun clearPositions()

    @Query("UPDATE shelves SET position = :position WHERE kind = :kind")
    abstract suspend fun setPosition(kind: String, position: Int)

    @Query("SELECT * FROM shelves WHERE kind = :kind")
    abstract suspend fun shelf(kind: String): ShelfEntity?

    /** Not a movie / Undo: every copy of the item on that shelf. */
    @Query(
        "UPDATE save_items SET hidden = :hidden WHERE itemKey = :key " +
            "AND saveId IN (SELECT saveId FROM shelf_saves WHERE shelf = :shelf)",
    )
    abstract suspend fun setItemHiddenOnShelf(shelf: String, key: String, hidden: Boolean): Int

    /** The shelf an item key already sits on, if any (for + Add a movie). Not counting [exceptSave]. */
    @Query(
        "SELECT v.shelf FROM save_items i JOIN shelf_saves v ON v.saveId = i.saveId " +
            "WHERE i.itemKey = :key AND i.saveId != :exceptSave AND i.hidden = 0 " +
            "AND (SELECT COUNT(*) FROM shelf_saves v2 WHERE v2.shelf = v.shelf) >= 3 LIMIT 1",
    )
    abstract suspend fun shelfWithItem(key: String, exceptSave: Long): String?

    // ---- Items on one save ----

    @Query(
        "SELECT i.id AS id, i.name AS name, i.hidden AS hidden, " +
            "(SELECT COUNT(DISTINCT j.saveId) FROM save_items j JOIN saves s2 ON s2.id = j.saveId " +
            " WHERE j.itemKey = i.itemKey AND j.saveId != i.saveId AND s2.deletedAt IS NULL AND i.itemKey != '') AS otherSaves " +
            "FROM save_items i WHERE i.saveId = :saveId ORDER BY i.id",
    )
    abstract fun observeItems(saveId: Long): Flow<List<SaveItemRow>>

    @Query("SELECT * FROM save_items WHERE id = :id")
    abstract suspend fun item(id: Long): SaveItemEntity?

    @Query("UPDATE save_items SET name = :name, itemKey = :key WHERE id = :id")
    abstract suspend fun renameItem(id: Long, name: String, key: String)

    @Query("DELETE FROM save_items WHERE id = :id")
    abstract suspend fun deleteItem(id: Long)

    @Insert
    abstract suspend fun insertItem(item: SaveItemEntity): Long

    // ---- Edits ----

    @Query("UPDATE saves SET title = :title, type = :type WHERE id = :id")
    abstract suspend fun setTitleType(id: Long, title: String, type: String)

    @Query("UPDATE saves SET type = :type WHERE id = :id")
    abstract suspend fun setType(id: Long, type: String)

    @Query("UPDATE saves SET kind = :kind WHERE id = :id")
    abstract suspend fun setKind(id: Long, kind: String)

    @Query("DELETE FROM save_tags WHERE saveId = :saveId")
    abstract suspend fun clearTags(saveId: Long)

    @Query("DELETE FROM save_tags WHERE saveId = :saveId AND tag IN (:tags)")
    abstract suspend fun removeTags(saveId: Long, tags: List<String>)

    @Query("INSERT OR IGNORE INTO save_tags (saveId, tag) VALUES (:saveId, :tag)")
    abstract suspend fun addTag(saveId: Long, tag: String)

    @Query("DELETE FROM search_fts WHERE rowid = :saveId")
    abstract suspend fun deleteFts(saveId: Long)

    @Insert
    abstract suspend fun insertFts(row: SearchFtsEntity)

    // ---- Delete + Undo, Read again ----

    @Query("UPDATE saves SET deletedAt = :now WHERE id IN (:ids) AND deletedAt IS NULL")
    abstract suspend fun softDelete(ids: List<Long>, now: Long): Int

    @Query("UPDATE saves SET deletedAt = NULL WHERE id IN (:ids)")
    abstract suspend fun restore(ids: List<Long>)

    @Query("SELECT id FROM saves WHERE deletedAt IS NOT NULL AND deletedAt <= :before")
    abstract suspend fun deletedBefore(before: Long): List<Long>

    /** Edited by hand before (or instead of) a read: the card is now the user's. */
    @Query(
        "UPDATE saves SET status = 'READ', readBy = 'HAND', failReason = NULL, readAt = :now " +
            "WHERE id = :id AND status != 'READ'",
    )
    abstract suspend fun markHandRead(id: Long, now: Long)

    /** Read again: back in line with a fresh try count. The old card stays until the new one lands. */
    @Query(
        "UPDATE saves SET status = 'QUEUED', attempts = 0, nextAttemptAt = 0, failReason = NULL " +
            "WHERE id = :id AND deletedAt IS NULL AND status IN ('READ', 'NEEDS_LOOK')",
    )
    abstract suspend fun readAgain(id: Long): Int

    private companion object {
        /** Shared type + shelf narrowing; the outer table must be called `saves`. */
        const val SCOPE = "AND (:type IS NULL OR saves.type = :type) " +
            "AND (:shelf IS NULL OR saves.id IN (SELECT saveId FROM shelf_saves WHERE shelf = :shelf))"
    }
}

/** A save with its tags and items, for lists, the hero and detail. */
data class SaveWithDetails(
    @Embedded val save: SaveEntity,
    @Relation(parentColumn = "id", entityColumn = "saveId", entity = SaveTagEntity::class, projection = ["tag"])
    val tags: List<String>,
    @Relation(parentColumn = "id", entityColumn = "saveId")
    val items: List<SaveItemEntity>,
)

data class TypeCountRow(val type: String, val n: Int)

data class SourceCountRow(val screenshots: Int, val links: Int)

data class TagCountRow(val tag: String, val n: Int)

data class ShelfRow(
    val kind: String,
    val saves: Int,
    val items: Int,
    val bareSaves: Int,
    val name: String?,
    val hidden: Boolean,
    val position: Int?,
)

data class ShelfEntryRow(
    val itemKey: String,
    val name: String,
    val saves: Int,
    val saveId: Long,
    val sourceApp: String?,
    val newest: Long,
)

data class SaveItemRow(val id: Long, val name: String, val hidden: Boolean, val otherSaves: Int)
