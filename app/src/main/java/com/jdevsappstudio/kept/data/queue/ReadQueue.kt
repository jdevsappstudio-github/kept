package com.jdevsappstudio.kept.data.queue

import com.jdevsappstudio.kept.core.model.SentKind
import com.jdevsappstudio.kept.data.sent.SentLogLocalDataSource

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.data.ai.CardReader
import com.jdevsappstudio.kept.data.ai.ReadEngine
import com.jdevsappstudio.kept.data.ai.ReadFailure
import com.jdevsappstudio.kept.data.ai.ReadResult
import com.jdevsappstudio.kept.data.files.ImageLoader
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.CloudQuota
import com.jdevsappstudio.kept.data.saves.QueuedSave
import com.jdevsappstudio.kept.data.saves.SaveLocalDataSource
import com.jdevsappstudio.kept.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Why the queue isn't reading right now (home strip copy). */
/** Why the queue isn't reading. HELD = the person tapped Pause on Home (in memory only: a restart reads again). */
enum class QueuePause { NONE, NEEDS_CONSENT, OFFLINE, IN_BACKGROUND, CLOUD_CAP, NOT_SET_UP, HELD }

/**
 * One loop, app-scoped (BUILD-SPEC §4). Runs while there is something to read and the route allows it:
 * Nano only in the foreground (AICore blocks background apps); cloud also in the background while the process lives.
 * One read at a time. Nothing in here can crash the app: every failure becomes a typed retry or "Needs a look".
 *
 * Threads: the loop runs on [scope] (Default); Room on its executors; decode on the image dispatcher; AI calls suspend.
 */
