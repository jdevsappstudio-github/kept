package com.jdevsappstudio.kept.feature.onboarding

import android.graphics.Bitmap
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.ShareApp
import com.jdevsappstudio.kept.data.ai.CardReader
import com.jdevsappstudio.kept.data.ai.CloudChoice
import com.jdevsappstudio.kept.data.ai.DeviceModelDownloads
import com.jdevsappstudio.kept.data.ai.DeviceModelState
import com.jdevsappstudio.kept.data.ai.ModelDownload
import com.jdevsappstudio.kept.data.ai.ModelDownloadStep
import com.jdevsappstudio.kept.data.ai.ReadEngine
import com.jdevsappstudio.kept.data.ai.ReadFailure
import com.jdevsappstudio.kept.data.ai.ReadResult
import com.jdevsappstudio.kept.data.capture.FakeCopier
import com.jdevsappstudio.kept.data.capture.FakeSaveStore
import com.jdevsappstudio.kept.data.capture.Keeper
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.feature.add.PickedImports
import com.jdevsappstudio.kept.feature.library.FakeLibrary
import com.jdevsappstudio.kept.feature.library.FakeSettings
import com.jdevsappstudio.kept.feature.library.MainDispatcherExtension
import com.jdevsappstudio.kept.feature.library.keepActive
import com.jdevsappstudio.kept.feature.library.save
import com.jdevsappstudio.kept.feature.library.testSnackbars
import com.jdevsappstudio.kept.navigation.OpenRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

class OnboardingRulesTest {

    @Test
    fun `B1 keeps three picks and a fourth drops the oldest`() {
        var picked = listOf<SaveType>()
        listOf(SaveType.RECIPE, SaveType.PLACE, SaveType.EVENT).forEach { picked = OnboardingRules.toggleType(picked, it) }
        picked = OnboardingRules.toggleType(picked, SaveType.APP)
        assertThat(picked).containsExactly(SaveType.PLACE, SaveType.EVENT, SaveType.APP)
        assertThat(OnboardingRules.toggleType(picked, SaveType.EVENT)).containsExactly(SaveType.PLACE, SaveType.APP)
        assertThat(OnboardingRules.B1_TYPES.contains(SaveType.OTHER)).isFalse()
    }

    @Test
    fun `B2 picks stay in list order, and the app button skips what isn't installed`() {
        var apps = OnboardingRules.toggleApp(emptyList(), ShareApp.WHATSAPP)
        apps = OnboardingRules.toggleApp(apps, ShareApp.SCREENSHOTS)
        apps = OnboardingRules.toggleApp(apps, ShareApp.INSTAGRAM)
        assertThat(apps).containsExactly(ShareApp.INSTAGRAM, ShareApp.SCREENSHOTS, ShareApp.WHATSAPP)
        assertThat(OnboardingRules.appToOpen(apps) { it == "com.whatsapp" }).isEqualTo(ShareApp.WHATSAPP)
        assertThat(OnboardingRules.appToOpen(listOf(ShareApp.SCREENSHOTS, ShareApp.OTHER)) { true }).isNull()
        assertThat(OnboardingRules.shareTip(apps).title).isEqualTo("From Instagram")
        assertThat(OnboardingRules.shareTip(emptyList()).title).isEqualTo("From any app")
    }

    @Test
    fun `the read line names the side, the measured seconds and what was sent`() {
        val device = save(1, readBy = ReadBy.DEVICE).copy(createdAt = 10_000, readAt = 14_200)
        assertThat(OnboardingRules.readLine(device)).isEqualTo("Read on this phone in 4 seconds. Nothing left it.")
        val cloud = save(2, readBy = ReadBy.CLOUD).copy(createdAt = 0, readAt = 5_000, imagePath = "/x.jpg")
        assertThat(OnboardingRules.readLine(cloud)).isEqualTo("Read by Gemini in the cloud in 5 seconds.")
        // A long wait in line isn't 'reading time': the seconds are left out.
        val waited = device.copy(readAt = device.createdAt + 600_000)
        assertThat(OnboardingRules.readLine(waited)).isEqualTo("Read on this phone. Nothing left it.")
        assertThat(OnboardingRules.readLine(save(3, readBy = ReadBy.HAND))).isNull()
    }

