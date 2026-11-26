package com.jdevsappstudio.kept.feature.settings

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.ThemeMode
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.ai.DeviceModelState
import com.jdevsappstudio.kept.data.ask.FakeSentLog
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.MeterView
import com.jdevsappstudio.kept.core.model.SentKind
import com.jdevsappstudio.kept.feature.library.FakeLibrary
import com.jdevsappstudio.kept.feature.library.FakeSettings
import com.jdevsappstudio.kept.feature.library.MainDispatcherExtension
import com.jdevsappstudio.kept.feature.library.keepActive
import com.jdevsappstudio.kept.feature.library.next
import com.jdevsappstudio.kept.feature.library.save
import com.jdevsappstudio.kept.feature.library.testSnackbars
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

class SettingsCopyTest {

    @Test
    fun `privacy line says where saves are read and whether cloud help is on`() {
        assertThat(SettingsCopy.privacyLine(DeviceModelState.AVAILABLE, false)).isEqualTo("On this phone · Cloud help off")
        assertThat(SettingsCopy.privacyLine(DeviceModelState.UNAVAILABLE, true)).isEqualTo("Read in the cloud · Cloud help on")
        assertThat(SettingsCopy.privacyLine(DeviceModelState.UNAVAILABLE, false)).isEqualTo("No AI on this phone · Cloud help off")
        assertThat(SettingsCopy.privacyLine(null, false)).isEqualTo("Checking this phone · Cloud help off")
    }

    @Test
    fun `only a phone without Nano opens k-unsupported`() {
        assertThat(SettingsCopy.readingRow(DeviceModelState.AVAILABLE, false).opensUnsupported).isFalse()
        assertThat(SettingsCopy.readingRow(DeviceModelState.DOWNLOADING, true).opensUnsupported).isFalse()
        assertThat(SettingsCopy.readingRow(null, true).opensUnsupported).isFalse()
        assertThat(SettingsCopy.readingRow(DeviceModelState.UNAVAILABLE, false).opensUnsupported).isTrue()
        assertThat(SettingsCopy.readingRow(DeviceModelState.UNAVAILABLE, true).title).isEqualTo("In the cloud")
    }

    @Test
    fun `cloud help names images only where it sends them`() {
        assertThat(SettingsCopy.cloudHelpBody(DeviceModelState.AVAILABLE)).contains("never images")
        assertThat(SettingsCopy.cloudHelpBody(DeviceModelState.UNAVAILABLE)).contains("images too")
    }

    @Test
    fun `sent and delete lines count right`() {
        assertThat(SettingsCopy.sentLine(0)).isEqualTo("Last 30 days · nothing sent")
        assertThat(SettingsCopy.sentLine(1)).isEqualTo("Last 30 days · 1 request")
        assertThat(SettingsCopy.sentLine(12)).isEqualTo("Last 30 days · 12 requests")
        assertThat(SettingsCopy.deleteBody(412)).contains("All 412 saves")
        assertThat(SettingsCopy.deleteBody(1)).contains("Your 1 save and its image")
        assertThat(SettingsCopy.deleteBody(0)).doesNotContain("0 saves")
        assertThat(SettingsCopy.deleteBody(3)).contains("Settings stay")
    }

    @Test
    fun `k-unsupported shows today's limits from the quota`() {
        val reads = MeterView(CloudMeter.READS, used = 3, bonus = 0, limit = 100, perAd = 0)
        val answers = MeterView(CloudMeter.ANSWERS, used = 0, bonus = 0, limit = 20, perAd = 0)
        val line = SettingsCopy.unsupportedLimits(reads, answers)
        assertThat(line).contains("100 saves read a day")
        assertThat(line).contains("20 a day")
    }

    @Test
    fun `feedback email carries device facts, never saves`() {
        val email = SettingsCopy.feedbackEmail("1.0", "OnePlus IN2011", "13")
        assertThat(email.subject).isEqualTo("Kept feedback")
        assertThat(email.body).contains("Kept 1.0 · OnePlus IN2011 · Android 13")
    }

    @Test
    fun `theme mode follows the system only on System`() {
        assertThat(ThemeMode.SYSTEM.isDark(true)).isTrue()
        assertThat(ThemeMode.LIGHT.isDark(true)).isFalse()
        assertThat(ThemeMode.DARK.isDark(false)).isTrue()
    }
}

@ExtendWith(MainDispatcherExtension::class)
class SettingsViewModelTest {
    private val settings = FakeSettings()

