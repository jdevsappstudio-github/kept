package com.jdevsappstudio.kept.feature.library

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.ShelfEntry
import org.junit.jupiter.api.Test

class SaveDisplayTest {

    private fun save(source: SaveSource, summary: String = "", app: String? = null, status: SaveStatus = SaveStatus.READ, title: String = "T") = Save(
        id = 1, createdAt = 0, source = source, sourceApp = app, sharedText = null, url = null, imagePath = null, thumbPath = null,
        status = status, failReason = null, readBy = ReadBy.CLOUD, title = title, summary = summary, type = SaveType.RECIPE,
        kind = "", place = "", eventDate = "", link = "", tags = emptyList(), items = emptyList(), readAt = null,
    )

    @Test
    fun `meta line is the summary's first line, else where it came from`() {
        assertThat(save(SaveSource.PICKER, summary = "\n15 min · serves 2\nmore").metaLine()).isEqualTo("15 min · serves 2")
        assertThat(save(SaveSource.SHARE_IMAGE).metaLine()).isEqualTo("from a screenshot")
        assertThat(save(SaveSource.SHARE_TEXT, app = "Instagram").metaLine()).isEqualTo("from Instagram")
        assertThat(save(SaveSource.PASTE).metaLine()).isEqualTo("from a link")
    }

    @Test
    fun `title by status`() {
        assertThat(save(SaveSource.PICKER, status = SaveStatus.READING, title = "").displayTitle()).isEqualTo("Reading it…")
        assertThat(save(SaveSource.PICKER, status = SaveStatus.READ, title = "").displayTitle()).isEqualTo("Untitled")
    }

    @Test
    fun `shelf row meta is source plus merge count`() {
        assertThat(ShelfEntry("a", "A", 2, 1, "Instagram").metaLine()).isEqualTo("Instagram · from 2 saves")
        assertThat(ShelfEntry("a", "A", 1, 1, "YouTube").metaLine()).isEqualTo("YouTube")
        assertThat(ShelfEntry("a", "A", 3, 1, null).metaLine()).isEqualTo("from 3 saves")
    }
}
