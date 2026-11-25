package com.jdevsappstudio.kept.core.model

/** Where a save came from (BUILD-SPEC §5). */
enum class SaveSource { SHARE_IMAGE, SHARE_TEXT, PICKER, PASTE }

/** Read state. The queue lives in Room, so a process death never loses a save. */
enum class SaveStatus { QUEUED, READING, READ, NEEDS_LOOK }

/** Who filled the card in. Drives the routing chip, so it is always truthful. */
enum class ReadBy { DEVICE, CLOUD, HAND }

/** One kept thing. Empty strings mean "not there" (same convention as the reader's card). */
data class Save(
    val id: Long,
    val createdAt: Long,
    val source: SaveSource,
    val sourceApp: String?,
    val sharedText: String?,
    val url: String?,
    val imagePath: String?,
    val thumbPath: String?,
    val status: SaveStatus,
    /** A [com.jdevsappstudio.kept.data.ai.ReadFailure] name when status is NEEDS_LOOK. */
    val failReason: String?,
    val readBy: ReadBy?,
    val title: String,
    val summary: String,
    val type: SaveType,
    val kind: String,
    val place: String,
    val eventDate: String,
    val link: String,
    val tags: List<String>,
    val items: List<String>,
    val readAt: Long?,
    /** H59: set when Fill in with cloud wrote this card from the page. */
    val filledAt: Long? = null,
) {
    /** k-link: a link that came with only its caption (no image), so Kept had little to read. */
    val captionOnly: Boolean get() = !url.isNullOrBlank() && imagePath == null
}

/** A new save before it has an id: what the share target / picker hands to the store. */
data class NewSave(
    val source: SaveSource,
    val sourceApp: String?,
    val sharedText: String?,
    val url: String?,
    val imagePath: String?,
    val contentHash: String? = null,
)

/** Counts for the home queue strip ("Reading 3 of 12"). */
data class QueueCounts(val queued: Int, val reading: Int, val needsLook: Int, val read: Int) {
    val pending get() = queued + reading

    companion object {
        val EMPTY = QueueCounts(0, 0, 0, 0)
    }
}
