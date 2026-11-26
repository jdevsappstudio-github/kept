package com.jdevsappstudio.kept.core.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** An event's date and time, read from the text the reader kept "as written" (k-date). Either part can be missing. */
data class EventWhen(val date: LocalDate?, val time: LocalTime?) {
    val found get() = date != null || time != null
}

/**
 * Simple parse of an event date as written (owner 26 Nov: common formats only, no fix picker in the MVP).
 * Numbers with a slash are day/month ('6/12' = 6 Dec), as the doc reads them. No year: this year, or the next
 * one when the date is over [PAST_DAYS] days gone (a December screenshot of a January gig).
 */
object EventDates {
    private const val PAST_DAYS = 60L

    private val months: Map<String, Month> = Month.entries.flatMap { m ->
        val full = m.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
        listOf(full to m, full.take(3) to m) + if (m == Month.SEPTEMBER) listOf("sept" to m) else emptyList()
    }.toMap()

    private const val MON = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
    private const val DAY = "(\\d{1,2})(?:st|nd|rd|th)?"
    private const val YEAR = "(?:,?\\s*(\\d{4}))?"

    private val isoDate = Regex("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b")
    private val dayMonth = Regex("\\b$DAY\\s+(?:of\\s+)?$MON\\.?$YEAR\\b", RegexOption.IGNORE_CASE)
    private val monthDay = Regex("\\b$MON\\.?\\s+$DAY$YEAR\\b", RegexOption.IGNORE_CASE)
    private val slash = Regex("\\b(\\d{1,2})[/.](\\d{1,2})(?:[/.](\\d{2}|\\d{4}))?\\b")
    private val time12 = Regex("\\b(\\d{1,2})(?:[:.](\\d{2}))?\\s*([ap])\\.?\\s*m\\b\\.?", RegexOption.IGNORE_CASE)
    private val time24 = Regex("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b")

    fun parse(text: String, today: LocalDate): EventWhen {
        val t = text.trim()
        if (t.isEmpty()) return EventWhen(null, null)
        val time = time(t)
        // Take the time out first so '7.30 pm' isn't read as 7 July.
        val rest = time12.replace(t, " ").let { time24.replace(it, " ") }
        return EventWhen(date(rest, today), time)
    }

    private fun date(t: String, today: LocalDate): LocalDate? {
        isoDate.find(t)?.let { m ->
            val (y, mo, d) = m.destructured
            return of(y.toInt(), mo.toInt(), d.toInt())
        }
        dayMonth.find(t)?.let { m -> return named(m.groupValues[1], m.groupValues[2], m.groupValues[3], today) }
        monthDay.find(t)?.let { m -> return named(m.groupValues[2], m.groupValues[1], m.groupValues[3], today) }
        slash.find(t)?.let { m ->
            val (d, mo, y) = m.destructured
            val year = when (y.length) {
                4 -> y.toInt()
                2 -> 2000 + y.toInt()
                else -> null
            }
            return withYear(d.toInt(), mo.toInt(), year, today)
        }
        return null
    }

    private fun named(day: String, month: String, year: String, today: LocalDate): LocalDate? {
        val m = months[month.lowercase().trimEnd('.')] ?: months[month.lowercase().take(3)] ?: return null
        return withYear(day.toInt(), m.value, year.toIntOrNull(), today)
    }

    private fun withYear(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? {
        if (year != null) return of(year, month, day)
        val d = of(today.year, month, day) ?: return null
        return if (d.isBefore(today.minusDays(PAST_DAYS))) d.plusYears(1) else d
    }

    private fun of(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, m, d) }.getOrNull()

    private fun time(t: String): LocalTime? {
        time12.find(t)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toIntOrNull() ?: 0
            if (h !in 1..12 || min > 59) return null
            val pm = m.groupValues[3].equals("p", ignoreCase = true)
            return LocalTime.of((h % 12) + if (pm) 12 else 0, min)
        }
        time24.find(t)?.let { m -> return LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        return null
    }

    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    /** 'Sun 6 Dec 2026' (k-date). */
    fun formatDate(date: LocalDate): String = dayFormat.format(date)

    /** '7:00 PM'. */
    fun formatTime(time: LocalTime): String = timeFormat.format(time).uppercase(Locale.ENGLISH)
}
