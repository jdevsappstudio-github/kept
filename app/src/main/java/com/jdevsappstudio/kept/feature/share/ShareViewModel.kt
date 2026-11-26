package com.jdevsappstudio.kept.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.data.capture.Keeper
import com.jdevsappstudio.kept.data.capture.TextKept
import com.jdevsappstudio.kept.data.queue.ReadRoute
import com.jdevsappstudio.kept.data.queue.ReadRouting
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.CloudQuota
import com.jdevsappstudio.kept.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SharePhase { KEEPING, KEPT, ALREADY_KEPT, KEPT_AS_NOTE, FAILED }

/** Why a share became a note instead of a read (k-shared-cant). */
enum class NoteReason { PLAIN_TEXT, TOO_BIG }

data class ShareState(
    val phase: SharePhase = SharePhase.KEEPING,
    val done: Int = 0,
    val total: Int = 0,
    val kept: Int = 0,
    val duplicates: Int = 0,
    /** Over the per-share cap; the user shares them again. */
    val overCap: Int = 0,
    /** Couldn't be opened (the other app revoked or gave a broken stream). */
    val unreadable: Int = 0,
    val route: ReadRoute? = null,
    /** Cloud route but today's cloud reads are used up: the save waits for midnight (H46). */
    val readsOut: Boolean = false,
    val noteReason: NoteReason? = null,
    /** Save to open from "Open it" / "Add a title". */
    val saveId: Long? = null,
)

sealed interface ShareAction {
    data object OnOpenKept : ShareAction
    data object OnOpenSave : ShareAction
    data object OnDone : ShareAction
}

sealed interface ShareEvent {
    data class OpenApp(val saveId: Long?) : ShareEvent
    data object Close : ShareEvent
    /** First share that will be read: the activity asks for POST_NOTIFICATIONS (Android 13+, if not granted). */
    data object AskNotifications : ShareEvent
}

/**
 * Keeps a share through [Keeper] (copy, dedupe, QUEUED rows). Lives in viewModelScope, so a rotation never cuts a copy short.
 */
class ShareViewModel(
    private val keeper: Keeper,
    private val routing: ReadRouting,
    private val settings: SettingsStore,
    private val quota: CloudQuota,
) : ViewModel() {

    private val _state = MutableStateFlow(ShareState())
    val state = _state.asStateFlow()

    private val _events = Channel<ShareEvent>()
    val events = _events.receiveAsFlow()

    private var started = false

    fun start(payload: SharePayload) {
        if (started) return
        started = true
        viewModelScope.launch {
            val route = async { routing.current().let { it to (it == ReadRoute.CLOUD && quota.left(CloudMeter.READS) <= 0) } }
            try {
                if (payload.images.isEmpty()) keepText(payload) else keepImages(payload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = SharePhase.FAILED) }
            }
            val (r, out) = route.await()
            _state.update { it.copy(route = r, readsOut = out) }
            if (_state.value.phase == SharePhase.KEPT && settings.claimNotificationAsk()) _events.send(ShareEvent.AskNotifications)
        }
    }

    fun onAction(action: ShareAction) {
        val s = _state.value
        if (s.phase == SharePhase.KEEPING) return // the URI grant ends with the activity: finish the copy first
        viewModelScope.launch {
            _events.send(
                when (action) {
                    ShareAction.OnOpenKept -> ShareEvent.OpenApp(null)
                    ShareAction.OnOpenSave -> ShareEvent.OpenApp(s.saveId)
                    ShareAction.OnDone -> ShareEvent.Close
                },
            )
        }
    }

    private suspend fun keepText(payload: SharePayload) {
        val next = when (val r = keeper.keepSharedText(payload.text, payload.sourceApp)) {
            is TextKept.Queued -> ShareState(phase = SharePhase.KEPT, kept = 1, saveId = r.id)
            is TextKept.Duplicate -> ShareState(phase = SharePhase.ALREADY_KEPT, duplicates = 1, saveId = r.id)
            is TextKept.Note -> ShareState(phase = SharePhase.KEPT_AS_NOTE, noteReason = NoteReason.PLAIN_TEXT, saveId = r.id)
            TextKept.Empty, TextKept.NotALink -> ShareState(phase = SharePhase.FAILED)
        }
        _state.update { next.copy(route = it.route) }
    }

    private suspend fun keepImages(payload: SharePayload) {
        val r = keeper.keepImages(payload.images.map(Uri::toString), SaveSource.SHARE_IMAGE, payload.sourceApp, payload.text) { done, total ->
            _state.update { it.copy(done = done, total = total) }
        }
        _state.update {
            val s = it.copy(overCap = r.overCap)
            when {
                r.ids.isNotEmpty() -> s.copy(
                    phase = SharePhase.KEPT, kept = r.kept, duplicates = r.duplicates,
                    unreadable = r.unreadable, saveId = r.ids.first(),
                )
                r.duplicates > 0 -> s.copy(phase = SharePhase.ALREADY_KEPT, duplicates = r.duplicates, saveId = r.firstDuplicate)
                r.noteIds.isNotEmpty() -> s.copy(phase = SharePhase.KEPT_AS_NOTE, noteReason = NoteReason.TOO_BIG, saveId = r.noteIds.last())
                else -> s.copy(phase = SharePhase.FAILED, unreadable = r.unreadable)
            }
        }
    }

    companion object {
        /** Same cap as the photo picker (H45). */
        const val MAX_PER_SHARE = Keeper.MAX_PER_BATCH
    }
}