    @Test
    fun `the first card adapts to recipes, places and several things`() {
        val recipe = save(1, type = SaveType.RECIPE).copy(items = List(9) { "i$it" })
        assertThat(OnboardingRules.cardFoot(recipe)).isEqualTo("9 ingredients found.")
        assertThat(OnboardingRules.secondaryLabel(recipe)).isEqualTo("Open the recipe")
        val place = save(2, type = SaveType.PLACE).copy(place = "Assagao, North Goa")
        assertThat(OnboardingRules.cardKind(place)).isEqualTo(FirstCardKind.PLACE)
        assertThat(OnboardingRules.cardFoot(place)).isEqualTo("Assagao, North Goa")
        val movies = save(3, type = SaveType.MOVIE_SHOW).copy(kind = "movies", items = listOf("A", "B", "C", "D", "E"))
        assertThat(OnboardingRules.cardFoot(movies)).isEqualTo("5 found, each on your Movies shelf.")
        assertThat(OnboardingRules.secondaryLabel(movies)).isEqualTo("See all 5")
        assertThat(OnboardingRules.cardKind(save(4, type = SaveType.TIP))).isEqualTo(FirstCardKind.OTHER)
    }

    @Test
    fun `a waiting first save says why, and offers tagging by hand only when nothing will read it`() {
        val queued = save(1).copy(status = SaveStatus.QUEUED, readBy = null)
        assertThat(OnboardingRules.pendingLine(queued, DeviceModelState.DOWNLOADING, QueuePause.NEEDS_CONSENT, false))
            .contains("once your phone’s AI is ready")
        assertThat(OnboardingRules.canTagByHand(queued, DeviceModelState.DOWNLOADING, QueuePause.NEEDS_CONSENT)).isFalse()
        assertThat(OnboardingRules.pendingLine(queued, DeviceModelState.UNAVAILABLE, QueuePause.NEEDS_CONSENT, false))
            .contains("Cloud help is off")
        assertThat(OnboardingRules.canTagByHand(queued, DeviceModelState.UNAVAILABLE, QueuePause.NEEDS_CONSENT)).isTrue()
        assertThat(OnboardingRules.pendingLine(queued, DeviceModelState.UNAVAILABLE, QueuePause.OFFLINE, true)).contains("back online")
        val failed = queued.copy(status = SaveStatus.NEEDS_LOOK, failReason = ReadFailure.UNREADABLE.name)
        assertThat(OnboardingRules.canTagByHand(failed, DeviceModelState.UNAVAILABLE, QueuePause.NONE)).isTrue()
    }

    @Test
    fun `the phone line on k-ob-ready follows the model and cloud help`() {
        val idle = ModelDownload()
        assertThat(OnboardingRules.phoneLine(DeviceModelState.AVAILABLE, idle, false)).isNull()
        assertThat(OnboardingRules.phoneLine(DeviceModelState.DOWNLOADING, idle, false)?.target).isEqualTo(PhoneLine.Target.MODEL)
        assertThat(OnboardingRules.phoneLine(DeviceModelState.DOWNLOADING, ModelDownload(ModelDownload.Phase.DONE), false)).isNull()
        assertThat(OnboardingRules.phoneLine(DeviceModelState.DOWNLOADABLE, ModelDownload(ModelDownload.Phase.FAILED, 1), false)?.text)
            .isEqualTo("Phone AI download stopped. Tap to try again.")
        assertThat(OnboardingRules.phoneLine(DeviceModelState.UNAVAILABLE, idle, true)?.target).isNull()
        assertThat(OnboardingRules.phoneLine(DeviceModelState.UNAVAILABLE, idle, false)?.target).isEqualTo(PhoneLine.Target.UNSUPPORTED)
    }

    @Test
    fun `k-model offers the cloud only after two failed tries`() {
        val once = OnboardingRules.modelCopy(DeviceModelState.DOWNLOADABLE, ModelDownload(ModelDownload.Phase.FAILED, 1))
        assertThat(once.failed).isTrue()
        assertThat(once.offerCloud).isFalse()
        assertThat(OnboardingRules.modelCopy(DeviceModelState.DOWNLOADABLE, ModelDownload(ModelDownload.Phase.FAILED, 2)).offerCloud).isTrue()
        assertThat(OnboardingRules.modelCopy(DeviceModelState.DOWNLOADING, ModelDownload(ModelDownload.Phase.DOWNLOADING)).status).isEqualTo("Downloading")
        assertThat(OnboardingRules.modelCopy(DeviceModelState.AVAILABLE, ModelDownload()).ready).isTrue()
    }
}

