package com.jdevsappstudio.kept.feature.library

import androidx.paging.PagingData
import com.jdevsappstudio.kept.core.model.AddItemResult
import com.jdevsappstudio.kept.core.model.ListLayout
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveFilter
import com.jdevsappstudio.kept.core.model.SaveItem
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.Shelf
import com.jdevsappstudio.kept.core.model.ShelfEntry
import com.jdevsappstudio.kept.core.model.ShelfMerge
import com.jdevsappstudio.kept.core.model.ShelfRemoval
import com.jdevsappstudio.kept.core.model.SourceCounts
import com.jdevsappstudio.kept.core.model.TagCount
import com.jdevsappstudio.kept.core.model.TypeChip
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import com.jdevsappstudio.kept.data.settings.SettingsStore
import com.jdevsappstudio.kept.core.presentation.SnackbarMessage
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext?) = Dispatchers.setMain(UnconfinedTestDispatcher())
    override fun afterEach(context: ExtensionContext?) = Dispatchers.resetMain()
}

fun save(
    id: Long,
    title: String = "Save $id",
    type: SaveType = SaveType.RECIPE,
    tags: List<String> = emptyList(),
    readBy: ReadBy? = ReadBy.CLOUD,
) = Save(
    id = id, createdAt = id, source = SaveSource.SHARE_IMAGE, sourceApp = null, sharedText = null, url = null,
    imagePath = null, thumbPath = null, status = SaveStatus.READ, failReason = null, readBy = readBy, title = title,
    summary = "", type = type, kind = "", place = "", eventDate = "", link = "", tags = tags, items = emptyList(),
    readAt = id,
)

fun shelf(kind: String, entries: Int = 3, hidden: Boolean = false) =
    Shelf(kind, kind.replaceFirstChar { it.uppercase() }, saves = 3, items = 0, entries = entries, hidden = hidden, position = null)

/** In-memory library: flows the tests push into, and a log of the writes. */
class FakeLibrary : LibraryLocalDataSource {
    val saves = MutableStateFlow<Map<Long, Save>>(emptyMap())
    val items = MutableStateFlow<Map<Long, List<SaveItem>>>(emptyMap())
    val shelves = MutableStateFlow<List<Shelf>>(emptyList())
    val chips = MutableStateFlow<List<TypeChip>>(emptyList())
    val tags = MutableStateFlow<List<TagCount>>(emptyList())
    val allTags = MutableStateFlow<List<String>>(emptyList())
    val entries = MutableStateFlow<Map<String?, List<ShelfEntry>>>(emptyMap())

    val pagedFilters = mutableListOf<SaveFilter>()
    val tagFilters = mutableListOf<Pair<SaveFilter, Int>>()
    val log = mutableListOf<String>()
    private val trash = mutableMapOf<Long, Save>()
    var readAgainResult = true
    var addResult = AddItemResult(added = true, alreadyOnShelf = null)

    fun put(vararg s: Save) = saves.update { m -> m + s.associateBy { it.id } }

    override fun pagedSaves(filter: SaveFilter): Flow<PagingData<Save>> {
        pagedFilters += filter
        return flowOf(PagingData.empty())
    }

    override fun observeNewest(): Flow<Save?> = saves.map { m -> m.values.maxByOrNull { it.createdAt } }
    override fun observeSave(id: Long): Flow<Save?> = saves.map { it[id] }
    override fun observeItems(saveId: Long): Flow<List<SaveItem>> = items.map { it[saveId].orEmpty() }
    override fun observeTypeChips(preferred: List<SaveType>): Flow<List<TypeChip>> = chips
    override fun observeSourceCounts(filter: SaveFilter): Flow<SourceCounts> = flowOf(SourceCounts(2, 1))
    override fun observeTags(filter: SaveFilter, limit: Int): Flow<List<TagCount>> {
        tagFilters += filter to limit
        return tags.map { it.take(limit) }
    }
    override fun observeAllTags(): Flow<List<String>> = allTags
    override fun observeShelves(): Flow<List<Shelf>> = shelves
    override fun observeShelfEntries(shelf: String, tag: String?): Flow<List<ShelfEntry>> = entries.map { it[tag].orEmpty() }

