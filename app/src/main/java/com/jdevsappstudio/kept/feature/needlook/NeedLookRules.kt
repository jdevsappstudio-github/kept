package com.jdevsappstudio.kept.feature.needlook

import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.data.ai.ReadFailure
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.data.quota.MeterView
import com.jdevsappstudio.kept.feature.library.appName
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The banner at the top of k-failed: why waiting saves aren't being read right now. */
data class NeedLookBanner(val title: String, val body: String, val readWithCloud: Boolean = false)

/** What a given-up save says and offers (H68: no Not sure, no battery pause). */
data class FailCopy(val line: String, val canRetry: Boolean, val readWithCloud: Boolean)

/** One row: a waiting / reading save ([ahead] = saves before it in line) or one that needs a look. */
sealed interface NeedLookRow {
    val save: Save

    data class Pending(override val save: Save, val ahead: Int) : NeedLookRow
    data class Failed(override val save: Save, val copy: FailCopy) : NeedLookRow
}

/** k-failed / k-failed-cloud rules, in one place so the review panel can swap the words. */
object NeedLookRules {

    const val LIMIT = 300

    /** Needs a look first (newest first: what to fix), then the line in reading order (oldest first). */
    fun rows(unread: List<Save>, cloudHelp: Boolean): List<NeedLookRow> {
        val failed = unread.filter { it.status == SaveStatus.NEEDS_LOOK }.sortedByDescending { it.createdAt }
        val line = unread.filter { it.status != SaveStatus.NEEDS_LOOK }
            .sortedWith(compareBy<Save> { it.status != SaveStatus.READING }.thenBy { it.createdAt }.thenBy { it.id })
        return failed.map { NeedLookRow.Failed(it, failCopy(it.failReason, cloudHelp)) } +
            line.mapIndexed { i, s -> NeedLookRow.Pending(s, ahead = i) }
    }

    fun failCopy(reason: String?, cloudHelp: Boolean): FailCopy =
        when (ReadFailure.entries.firstOrNull { it.name == reason }) {
            ReadFailure.BLOCKED -> FailCopy("Gemini wouldn’t read this one. You can tag it by hand.", canRetry = false, readWithCloud = false)
            ReadFailure.UNREADABLE -> FailCopy("Kept couldn’t find anything to read in it.", canRetry = true, readWithCloud = false)
            ReadFailure.NO_MODEL ->
                if (cloudHelp) FailCopy("Not read yet. It can be read with cloud help now.", canRetry = true, readWithCloud = false)
                else FailCopy("This phone can’t read it on its own.", canRetry = false, readWithCloud = true)
            ReadFailure.MODEL_GONE, ReadFailure.NOT_SET_UP ->
                FailCopy("Reading isn’t working in this version. The save is kept.", canRetry = true, readWithCloud = false)
            else -> FailCopy("Couldn’t be read this time. The save is kept.", canRetry = true, readWithCloud = false)
        }

    /** Pending row line: 'Reading…', 'Waiting in line · 3 ahead', or the pause reason when the queue is stopped. */
    fun pendingLine(row: NeedLookRow.Pending, pause: QueuePause): String = when {
        row.save.status == SaveStatus.READING -> "Reading…"
        pause == QueuePause.CLOUD_CAP -> "Waiting: today’s cloud reads are used"
        pause == QueuePause.OFFLINE -> "Waiting for a connection"
        pause == QueuePause.NEEDS_CONSENT -> "Waiting: cloud help is off"
        pause == QueuePause.NOT_SET_UP -> "Waiting: reading isn’t set up"
        pause == QueuePause.HELD -> "Paused · Resume on Home"
        row.ahead == 0 -> "Next in line"
        else -> "Waiting in line · ${row.ahead} ahead"
    }

    /** Shown only while something waits and the queue is stopped for a reason the person can see. */
    fun banner(pause: QueuePause, waiting: Int): NeedLookBanner? {
        if (waiting == 0) return null
        val saves = if (waiting == 1) "1 save is" else "$waiting saves are"
        return when (pause) {
            QueuePause.OFFLINE -> NeedLookBanner(
                "Waiting for a connection",
                "$saves kept and will be read in the cloud when you’re back online.",
            )
            QueuePause.CLOUD_CAP -> NeedLookBanner(
                "Today’s cloud reads are used up",
                "$saves kept and will be read after midnight, ${if (waiting == 1) "by itself" else "by themselves"}. " +
                    "Questions (Ask) are counted separately.",
            )
            QueuePause.NEEDS_CONSENT -> NeedLookBanner(
                "This phone can’t read on its own",
                "$saves kept. Read them with Gemini in the cloud, or tag them by hand.",
                readWithCloud = true,
            )
            QueuePause.NOT_SET_UP -> NeedLookBanner(
                "Reading isn’t set up",
                "$saves kept and will be read once this version can reach Gemini.",
            )
            QueuePause.IN_BACKGROUND, QueuePause.NONE, QueuePause.HELD -> null
        }
    }

    /** A save with no card title yet: 'Screenshot, 24 Nov', 'Link from Instagram', 'Link from bbc.com'. */
    fun title(save: Save, zone: ZoneId = ZoneId.systemDefault()): String {
        if (save.title.isNotBlank()) return save.title
        val day = Instant.ofEpochMilli(save.createdAt).atZone(zone).format(DAY)
        return when (save.source) {
            SaveSource.SHARE_IMAGE, SaveSource.PICKER -> "Screenshot, $day"
            SaveSource.SHARE_TEXT, SaveSource.PASTE ->
                (appName(save.sourceApp) ?: host(save.url))?.let { "Link from $it" } ?: "Link, $day"
        }
    }

    private fun host(url: String?): String? =
        url?.let { runCatching { URI(it.trim()).host }.getOrNull() }?.removePrefix("www.")?.takeIf { it.isNotBlank() }

    private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /** '28 of 30 read today · resets at midnight', on cloud-reading phones once few reads are left (or none). */
    fun readsLine(reads: MeterView?): String? =
        reads?.takeIf { it.showCounter }?.let { "${it.used.coerceAtMost(it.total)} of ${it.total} read today · resets at midnight" }

    const val CONSENT_TITLE = "Read your saves with cloud?"
    const val CONSENT_BODY =
        "This phone can’t read saves on its own. With Cloud help on, Kept sends each save (the image, text and link) to " +
            "Google Gemini to be read. Cards read this way are marked Cloud. During the beta, Google may use what is sent " +
            "to improve its products.\n\nChange it any time in Privacy."
    const val CONSENT_YES = "Turn on Cloud help"
    const val CONSENT_NO = "Not now"
}
