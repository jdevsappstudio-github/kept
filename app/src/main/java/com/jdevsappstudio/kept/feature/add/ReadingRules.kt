package com.jdevsappstudio.kept.feature.add

import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.feature.home.ReadsWith

/** The one count line under the k-reading title (H63: it counts this batch only). */
sealed interface ReadingLine {
    /** 'Adding 34 of 100': picked screenshots still being copied in. */
    data class Adding(val done: Int, val total: Int) : ReadingLine

    /** 'Reading 1 of 3 on this phone · one at a time, so it stays cool'. */
    data class Reading(val current: Int, val total: Int, val with: ReadsWith) : ReadingLine

    /** '2 waiting · read when you're back online' (same reasons as the home line). */
    data class Waiting(val count: Int, val pause: QueuePause) : ReadingLine

    /** 'All 3 read' / '2 read · 1 needs a look'. */
    data class Done(val read: Int, val needLook: Int) : ReadingLine

    /** Everything picked was already in Kept (the import snackbar says how many). */
    data object NothingNew : ReadingLine
}

object ReadingRules {

    /** H63: a 'Tagged Recipe · air-fryer · Edit' snackbar per finished card only for batches this small. */
    const val TAG_SNACK_MAX = 3

    /** Cards at most on the screen; a bigger batch still counts in full on the line. */
    const val BATCH_LIMIT = 300

    fun line(batch: List<Save>, adding: AddProgress?, pause: QueuePause, with: ReadsWith): ReadingLine {
        if (adding != null) return ReadingLine.Adding(adding.done, adding.total)
        val pending = batch.count { it.status == SaveStatus.QUEUED || it.status == SaveStatus.READING }
        val needLook = batch.count { it.status == SaveStatus.NEEDS_LOOK }
        val read = batch.count { it.status == SaveStatus.READ }
        return when {
            pending > 0 && pause == QueuePause.NONE && batch.any { it.status == SaveStatus.READING } ->
                ReadingLine.Reading(current = read + needLook + 1, total = batch.size, with = with)
            pending > 0 -> ReadingLine.Waiting(pending, pause)
            batch.isEmpty() -> ReadingLine.NothingNew
            else -> ReadingLine.Done(read, needLook)
        }
    }

    /** Saves that went from waiting to read since the last look ([before] = id → status). */
    fun justRead(before: Map<Long, SaveStatus>, now: List<Save>): List<Save> = now.filter { save ->
        save.status == SaveStatus.READ && before[save.id].let { it == SaveStatus.QUEUED || it == SaveStatus.READING }
    }

    /** Read card link lead-in: 'Not a recipe?', 'Not an event?', 'Not a movie or show?', 'Not something to read?'; Other gets 'Wrong type?'. */
    fun editPrompt(type: SaveType): String = when (type) {
        SaveType.OTHER -> "Wrong type?"
        SaveType.MOVIE_SHOW -> "Not a movie or show?"
        SaveType.READ -> "Not something to read?"
        else -> type.label.lowercase().let { w -> if (w.first() in "aeiou") "Not an $w?" else "Not a $w?" }
    }

    /** Snackbar words: 'Tagged Recipe · air-fryer' (type, then up to 2 tags). */
    fun taggedText(save: Save): String =
        (listOf("Tagged ${save.type.label}") + save.tags.take(2)).joinToString(" · ")
}
