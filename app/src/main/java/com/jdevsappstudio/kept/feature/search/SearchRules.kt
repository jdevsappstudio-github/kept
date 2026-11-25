package com.jdevsappstudio.kept.feature.search

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.jdevsappstudio.kept.core.model.SearchHit
import com.jdevsappstudio.kept.core.model.Shelf

/** "…banana pancakes, North \u0002Goa\u0003…" → matched words bold, markers dropped. An unclosed marker bolds to the end. */
fun snippetText(snippet: String): AnnotatedString = buildAnnotatedString {
    var bold = false
    val run = StringBuilder()
    fun flush() {
        if (run.isEmpty()) return
        if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(run) } else append(run)
        run.clear()
    }
    for (c in snippet) when (c) {
        SearchHit.HIT_START -> { flush(); bold = true }
        SearchHit.HIT_END -> { flush(); bold = false }
        else -> run.append(c)
    }
    flush()
}

/** k-search status line: '2 still reading, 1 not read: they show up once read.' (no 'See'; the screen adds it). */
fun statusLine(stillReading: Int, notRead: Int): String {
    val parts = listOfNotNull(
        if (stillReading > 0) "$stillReading still reading" else null,
        if (notRead > 0) "$notRead not read" else null,
    )
    return if (parts.isEmpty()) "" else parts.joinToString(", ") + ": they show up once read."
}

/** k-search-none body: says why, and names the saves that are still reading. */
fun nothingFoundBody(stillReading: Int): String {
    val base = "Search looks at every word Kept read, plus tags and items."
    return when (stillReading) {
        0 -> base
        1 -> "$base 1 save is still reading: it shows up once read."
        else -> "$base $stillReading saves are still reading: they show up once read."
    }
}

/** k-search-tag card: 'Movies shelf · 19 items' (counts only, no to-watch wording: H43). */
fun Shelf.matchHeadline(): String = "$title shelf · " + if (countsItems) plural(entries, "item") else plural(saves, "save")

/** '6 saves' over the hits; '1 save'. */
fun plural(n: Int, word: String): String = "$n $word" + if (n == 1) "" else "s"

/** H62: the keyboard comes up only when search opens with nothing typed, and never in pick mode. */
fun autoFocusSearch(state: SearchState): Boolean = state.query.isEmpty() && state.pick == null
