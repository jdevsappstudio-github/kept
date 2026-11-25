package com.jdevsappstudio.kept.data.saves

import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.data.ai.CardDraft

/** Pure clean-up of a model answer before it is stored (unit-tested; the model can ignore maxItems). */
internal object CardMapping {
    const val MAX_TAGS = 5
    const val MAX_ITEMS = 8

    fun tags(card: CardDraft): List<String> =
        card.tags.asSequence()
            .map { it.trim().trimStart('#').lowercase() }
            .filter { it.isNotEmpty() && it.length <= 40 }
            .distinct()
            .take(MAX_TAGS)
            .toList()

    fun items(card: CardDraft): List<String> =
        card.items.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(MAX_ITEMS)
            .toList()

    fun kind(card: CardDraft): String = card.kind.trim().lowercase()

    /** One searchable blob: everything a person might type to find the save again. */
    fun searchBody(card: CardDraft, tags: List<String>, items: List<String>, sharedText: String?, url: String?): String =
        listOf(
            card.title, card.summary, kind(card), SaveType.fromWire(card.type).label,
            tags.joinToString(" "), items.joinToString(" "), card.place, card.date, card.link,
            sharedText.orEmpty(), url.orEmpty(),
        ).filter { it.isNotBlank() }.joinToString("\n")
}
