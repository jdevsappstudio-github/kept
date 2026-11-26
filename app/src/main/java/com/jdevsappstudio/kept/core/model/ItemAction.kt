package com.jdevsappstudio.kept.core.model

/** The one-tap open action of a save or an item (BUILD-SPEC §6, H43: opens only, no state change). */
data class ItemAction(val label: String, val target: Target) {
    sealed interface Target {
        data class WebSearch(val query: String) : Target
        data class Maps(val query: String) : Target
        data class PlayStore(val query: String) : Target
        data class OpenLink(val url: String) : Target
        /** [beginMillis] null = the calendar's own default; [allDay] when only a date was read; [notes] = the date as written. */
        data class Calendar(
            val title: String,
            val location: String? = null,
            val beginMillis: Long? = null,
            val allDay: Boolean = false,
            val notes: String? = null,
        ) : Target
        data class Copy(val text: String) : Target
    }
}

/**
 * §6 by type. [link] only when it belongs to this one thing: a save row's own link, never the link of a
 * 5-item save for one of its items (each item would open the same page). Without it, link types search instead.
 * Idea has none here (Add to collection lives on the save).
 */
fun itemAction(type: SaveType, name: String, link: String?): ItemAction? {
    val n = name.trim()
    if (n.isEmpty()) return null
    val url = link?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    return when (type) {
        SaveType.MOVIE_SHOW -> ItemAction("Where to watch", ItemAction.Target.WebSearch("$n where to watch"))
        SaveType.PLACE -> ItemAction("Maps", ItemAction.Target.Maps(n))
        SaveType.PRODUCT -> ItemAction("Search to buy", ItemAction.Target.WebSearch("$n buy"))
        SaveType.APP -> ItemAction("Play Store", ItemAction.Target.PlayStore(n))
        SaveType.RECIPE ->
            if (url != null) ItemAction("Open link", ItemAction.Target.OpenLink(url))
            else ItemAction("Search recipe", ItemAction.Target.WebSearch("$n recipe"))
        SaveType.VIDEO, SaveType.READ ->
            if (url != null) ItemAction("Open link", ItemAction.Target.OpenLink(url))
            else ItemAction("Search", ItemAction.Target.WebSearch(n))
        SaveType.EVENT -> ItemAction("Add to calendar", ItemAction.Target.Calendar(n))
        SaveType.TIP, SaveType.OTHER -> ItemAction("Copy", ItemAction.Target.Copy(n))
        SaveType.IDEA -> null
    }
}

/** A shelf row's action: an item row never borrows its save's link. */
fun ShelfEntry.action(): ItemAction? = itemAction(type, name, if (isSave) link else null)
