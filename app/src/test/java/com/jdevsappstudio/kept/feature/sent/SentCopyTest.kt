package com.jdevsappstudio.kept.feature.sent

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jdevsappstudio.kept.core.model.SentKind
import com.jdevsappstudio.kept.core.model.SentRequest
import com.jdevsappstudio.kept.data.ask.AskHistory
import com.jdevsappstudio.kept.data.ask.AskPrompt
import com.jdevsappstudio.kept.data.ask.SourceSave
import com.jdevsappstudio.kept.data.fill.FillPrompt
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class SentCopyTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 26)
    private fun at(t: LocalDateTime) = t.toInstant(zone).toEpochMilli()
    private fun row(kind: SentKind, text: String, images: Int = 0, sentAt: Long = 0) = SentRequest(1, sentAt, kind, text, images, null)

    private fun src(id: Long, title: String) = SourceSave(id, id, title, "place", place = "Goa", summary = "Question: not this one")

    @Test
    fun `ask row reads the question and save count back out of the real prompt`() {
        val p = AskPrompt.build(
            "Which Goa cafés\ndid I save?",
            listOf(AskHistory("Earlier?", "Yes")),
            listOf(src(1, "Sunbeam Café"), src(2, "Tide Table"), src(3, "Little Fig")),
        )
        val r = row(SentKind.ASK, p.text)
        assertThat(SentCopy.askQuestion(p.text)).isEqualTo("Which Goa cafés did I save?")
        assertThat(SentCopy.askSaveCount(p.text)).isEqualTo(3)
        assertThat(SentCopy.summary(r)).isEqualTo("“Which Goa cafés did I save?” + the text of 3 saves")
    }

    @Test
    fun `ask with one or no saves`() {
        assertThat(SentCopy.summary(row(SentKind.ASK, AskPrompt.build("q", emptyList(), listOf(src(1, "A"))).text)))
            .isEqualTo("“q” + the text of 1 save")
        assertThat(SentCopy.summary(row(SentKind.ASK, AskPrompt.build("q", emptyList(), emptyList()).text)))
            .isEqualTo("“q” + no saves matched")
    }

    @Test
    fun `fill in row shows the link without the scheme`() {
        val text = FillPrompt.build("https://www.instagram.com/reel/abc", "Best tacos")
        assertThat(SentCopy.summary(row(SentKind.FILL_IN, text))).isEqualTo("1 link: instagram.com/reel/abc")
        assertThat(SentCopy.summary(row(SentKind.FILL_IN, "no link here"))).isEqualTo("1 link")
    }

    @Test
    fun `read rows say what went`() {
        assertThat(SentCopy.summary(row(SentKind.READ, "", images = 1))).isEqualTo("Image of 1 save, to be read")
        assertThat(SentCopy.summary(row(SentKind.READ, "caption", images = 1))).isEqualTo("Image and text of 1 save, to be read")
        assertThat(SentCopy.summary(row(SentKind.READ, "a link"))).isEqualTo("Text of 1 save, to be read")
        assertThat(SentCopy.imageNote(0)).isNull()
    }

    @Test
    fun `titles - today with time, yesterday, date, other year`() {
        val ask = { t: LocalDateTime -> SentCopy.title(row(SentKind.ASK, "", sentAt = at(t)), today, zone) }
        assertThat(ask(LocalDateTime.of(2026, 11, 26, 19, 40))).isEqualTo("Ask · today, 7:40 PM")
        assertThat(SentCopy.title(row(SentKind.FILL_IN, "", sentAt = at(LocalDateTime.of(2026, 11, 25, 9, 0))), today, zone))
            .isEqualTo("Fill in with cloud · yesterday")
        assertThat(ask(LocalDateTime.of(2026, 11, 22, 9, 0))).isEqualTo("Ask · 22 Nov")
        assertThat(ask(LocalDateTime.of(2025, 12, 30, 9, 0))).isEqualTo("Ask · 30 Dec 2025")
    }

    @Test
    fun `top line counts and names images only when one went`() {
        assertThat(SentCopy.topLine(12, false)).isEqualTo("Last 30 days · 12 cloud requests · text only, never images")
        assertThat(SentCopy.topLine(1, true))
            .isEqualTo("Last 30 days · 1 cloud request · text, links and images of saves you chose to read in the cloud")
    }
}