/** A reader whose model state and download are scripted. */
class FakeModelReader(var device: DeviceModelState, var steps: () -> Flow<ModelDownloadStep> = { flowOf() }) : CardReader {
    var downloads = 0
    override suspend fun deviceModelState() = device
    override fun downloadDeviceModel(): Flow<ModelDownloadStep> {
        downloads++
        return steps()
    }
    override suspend fun read(image: Bitmap?, text: String?, engine: ReadEngine, override: CloudChoice?): ReadResult =
        ReadResult.Failure(ReadFailure.NO_MODEL, null)
}

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceModelDownloadsTest {

    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    @Test
    fun `a completed download is DONE`() {
        val reader = FakeModelReader(DeviceModelState.DOWNLOADABLE) { flowOf(ModelDownloadStep.Started(100), ModelDownloadStep.Done) }
        val d = DeviceModelDownloads(reader, scope)
        d.startIfNeeded()
        assertThat(d.state.value.phase).isEqualTo(ModelDownload.Phase.DONE)
    }

    @Test
    fun `a failed download counts the try, and a retry can follow`() {
        val reader = FakeModelReader(DeviceModelState.DOWNLOADABLE) { flowOf(ModelDownloadStep.Failed("no space")) }
        val d = DeviceModelDownloads(reader, scope)
        d.start()
        d.start()
        assertThat(d.state.value).isEqualTo(ModelDownload(ModelDownload.Phase.FAILED, 2))
        assertThat(reader.downloads).isEqualTo(2)
    }

    @Test
    fun `a running download is never asked for twice`() {
        val gate = MutableStateFlow(false)
        val reader = FakeModelReader(DeviceModelState.DOWNLOADING) { flow { gate.first { it }; emit(ModelDownloadStep.Done) } }
        val d = DeviceModelDownloads(reader, scope)
        d.start()
        d.start()
        assertThat(reader.downloads).isEqualTo(1)
        assertThat(d.state.value.phase).isEqualTo(ModelDownload.Phase.DOWNLOADING)
        gate.value = true
        assertThat(d.state.value.phase).isEqualTo(ModelDownload.Phase.DONE)
    }

    @Test
    fun `no model to get means no ask, and a flow that ends with the model there is DONE`() {
        val none = FakeModelReader(DeviceModelState.UNAVAILABLE)
        DeviceModelDownloads(none, scope).also { it.startIfNeeded() }.let { assertThat(it.state.value.phase).isEqualTo(ModelDownload.Phase.IDLE) }
        assertThat(none.downloads).isEqualTo(0)

        val quiet = FakeModelReader(DeviceModelState.DOWNLOADABLE)
        quiet.steps = { quiet.device = DeviceModelState.AVAILABLE; flowOf() }
        val d = DeviceModelDownloads(quiet, scope)
        d.start()
        assertThat(d.state.value.phase).isEqualTo(ModelDownload.Phase.DONE)
    }
}

class OnboardingGateTest {

    @Test
    fun `a fresh install shows onboarding`() = runTest {
        val settings = FakeSettings()
        assertThat(OnboardingGate(settings) { false }.showOnboarding.first()).isTrue()
        assertThat(settings.onboardingDone.value).isFalse()
    }

    @Test
    fun `an existing library is marked done silently`() = runTest {
        val settings = FakeSettings()
        assertThat(OnboardingGate(settings) { true }.showOnboarding.first()).isFalse()
        assertThat(settings.onboardingDone.value).isTrue()
    }

