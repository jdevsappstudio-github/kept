package com.jdevsappstudio.kept.data.queue

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.data.saves.SaveLocalDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach

/**
 * Keeps cloud reads going after Kept leaves the screen (H49: plain worker; a foreground service comes later).
 *
 * It does not read anything itself: [ReadQueue] stays the only reader, so there are never two loops claiming saves.
 * The worker keeps the process alive (and brings it back after Android kills it: Application.onCreate starts the queue),
 * shows "Reading 3 of 12", and ends when the queue is empty or can't go on. WorkManager gives it up to 10 minutes;
 * if it is stopped, WorkManager runs it again later.
 */
class CloudReadWorker(
    context: Context,
    params: WorkerParameters,
    private val queue: ReadQueue,
    private val store: SaveLocalDataSource,
    private val notices: ReadNotifications,
) : CoroutineWorker(context, params) {

    @OptIn(FlowPreview::class)
    override suspend fun doWork(): Result {
        val start = store.observeCounts().first()
        var shown = -1
        val end = try {
            combine(store.observeCounts(), queue.pause, queue.route, queue.foreground, ::Snapshot)
                .onEach { s ->
                    val done = s.finished - (start.read + start.needsLook)
                    if (!s.foreground && s.counts.pending > 0 && done != shown) {
                        notices.progress(done, done + s.counts.pending)
                        shown = done
                    }
                }
                // A pause can be stale for a moment (e.g. OFFLINE just after the network came back): wait for it to hold.
                .debounce(SETTLE_MS)
                .first { it.settled }
        } catch (e: CancellationException) {
            notices.cancel() // stopped by the system; the next run posts fresh numbers
            throw e
        }
        val read = end.counts.read - start.read
        if (!end.foreground && read > 0) notices.finished(read) else notices.cancel()
        // OFFLINE: the network constraint brings it back. Everything else waits for the app (consent, tomorrow's cap, Nano).
        return if (end.pause == QueuePause.OFFLINE) Result.retry() else Result.success()
    }

    private data class Snapshot(val counts: QueueCounts, val pause: QueuePause, val route: ReadRoute?, val foreground: Boolean) {
        val finished get() = counts.read + counts.needsLook
        val settled get() = counts.pending == 0 || pause != QueuePause.NONE || route == ReadRoute.DEVICE
    }

    companion object {
        const val NAME = "cloud-read"
        private const val SETTLE_MS = 2_000L
    }
}
