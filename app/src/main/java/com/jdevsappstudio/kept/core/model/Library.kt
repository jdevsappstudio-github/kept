package com.jdevsappstudio.kept.core.model

/** Which saves a list shows (k-list, k-shelf for save shelves, k-collection). Null = no narrowing. */
data class SaveFilter(
    val type: SaveType? = null,
    val shelf: String? = null,
    val tag: String? = null,
    val source: SourceFilter? = null,
    /** k-collection: only the saves in this collection. */
    val collection: Long? = null,
) {
    companion object {
        val ALL = SaveFilter()
    }
}

/** Source chips on k-list: a screenshot vs a link or reel shared as text. */
enum class SourceFilter { SCREENSHOTS, LINKS }

data class SourceCounts(val screenshots: Int, val links: Int) {
    companion object {
        val EMPTY = SourceCounts(0, 0)
    }
}

/** One home chip: a type with its count of read saves. */
data class TypeChip(val type: SaveType, val count: Int)

data class TagCount(val tag: String, val count: Int)

/** H50: how every save list draws. */
enum class ListLayout { ROWS, GRID }

/**
 * An auto shelf (H28): a kind with 3+ saves. [entries] = merged items + saves with no items,
 * so a shelf of single-movie screenshots and 5-movie lists counts every movie once.
 */
data class Shelf(
    val kind: String,
    val title: String,
    val saves: Int,
    val items: Int,
    val entries: Int,
    val hidden: Boolean,
    val position: Int?,
    /** Tile pictures: up to 3 newest thumbs (or originals) of the shelf's saves. */
    val previews: List<String> = emptyList(),
) {
    /** Tile reads "23 items" for item shelves, "7 saves" otherwise. */
    val countsItems: Boolean get() = items > 0
}

/** One row on a shelf: a merged item ("from 2 saves") or, for a save without items, the save itself. */
data class ShelfEntry(
    /** [com.jdevsappstudio.kept.data.saves.ItemKey]; empty for a save row. */
    val key: String,
    val name: String,
    val saves: Int,
    /** Newest save that holds it: tap → that save. */
    val saveId: Long,
    val sourceApp: String?,
    /** Type of the save that holds it: picks the row action (BUILD-SPEC §6). */
    val type: SaveType = SaveType.OTHER,
    /** That save's link (card link, else the shared URL), for Open link rows. */
    val link: String? = null,
) {
    val isSave: Boolean get() = key.isEmpty()
}

/** An item on the detail screen, with its id for fix / delete. */
data class SaveItem(val id: Long, val name: String, val hidden: Boolean, val otherSaves: Int)

/** Result of + Add a movie: [alreadyOnShelf] names the shelf that already has it ("Already on your Movies shelf"). */
data class AddItemResult(val added: Boolean, val alreadyOnShelf: String?)

/** What Undo needs after Not a movie: a bare save row loses its kind, so the old kind rides along. */
data class ShelfRemoval(val shelf: String, val entry: ShelfEntry, val previousKind: String?)

/** What Undo needs after Merge into…: [carried] were merged into [from] before and go back to it. */
data class ShelfMerge(val from: String, val into: String, val carried: List<String>)
