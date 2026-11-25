package com.jdevsappstudio.kept.feature.share

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jdevsappstudio.kept.data.queue.ReadRoute
import com.jdevsappstudio.kept.data.queue.ReadRouting
import org.junit.jupiter.api.Test

class ShareTextTest {
    @Test fun `finds the link inside a reel share`() =
        assertThat(ShareText.firstUrl("Check this out! https://www.instagram.com/reel/abc123/?igsh=x."))
            .isEqualTo("https://www.instagram.com/reel/abc123/?igsh=x")

    @Test fun `link in brackets loses the bracket`() =
        assertThat(ShareText.firstUrl("(see https://example.com/a)")).isEqualTo("https://example.com/a")

    @Test fun `plain text has no link`() = assertThat(ShareText.firstUrl("buy oat milk")).isNull()

    @Test fun `note title is the first line, capped`() {
        assertThat(ShareText.noteTitle("\n  Buy oat milk  \nand eggs")).isEqualTo("Buy oat milk")
        assertThat(ShareText.noteTitle("x".repeat(80)).length).isEqualTo(60)
    }

    @Test fun `routing never sends without a yes`() {
        assertThat(ReadRouting.route(deviceReady = true, cloudHelp = false, online = false)).isEqualTo(ReadRoute.DEVICE)
        assertThat(ReadRouting.route(deviceReady = false, cloudHelp = false, online = true)).isEqualTo(ReadRoute.NEEDS_CONSENT)
        assertThat(ReadRouting.route(deviceReady = false, cloudHelp = true, online = true)).isEqualTo(ReadRoute.CLOUD)
        assertThat(ReadRouting.route(deviceReady = false, cloudHelp = true, online = false)).isEqualTo(ReadRoute.OFFLINE)
    }
}
