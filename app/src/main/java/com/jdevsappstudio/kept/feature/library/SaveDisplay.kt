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
    SaveSource.SHARE_TEXT, SaveSource.PASTE -> appName(sourceApp)?.let { "from $it" } ?: "from a link"
}

/** Share referrers are package names: the apps people share from get their name, others none (the line falls back). */
private val APP_NAMES = mapOf(
    "com.instagram.android" to "Instagram", "com.instagram.barcelona" to "Threads",
    "com.google.android.youtube" to "YouTube", "com.zhiliaoapp.musically" to "TikTok", "com.ss.android.ugc.trill" to "TikTok",
    "com.twitter.android" to "X", "com.reddit.frontpage" to "Reddit", "com.pinterest" to "Pinterest",
    "com.facebook.katana" to "Facebook", "com.whatsapp" to "WhatsApp", "org.telegram.messenger" to "Telegram",
    "com.snapchat.android" to "Snapchat", "com.linkedin.android" to "LinkedIn", "com.android.chrome" to "Chrome",
    "org.mozilla.firefox" to "Firefox", "com.google.android.googlequicksearchbox" to "Google", "com.google.android.gm" to "Gmail",
    "com.google.android.apps.maps" to "Maps", "com.google.android.apps.photos" to "Photos", "com.spotify.music" to "Spotify",
    "com.netflix.mediaclient" to "Netflix", "com.amazon.mShop.android.shopping" to "Amazon", "com.flipkart.android" to "Flipkart",
    "com.application.zomato" to "Zomato", "in.swiggy.android" to "Swiggy", "com.myntra.android" to "Myntra",
)

/** 'Instagram' for com.instagram.android; a plain name stays as it is; an unknown package is null. */
fun appName(source: String?): String? {
    val s = source?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    APP_NAMES[s]?.let { return it }
    return if ('.' in s) null else s
}

/** k-list source chip words (doc: 'From reels 7', 'Screenshots 5'). */
fun SourceFilter.chipLabel(): String = when (this) {
    SourceFilter.LINKS -> "From reels"
    SourceFilter.SCREENSHOTS -> "Screenshots"
}

/** k-shelf row line under the name: 'Instagram · from 2 saves' (H60 owner pick: source + count, no year). */
fun ShelfEntry.metaLine(): String =
    listOfNotNull(appName(sourceApp), if (saves > 1) "from $saves saves" else null).joinToString(" · ")
