package com.jdevsappstudio.kept.core.model

/** A user-made collection (H26): any mix of types. [saves] counts saves that aren't deleted. */
data class SaveCollection(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val saves: Int,
    /** Tile pictures: up to 3 newest thumbs (or originals). */
    val previews: List<String> = emptyList(),
)

/** Home card "4 saves mention Pune" (H26, H55): [name] is what Make it calls the collection. */
data class CollectionSuggestion(val tag: String, val name: String, val saves: Int)

/** What Make it made, for the snackbar ("Made Pune · 4 saves") and its Undo. */
data class MadeCollection(val id: Long, val name: String, val saves: Int)
