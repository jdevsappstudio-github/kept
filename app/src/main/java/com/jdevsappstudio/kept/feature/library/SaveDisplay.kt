package com.jdevsappstudio.kept.feature.library

import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.ShelfEntry
import com.jdevsappstudio.kept.core.model.SourceFilter

/** Card title by status (same words as the Just-kept hero). */
fun Save.displayTitle(): String = when (status) {
    SaveStatus.QUEUED, SaveStatus.READING -> title.ifEmpty { "Reading it…" }
    SaveStatus.NEEDS_LOOK -> title.ifEmpty { "Needs a look" }
    SaveStatus.READ -> title.ifEmpty { "Untitled" }
}

/** UI2: k-list card line under the title: the card summary, else where it came from. */
fun Save.metaLine(): String = summary.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: sourcePhrase()

fun Save.sourcePhrase(): String = when (source) {
    SaveSource.SHARE_IMAGE, SaveSource.PICKER -> "from a screenshot"
    SaveSource.SHARE_TEXT, SaveSource.PASTE -> sourceApp?.takeIf { it.isNotBlank() }?.let { "from $it" } ?: "from a link"
}

/** k-list source chip words (doc: 'From reels 7', 'Screenshots 5'). */
fun SourceFilter.chipLabel(): String = when (this) {
    SourceFilter.LINKS -> "From reels"
    SourceFilter.SCREENSHOTS -> "Screenshots"
}

/** k-shelf row line under the name: 'Instagram · from 2 saves' (H60 owner pick: source + count, no year). */
fun ShelfEntry.metaLine(): String =
    listOfNotNull(sourceApp?.takeIf { it.isNotBlank() }, if (saves > 1) "from $saves saves" else null).joinToString(" · ")
