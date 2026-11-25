package com.jdevsappstudio.kept.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
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
    indices = [Index("saveId")],
)
data class SaveItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saveId: Long,
    val name: String,
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

/** Full-text index, rowid = save id. One `body` column, rebuilt whenever a save's card changes. */
@Fts4
@Entity(tableName = "search_fts")
data class SearchFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Long,
    val body: String,
)

/** Row of the status count query. */
data class StatusCount(val status: SaveStatus, val n: Int)
