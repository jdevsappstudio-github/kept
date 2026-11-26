package com.jdevsappstudio.kept.data.saves

import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.data.ai.CardDraft

/** Pure clean-up of a model answer before it is stored (unit-tested; the model can ignore maxItems). */
internal object CardMapping {
    const val MAX_TAGS = 5
    /** 15 fits a recipe's ingredients (owner 26 Nov); movies and the like rarely pass 8. */
    const val MAX_ITEMS = 15

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
        searchBody(
            card.title, card.summary, kind(card), SaveType.fromWire(card.type), tags, items,
            card.place, card.date, card.link, sharedText, url,
        )

    /** Same blob from stored fields: rebuilt after an edit (title, type, tags, items). */
    fun searchBody(
        title: String, summary: String, kind: String, type: SaveType, tags: List<String>, items: List<String>,
        place: String, date: String, link: String, sharedText: String?, url: String?,
    ): String =
        listOf(
            title, summary, kind, type.label, tags.joinToString(" "), items.joinToString(" "),
            place, date, link, sharedText.orEmpty(), url.orEmpty(),
        ).filter { it.isNotBlank() }.joinToString("\n")

    /** One tag as the user typed it: same clean-up as the reader's tags. Null if nothing is left. */
    fun cleanTag(raw: String): String? = raw.trim().trimStart('#').lowercase().takeIf { it.isNotEmpty() && it.length <= 40 }
}
