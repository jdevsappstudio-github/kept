package com.jdevsappstudio.kept.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.core.model.NewSave
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.data.files.CopyResult
import com.jdevsappstudio.kept.data.files.SaveFiles
import com.jdevsappstudio.kept.data.queue.ReadRoute
import com.jdevsappstudio.kept.data.queue.ReadRouting
import com.jdevsappstudio.kept.data.saves.SaveLocalDataSource
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
}

/**
 * Keeps a share: copy raw bytes (image dispatcher), skip duplicates, insert QUEUED rows. The read queue sees the new rows
 * through Room and starts on its own. Lives in viewModelScope, so a rotation never cuts a copy short.
 */
class ShareViewModel(
    private val store: SaveLocalDataSource,
    private val files: SaveFiles,
    private val routing: ReadRouting,
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
            val route = async { routing.current() }
            try {
                if (payload.images.isEmpty()) keepText(payload) else keepImages(payload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = SharePhase.FAILED) }
            }
            _state.update { it.copy(route = route.await()) }
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
        val text = payload.text?.trim().orEmpty()
        if (text.isEmpty()) {
            _state.update { it.copy(phase = SharePhase.FAILED) }
            return
        }
        val url = ShareText.firstUrl(text)
        val save = NewSave(SaveSource.SHARE_TEXT, payload.sourceApp, text, url, imagePath = null)
        if (url == null) {
            val id = store.addNote(save, ShareText.noteTitle(text))
            _state.update { it.copy(phase = SharePhase.KEPT_AS_NOTE, noteReason = NoteReason.PLAIN_TEXT, saveId = id) }
            return
        }
        val dup = store.findDuplicate(contentHash = null, url = url)
        if (dup != null) {
            _state.update { it.copy(phase = SharePhase.ALREADY_KEPT, saveId = dup, duplicates = 1) }
        } else {
            val id = store.add(listOf(save)).first()
            _state.update { it.copy(phase = SharePhase.KEPT, kept = 1, saveId = id) }
        }
    }

    private suspend fun keepImages(payload: SharePayload) {
        val uris = payload.images.distinct()
        val batch = uris.take(MAX_PER_SHARE)
        _state.update { it.copy(total = batch.size, overCap = uris.size - batch.size) }
        val url = ShareText.firstUrl(payload.text)
        val fresh = mutableListOf<NewSave>()
        val seen = HashSet<String>()
        var firstDup: Long? = null
        var duplicates = 0
        var tooBig = 0
        var unreadable = 0
        batch.forEachIndexed { i, uri ->
            when (val copy = files.copyIn(uri)) {
                is CopyResult.Copied -> {
                    val dup = if (copy.sha256 in seen) -1L else store.findDuplicate(copy.sha256, url = null)
                    if (dup != null) {
                        files.delete(copy.path)
                        duplicates++
                        if (firstDup == null && dup > 0) firstDup = dup
                    } else {
                        seen += copy.sha256
                        fresh += NewSave(SaveSource.SHARE_IMAGE, payload.sourceApp, payload.text, url, copy.path, copy.sha256)
                    }
                }
                CopyResult.TooBig -> tooBig++
                CopyResult.Failed -> unreadable++
            }
            _state.update { it.copy(done = i + 1) }
        }
        val ids = if (fresh.isEmpty()) emptyList() else store.add(fresh)
        var noteId: Long? = null
        repeat(tooBig) {
            noteId = store.addNote(
                NewSave(SaveSource.SHARE_IMAGE, payload.sourceApp, payload.text, url, imagePath = null),
                title = payload.text?.let(ShareText::noteTitle)?.takeIf { it.isNotEmpty() } ?: "Image (too big to read)",
            )
        }
        _state.update {
            when {
                ids.isNotEmpty() -> it.copy(
                    phase = SharePhase.KEPT, kept = ids.size + tooBig, duplicates = duplicates,
                    unreadable = unreadable, saveId = ids.first(),
                )
                duplicates > 0 -> it.copy(phase = SharePhase.ALREADY_KEPT, duplicates = duplicates, saveId = firstDup)
                noteId != null -> it.copy(phase = SharePhase.KEPT_AS_NOTE, noteReason = NoteReason.TOO_BIG, saveId = noteId)
                else -> it.copy(phase = SharePhase.FAILED, unreadable = unreadable)
            }
        }
    }

    companion object {
        /** Same cap as the photo picker (H45). */
        const val MAX_PER_SHARE = 100
    }
}