    override suspend fun edit(id: Long, title: String, type: SaveType, tags: List<String>) {
        log += "edit $id $title ${type.wire} $tags"
    }
    override suspend fun editMany(ids: List<Long>, type: SaveType?, addTags: List<String>, removeTags: List<String>) {
        log += "editMany $ids ${type?.wire} +$addTags -$removeTags"
    }
    override suspend fun renameItem(itemId: Long, name: String) { log += "renameItem $itemId $name" }
    override suspend fun deleteItem(itemId: Long) { log += "deleteItem $itemId" }
    override suspend fun addItem(saveId: Long, name: String): AddItemResult {
        log += "addItem $saveId $name"
        return addResult
    }

    override suspend fun delete(ids: List<Long>) {
        log += "delete $ids"
        saves.update { m -> ids.forEach { id -> m[id]?.let { trash[id] = it } }; m - ids.toSet() }
    }
    override suspend fun undoDelete(ids: List<Long>) {
        log += "undoDelete $ids"
        put(*ids.mapNotNull { trash.remove(it) }.toTypedArray())
    }
    override suspend fun purgeStale(graceMs: Long) { log += "purgeStale" }
    override suspend fun readAgain(id: Long): Boolean {
        log += "readAgain $id"
        return readAgainResult
    }

    override suspend fun setShelfHidden(kind: String, hidden: Boolean) {
        log += "hidden $kind $hidden"
        shelves.update { l -> l.map { if (it.kind == kind) it.copy(hidden = hidden) else it } }
    }
    override suspend fun renameShelf(kind: String, name: String?) { log += "rename $kind $name" }
    override suspend fun mergeShelf(from: String, into: String): ShelfMerge {
        log += "merge $from $into"
        return ShelfMerge(from, into, emptyList())
    }
    override suspend fun undoMerge(merge: ShelfMerge) { log += "undoMerge ${merge.from}" }
    override suspend fun reorderShelves(kinds: List<String>) { log += "reorder $kinds" }
    override suspend fun removeFromShelf(shelf: String, entry: ShelfEntry): ShelfRemoval {
        log += "remove $shelf ${entry.name}"
        return ShelfRemoval(shelf, entry, previousKind = null)
    }
    override suspend fun undoRemoveFromShelf(removal: ShelfRemoval) { log += "undoRemove ${removal.entry.name}" }
}

class FakeSettings : SettingsStore {
    override val cloudHelp = MutableStateFlow(false)
    override suspend fun cloudHelpNow() = cloudHelp.first()
    override suspend fun setCloudHelp(on: Boolean) { cloudHelp.value = on }
    override suspend fun cloudReadsOn(day: Int) = 0
    override suspend fun countCloudRead(day: Int) = Unit
    override val listLayout = MutableStateFlow(ListLayout.ROWS)
    override suspend fun setListLayout(layout: ListLayout) { listLayout.value = layout }
    override suspend fun claimNotificationAsk() = false
    override val recentSearches = MutableStateFlow<List<String>>(emptyList())
    override suspend fun addRecentSearch(text: String) { recentSearches.value = SettingsStore.pushRecent(recentSearches.value, text) }
}

/** Snackbars whose Undo runs at once. */
@OptIn(ExperimentalCoroutinesApi::class)
fun testSnackbars() = Snackbars(CoroutineScope(UnconfinedTestDispatcher()))

/** The next queued snackbar (the channel is buffered, so it waits for us). */
suspend fun Snackbars.next(): SnackbarMessage = messages.first()

/** Keeps a WhileSubscribed state live for the whole test. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.keepActive(flow: Flow<*>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}

fun UiText.resId(): Int = when (this) {
    is UiText.Res -> id
    is UiText.Plural -> id
    is UiText.Dynamic -> 0
}
