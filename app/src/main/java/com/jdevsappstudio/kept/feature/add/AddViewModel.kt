package com.jdevsappstudio.kept.feature.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.capture.ClipPeek
import com.jdevsappstudio.kept.data.capture.ClipboardLinks
import com.jdevsappstudio.kept.data.capture.Keeper
import com.jdevsappstudio.kept.data.capture.TextKept
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The text field Paste a link falls back to when no link is copied. */
data class PasteField(val text: String = "", val error: UiText? = null) {
    val canAdd get() = text.isNotBlank()
}

data class AddState(
    /** Drives the Paste a link row: LINK → "A link is copied". Peeked, never read (H56). */
    val clip: ClipPeek = ClipPeek.NONE,
    val field: PasteField? = null,
    /** A pasted link is being stored: taps wait. */
    val keeping: Boolean = false,
)

sealed interface AddAction {
    /** Sheet shown, or Kept came back to the front (the person may have copied a link meanwhile). */
    data object OnShown : AddAction
    data object OnPickScreenshots : AddAction
    /** Picker result as content-URI strings; empty = cancelled. */
    data class OnPicked(val refs: List<String>) : AddAction
    data object OnPasteLink : AddAction
    data class OnPasteTextChange(val text: String) : AddAction
    data object OnAddPasted : AddAction
    data object OnShareFromApp : AddAction
    /** Back or a tap outside the sheet: handled by the Root (closes). */
    data object OnDismiss : AddAction
}

sealed interface AddEvent {
    data class LaunchPicker(val max: Int) : AddEvent
    /** k-reading: a pick is being added, or a pasted link was queued. [since] = the batch start (H63). */
    data class OpenReading(val since: Long) : AddEvent
    /** Pasted a link that's already in Kept: open it (with an "Already in Kept" snackbar). */
    data class OpenSave(val id: Long) : AddEvent
    data object OpenHowTo : AddEvent
    /** Picker cancelled → back to Home (flows: SYS:picker cancelled → k-home). */
    data object Close : AddEvent
}

/** k-addmenu, the + sheet (H33, H45, H56): Pick screenshots · Paste a link · Share from an app. */
class AddViewModel(
    private val keeper: Keeper,
    private val imports: PickedImports,
    private val clipboard: ClipboardLinks,
    private val snackbars: Snackbars,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(AddState())
    val state = _state.asStateFlow()

    private val _events = Channel<AddEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: AddAction) {
        when (action) {
            AddAction.OnShown -> _state.update { it.copy(clip = clipboard.peek()) }
            AddAction.OnPickScreenshots -> send(AddEvent.LaunchPicker(Keeper.MAX_PER_BATCH))
            is AddAction.OnPicked -> if (action.refs.isEmpty()) {
                send(AddEvent.Close)
            } else {
                val since = clock()
                imports.start(action.refs)
                send(AddEvent.OpenReading(since))
            }
            AddAction.OnPasteLink -> if (_state.value.field == null) keep(clipboard.readText(), fromField = false)
            is AddAction.OnPasteTextChange -> _state.update { s -> s.copy(field = s.field?.copy(text = action.text, error = null)) }
            AddAction.OnAddPasted -> _state.value.field?.takeIf { it.canAdd }?.let { keep(it.text, fromField = true) }
            AddAction.OnShareFromApp -> send(AddEvent.OpenHowTo)
            AddAction.OnDismiss -> send(AddEvent.Close)
        }
    }

    private fun keep(text: String?, fromField: Boolean) {
        if (_state.value.keeping) return
        _state.update { it.copy(keeping = true) }
        val since = clock()
        viewModelScope.launch {
            when (val r = keeper.keepPastedLink(text)) {
                is TextKept.Queued -> {
                    _state.update { it.copy(field = null) }
                    _events.send(AddEvent.OpenReading(since))
                }
                is TextKept.Duplicate -> {
                    _state.update { it.copy(field = null) }
                    snackbars.show(UiText.Res(R.string.add_already_kept))
                    _events.send(AddEvent.OpenSave(r.id))
                }
                TextKept.NotALink, TextKept.Empty, is TextKept.Note -> _state.update { s ->
                    // From the clipboard: no link copied → show the field. From the field: say why.
                    if (fromField) s.copy(field = s.field?.copy(error = UiText.Res(R.string.add_not_a_link)))
                    else s.copy(field = PasteField())
                }
            }
            _state.update { it.copy(keeping = false) }
        }
    }

    private fun send(event: AddEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
