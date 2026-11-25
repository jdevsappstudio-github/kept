package com.jdevsappstudio.kept.data.library

import androidx.paging.PagingData
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
import com.jdevsappstudio.kept.core.model.TagCount
import com.jdevsappstudio.kept.core.model.TypeChip
import kotlinx.coroutines.flow.Flow

/** The library as home, lists, shelves, detail and the edit sheet see it (H51 step 1). Every call is main-safe. */
interface LibraryLocalDataSource {
    fun pagedSaves(filter: SaveFilter): Flow<PagingData<Save>>
    fun observeNewest(): Flow<Save?>
    /** k-reading (H63): saves kept since [since], newest first, read or not (at most [limit]). */
    fun observeSince(since: Long, limit: Int = 300): Flow<List<Save>>
    fun observeSave(id: Long): Flow<Save?>
    fun observeItems(saveId: Long): Flow<List<SaveItem>>

    /** Home chips, ordered: [preferred] (onboarding B1 picks) first, then biggest. Zero-count types left out. */
    fun observeTypeChips(preferred: List<SaveType> = emptyList()): Flow<List<TypeChip>>
    fun observeSourceCounts(filter: SaveFilter): Flow<SourceCounts>
    fun observeTags(filter: SaveFilter, limit: Int = 30): Flow<List<TagCount>>
    fun observeAllTags(): Flow<List<String>>

    /** Every shelf in display order, hidden ones included (k-shelves); home takes [ShelfOrder.onHome]. */
    fun observeShelves(): Flow<List<Shelf>>
    /** Saves kept since [since] (home subtitle 'N new this week'). */
    fun observeCountSince(since: Long): Flow<Int>
    fun observeShelfEntries(shelf: String, tag: String? = null): Flow<List<ShelfEntry>>

    // Edit sheet (k-organize)
    suspend fun edit(id: Long, title: String, type: SaveType, tags: List<String>)
    suspend fun editMany(ids: List<Long>, type: SaveType?, addTags: List<String>, removeTags: List<String>)

    // Items on a save (k-movies)
    suspend fun renameItem(itemId: Long, name: String)
    suspend fun deleteItem(itemId: Long)
    suspend fun addItem(saveId: Long, name: String): AddItemResult

    // Delete + Undo (soft until the snackbar ends), Read again
    suspend fun delete(ids: List<Long>)
    suspend fun undoDelete(ids: List<Long>)
    /** Rows, search rows and files of saves deleted over [graceMs] ago go for good (start-up, home open). */
    suspend fun purgeStale(graceMs: Long = PURGE_GRACE_MS)
    /** False if the save is being read right now (or gone). */
    suspend fun readAgain(id: Long): Boolean

    // Shelf menu (H52)
    suspend fun setShelfHidden(kind: String, hidden: Boolean)
    suspend fun renameShelf(kind: String, name: String?)
    suspend fun mergeShelf(from: String, into: String): ShelfMerge?
    suspend fun undoMerge(merge: ShelfMerge)
    suspend fun reorderShelves(kinds: List<String>)
    /** Not a movie: an item row off the shelf, or a bare save row off the shelf (its kind cleared). */
    suspend fun removeFromShelf(shelf: String, entry: ShelfEntry): ShelfRemoval
    suspend fun undoRemoveFromShelf(removal: ShelfRemoval)

    companion object {
        /** Longer than any snackbar: an Undo can never race the purge. */
        const val PURGE_GRACE_MS = 60_000L
    }
}
