package com.jdevsappstudio.kept.feature.home

import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.data.queue.ReadRoute
import com.jdevsappstudio.kept.data.quota.MeterView

/** How this phone reads, for the subtitle: 'all read on this phone' or 'read with Gemini in the cloud'. */
enum class ReadsWith { DEVICE, CLOUD, UNKNOWN }

/** One status line under the home header (k-home note: shown only when it applies, in this order). */
sealed interface HomeLine {
    /** 'Reading 3 of 12 · On this phone'; [current]/[total] count this batch only, not the whole library. */
    data class Reading(val current: Int, val total: Int, val with: ReadsWith) : HomeLine {
        /** '· Pause' on a big pick only (owner 26 Nov: 10 or more in this batch). */
        val canPause: Boolean get() = total >= PAUSE_FROM
    }

    /** '4 waiting · read when you're back online' etc.; [pause] NONE = waiting for a retry ('trying again soon'). */
    data class Waiting(val count: Int, val pause: QueuePause) : HomeLine

    /** '2 need a look' → k-failed. */
    data class NeedLook(val count: Int) : HomeLine

    /** '3 of 100 cloud reads left today' on a cloud-reading phone, only when low. */
    data class ReadsLeft(val left: Int, val total: Int) : HomeLine
}

/** Batch size from which the reading line offers Pause. */
const val PAUSE_FROM = 10

object HomeLines {

    /** Why saves wait, after 'N waiting · ' (home and k-reading). */
    fun waitReason(pause: QueuePause): String = when (pause) {
        QueuePause.NEEDS_CONSENT -> "turn on cloud help or tag by hand"
        QueuePause.OFFLINE -> "read when you're back online"
        QueuePause.IN_BACKGROUND -> "read when you open Kept"
        QueuePause.CLOUD_CAP -> "today's cloud reads are used up"
        QueuePause.NOT_SET_UP -> "reading isn't set up"
        QueuePause.NONE -> "trying again soon"
        QueuePause.HELD -> "paused"
    }

    fun readsWith(route: ReadRoute?, cloudHelp: Boolean): ReadsWith = when (route) {
        ReadRoute.DEVICE -> ReadsWith.DEVICE
        ReadRoute.CLOUD, ReadRoute.OFFLINE -> ReadsWith.CLOUD
        ReadRoute.NEEDS_CONSENT -> ReadsWith.UNKNOWN
        null -> if (cloudHelp) ReadsWith.CLOUD else ReadsWith.UNKNOWN
    }

    /**
     * Where the current batch started: saves finished (read or need a look) when the queue last went from empty
     * to busy. Null while nothing is waiting. Opened mid-batch, the batch starts now ('Reading 1 of 5').
     */
    fun batchStart(previous: Int?, counts: QueueCounts): Int? = when {
        counts.pending == 0 -> null
        previous != null -> previous
        else -> counts.read + counts.needsLook
    }

    fun lines(
        counts: QueueCounts,
        batchStart: Int?,
        route: ReadRoute?,
        pause: QueuePause,
        cloudHelp: Boolean,
        reads: MeterView?,
    ): List<HomeLine> = buildList {
        val with = readsWith(route, cloudHelp)
        if (counts.pending > 0) {
            // Reading only while a save is actually being read; a save parked for a retry 'waits' (UI-sweep nit).
            if (pause == QueuePause.NONE && counts.reading > 0) {
                val done = (counts.read + counts.needsLook - (batchStart ?: (counts.read + counts.needsLook))).coerceAtLeast(0)
                add(HomeLine.Reading(current = done + 1, total = done + counts.pending, with = with))
            } else {
                add(HomeLine.Waiting(counts.pending, pause))
            }
        }
        if (counts.needsLook > 0) add(HomeLine.NeedLook(counts.needsLook))
        if (reads != null && with == ReadsWith.CLOUD && reads.showCounter && !reads.isOut) {
            add(HomeLine.ReadsLeft(reads.left, reads.total))
        }
    }
}
