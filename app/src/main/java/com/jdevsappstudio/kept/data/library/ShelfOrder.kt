package com.jdevsappstudio.kept.data.library

import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.Shelf
import com.jdevsappstudio.kept.core.model.TypeChip

/** Pure ordering rules for home (unit-tested). */
object ShelfOrder {
    const val ON_HOME = 4

    /** Dragged order first, then the rest biggest first, then by name. */
    fun sort(shelves: List<Shelf>): List<Shelf> =
        shelves.sortedWith(
            compareBy<Shelf> { it.position == null }
                .thenBy { it.position ?: 0 }
                .thenByDescending { it.entries }
                .thenBy { it.title.lowercase() },
        )

    /** Home shows the first 4 that are on (k-shelves copy). */
    fun onHome(sorted: List<Shelf>): List<Shelf> = sorted.filterNot { it.hidden }.take(ON_HOME)

    /** Kind tag → shelf title: "movies" → "Movies". */
    fun title(kind: String, name: String?): String =
        name?.takeIf { it.isNotBlank() } ?: kind.replaceFirstChar { it.titlecase() }
}

object TypeChips {
    /** Folds stored type strings (unknown → Other), drops zeros, orders [preferred] first, then by count. */
    fun from(counts: Map<String, Int>, preferred: List<SaveType>): List<TypeChip> {
        val byType = counts.entries.groupBy({ SaveType.fromWire(it.key) }, { it.value }).mapValues { it.value.sum() }
        return byType.filter { it.value > 0 }
            .map { TypeChip(it.key, it.value) }
            .sortedWith(
                compareBy<TypeChip> { preferred.indexOf(it.type).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                    .thenByDescending { it.count }
                    .thenBy { it.type.ordinal },
            )
    }
}