    @Test
    fun `a replay over a full library survives a restart`() = runTest {
        val settings = FakeSettings()
        settings.setOnboardingDone(false) // Settings → Replay onboarding
        assertThat(OnboardingGate(settings) { true }.showOnboarding.first()).isTrue()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class OnboardingViewModelTest {

    private val settings = FakeSettings()
    private val library = FakeLibrary()
    private val scope = CoroutineScope(UnconfinedTestDispatcher())
    private val imports = PickedImports(Keeper(FakeSaveStore(), FakeCopier()), testSnackbars(), scope)

    private fun vm(device: DeviceModelState, reader: FakeModelReader = FakeModelReader(device), now: Long = 1_000) =
        OnboardingViewModel(
            settings, library, imports, DeviceModelDownloads(reader, scope), { reader.device },
            flowOf(QueuePause.NONE), clock = { now },
        )

    @Test
    fun `no Nano, Get started goes to the honest choice first`() = runTest {
        val vm = vm(DeviceModelState.UNAVAILABLE)
        keepActive(vm.state)
        vm.onAction(OnboardingAction.OnGetStarted)
        assertThat(vm.events.first()).isEqualTo(OnboardingEvent.ToUnsupported)
        assertThat(vm.state.value.cloudPhone).isTrue()
    }

    @Test
    fun `a downloadable model starts downloading on Get started`() = runTest {
        val reader = FakeModelReader(DeviceModelState.DOWNLOADABLE) { flowOf(ModelDownloadStep.Started(1)) }
        val vm = vm(DeviceModelState.DOWNLOADABLE, reader)
        keepActive(vm.state)
        vm.onAction(OnboardingAction.OnGetStarted)
        assertThat(vm.events.first()).isEqualTo(OnboardingEvent.ToTypes)
        assertThat(reader.downloads).isEqualTo(1)
    }

    @Test
    fun `B1 and B2 picks are saved on Next`() = runTest {
        val vm = vm(DeviceModelState.AVAILABLE)
        keepActive(vm.state)
        vm.onAction(OnboardingAction.OnToggleType(SaveType.PLACE))
        vm.onAction(OnboardingAction.OnToggleType(SaveType.RECIPE))
        vm.onAction(OnboardingAction.OnTypesNext)
        assertThat(vm.events.first()).isEqualTo(OnboardingEvent.ToApps)
        assertThat(settings.preferredTypes.value).containsExactly(SaveType.PLACE, SaveType.RECIPE)
        vm.onAction(OnboardingAction.OnToggleApp(ShareApp.YOUTUBE))
        vm.onAction(OnboardingAction.OnAppsNext)
        assertThat(vm.events.first()).isEqualTo(OnboardingEvent.ToFirst)
        assertThat(settings.shareApps.value).containsExactly(ShareApp.YOUTUBE)
    }

    @Test
    fun `Skip finishes with nothing to open, then marks onboarding done`() = runTest {
        val vm = vm(DeviceModelState.AVAILABLE)
        vm.onAction(OnboardingAction.OnSkip)
        assertThat(vm.events.first()).isEqualTo(OnboardingEvent.Finish(null))
        assertThat(settings.onboardingDone.value).isTrue()
    }

    @Test
    fun `only a save made after onboarding opened is the first save, and Edit tags opens it`() = runTest {
        library.saves.value = mapOf(1L to save(1).copy(createdAt = 500)) // older: a debug replay over a full library
        val vm = vm(DeviceModelState.AVAILABLE, now = 1_000)
        keepActive(vm.state)
        assertThat(vm.state.value.firstSave).isNull()
        library.saves.value += 2L to save(2).copy(createdAt = 2_000, status = SaveStatus.READING)
        assertThat(vm.state.value.firstSave?.id).isEqualTo(2L)
        // Followed live: reading → read.
        library.saves.value += 2L to save(2).copy(createdAt = 2_000, status = SaveStatus.READ, title = "Paneer tikka")
        assertThat(vm.state.value.firstSave?.title).isEqualTo("Paneer tikka")
        vm.onAction(OnboardingAction.OnEditTags)
        assertThat(vm.events.first()).isEqualTo(OnboardingEvent.Finish(OpenRequest(2, edit = true)))
    }

    @Test
    fun `Open in Maps launches a maps search for the place`() = runTest {
        val vm = vm(DeviceModelState.AVAILABLE, now = 0)
        keepActive(vm.state)
        library.saves.value = mapOf(5L to save(5, type = SaveType.PLACE).copy(createdAt = 10, place = "Sunbeam Café, Assagao"))
        vm.onAction(OnboardingAction.OnOpenInMaps)
        assertThat(vm.events.first()).isInstanceOf(OnboardingEvent.Launch::class)
    }

    // --- H73 bold phrases

    @Test
    fun `first card line bolds the measured seconds, else Nothing left it`() {
        assertThat(OnboardingRules.boldInLine("Read by Gemini in the cloud in 4 seconds.")).isEqualTo("in 4 seconds")
        assertThat(OnboardingRules.boldInLine("Read on this phone in 1 second. Nothing left it.")).isEqualTo("in 1 second")
        assertThat(OnboardingRules.boldInLine("Read on this phone. Nothing left it.")).isEqualTo("Nothing left it.")
        assertThat(OnboardingRules.boldInLine("Reading it now…")).isNull()
    }

    @Test
    fun `boldPart keeps the text and spans only the part`() {
        val s = boldPart(OnboardingRules.B1_BODY, OnboardingRules.B1_BODY_BOLD)
        assertThat(s.text).isEqualTo("Pick up to 3.")
        assertThat(s.spanStyles.single().let { it.start to it.end }).isEqualTo(5 to 12)
        assertThat(boldPart("Pick up to 3.", "absent").spanStyles.size).isEqualTo(0)
        assertThat(boldPart("Pick up to 3.", null).text).isEqualTo("Pick up to 3.")
    }
}
