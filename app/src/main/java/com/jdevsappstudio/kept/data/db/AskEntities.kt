package com.jdevsappstudio.kept.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jdevsappstudio.kept.core.model.AskTurnStatus
import com.jdevsappstudio.kept.core.model.SentKind

/** Ask chat (H58, Room v4): one running chat, oldest first by id. */
@Entity(tableName = "ask_turns")
data class AskTurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val question: String,
    val answer: String = "",
    val status: AskTurnStatus = AskTurnStatus.PENDING,
    val failReason: String? = null,
    val hidden: Boolean = false,
    val model: String? = null,
    /** Save ids sent as sources, comma separated, in source order ([1] first). A follow-up with no hits reuses them. */
    val sourceIds: String = "",
    val sentId: Long? = null,
)

/** Saves an answer points to. Cascades from both sides: a purged save drops out of old answers. */
@Entity(
    tableName = "ask_refs",
    primaryKeys = ["turnId", "saveId"],
    foreignKeys = [
        ForeignKey(AskTurnEntity::class, ["id"], ["turnId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SaveEntity::class, ["id"], ["saveId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("saveId")],
)
data class AskRefEntity(val turnId: Long, val saveId: Long, val position: Int)

/** k-sent: the exact text of every cloud request, kept 30 days (H58). No foreign key: the log outlives a deleted save. */
@Entity(tableName = "sent_log", indices = [Index("sentAt")])
data class SentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sentAt: Long,
    val kind: SentKind,
    val text: String,
    val images: Int = 0,
    val saveId: Long? = null,
)
