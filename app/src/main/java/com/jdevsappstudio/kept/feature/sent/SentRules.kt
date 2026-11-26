package com.jdevsappstudio.kept.feature.sent

import com.jdevsappstudio.kept.core.model.SentKind
import com.jdevsappstudio.kept.core.model.SentRequest
import com.jdevsappstudio.kept.data.sent.SentLogLocalDataSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * k-sent words in one place (H67; UX open to the review panel). The row summary is read back out of the logged
 * text itself (AskPrompt / FillPrompt formats), so what the row claims can never drift from what went.
 */
internal object SentCopy {
    const val TITLE = "What was sent"
    const val EMPTY = "Nothing sent yet. Kept has only used the AI on this phone."
    const val EXACT = "Exactly what went"
    val FOOT = "The log stays on this phone for ${SentLogLocalDataSource.KEEP_DAYS} days. " +
        "Kept has no servers: requests go from this phone to Google Gemini through Firebase."

    /** 'Last 30 days · 12 cloud requests · text only, never images'; names images only if a row had one. */
    fun topLine(count: Int, anyImages: Boolean): String {
        val n = "$count cloud ${if (count == 1) "request" else "requests"}"
        val what = if (anyImages) "text, links and images of saves you chose to read in the cloud" else "text only, never images"
        return "Last ${SentLogLocalDataSource.KEEP_DAYS} days · $n · $what"
    }

    fun kindLabel(kind: SentKind): String = when (kind) {
        SentKind.ASK -> "Ask"
        SentKind.FILL_IN -> "Fill in with cloud"
        SentKind.READ -> "Read in the cloud"
    }

    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val day = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dayYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /** 'Ask · today, 7:40 PM' · 'Fill in with cloud · yesterday' · 'Ask · 22 Nov'. */
    fun title(row: SentRequest, today: LocalDate, zone: ZoneId): String {
        val at = Instant.ofEpochMilli(row.sentAt).atZone(zone)
        val d = at.toLocalDate()
        val whenText = when {
            d == today -> "today, ${time.format(at)}"
            d == today.minusDays(1) -> "yesterday"
            d.year == today.year -> day.format(d)
            else -> dayYear.format(d)
        }
        return "${kindLabel(row.kind)} · $whenText"
    }

    /** The second line: what went, in a few words. */
    fun summary(row: SentRequest): String = when (row.kind) {
        SentKind.ASK -> {
            val n = askSaveCount(row.text)
            val q = askQuestion(row.text)
            val saves = if (n == 0) "no saves matched" else "the text of $n ${if (n == 1) "save" else "saves"}"
            if (q.isEmpty()) saves.replaceFirstChar { it.uppercase() } else "“$q” + $saves"
        }
        SentKind.FILL_IN -> fillLink(row.text)?.let { "1 link: ${shortLink(it)}" } ?: "1 link"
        SentKind.READ -> when {
            row.images > 0 && row.text.isBlank() -> "Image of 1 save, to be read"
            row.images > 0 -> "Image and text of 1 save, to be read"
            else -> "Text of 1 save, to be read"
        }
    }

    /** Under the exact text of a cloud read that carried the screenshot: the image went too, it just can't be shown as text. */
    fun imageNote(images: Int): String? = when {
        images <= 0 -> null
        images == 1 -> "+ the screenshot itself (1 image, made smaller first)"
        else -> "+ $images images"
    }

    /** A read with only an image has no text to show. */
    const val NO_TEXT = "No text: only the image went."

    // --- read back from the logged text -------------------------------------------------------------------------

    /**
     * AskPrompt ends with a blank line + 'Question: …'. Everything above it is one-lined, so the first such marker is
     * the real one even if the question itself holds newlines or the word 'Question:'.
     */
    private const val Q_MARK = "\n\nQuestion: "

    /** The question on one line ('' if the text isn't an AskPrompt). */
    fun askQuestion(text: String): String {
        val i = text.indexOf(Q_MARK)
        return if (i < 0) "" else text.substring(i + Q_MARK.length).replace(Regex("\\s+"), " ").trim()
    }

    private val SOURCE = Regex("^\\[\\d+] ", RegexOption.MULTILINE)

    /** Each source block starts a line with '[n] ' (only counted above the question). */
    fun askSaveCount(text: String): Int {
        val i = text.indexOf(Q_MARK)
        return SOURCE.findAll(if (i < 0) text else text.substring(0, i)).count()
    }

    /** FillPrompt carries 'Link: <url>' on its own line. */
    fun fillLink(text: String): String? =
        text.lineSequence().firstOrNull { it.startsWith("Link: ") }?.removePrefix("Link: ")?.trim()?.takeIf { it.isNotEmpty() }

    /** 'https://www.instagram.com/reel/abc' → 'instagram.com/reel/abc' (the row ellipsizes the rest). */
    fun shortLink(url: String): String = url.trim().replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "").removePrefix("www.")
}
