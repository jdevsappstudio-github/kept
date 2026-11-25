package com.jdevsappstudio.kept.feature.quota

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.ads.AdOutcome
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.testQuota
import com.jdevsappstudio.kept.feature.library.MainDispatcherExtension
import com.jdevsappstudio.kept.feature.library.keepActive
import com.jdevsappstudio.kept.feature.library.next
import com.jdevsappstudio.kept.feature.library.resId
import com.jdevsappstudio.kept.feature.library.testSnackbars
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class CloudOutViewModelTest {
    private val quota = testQuota()
    private val snackbars = testSnackbars()

    private fun TestScope.vm(meter: CloudMeter? = null) = CloudOutViewModel(
        SavedStateHandle(meter?.let { mapOf(CloudOutViewModel.ARG_METER to it.name) } ?: emptyMap()),
        quota,
        snackbars,
    ).also { keepActive(it.state) }

    @Test
    fun `state shows the meter from the argument, answers by default`() = runTest {
        repeat(5) { quota.use(CloudMeter.ANSWERS) }
        val answers = vm()
        assertThat(answers.state.value.meter).isEqualTo(CloudMeter.ANSWERS)
        assertThat(answers.state.value.left).isEqualTo(0)
        assertThat(answers.state.value.perAd).isEqualTo(3)
        val reads = vm(CloudMeter.READS)
        assertThat(reads.state.value.left).isEqualTo(100)
        assertThat(reads.state.value.perAd).isEqualTo(10)
    }

    @Test
    fun `watch ad once, double tap ignored, reward adds and closes`() = runTest {
        repeat(5) { quota.use(CloudMeter.ANSWERS) }
        val vm = vm()
        vm.events.test {
            vm.onAction(CloudOutAction.OnWatchAd)
            vm.onAction(CloudOutAction.OnWatchAd)
            assertThat(awaitItem()).isEqualTo(CloudOutEvent.ShowAd)
            assertThat(vm.state.value.showingAd).isTrue()
            vm.onAction(CloudOutAction.OnAdResult(AdOutcome.REWARDED))
            assertThat(awaitItem()).isEqualTo(CloudOutEvent.Granted(3))
            expectNoEvents()
        }
        val msg = snackbars.next().text
        assertThat(msg.resId()).isEqualTo(R.plurals.quota_answers_added)
        assertThat((msg as UiText.Plural).count).isEqualTo(3)
        assertThat(vm.state.value.left).isEqualTo(3)
        assertThat(vm.state.value.showingAd).isFalse()
    }

    @Test
    fun `reads ad adds 10`() = runTest {
        val vm = vm(CloudMeter.READS)
        vm.events.test {
            vm.onAction(CloudOutAction.OnWatchAd)
            awaitItem()
            vm.onAction(CloudOutAction.OnAdResult(AdOutcome.REWARDED))
            assertThat(awaitItem()).isEqualTo(CloudOutEvent.Granted(10))
        }
        assertThat(snackbars.next().text.resId()).isEqualTo(R.plurals.quota_reads_added)
        assertThat(quota.now(CloudMeter.READS).total).isEqualTo(110)
    }

    @Test
    fun `no ad shows a snackbar and adds nothing`() = runTest {
        val vm = vm()
        vm.onAction(CloudOutAction.OnWatchAd)
        vm.onAction(CloudOutAction.OnAdResult(AdOutcome.NO_AD))
        assertThat(snackbars.next().text.resId()).isEqualTo(R.string.quota_no_ad)
        assertThat(quota.now(CloudMeter.ANSWERS).bonus).isEqualTo(0)
        assertThat(vm.state.value.showingAd).isFalse()
    }

    @Test
    fun `closed early stays on the sheet and adds nothing`() = runTest {
        val vm = vm()
        vm.events.test {
            vm.onAction(CloudOutAction.OnWatchAd)
            awaitItem()
            vm.onAction(CloudOutAction.OnAdResult(AdOutcome.CLOSED_EARLY))
            expectNoEvents()
        }
        assertThat(quota.now(CloudMeter.ANSWERS).bonus).isEqualTo(0)
        assertThat(vm.state.value.showingAd).isFalse()
    }

    @Test
    fun `wait closes`() = runTest {
        val vm = vm()
        vm.events.test {
            vm.onAction(CloudOutAction.OnWait)
            assertThat(awaitItem()).isInstanceOf(CloudOutEvent.Close::class)
        }
    }
}
