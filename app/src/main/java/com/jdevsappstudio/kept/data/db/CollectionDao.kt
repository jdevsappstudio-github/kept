package com.jdevsappstudio.kept.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Collections (H51 step 3, H55). Counts skip soft-deleted saves; links to them stay so Undo of a delete
 * puts the save back in its collections. Multi-step edits run in `withTransaction` in the data source.
 */
@Dao
abstract class CollectionDao {

    /** H55: newest made first. */
    @Query("SELECT $COLUMNS FROM collections c ORDER BY c.createdAt DESC, c.id DESC")
    abstract fun observeAll(): Flow<List<CollectionRow>>

    /** Tile pictures: per collection, the 3 newest saves that have a picture. */
    @Query(
        "SELECT CAST(cs.collectionId AS TEXT) AS grp, COALESCE(s.thumbPath, s.imagePath) AS path " +
            "FROM collection_saves cs JOIN saves s ON s.id = cs.saveId " +
            "WHERE s.id IN (SELECT cs2.saveId FROM collection_saves cs2 JOIN saves s2 ON s2.id = cs2.saveId " +
            "  WHERE cs2.collectionId = cs.collectionId AND s2.deletedAt IS NULL " +
            "  AND COALESCE(s2.thumbPath, s2.imagePath) IS NOT NULL ORDER BY s2.createdAt DESC LIMIT 3) " +
            "ORDER BY s.createdAt DESC",
    )
    abstract fun observePreviews(): Flow<List<PreviewRow>>

    @Query("SELECT $COLUMNS FROM collections c WHERE c.id = :id")
    abstract fun observe(id: Long): Flow<CollectionRow?>

    @Query("SELECT $COLUMNS FROM collections c WHERE c.id = :id")
    abstract suspend fun get(id: Long): CollectionRow?

    @Query("SELECT $COLUMNS FROM collections c")
    abstract suspend fun all(): List<CollectionRow>

    /** Type chips inside one collection (read saves only, like home's). */
    @Query(
        "SELECT s.type AS type, COUNT(*) AS n FROM collection_saves cs JOIN saves s ON s.id = cs.saveId " +
            "WHERE cs.collectionId = :id AND s.deletedAt IS NULL AND s.status = 'READ' GROUP BY s.type",
    )
    abstract fun observeTypeCounts(id: Long): Flow<List<TypeCountRow>>

    /** Pick mode: what is already in (ticked and locked, H55). */
    @Query(
        "SELECT cs.saveId FROM collection_saves cs JOIN saves s ON s.id = cs.saveId " +
            "WHERE cs.collectionId = :id AND s.deletedAt IS NULL",
    )
    abstract fun observeSaveIds(id: Long): Flow<List<Long>>

    /** Edit sheet ticks: for each collection, how many of [saveIds] it holds. */
    @Query("SELECT collectionId, COUNT(*) AS n FROM collection_saves WHERE saveId IN (:saveIds) GROUP BY collectionId")
    abstract suspend fun membership(saveIds: List<Long>): List<MembershipRow>

    /** Suggestion candidates: tags on [min]+ read saves, most saves first, then the most recently used. */
    @Query(
        "SELECT t.tag AS tag, COUNT(*) AS n FROM save_tags t JOIN saves s ON s.id = t.saveId " +
            "WHERE s.deletedAt IS NULL AND s.status = 'READ' GROUP BY t.tag HAVING COUNT(*) >= :min " +
            "ORDER BY n DESC, MAX(s.createdAt) DESC",
    )
    abstract fun observeTagsWithAtLeast(min: Int): Flow<List<TagCountRow>>

    /** The saves Make it files: read, not deleted, with [tag]. */
    @Query(
        "SELECT s.id FROM saves s JOIN save_tags t ON t.saveId = s.id " +
            "WHERE t.tag = :tag AND s.deletedAt IS NULL AND s.status = 'READ'",
    )
    abstract suspend fun readSavesWithTag(tag: String): List<Long>

    @Insert
    abstract suspend fun insert(collection: CollectionEntity): Long

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    /** Links go with it (cascade); the saves stay. */
    @Query("DELETE FROM collections WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /** -1 for a save that was already in. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun link(rows: List<CollectionSaveEntity>): List<Long>

    @Query("DELETE FROM collection_saves WHERE collectionId = :id AND saveId IN (:saveIds)")
    abstract suspend fun unlink(id: Long, saveIds: List<Long>): Int

    /** Of [saveIds], the ones that still exist (a save purged mid-pick would break the foreign key). */
    @Query("SELECT id FROM saves WHERE id IN (:saveIds)")
    abstract suspend fun existing(saveIds: List<Long>): List<Long>

    private companion object {
        const val COLUMNS = "c.id AS id, c.name AS name, c.createdAt AS createdAt, " +
            "(SELECT COUNT(*) FROM collection_saves cs JOIN saves s ON s.id = cs.saveId " +
            " WHERE cs.collectionId = c.id AND s.deletedAt IS NULL) AS saves"
    }
}

data class CollectionRow(val id: Long, val name: String, val createdAt: Long, val saves: Int)

data class MembershipRow(val collectionId: Long, val n: Int)