    @Test
    fun `mode and reading updates are saved`() = runTest {
        val vm = SettingsViewModel(settings) { DeviceModelState.UNAVAILABLE }
        keepActive(vm.state)
        vm.onAction(SettingsAction.OnThemeModeClick(ThemeMode.DARK))
        vm.onAction(SettingsAction.OnReadingUpdatesChange(false))
        assertThat(settings.themeMode.value).isEqualTo(ThemeMode.DARK)
        assertThat(vm.state.value.readingUpdates).isFalse()
        assertThat(vm.state.value.privacyLine).isEqualTo("No AI on this phone · Cloud help off")
    }
}

@ExtendWith(MainDispatcherExtension::class)
class PrivacyViewModelTest {
    private val settings = FakeSettings()
    private val library = FakeLibrary()
    private val sent = FakeSentLog()
    private val snackbars = testSnackbars()
    private var wiped = 0
    private val counts = MutableStateFlow(QueueCounts(queued = 1, reading = 0, needsLook = 2, read = 9))

    private fun vm(device: DeviceModelState = DeviceModelState.UNAVAILABLE) = PrivacyViewModel(
        settings, library, { wiped++ }, snackbars, sent, counts, { device },
    )

    private fun s(id: Long, status: SaveStatus, reason: String? = null) = save(id, title = "").copy(status = status, failReason = reason)

    @Test
    fun `counts saves and sent requests`() = runTest {
        sent.log(SentKind.READ, "a")
        sent.log(SentKind.ASK, "b")
        val vm = vm()
        keepActive(vm.state)
        assertThat(vm.state.value.saveCount).isEqualTo(12)
        assertThat(vm.state.value.sentCount).isEqualTo(2)
    }

    @Test
    fun `without Nano, turning cloud help on asks first, then re-queues the no-Nano saves`() = runTest {
        library.put(s(1, SaveStatus.NEEDS_LOOK, "NO_MODEL"), s(2, SaveStatus.NEEDS_LOOK, "BLOCKED"))
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(PrivacyAction.OnCloudHelpChange(true))
        assertThat(vm.state.value.dialog).isEqualTo(PrivacyDialog.CLOUD_CONSENT)
        assertThat(settings.cloudHelp.value).isFalse()
        vm.onAction(PrivacyAction.OnConsentYes)
        assertThat(settings.cloudHelp.value).isTrue()
        assertThat(vm.state.value.dialog).isEqualTo(PrivacyDialog.NONE)
        assertThat(library.log).containsExactly("readAgain 1")
    }

    @Test
    fun `with Nano the switch flips at once, and off never asks`() = runTest {
        val vm = vm(DeviceModelState.AVAILABLE)
        keepActive(vm.state)
        vm.onAction(PrivacyAction.OnCloudHelpChange(true))
        assertThat(settings.cloudHelp.value).isTrue()
        vm.onAction(PrivacyAction.OnCloudHelpChange(false))
        assertThat(settings.cloudHelp.value).isFalse()
        assertThat(vm.state.value.dialog).isEqualTo(PrivacyDialog.NONE)
    }

    @Test
    fun `cancel keeps everything`() = runTest {
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(PrivacyAction.OnDeleteClick)
        assertThat(vm.state.value.dialog).isEqualTo(PrivacyDialog.DELETE)
        vm.onAction(PrivacyAction.OnDismissDialog)
        assertThat(wiped).isEqualTo(0)
    }

    @Test
    fun `delete wipes once, says so and leaves for Home`() = runTest {
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(PrivacyAction.OnDeleteClick)
        vm.onAction(PrivacyAction.OnDeleteConfirm)
        vm.onAction(PrivacyAction.OnDeleteConfirm) // a double tap wipes once
        assertThat(wiped).isEqualTo(1)
        assertThat((snackbars.next().text as UiText.Dynamic).value).isEqualTo(SettingsCopy.DELETE_DONE)
        assertThat(vm.events.first()).isEqualTo(PrivacyEvent.Deleted)
    }

    @Test
    fun `k-unsupported buttons set cloud help and close`() = runTest {
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(PrivacyAction.OnReadWithCloud)
        assertThat(settings.cloudHelp.value).isTrue()
        assertThat(vm.events.first()).isEqualTo(PrivacyEvent.Close)
        vm.onAction(PrivacyAction.OnTagByHand)
        assertThat(settings.cloudHelp.value).isFalse()
        assertThat(vm.events.first()).isEqualTo(PrivacyEvent.Close)
    }

}
