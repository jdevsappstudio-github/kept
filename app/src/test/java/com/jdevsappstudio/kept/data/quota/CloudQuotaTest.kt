package com.jdevsappstudio.kept.data.quota

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CloudQuotaTest {
    private val store = FakeQuotaStore()
    private val day = FakeQuotaDay()
    private val quota = testQuota(store, day)

    @Test
    fun `a fresh day has the spec numbers`() = runTest {
        val a = quota.now(CloudMeter.ANSWERS)
        assertThat(a.left).isEqualTo(5)
        assertThat(a.perAd).isEqualTo(3)
        val r = quota.now(CloudMeter.READS)
        assertThat(r.left).isEqualTo(100)
        assertThat(r.perAd).isEqualTo(10)
    }

    @Test
    fun `use counts down to out and never below 0`() = runTest {
        repeat(7) { quota.use(CloudMeter.ANSWERS) }
        val a = quota.now(CloudMeter.ANSWERS)
        assertThat(a.left).isEqualTo(0)
        assertThat(a.isOut).isTrue()
        assertThat(quota.left(CloudMeter.READS)).isEqualTo(100) // meters are separate
    }

    @Test
    fun `every ad adds its bonus, no cap (H57)`() = runTest {
        repeat(5) { quota.use(CloudMeter.ANSWERS) }
        repeat(4) { assertThat(quota.grantAd(CloudMeter.ANSWERS)).isEqualTo(3) }
        val a = quota.now(CloudMeter.ANSWERS)
        assertThat(a.total).isEqualTo(17)
        assertThat(a.left).isEqualTo(12)
        assertThat(quota.grantAd(CloudMeter.READS)).isEqualTo(10)
        assertThat(quota.now(CloudMeter.READS).total).isEqualTo(110)
    }

    @Test
    fun `a new local day starts used and bonus again`() = runTest {
        repeat(5) { quota.use(CloudMeter.ANSWERS) }
        quota.grantAd(CloudMeter.ANSWERS)
        day.day.value = 20261126
        val a = quota.now(CloudMeter.ANSWERS)
        assertThat(a.used).isEqualTo(0)
        assertThat(a.bonus).isEqualTo(0)
        assertThat(a.left).isEqualTo(5)
        quota.use(CloudMeter.ANSWERS)
        assertThat(store.get(CloudMeter.ANSWERS)).isEqualTo(QuotaRecord(20261126, 1, 0))
    }

    @Test
    fun `observe re-emits at midnight so a paused queue wakes up`() = runTest {
        repeat(100) { quota.use(CloudMeter.READS) }
        quota.observe(CloudMeter.READS).test {
            assertThat(awaitItem().isOut).isTrue()
            day.day.value = 20261126
            assertThat(awaitItem().left).isEqualTo(100)
        }
    }

    @Test
    fun `observe sees an ad bonus`() = runTest {
        repeat(100) { quota.use(CloudMeter.READS) }
        quota.observe(CloudMeter.READS).test {
            assertThat(awaitItem().left).isEqualTo(0)
            quota.grantAd(CloudMeter.READS)
            assertThat(awaitItem().left).isEqualTo(10)
        }
    }

    @Test
    fun `counter shows at 2 answers and 5 reads left`() = runTest {
        repeat(2) { quota.use(CloudMeter.ANSWERS) }
        assertThat(quota.now(CloudMeter.ANSWERS).showCounter).isFalse() // 3 left
        quota.use(CloudMeter.ANSWERS)
        assertThat(quota.now(CloudMeter.ANSWERS).showCounter).isTrue() // 2 left
        repeat(94) { quota.use(CloudMeter.READS) }
        assertThat(quota.now(CloudMeter.READS).showCounter).isFalse() // 6 left
        quota.use(CloudMeter.READS)
        assertThat(quota.now(CloudMeter.READS).showCounter).isTrue() // 5 left
    }

    @Test
    fun `remote limits fall back to code defaults when unpublished or bad`() = runTest {
        assertThat(RemoteQuotaLimits(FakeRemoteValues()).get()).isEqualTo(QuotaLimits())
        val bad = FakeRemoteValues(
            mapOf(
                RemoteQuotaLimits.KEY_READS_PER_DAY to "abc",
                RemoteQuotaLimits.KEY_ANSWERS_PER_DAY to "-1",
                RemoteQuotaLimits.KEY_ANSWERS_PER_AD to "0",
                RemoteQuotaLimits.KEY_READS_PER_AD to "999999",
            ),
        )
        assertThat(RemoteQuotaLimits(bad).get()).isEqualTo(QuotaLimits())
    }

    @Test
    fun `remote limits apply when published, 0 a day is a kill switch`() = runTest {
        val published = FakeRemoteValues(
            mapOf(
                RemoteQuotaLimits.KEY_READS_PER_DAY to "30",
                RemoteQuotaLimits.KEY_ANSWERS_PER_DAY to "0",
                RemoteQuotaLimits.KEY_ANSWERS_PER_AD to "2",
                RemoteQuotaLimits.KEY_READS_PER_AD to " 15 ",
            ),
        )
        assertThat(RemoteQuotaLimits(published).get()).isEqualTo(QuotaLimits(0, 30, 2, 15))
    }
}
