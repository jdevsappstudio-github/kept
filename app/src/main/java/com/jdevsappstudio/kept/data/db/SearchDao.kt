package com.jdevsappstudio.kept.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Search (H51 step 2): FTS4 over `search_fts.body` (unicode61, accents folded, H54). Soft-deleted saves never show.
 * [match] comes from [com.jdevsappstudio.kept.data.search.SearchQuery.match] only: never raw user text.
 */
@Dao
abstract class SearchDao {

    /** H54: title hits first, then newest. [titleWord] is lowercase; `lower()` folds ASCII only, close enough. */
    @Transaction
    @Query(
        "SELECT saves.*, snippet(search_fts, :open, :close, '…', -1, 10) AS snippet " +
            "FROM search_fts JOIN saves ON saves.id = search_fts.rowid " +
            "WHERE search_fts MATCH :match AND saves.deletedAt IS NULL " +
            "AND (:type IS NULL OR saves.type = :type) " +
            "ORDER BY (:titleWord != '' AND instr(lower(saves.title), :titleWord) > 0) DESC, saves.createdAt DESC",
    )
    abstract fun paging(match: String, titleWord: String, type: String?, open: String, close: String): PagingSource<Int, SearchHitRow>

    /** Type chips over the results ("Place 4 · Event 1"). */
    @Query(
        "SELECT saves.type AS type, COUNT(*) AS n FROM search_fts JOIN saves ON saves.id = search_fts.rowid " +
            "WHERE search_fts MATCH :match AND saves.deletedAt IS NULL GROUP BY saves.type",
    )
    abstract fun observeTypeCounts(match: String): Flow<List<TypeCountRow>>
}

data class SearchHitRow(
    @Embedded val save: SaveEntity,
    val snippet: String,
    @Relation(parentColumn = "id", entityColumn = "saveId", entity = SaveTagEntity::class, projection = ["tag"])
    val tags: List<String>,
    @Relation(parentColumn = "id", entityColumn = "saveId")
    val items: List<SaveItemEntity>,
)
