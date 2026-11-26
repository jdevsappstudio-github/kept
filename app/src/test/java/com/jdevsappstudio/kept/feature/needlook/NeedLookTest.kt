package com.jdevsappstudio.kept.feature.needlook

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.MeterView
import com.jdevsappstudio.kept.feature.home.QueueView
import com.jdevsappstudio.kept.feature.library.FakeLibrary
import com.jdevsappstudio.kept.feature.library.FakeSettings
import com.jdevsappstudio.kept.feature.library.MainDispatcherExtension
import com.jdevsappstudio.kept.feature.library.keepActive
import com.jdevsappstudio.kept.feature.library.next
import com.jdevsappstudio.kept.feature.library.resId
import com.jdevsappstudio.kept.feature.library.save
import com.jdevsappstudio.kept.feature.library.testSnackbars
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.ZoneOffset

private fun s(id: Long, status: SaveStatus, reason: String? = null) =
    save(id, title = "").copy(status = status, failReason = reason)

class NeedLookRulesTest {

    @Test
    fun `needs-a-look rows come first newest first, then the line in reading order`() {
        val rows = NeedLookRules.rows(
            listOf(s(5, SaveStatus.QUEUED), s(4, SaveStatus.NEEDS_LOOK), s(3, SaveStatus.READING), s(2, SaveStatus.QUEUED), s(1, SaveStatus.NEEDS_LOOK)),
            cloudHelp = false,
        )
        assertThat(rows.map { it.save.id }).containsExactly(4L, 1L, 3L, 2L, 5L)
        assertThat(rows.filterIsInstance<NeedLookRow.Pending>().map { it.ahead }).containsExactly(0, 1, 2)
    }

    @Test
    fun `blocked saves are never offered again, unreadable ones are`() {
        assertThat(NeedLookRules.failCopy("BLOCKED", cloudHelp = true).canRetry).isFalse()
        assertThat(NeedLookRules.failCopy("UNREADABLE", cloudHelp = false).canRetry).isTrue()
        assertThat(NeedLookRules.failCopy(null, cloudHelp = false).canRetry).isTrue()
    }

    @Test
    fun `no Nano offers Read with cloud until cloud help is on, then Try again`() {
        val off = NeedLookRules.failCopy("NO_MODEL", cloudHelp = false)
        assertThat(off.readWithCloud).isTrue()
        assertThat(off.canRetry).isFalse()
        val on = NeedLookRules.failCopy("NO_MODEL", cloudHelp = true)
        assertThat(on.readWithCloud).isFalse()
        assertThat(on.canRetry).isTrue()
    }

    @Test
    fun `the banner shows only when something waits and the queue is stopped for a reason`() {
        assertThat(NeedLookRules.banner(QueuePause.OFFLINE, 0)).isNull()
        assertThat(NeedLookRules.banner(QueuePause.NONE, 3)).isNull()
        assertThat(NeedLookRules.banner(QueuePause.IN_BACKGROUND, 3)).isNull()
        assertThat(NeedLookRules.banner(QueuePause.OFFLINE, 3)?.body)
            .isEqualTo("3 saves are kept and will be read in the cloud when you’re back online.")
        assertThat(NeedLookRules.banner(QueuePause.CLOUD_CAP, 1)?.body.orEmpty().startsWith("1 save is kept and will be read after midnight, by itself."))
            .isTrue()
        assertThat(NeedLookRules.banner(QueuePause.NEEDS_CONSENT, 2)?.readWithCloud).isEqualTo(true)
    }

    @Test
    fun `pending lines say reading, the pause, or the place in line`() {
        val reading = NeedLookRow.Pending(s(1, SaveStatus.READING), 0)
        val third = NeedLookRow.Pending(s(2, SaveStatus.QUEUED), 3)
        assertThat(NeedLookRules.pendingLine(reading, QueuePause.CLOUD_CAP)).isEqualTo("Reading…")
        assertThat(NeedLookRules.pendingLine(third, QueuePause.CLOUD_CAP)).isEqualTo("Waiting: today’s cloud reads are used")
        assertThat(NeedLookRules.pendingLine(third, QueuePause.NONE)).isEqualTo("Waiting in line · 3 ahead")
        assertThat(NeedLookRules.pendingLine(third.copy(ahead = 0), QueuePause.NONE)).isEqualTo("Next in line")
    }

