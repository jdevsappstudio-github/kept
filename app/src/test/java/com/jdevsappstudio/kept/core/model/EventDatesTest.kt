package com.jdevsappstudio.kept.core.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import java.time.LocalDate
import java.time.LocalTime
import org.junit.jupiter.api.Test

class EventDatesTest {

    private val today = LocalDate.of(2026, 11, 26)
    private fun parse(t: String) = EventDates.parse(t, today)

    @Test
    fun `day and month in words, with or without a year`() {
        assertThat(parse("Sun 6 Dec").date).isEqualTo(LocalDate.of(2026, 12, 6))
        assertThat(parse("6th December 2027").date).isEqualTo(LocalDate.of(2027, 12, 6))
        assertThat(parse("Dec 6, 2026").date).isEqualTo(LocalDate.of(2026, 12, 6))
        assertThat(parse("Saturday, January 10").date).isEqualTo(LocalDate.of(2027, 1, 10))
        assertThat(parse("Sept 3").date).isEqualTo(LocalDate.of(2027, 9, 3))
    }

    @Test
    fun `slashes are day then month`() {
        assertThat(parse("6/12").date).isEqualTo(LocalDate.of(2026, 12, 6))
        assertThat(parse("06/12/2026").date).isEqualTo(LocalDate.of(2026, 12, 6))
        assertThat(parse("6.12.27").date).isEqualTo(LocalDate.of(2027, 12, 6))
        assertThat(parse("31/02").date).isNull()
    }

    @Test
    fun `iso dates`() {
        assertThat(parse("2026-12-06 19:30").date).isEqualTo(LocalDate.of(2026, 12, 6))
    }

    @Test
    fun `times, 12 and 24 hour`() {
        assertThat(parse("6 Dec, 7 PM").time).isEqualTo(LocalTime.of(19, 0))
        assertThat(parse("6/12 7.30pm").let { it.date to it.time }).isEqualTo(LocalDate.of(2026, 12, 6) to LocalTime.of(19, 30))
        assertThat(parse("12 am").time).isEqualTo(LocalTime.of(0, 0))
        assertThat(parse("2026-12-06 19:30").time).isEqualTo(LocalTime.of(19, 30))
        assertThat(parse("Doors 8:15").time).isEqualTo(LocalTime.of(8, 15))
    }

    @Test
    fun `a past date without a year stays this year when recent`() {
        assertThat(parse("20 Nov").date).isEqualTo(LocalDate.of(2026, 11, 20))
        assertThat(parse("1 Feb").date).isEqualTo(LocalDate.of(2027, 2, 1))
    }

    @Test
    fun `nothing to read`() {
        assertThat(parse("this weekend").found).isFalse()
        assertThat(parse("").found).isFalse()
    }

    @Test
    fun `formats like the doc`() {
        assertThat(EventDates.formatDate(LocalDate.of(2026, 12, 6))).isEqualTo("Sun 6 Dec 2026")
        assertThat(EventDates.formatTime(LocalTime.of(19, 0))).isEqualTo("7:00 PM")
    }
}
