package com.jdevsappstudio.kept.data.ask

import com.jdevsappstudio.kept.core.model.SaveType

/**
 * k-ask-first: 3 starters built on the phone from the person's own types and tags (a recipe-heavy user gets
 * 'What can I cook tonight?'). Under [MIN_SAVES] read saves there are none ('Keep a few saves first').
 * Questions only; no tracking words (H43).
 */
object AskStarters {
    const val MIN_SAVES = 3
    private const val COUNT = 3

    private val BY_TYPE = mapOf(
        SaveType.RECIPE to "What can I cook tonight?",
        SaveType.MOVIE_SHOW to "What should I watch tonight?",
        SaveType.PLACE to "Which places should I go to first?",
        SaveType.PRODUCT to "Compare the products I saved",
        SaveType.VIDEO to "Which videos did I save, and what are they about?",
        SaveType.READ to "Sum up what I've been reading",
        SaveType.EVENT to "What events did I save, by date?",
        SaveType.TIP to "What are the most useful tips I saved?",
        SaveType.APP to "Which apps did I save, and what for?",
        SaveType.IDEA to "Remind me of my ideas",
    )

    /** [typeCounts] by wire type, [topTags] most used first. */
    fun build(readSaves: Int, typeCounts: Map<String, Int>, topTags: List<String>): List<String> {
        if (readSaves < MIN_SAVES) return emptyList()
        val byType = typeCounts.entries
            .filter { it.value >= 2 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .mapNotNull { BY_TYPE[SaveType.fromWire(it.key)] }
            .distinct()
            .take(COUNT)
        val typeWords = SaveType.entries.flatMap { listOf(it.wire, it.label.lowercase()) }.toSet()
        val byTag = topTags.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.lowercase() !in typeWords }
            .map { "What did I save about $it?" }
        return (byType + byTag).distinct().take(COUNT).toList()
    }
}