    @Test
    fun `untitled saves are named from where they came from`() {
        val day = 1_764_028_800_000 // 25 Nov 2025 00:00 UTC
        val shot = s(1, SaveStatus.QUEUED).copy(createdAt = day)
        assertThat(NeedLookRules.title(shot, ZoneOffset.UTC)).isEqualTo("Screenshot, 25 Nov")
        val reel = shot.copy(source = SaveSource.SHARE_TEXT, sourceApp = "com.instagram.android")
        assertThat(NeedLookRules.title(reel, ZoneOffset.UTC)).isEqualTo("Link from Instagram")
        val page = shot.copy(source = SaveSource.PASTE, url = "https://www.bbc.com/news/1")
        assertThat(NeedLookRules.title(page, ZoneOffset.UTC)).isEqualTo("Link from bbc.com")
        assertThat(NeedLookRules.title(shot.copy(title = "Paneer tikka"), ZoneOffset.UTC)).isEqualTo("Paneer tikka")
    }

    @Test
    fun `the reads line shows once few cloud reads are left`() {
        assertThat(NeedLookRules.readsLine(MeterView(CloudMeter.READS, used = 10, bonus = 0, limit = 30, perAd = 10))).isNull()
        assertThat(NeedLookRules.readsLine(MeterView(CloudMeter.READS, used = 28, bonus = 0, limit = 30, perAd = 10)))
            .isEqualTo("28 of 30 read today · resets at midnight")
        assertThat(NeedLookRules.readsLine(null)).isNull()
    }
}

@ExtendWith(MainDispatcherExtension::class)
class NeedLookViewModelTest {

    private val library = FakeLibrary()
    private val settings = FakeSettings()
    private val snackbars = testSnackbars()
    private val queue = MutableStateFlow(QueueView(QueueCounts.EMPTY, null, QueuePause.NONE))

    private fun vm() = NeedLookViewModel(library, queue, settings, snackbars)

    @Test
    fun `lists only saves that aren't read, with the banner for the pause`() = runTest {
        library.put(s(1, SaveStatus.READ), s(2, SaveStatus.QUEUED), s(3, SaveStatus.NEEDS_LOOK, "UNREADABLE"))
        queue.value = queue.value.copy(pause = QueuePause.OFFLINE)
        val vm = vm()
        keepActive(vm.state)
        assertThat(vm.state.value.rows.map { it.save.id }).containsExactly(3L, 2L)
        assertThat(vm.state.value.banner).isNotNull()
    }

    @Test
    fun `Try again puts the save back in line`() = runTest {
        library.put(s(3, SaveStatus.NEEDS_LOOK, "UNREADABLE"))
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(NeedLookAction.OnTryAgainClick(3))
        assertThat(library.log).containsExactly("readAgain 3")
        assertThat(snackbars.next().text.resId()).isEqualTo(R.string.snack_read_again)
    }

    @Test
    fun `turning on cloud help re-queues the saves that had no Nano`() = runTest {
        library.put(s(1, SaveStatus.NEEDS_LOOK, "NO_MODEL"), s(2, SaveStatus.NEEDS_LOOK, "BLOCKED"))
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(NeedLookAction.OnReadWithCloudClick)
        assertThat(vm.state.value.consent).isTrue()
        vm.onAction(NeedLookAction.OnTurnOnCloudHelp)
        assertThat(settings.cloudHelp.value).isTrue()
        assertThat(vm.state.value.consent).isFalse()
        assertThat(library.log).containsExactly("readAgain 1")
    }

    @Test
    fun `Not now leaves cloud help off`() = runTest {
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(NeedLookAction.OnReadWithCloudClick)
        vm.onAction(NeedLookAction.OnNotNow)
        assertThat(vm.state.value.consent).isFalse()
        assertThat(settings.cloudHelp.value).isFalse()
    }
}
