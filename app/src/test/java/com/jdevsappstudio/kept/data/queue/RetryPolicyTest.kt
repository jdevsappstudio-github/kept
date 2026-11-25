package com.jdevsappstudio.kept.data.queue

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jdevsappstudio.kept.data.ai.ReadFailure
import org.junit.jupiter.api.Test

class RetryPolicyTest {
    private val now = 1_000_000L

    @Test fun `busy backs off then gives up after 3 tries`() {
        assertThat(RetryPolicy.decide(ReadFailure.BUSY, 0, now)).isEqualTo(RetryDecision.Wait(now + 2_000, 1, true))
        assertThat(RetryPolicy.decide(ReadFailure.BUSY, 1, now)).isEqualTo(RetryDecision.Wait(now + 4_000, 2, true))
        assertThat(RetryPolicy.decide(ReadFailure.BUSY, 2, now)).isEqualTo(RetryDecision.GiveUp(3, false))
    }

    @Test fun `offline and quota never burn a try`() {
        assertThat(RetryPolicy.decide(ReadFailure.OFFLINE, 2, now)).isEqualTo(RetryDecision.Wait(now, 2, true))
        assertThat(RetryPolicy.decide(ReadFailure.QUOTA, 2, now)).isEqualTo(RetryDecision.Wait(now + 3_600_000, 2, true))
    }

    @Test fun `unreadable gets one more try`() {
        assertThat(RetryPolicy.decide(ReadFailure.UNREADABLE, 0, now)).isEqualTo(RetryDecision.Wait(now, 1, false))
        assertThat(RetryPolicy.decide(ReadFailure.UNREADABLE, 1, now)).isEqualTo(RetryDecision.GiveUp(2, false))
    }

    @Test fun `set-up errors stop the queue, a blocked save does not`() {
        assertThat(RetryPolicy.decide(ReadFailure.NOT_SET_UP, 0, now)).isEqualTo(RetryDecision.GiveUp(1, true))
        assertThat(RetryPolicy.decide(ReadFailure.BLOCKED, 0, now)).isEqualTo(RetryDecision.GiveUp(1, false))
    }

    @Test fun `backoff caps at a minute`() = assertThat(RetryPolicy.backoff(9)).isEqualTo(60_000L)
}
