package com.jdevsappstudio.kept.feature.library.detail

import com.jdevsappstudio.kept.core.model.EventDates
import com.jdevsappstudio.kept.core.model.EventWhen
import com.jdevsappstudio.kept.core.model.ItemAction
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveItem
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.itemAction
import com.jdevsappstudio.kept.feature.library.appName
import com.jdevsappstudio.kept.feature.library.sourcePhrase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Detail layout rules (k-recipe, k-movies, k-place, k-date, k-link), pure so a panel change is one edit. */
object DetailRules {

    /** k-link copy: reels/shorts share only a caption; any other page is 'the link's preview'. */
    fun isReel(url: String?): Boolean {
        val u = url?.lowercase() ?: return false
        return REEL_MARKS.any { it in u }
    }

    private val REEL_MARKS = listOf(
        "instagram.com/reel", "instagram.com/p/", "tiktok.com/", "youtube.com/shorts", "facebook.com/reel", "fb.watch/",
    )

    /** k-movies: the items list shows at 2+ items; a recipe's items are its ingredients and show from 1. */
    fun showsItems(type: SaveType, items: List<SaveItem>): Boolean =
        if (type == SaveType.RECIPE) items.isNotEmpty() else items.size >= 2

    /** 'INGREDIENTS 7', 'MOVIES IN IT 5', 'IN IT 3'. */
    fun itemsLabel(type: SaveType): String = when (type) {
        SaveType.RECIPE -> "Ingredients"
        SaveType.MOVIE_SHOW -> "Movies in it"
        else -> "In it"
    }

    /** k-movies footer: 'Missed one? + Add a movie'. None on a recipe (ingredients are as read, H43). */
    fun addItemLabel(type: SaveType): String? = when (type) {
        SaveType.RECIPE -> null
        SaveType.MOVIE_SHOW -> "Add a movie"
        else -> "Add an item"
    }

    /** The post or page the save came from, if it has one. */
    fun originalLink(save: Save): String? =
        listOfNotNull(save.url, save.link).map { it.trim() }.firstOrNull { it.startsWith("http://") || it.startsWith("https://") }

    /** 'Open on Instagram' when the app is known, else 'Open link'. */
    fun openOriginalLabel(save: Save): String =
        appName(save.sourceApp)?.let { "Open on $it" } ?: "Open link"

    /**
     * The one big button (§6, H43: opens only). Null when the item rows carry their own buttons (2+ items, not a
     * recipe) or the type has none here (Idea: the screen shows Add to collection instead).
     */
    fun mainAction(save: Save, items: List<SaveItem>, today: LocalDate, zone: ZoneId): ItemAction? {
        val title = save.title.trim()
        if (title.isEmpty()) return null
        val link = originalLink(save)
        return when (save.type) {
            SaveType.RECIPE ->
                if (items.isNotEmpty()) ItemAction("Copy ingredients", ItemAction.Target.Copy(items.joinToString("\n") { it.name }))
                else itemAction(SaveType.RECIPE, title, link)
            SaveType.PLACE -> ItemAction("Open in Maps", ItemAction.Target.Maps(listOf(title, save.place.trim()).filter { it.isNotEmpty() }.distinct().joinToString(", ")))
            SaveType.EVENT -> ItemAction("Add to calendar", calendar(save, today, zone))
            SaveType.APP -> ItemAction("Open in Play Store", ItemAction.Target.PlayStore(title))
            SaveType.TIP, SaveType.OTHER -> ItemAction("Copy", ItemAction.Target.Copy(listOf(title, save.summary.trim()).filter { it.isNotEmpty() }.joinToString("\n")))
            SaveType.IDEA -> null
            else -> when {
                items.size >= 2 -> null
                else -> itemAction(save.type, items.singleOrNull()?.name ?: title, link)
            }
        }
    }

    /** The second button: back to the post, unless the main button already opens it. */
    fun showsOpenOriginal(save: Save, main: ItemAction?): Boolean {
        val link = originalLink(save) ?: return false
        return (main?.target as? ItemAction.Target.OpenLink)?.url != link
    }

    fun eventWhen(save: Save, today: LocalDate): EventWhen = EventDates.parse(save.eventDate, today)

    /** Owner 26 Nov: simple parse. A date → the start (all day without a time); the text as written rides in the notes. */
    fun calendar(save: Save, today: LocalDate, zone: ZoneId): ItemAction.Target.Calendar {
        val w = eventWhen(save, today)
        val begin = w.date?.let { d -> (w.time?.let { d.atTime(it) } ?: d.atStartOfDay()).atZone(zone).toInstant().toEpochMilli() }
        return ItemAction.Target.Calendar(
            title = save.title.trim(),
            location = save.place.trim().ifEmpty { null },
            beginMillis = begin,
            allDay = w.date != null && w.time == null,
            notes = save.eventDate.trim().takeIf { it.isNotEmpty() && w.date == null },
        )
    }

    private val day = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dayYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /** 'Saved 24 Nov from Instagram' · 'Saved 21 Nov from a screenshot'; the year only when it isn't this one. */
    fun savedLine(save: Save, today: LocalDate, zone: ZoneId): String {
        val d = Instant.ofEpochMilli(save.createdAt).atZone(zone).toLocalDate()
        return "Saved ${(if (d.year == today.year) day else dayYear).format(d)} ${save.sourcePhrase()}"
    }
}