class ReadQueue(
    private val store: SaveLocalDataSource,
    private val reader: CardReader,
    private val routing: ReadRouting,
    private val images: ImageLoader,
    private val settings: SettingsStore,
    private val quota: CloudQuota,
    private val network: NetworkStatus,
    private val background: BackgroundReads,
    private val sentLog: SentLogLocalDataSource,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _pause = MutableStateFlow(QueuePause.NONE)
    val pause: StateFlow<QueuePause> = _pause.asStateFlow()

    private val _route = MutableStateFlow<ReadRoute?>(null)
    val route: StateFlow<ReadRoute?> = _route.asStateFlow()

    private val _foreground = MutableStateFlow(true)
    /** Kept (any of its activities) is on screen. The cloud worker hides its notice while this is true. */
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

    private val _held = MutableStateFlow(false)

    /** Home's Pause / Resume on a big pick. Pausing cuts the current read short; that save goes back to the queue. */
    fun hold(on: Boolean) {
        _held.value = on
    }

    private var started = false

    /** Called once from Application.onCreate; returns at once, all work is on [scope]. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            store.requeueInterrupted()
            val foreground = withContext(Dispatchers.Main.immediate) {
                ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            }.map { it.isAtLeast(Lifecycle.State.STARTED) }.distinctUntilChanged().onEach { _foreground.value = it }
            combine(
                store.observeCounts().map { it.pending > 0 }.distinctUntilChanged(),
                foreground,
                settings.cloudHelp,
                network.online,
                // An ad's +10 or midnight turns reads back on: the paused queue restarts by itself.
                quota.observe(CloudMeter.READS).map { !it.isOut }.distinctUntilChanged(),
                ::Trigger,
            )
                .combine(_held) { t, held -> t.copy(held = held) }
                // Restart only when an input changes, not on every finished card (that would cut reads short).
                .distinctUntilChanged()
                .collectLatest { t ->
                    if (t.foreground) background.clearNotice()
                    if (!t.pending) {
                        _pause.value = QueuePause.NONE
                        _held.value = false // nothing left to hold: the next share reads straight away
                        return@collectLatest
                    }
                    if (t.held) return@collectLatest pause(QueuePause.HELD)
                    drain(t.foreground)
                }
        }
    }

    private suspend fun drain(foreground: Boolean) {
        var workerAsked = false
        while (true) {
            val route = routing.current()
            _route.value = route
            val engine = when (route) {
                ReadRoute.DEVICE -> if (foreground) ReadEngine.DEVICE else {
                    background.showWaiting(store.observeCounts().first().pending)
                    return pause(QueuePause.IN_BACKGROUND)
                }
                ReadRoute.CLOUD -> ReadEngine.CLOUD
                ReadRoute.OFFLINE -> return pause(QueuePause.OFFLINE)
                ReadRoute.NEEDS_CONSENT -> return pause(QueuePause.NEEDS_CONSENT)
            }
            if (engine == ReadEngine.CLOUD && quota.left(CloudMeter.READS) <= 0) {
                return pause(QueuePause.CLOUD_CAP) // saves wait for midnight or an ad (H32/H46/H57)
            }
            _pause.value = QueuePause.NONE
            // Cloud reads waiting: queue the worker now, not when Kept leaves the screen. ProcessLifecycleOwner reports
            // "stopped" ~700 ms late and OEM memory guards can kill the process sooner (seen on the 12R).
            if (engine == ReadEngine.CLOUD && !workerAsked) {
                background.keepCloudReading()
                workerAsked = true
            }
            val save = store.claimNext(clock())
            if (save == null) {
                val due = store.earliestDue() ?: return
                delay((due - clock()).coerceIn(250, 60_000))
                continue
            }
            when (val outcome = readOne(save, engine)) {
                Outcome.Done -> if (engine == ReadEngine.CLOUD) quota.use(CloudMeter.READS)
                is Outcome.Failed -> {
                    val decision = RetryPolicy.decide(outcome.reason, save.attempts, clock())
                    when (decision) {
                        is RetryDecision.Wait -> {
                            store.saveFailure(save.id, outcome.reason, decision.attempts, decision.at)
                            if (outcome.reason == ReadFailure.OFFLINE) return pause(QueuePause.OFFLINE)
                            if (outcome.reason == ReadFailure.QUOTA) return pause(QueuePause.CLOUD_CAP)
                            if (decision.pauseQueue) delay((decision.at - clock()).coerceIn(0, 60_000))
                        }
                        is RetryDecision.GiveUp -> {
                            store.saveFailure(save.id, outcome.reason, decision.attempts, retryAt = null)
                            if (decision.stopQueue) return pause(QueuePause.NOT_SET_UP)
                        }
                    }
                }
            }
        }
    }

    private data class Trigger(
        val pending: Boolean,
        val foreground: Boolean,
        val cloudHelp: Boolean,
        val online: Boolean,
        val readsLeft: Boolean,
        val held: Boolean = false,
    )

    private sealed interface Outcome {
        data object Done : Outcome
        data class Failed(val reason: ReadFailure) : Outcome
    }

    private suspend fun readOne(save: QueuedSave, engine: ReadEngine): Outcome {
        var finished = false
        var bitmap: Bitmap? = null
        try {
            bitmap = save.imagePath?.let { images.decodeScaled(Uri.fromFile(File(it))) }
            // Thumbnail once, from the bitmap we already have; a failed thumbnail never blocks the read.
            if (bitmap != null && save.thumbPath == null) {
                images.writeThumb(bitmap, save.id)?.let { store.saveThumb(save.id, it) }
            }
            val text = listOfNotNull(save.sharedText, save.url).distinct().joinToString("\n").ifBlank { null }
            if (bitmap == null && text == null) {
                finished = true
                return Outcome.Failed(ReadFailure.UNREADABLE)
            }
            // k-sent (H58): logged as it leaves; the save's own text + image count (the instructions hold nothing of theirs).
            if (engine == ReadEngine.CLOUD) {
                sentLog.log(SentKind.READ, text.orEmpty(), images = if (bitmap != null) 1 else 0, saveId = save.id, now = clock())
            }
            val result = reader.read(bitmap, text, engine)
            finished = true
            return when (result) {
                is ReadResult.Success -> {
                    withContext(NonCancellable) {
                        store.saveCard(save.id, result.card, result.readBy.toReadBy(), result.model, clock())
                    }
                    Outcome.Done
                }
                is ReadResult.Failure -> {
                    Log.w(TAG, "read ${save.id} failed: ${result.reason} ${result.detail.orEmpty()}")
                    Outcome.Failed(result.reason)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "read ${save.id} crashed", e)
            finished = true
            return Outcome.Failed(ReadFailure.UNKNOWN)
        } finally {
            bitmap?.recycle()
            // Cut short (left the screen, inputs changed): back to QUEUED with no try counted.
            if (!finished) withContext(NonCancellable) { store.requeue(save.id) }
        }
    }

    private fun pause(why: QueuePause) {
        _pause.value = why
    }

    private fun ReadEngine.toReadBy() = if (this == ReadEngine.DEVICE) ReadBy.DEVICE else ReadBy.CLOUD

    companion object {
        private const val TAG = "ReadQueue"
    }
}
