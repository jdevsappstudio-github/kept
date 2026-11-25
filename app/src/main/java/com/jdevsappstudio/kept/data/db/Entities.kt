package com.jdevsappstudio.kept.data.db

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveStatus

/** BUILD-SPEC §5. Enums are stored by name (Room's built-in enum support). */
@Entity(
    tableName = "saves",
    indices = [Index("status", "createdAt"), Index("kind"), Index("contentHash"), Index("url")],
)
data class SaveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val source: SaveSource,
    val sourceApp: String? = null,
    val sharedText: String? = null,
    val url: String? = null,
    val imagePath: String? = null,
    /** SHA-256 of the image bytes, taken while copying: the same screenshot is never kept twice. */
    val contentHash: String? = null,
    val thumbPath: String? = null,
    val status: SaveStatus = SaveStatus.QUEUED,
    val failReason: String? = null,
    val attempts: Int = 0,
    /** Epoch ms; the queue skips the row until then (backoff). */
    val nextAttemptAt: Long = 0,
    val readBy: ReadBy? = null,
    /** Model that answered ("gemini-3.5-flash-lite", "device"). */
    val model: String? = null,
    val title: String = "",
    val summary: String = "",
    /** [com.jdevsappstudio.kept.core.model.SaveType.wire]. */
    val type: String = "",
    val kind: String = "",
    val place: String = "",
    /** As written on the screenshot; parsed only when the user taps Add to calendar. */
    val eventDate: String = "",
    val link: String = "",
    val fullText: String = "",
    val readAt: Long? = null,
    /** Soft delete (v2): set on Delete, cleared by Undo, the row and files go when the snackbar ends (or on next start). */
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "save_tags",
    primaryKeys = ["saveId", "tag"],
    foreignKeys = [ForeignKey(SaveEntity::class, ["id"], ["saveId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tag")],
)
data class SaveTagEntity(val saveId: Long, val tag: String)

@Entity(
    tableName = "save_items",
    foreignKeys = [ForeignKey(SaveEntity::class, ["id"], ["saveId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("saveId"), Index("itemKey")],
)
data class SaveItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saveId: Long,
    val name: String,
    /** [com.jdevsappstudio.kept.data.saves.ItemKey]: one movie from two saves is one shelf row (H52). */
    @ColumnInfo(defaultValue = "") val itemKey: String = "",
    /** "Not a movie": off its shelf, still listed on its save. */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "collection_saves",
    primaryKeys = ["collectionId", "saveId"],
    foreignKeys = [
        ForeignKey(CollectionEntity::class, ["id"], ["collectionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SaveEntity::class, ["id"], ["saveId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("saveId")],
)
data class CollectionSaveEntity(val collectionId: Long, val saveId: Long)

/**
 * Full-text index, rowid = save id. One `body` column, rebuilt whenever a save's card changes.
 * unicode61 (v3, H54): case and accents fold for every script, so 'cafe' finds 'Café'.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "search_fts")
data class SearchFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Long,
    val body: String,
)

/** A shelf's own settings (H52). No row = defaults; shelves themselves come from kinds with 3+ saves. */
@Entity(tableName = "shelves")
data class ShelfEntity(
    @PrimaryKey val kind: String,
    /** Rename; null = the kind, capitalised. */
    val name: String? = null,
    val hidden: Boolean = false,
    /** Drag order on k-shelves; null = after the ordered ones, biggest first. */
    val position: Int? = null,
    /** Merge into…: saves of this kind show on that shelf. */
    val mergedInto: String? = null,
)

/** Which shelf each read, not-deleted save sits on (its kind, or the kind it was merged into). */
@DatabaseView(
    viewName = "shelf_saves",
    value = "SELECT s.id AS saveId, COALESCE(m.mergedInto, s.kind) AS shelf FROM saves s " +
        "LEFT JOIN shelves m ON m.kind = s.kind WHERE s.deletedAt IS NULL AND s.status = 'READ' AND s.kind != ''",
)
data class ShelfSaveView(val saveId: Long, val shelf: String)

/** Row of the status count query. */
data class StatusCount(val status: SaveStatus, val n: Int)
