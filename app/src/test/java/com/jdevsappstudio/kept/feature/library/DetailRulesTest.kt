package com.jdevsappstudio.kept.feature.library

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.core.model.ItemAction.Target
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveItem
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.feature.library.detail.DetailRules
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.jupiter.api.Test

class DetailRulesTest {

    private val today = LocalDate.of(2026, 11, 26)
    private val zone = ZoneOffset.UTC

    private fun save(
        type: SaveType, title: String = "Thing", url: String? = null, link: String = "", app: String? = null,
        place: String = "", date: String = "", summary: String = "", source: SaveSource = SaveSource.SHARE_TEXT,
    ) = Save(
        id = 1, createdAt = 1_795_000_000_000, source = source, sourceApp = app, sharedText = null, url = url, imagePath = null,
        thumbPath = null, status = SaveStatus.READ, failReason = null, readBy = ReadBy.CLOUD, title = title, summary = summary,
        type = type, kind = "", place = place, eventDate = date, link = link, tags = emptyList(), items = emptyList(), readAt = null,
    )

    private fun items(vararg names: String) = names.mapIndexed { i, n -> SaveItem(i.toLong(), n, false, 0) }

    @Test
    fun `recipe with ingredients copies them, and the reel opens from the second button`() {
        val s = save(SaveType.RECIPE, url = "https://instagram.com/reel/x", app = "Instagram")
        val main = DetailRules.mainAction(s, items("200 g paneer", "1 onion"), today, zone)
        assertThat(main?.label).isEqualTo("Copy ingredients")
        assertThat(main?.target).isEqualTo(Target.Copy("200 g paneer\n1 onion"))
        assertThat(DetailRules.showsOpenOriginal(s, main)).isTrue()
        assertThat(DetailRules.openOriginalLabel(s)).isEqualTo("Open on Instagram")
        assertThat(DetailRules.showsItems(SaveType.RECIPE, items("1 onion"))).isTrue()
    }

    @Test
    fun `a 5-movie save has no big button, one movie gets Where to watch`() {
        val s = save(SaveType.MOVIE_SHOW, title = "5 thrillers to watch")
        assertThat(DetailRules.mainAction(s, items("Prisoners", "Se7en"), today, zone)).isNull()
        assertThat(DetailRules.showsItems(SaveType.MOVIE_SHOW, items("Prisoners"))).isFalse()
        assertThat(DetailRules.mainAction(s, items("Prisoners"), today, zone)?.target).isEqualTo(Target.WebSearch("Prisoners where to watch"))
    }

    @Test
    fun `a read whose button opens the link shows no second button`() {
        val s = save(SaveType.READ, url = "https://example.com/a")
        val main = DetailRules.mainAction(s, emptyList(), today, zone)
        assertThat(main?.target).isEqualTo(Target.OpenLink("https://example.com/a"))
        assertThat(DetailRules.showsOpenOriginal(s, main)).isFalse()
    }

    @Test
    fun `place opens maps with name and area`() {
        val main = DetailRules.mainAction(save(SaveType.PLACE, title = "Sunbeam Café", place = "Assagao, North Goa"), emptyList(), today, zone)
        assertThat(main?.label).isEqualTo("Open in Maps")
        assertThat(main?.target).isEqualTo(Target.Maps("Sunbeam Café, Assagao, North Goa"))
    }

    @Test
    fun `event fills the calendar from a parsed date`() {
        val timed = DetailRules.calendar(save(SaveType.EVENT, title = "Indie Night", place = "The Courtyard", date = "6/12 7 PM"), today, zone)
        assertThat(timed.beginMillis).isEqualTo(LocalDate.of(2026, 12, 6).atTime(19, 0).toInstant(zone).toEpochMilli())
        assertThat(timed.allDay).isFalse()
        assertThat(timed.location).isEqualTo("The Courtyard")
        assertThat(timed.notes).isNull()
        val dayOnly = DetailRules.calendar(save(SaveType.EVENT, date = "Sun 6 Dec"), today, zone)
        assertThat(dayOnly.allDay).isTrue()
        val unread = DetailRules.calendar(save(SaveType.EVENT, date = "this weekend"), today, zone)
        assertThat(unread.beginMillis).isNull()
        assertThat(unread.notes).isEqualTo("this weekend")
    }

    @Test
    fun `idea has no big button, untitled saves neither`() {
        assertThat(DetailRules.mainAction(save(SaveType.IDEA), emptyList(), today, zone)).isNull()
        assertThat(DetailRules.mainAction(save(SaveType.PRODUCT, title = " "), emptyList(), today, zone)).isNull()
    }

    @Test
    fun `saved line`() {
        // 1_795_000_000_000 ms = 18 Nov 2026 (UTC)
        assertThat(DetailRules.savedLine(save(SaveType.OTHER, app = "Instagram"), today, zone)).isEqualTo("Saved 18 Nov from Instagram")
        assertThat(DetailRules.savedLine(save(SaveType.OTHER, source = SaveSource.PICKER), LocalDate.of(2027, 1, 2), zone))
            .isEqualTo("Saved 18 Nov 2026 from a screenshot")
    }

    @Test
    fun `reel copy only for reels and shorts, not a news link`() {
        assertThat(DetailRules.isReel("https://www.instagram.com/reel/abc/")).isTrue()
        assertThat(DetailRules.isReel("https://youtube.com/shorts/xyz")).isTrue()
        assertThat(DetailRules.isReel("https://www.bbc.com/news/articles/c123")).isFalse()
        assertThat(DetailRules.isReel(null)).isFalse()
    }
}
