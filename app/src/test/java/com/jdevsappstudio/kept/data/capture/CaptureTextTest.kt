package com.jdevsappstudio.kept.data.capture

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jdevsappstudio.kept.data.queue.ReadRoute
import com.jdevsappstudio.kept.data.queue.ReadRouting
import org.junit.jupiter.api.Test

class CaptureTextTest {
    @Test fun `finds the link inside a reel share`() =
        assertThat(CaptureText.firstUrl("Check this out! https://www.instagram.com/reel/abc123/?igsh=x."))
            .isEqualTo("https://www.instagram.com/reel/abc123/?igsh=x")

    @Test fun `link in brackets loses the bracket`() =
        assertThat(CaptureText.firstUrl("(see https://example.com/a)")).isEqualTo("https://example.com/a")

    @Test fun `plain text has no link`() = assertThat(CaptureText.firstUrl("buy oat milk")).isNull()

    @Test fun `note title is the first line, capped`() {
        assertThat(CaptureText.noteTitle("\n  Buy oat milk  \nand eggs")).isEqualTo("Buy oat milk")
        assertThat(CaptureText.noteTitle("x".repeat(80)).length).isEqualTo(60)
    }

    @Test fun `routing never sends without a yes`() {
        assertThat(ReadRouting.route(deviceReady = true, cloudHelp = false, online = false)).isEqualTo(ReadRoute.DEVICE)
        assertThat(ReadRouting.route(deviceReady = false, cloudHelp = false, online = true)).isEqualTo(ReadRoute.NEEDS_CONSENT)
        assertThat(ReadRouting.route(deviceReady = false, cloudHelp = true, online = true)).isEqualTo(ReadRoute.CLOUD)
        assertThat(ReadRouting.route(deviceReady = false, cloudHelp = true, online = false)).isEqualTo(ReadRoute.OFFLINE)
    }
}

class PastedUrlTest {
    @Test fun `bare addresses get https`() {
        assertThat(CaptureText.pastedUrl("instagram.com/reel/Cx3abc/")).isEqualTo("https://instagram.com/reel/Cx3abc/")
        assertThat(CaptureText.pastedUrl(" www.zomato.in. ")).isEqualTo("https://www.zomato.in")
    }

    @Test fun `a link inside text wins`() =
        assertThat(CaptureText.pastedUrl("watch this http://youtu.be/x ok")).isEqualTo("http://youtu.be/x")

    @Test fun `words, sentences and versions are not links`() {
        assertThat(CaptureText.pastedUrl("buy oat milk")).isNull()
        assertThat(CaptureText.pastedUrl("see you at 5.30")).isNull()
        assertThat(CaptureText.pastedUrl("v1.2")).isNull()
        assertThat(CaptureText.pastedUrl("hello.")).isNull()
    }
}
