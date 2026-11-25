package com.jdevsappstudio.kept.feature.home

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.data.queue.ReadRoute
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.MeterView
import org.junit.jupiter.api.Test

class HomeLinesTest {

    @Test
    fun `batch counts from when the queue got busy, not the whole library`() {
        val start = HomeLines.batchStart(null, QueueCounts(queued = 3, reading = 1, needsLook = 0, read = 100))
        assertThat(start).isEqualTo(100)
        // Two reads later the start stays put.
        val later = QueueCounts(queued = 1, reading = 1, needsLook = 0, read = 102)
        assertThat(HomeLines.batchStart(start, later)).isEqualTo(100)
        val lines = HomeLines.lines(later, 100, ReadRoute.CLOUD, QueuePause.NONE, cloudHelp = true, reads = null)
        assertThat(lines).containsExactly(HomeLine.Reading(current = 3, total = 4, with = ReadsWith.CLOUD))
    }

    @Test
    fun `an empty queue ends the batch`() {
        assertThat(HomeLines.batchStart(100, QueueCounts(0, 0, 0, 104))).isNull()
    }

    @Test
    fun `a save parked for a retry waits instead of reading`() {
        val counts = QueueCounts(queued = 1, reading = 0, needsLook = 0, read = 100)
        val lines = HomeLines.lines(counts, 100, ReadRoute.CLOUD, QueuePause.NONE, cloudHelp = true, reads = null)
        assertThat(lines).containsExactly(HomeLine.Waiting(1, QueuePause.NONE))
    }

    @Test
    fun `paused queue, need a look and a low cloud meter all show, in order`() {
        val counts = QueueCounts(queued = 2, reading = 0, needsLook = 1, read = 10)
        val meter = MeterView(CloudMeter.READS, used = 97, bonus = 0, limit = 100, perAd = 10)
        val lines = HomeLines.lines(counts, 11, ReadRoute.OFFLINE, QueuePause.OFFLINE, cloudHelp = true, reads = meter)
        assertThat(lines).containsExactly(
            HomeLine.Waiting(2, QueuePause.OFFLINE),
            HomeLine.NeedLook(1),
            HomeLine.ReadsLeft(3, 100),
        )
    }

    @Test
    fun `the cloud meter never shows on a phone that reads on device`() {
        val meter = MeterView(CloudMeter.READS, used = 99, bonus = 0, limit = 100, perAd = 10)
        val lines = HomeLines.lines(QueueCounts.EMPTY, null, ReadRoute.DEVICE, QueuePause.NONE, cloudHelp = true, reads = meter)
        assertThat(lines).containsExactly()
    }

    @Test
    fun `reads-with follows the last route, else cloud help`() {
        assertThat(HomeLines.readsWith(ReadRoute.DEVICE, cloudHelp = true)).isEqualTo(ReadsWith.DEVICE)
        assertThat(HomeLines.readsWith(null, cloudHelp = true)).isEqualTo(ReadsWith.CLOUD)
        assertThat(HomeLines.readsWith(null, cloudHelp = false)).isEqualTo(ReadsWith.UNKNOWN)
    }
}
