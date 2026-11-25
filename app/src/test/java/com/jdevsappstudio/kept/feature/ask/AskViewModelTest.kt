package com.jdevsappstudio.kept.feature.ask

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.AskTurnStatus
import com.jdevsappstudio.kept.data.ai.AskResult
import com.jdevsappstudio.kept.data.ai.ReadFailure
import com.jdevsappstudio.kept.data.ask.CloudAskRepository
import com.jdevsappstudio.kept.data.ask.FakeAskStore
import com.jdevsappstudio.kept.data.ask.FakeAsker
import com.jdevsappstudio.kept.data.ask.FakeSentLog
import com.jdevsappstudio.kept.data.ask.SourceSave
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.testQuota
import com.jdevsappstudio.kept.feature.library.FakeSettings
import com.jdevsappstudio.kept.feature.library.MainDispatcherExtension
import com.jdevsappstudio.kept.feature.library.keepActive
import com.jdevsappstudio.kept.feature.library.next
import com.jdevsappstudio.kept.feature.library.resId
import com.jdevsappstudio.kept.feature.library.testSnackbars
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class AskViewModelTest {
    private val store = FakeAskStore(
        listOf(
            SourceSave(1, 100, "Gone Girl", "movie_show", tags = listOf("thriller"), body = "Gone Girl thriller"),
            SourceSave(2, 200, "Prisoners", "movie_show", tags = listOf("thriller"), body = "Prisoners thriller"),
            SourceSave(3, 300, "Paneer tikka", "recipe", body = "Paneer tikka recipe"),
        ),
    ).apply { readCount.value = 3 }
    private val sent = FakeSentLog()
    private val quota = testQuota()
    private val settings = FakeSettings().apply { cloudHelp.value = true }
    private val snackbars = testSnackbars()
    private val online = MutableStateFlow(true)
    private lateinit var handle: SavedStateHandle

    private fun TestScope.vm(asker: FakeAsker = FakeAsker(), args: Map<String, Any?> = emptyMap()): AskViewModel {
        handle = SavedStateHandle(args)
        // The app scope, eager on the test scheduler: an answer lands before onAction returns.
        val appScope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val repo = CloudAskRepository(store, asker, quota, sent, settings, { online.value }, appScope)
        return AskViewModel(handle, repo, quota, settings, snackbars, online, "0.1-test").also { keepActive(it.state) }
    }

    @Test
    fun `first run shows starters from the person's saves`() = runTest {
        val vm = vm()
        val s = vm.state.value
        assertThat(s.firstRun).isTrue()
        assertThat(s.fewSaves).isFalse()
        assertThat(s.starters.first()).isEqualTo("What should I watch tonight?")
        assertThat(s.answersLeft).isEqualTo(5)
    }

    @Test
    fun `under 3 read saves there are no starters`() = runTest {
        store.readCount.value = 2
        val s = vm().state.value
        assertThat(s.fewSaves).isTrue()
        assertThat(s.starters).isEmpty()
    }

    @Test
    fun `send clears the box and the answer lands in the chat`() = runTest {
        val vm = vm(FakeAsker(FakeAsker.ok("Start with Prisoners.", 1)))
        vm.onAction(AskAction.OnInputChange("Which thriller first?"))
        vm.onAction(AskAction.OnSend)
        val s = vm.state.value
        assertThat(s.input).isEqualTo("")
        assertThat(s.sending).isFalse()
        val turn = s.turns.single()
        assertThat(turn.answer).isEqualTo("Start with Prisoners.")
        assertThat(turn.refs.map { it.saveId }).containsExactly(2L)
        assertThat(s.answersLeft).isEqualTo(4)
        assertThat(s.firstRun).isFalse()
    }

    @Test
    fun `cloud help off holds the question, Turn on sends it, Not now closes and drops it`() = runTest {
        settings.cloudHelp.value = false
        val vm = vm(FakeAsker(FakeAsker.ok("Yes.")))
        assertThat(vm.state.value.cloudHelp).isFalse() // k-consent shows over Ask
        vm.onAction(AskAction.OnStarter("What should I watch tonight?"))
        assertThat(vm.state.value.turns).isEmpty()
        assertThat(vm.state.value.input).isEqualTo("What should I watch tonight?")
        vm.onAction(AskAction.OnTurnOnCloudHelp)
        assertThat(settings.cloudHelp.value).isTrue()
        assertThat(vm.state.value.turns.single().status).isEqualTo(AskTurnStatus.DONE)
        assertThat(vm.state.value.input).isEqualTo("")
    }

    @Test
    fun `Not now closes and sends nothing`() = runTest {
        settings.cloudHelp.value = false
        val vm = vm()
        vm.events.test {
            vm.onAction(AskAction.OnStarter("Anything?"))
            vm.onAction(AskAction.OnNotNow)
            assertThat(awaitItem()).isEqualTo(AskEvent.Close)
        }
        assertThat(sent.rows.value).isEmpty()
    }

    @Test
    fun `0 answers left opens k-cloud-out, after the ad the question sends by itself`() = runTest {
        repeat(5) { quota.use(CloudMeter.ANSWERS) }
        val vm = vm(FakeAsker(FakeAsker.ok("Prisoners.")))
        vm.events.test {
            vm.onAction(AskAction.OnInputChange("thriller?"))
            vm.onAction(AskAction.OnSend)
            assertThat(awaitItem()).isEqualTo(AskEvent.OpenCloudOut)
        }
        assertThat(vm.state.value.input).isEqualTo("thriller?")
        assertThat(vm.state.value.turns).isEmpty()
        // k-cloud-out's Root after a watched ad:
        quota.grantAd(CloudMeter.ANSWERS)
        handle[AskViewModel.KEY_ANSWERS_GRANTED] = true
        assertThat(vm.state.value.turns.single().answer).isEqualTo("Prisoners.")
        assertThat(vm.state.value.input).isEqualTo("")
        assertThat(vm.state.value.answersLeft).isEqualTo(2) // 3 from the ad, 1 used
    }

    @Test
    fun `offline keeps the question in the box and Send disabled`() = runTest {
        online.value = false
        val vm = vm()
        vm.onAction(AskAction.OnInputChange("thriller"))
        assertThat(vm.state.value.canSend).isFalse()
        vm.onAction(AskAction.OnSend)
        assertThat(vm.state.value.input).isEqualTo("thriller")
        assertThat(vm.state.value.turns).isEmpty()
    }

    @Test
    fun `a failed answer shows on its turn, Try again answers it`() = runTest {
        val vm = vm(FakeAsker(AskResult.Failure(ReadFailure.BUSY), FakeAsker.ok("Gone Girl.")))
        vm.onAction(AskAction.OnStarter("thriller"))
        val failed = vm.state.value.turns.single()
        assertThat(failed.status).isEqualTo(AskTurnStatus.FAILED)
        assertThat(vm.state.value.answersLeft).isEqualTo(5)
        vm.onAction(AskAction.OnRetry(failed.id))
        assertThat(vm.state.value.turns.single().status).isEqualTo(AskTurnStatus.DONE)
    }

    @Test
    fun `Cloud chip opens k-sent at the request, refs open the save`() = runTest {
        val vm = vm(FakeAsker(FakeAsker.ok("Prisoners.", 1)))
        vm.onAction(AskAction.OnStarter("thriller"))
        val turn = vm.state.value.turns.single()
        vm.events.test {
            vm.onAction(AskAction.OnCloudChip(turn.id))
            assertThat(awaitItem()).isEqualTo(AskEvent.OpenSent(sent.rows.value.single().id))
            vm.onAction(AskAction.OnRefClick(2))
            assertThat(awaitItem()).isEqualTo(AskEvent.OpenSave(2))
        }
    }

    @Test
    fun `report builds an email with question, answer and reason only, hides the answer, says thanks`() = runTest {
        val vm = vm(FakeAsker(FakeAsker.ok("Prisoners.", 1)))
        vm.onAction(AskAction.OnStarter("thriller"))
        val turn = vm.state.value.turns.single()
        vm.onAction(AskAction.OnReport(turn.id))
        assertThat(vm.state.value.reportingTurnId).isEqualTo(turn.id)
        vm.events.test {
            vm.onAction(AskAction.OnReportSend(ReportReason.WRONG, "not a thriller"))
            val email = (awaitItem() as AskEvent.SendReport).email
            assertThat(email.subject).contains("Wrong")
            assertThat(email.body).contains("Question:\nthriller")
            assertThat(email.body).contains("Prisoners.")
            assertThat(email.body).contains("Note: not a thriller")
            assertThat(email.body).doesNotContain("Gone Girl") // no saves
        }
        assertThat(vm.state.value.reportingTurnId).isEqualTo(null)
        assertThat(vm.state.value.turns.single().hidden).isTrue()
        assertThat(snackbars.next().text.resId()).isEqualTo(R.string.ask_report_thanks)
        vm.onAction(AskAction.OnShowAnswer(turn.id))
        assertThat(vm.state.value.turns.single().hidden).isFalse()
    }

    @Test
    fun `search prefill lands in the box, Clear chat goes back to first run`() = runTest {
        val vm = vm(FakeAsker(FakeAsker.ok("Ok.")), mapOf(AskViewModel.ARG_QUESTION to "goa cafes"))
        assertThat(vm.state.value.input).isEqualTo("goa cafes")
        vm.onAction(AskAction.OnSend)
        assertThat(vm.state.value.turns.size).isEqualTo(1)
        vm.onAction(AskAction.OnClearChat)
        assertThat(vm.state.value.firstRun).isTrue()
    }

    @Test
    fun `typing survives process death`() = runTest {
        vm().onAction(AskAction.OnInputChange("half a quest"))
        val restored = vm(args = mapOf("input" to handle.get<String>("input")))
        assertThat(restored.state.value.input).isEqualTo("half a quest")
        assertThat(restored).isInstanceOf(AskViewModel::class)
    }
}
