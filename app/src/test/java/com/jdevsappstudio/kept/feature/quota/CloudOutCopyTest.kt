package com.jdevsappstudio.kept.feature.quota

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.jdevsappstudio.kept.data.quota.CloudMeter
import org.junit.jupiter.api.Test

class CloudOutCopyTest {
    @Test
    fun `answers and reads never mix`() {
        assertThat(CloudOutCopy.title(CloudMeter.ANSWERS, 5)).isEqualTo("That's today's 5 cloud answers")
        assertThat(CloudOutCopy.watchAd(CloudMeter.ANSWERS, 3)).isEqualTo("Watch a short ad: +3 answers")
        assertThat(CloudOutCopy.title(CloudMeter.READS, 100)).isEqualTo("That's today's 100 cloud reads")
        assertThat(CloudOutCopy.watchAd(CloudMeter.READS, 10)).isEqualTo("Watch a short ad: +10 reads")
        assertThat(CloudOutCopy.watchAd(CloudMeter.READS, 10)).doesNotContain("answer")
        assertThat(CloudOutCopy.body(CloudMeter.ANSWERS)).contains("counted on its own")
    }

    @Test
    fun `an ad bonus counts in the total`() {
        assertThat(CloudOutCopy.title(CloudMeter.ANSWERS, 8)).isEqualTo("That's today's 8 cloud answers")
        assertThat(CloudOutCopy.watchAd(CloudMeter.ANSWERS, 1)).isEqualTo("Watch a short ad: +1 answer")
    }
}
