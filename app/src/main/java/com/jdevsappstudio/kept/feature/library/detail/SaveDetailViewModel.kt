package com.jdevsappstudio.kept.feature.library.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.data.sent.SentLogLocalDataSource
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveItem
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.presentation.SnackbarAction
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.capture.ImageAttached
import com.jdevsappstudio.kept.data.capture.Keeper
import com.jdevsappstudio.kept.data.fill.FillOutcome
import com.jdevsappstudio.kept.data.fill.FillRepository
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.CloudQuota
import com.jdevsappstudio.kept.data.settings.SettingsStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the detail screen is asking before it acts (H53). */
sealed interface DetailDialog {
    /** Delete a save with 2+ items: "5 movies go with it". */
    data class ConfirmDelete(val items: Int) : DetailDialog
    /** Read again on a save edited by hand: "Your edits will be replaced". */
    data object ConfirmReadAgain : DetailDialog
    /** Fill in with cloud while Cloud help is off: k-consent (H29). Confirm turns it on and the fill goes. */
    data object CloudConsent : DetailDialog
}

/** k-link (H59): the two ways to get more out of a link that came with only a caption. */
data class LinkFill(
    /** Fill in with cloud is offered: caption-only, not filled since its last read, not waiting to be read. */
    val canFill: Boolean,
    val filling: Boolean,
    /** 'Fill in with cloud · 2 left' when few answers are left (review-ux #23). */
    val answersLeft: Int,
    val showAnswersLeft: Boolean,
)

/** Detail of any type (k-recipe, k-place, k-product, k-movies...): the type picks the layout and the action button. */
data class SaveDetailState(
    val loading: Boolean = true,
    val save: Save? = null,
    val items: List<SaveItem> = emptyList(),
    val dialog: DetailDialog? = null,
    /** The item whose fix-or-delete sheet is open (k-movies: tap a title). */
    val editingItem: SaveItem? = null,
    /** Null when the save isn't a caption-only link (no k-link buttons). */
    val link: LinkFill? = null,
)

sealed interface SaveDetailAction {
    data object OnDeleteClick : SaveDetailAction
    /** Back from k-cloud-out after a watched ad: the held fill goes (k-ad-rewarded -> k-link). */
    data object OnAnswersGranted : SaveDetailAction
    data object OnReadAgainClick : SaveDetailAction
    data object OnConfirmDialog : SaveDetailAction
    data object OnDismissDialog : SaveDetailAction
    data class OnItemClick(val item: SaveItem) : SaveDetailAction
    data class OnRenameItem(val name: String) : SaveDetailAction
    data object OnDeleteItem : SaveDetailAction
    data object OnDismissItem : SaveDetailAction
    data class OnAddItem(val name: String) : SaveDetailAction
    data object OnEditClick : SaveDetailAction
    data object OnOpenOriginalClick : SaveDetailAction
    data object OnShareClick : SaveDetailAction
    data object OnBackClick : SaveDetailAction
    data object OnFillInClick : SaveDetailAction
    /** Cloud chip (cloud read or filled in): k-sent opened at this save's latest request. */
    data object OnCloudChipClick : SaveDetailAction
    /** k-link 'Add a screenshot': the Root opens the photo picker ([SaveDetailEvent.PickScreenshot]). */
    data object OnAddScreenshotClick : SaveDetailAction
    /** The picker's result as a content URI string; null = cancelled (the save stays as it was). */
    data class OnScreenshotPicked(val ref: String?) : SaveDetailAction
    /** k-recipe Copy ingredients: the Root put [count] lines on the clipboard; snackbar 'Copied 7 items'. */
    data class OnCopied(val count: Int) : SaveDetailAction
}

sealed interface SaveDetailEvent {
    /** Deleted here or elsewhere: back to where it was opened. */
    data object Close : SaveDetailEvent
    /** Open the photo picker for one image; send the result back as [SaveDetailAction.OnScreenshotPicked]. */
    data object PickScreenshot : SaveDetailEvent
    /** 0 answers left: k-cloud-out. After an ad the Root sends [SaveDetailAction.OnAnswersGranted] and the fill goes. */
    data object OpenCloudOut : SaveDetailEvent
    /** k-sent at [sentId] (null = the whole list, e.g. the log row was pruned). */
    data class OpenSent(val sentId: Long?) : SaveDetailEvent
}

private data class Ui(val dialog: DetailDialog? = null, val editingItem: SaveItem? = null)

class SaveDetailViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val library: LibraryLocalDataSource,
    private val snackbars: Snackbars,
    private val fill: FillRepository,
    private val keeper: Keeper,
    quota: CloudQuota,
    private val settings: SettingsStore,
    private val sentLog: SentLogLocalDataSource,
) : ViewModel() {

    private val id: Long = checkNotNull(savedStateHandle.get<Long>(ARG_ID))
    private val ui = MutableStateFlow(Ui())
    private var closing = false
    /** A fill held by k-consent or k-cloud-out; it goes by itself after Turn on / a watched ad. */
    private var pendingFill = false

    private val fillState = combine(fill.filling.map { id in it }, quota.observe(CloudMeter.ANSWERS)) { f, m -> f to m }

    val state: StateFlow<SaveDetailState> = combine(
        library.observeSave(id), library.observeItems(id), ui, fillState,
    ) { save, items, u, (filling, meter) ->
        if (save == null) close()
        val link = save?.takeIf { it.captionOnly }?.let {
            LinkFill(
                canFill = it.filledAt == null && it.status != SaveStatus.QUEUED && it.status != SaveStatus.READING,
                filling = filling,
                answersLeft = meter.left,
                showAnswersLeft = meter.showCounter,
            )
        }
        SaveDetailState(loading = false, save = save, items = items, dialog = u.dialog, editingItem = u.editingItem, link = link)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SaveDetailState())

    private val _events = Channel<SaveDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
    }

    fun onAction(action: SaveDetailAction) {
        when (action) {
            SaveDetailAction.OnAnswersGranted -> if (pendingFill) startFill()
            SaveDetailAction.OnDeleteClick -> {
                val items = state.value.items.size
                if (items >= 2) ui.update { it.copy(dialog = DetailDialog.ConfirmDelete(items)) } else delete()
            }
            SaveDetailAction.OnReadAgainClick ->
                if (state.value.save?.readBy == ReadBy.HAND) ui.update { it.copy(dialog = DetailDialog.ConfirmReadAgain) }
                else readAgain()
            SaveDetailAction.OnConfirmDialog -> {
                val dialog = ui.value.dialog
                ui.update { it.copy(dialog = null) }
                when (dialog) {
                    is DetailDialog.ConfirmDelete -> delete()
                    DetailDialog.ConfirmReadAgain -> readAgain()
                    DetailDialog.CloudConsent -> viewModelScope.launch {
                        settings.setCloudHelp(true)
                        if (pendingFill) startFill()
                    }
                    null -> Unit
                }
            }
            SaveDetailAction.OnDismissDialog -> {
                if (ui.value.dialog == DetailDialog.CloudConsent) pendingFill = false // Not now: nothing is sent
                ui.update { it.copy(dialog = null) }
            }
            SaveDetailAction.OnFillInClick -> startFill()
            SaveDetailAction.OnCloudChipClick -> viewModelScope.launch {
                val sentId = sentLog.observe().first().firstOrNull { it.saveId == id }?.id // newest first
                _events.send(SaveDetailEvent.OpenSent(sentId))
            }
            SaveDetailAction.OnAddScreenshotClick ->
                if (state.value.link != null) _events.trySend(SaveDetailEvent.PickScreenshot)
            is SaveDetailAction.OnScreenshotPicked -> action.ref?.let(::attach)
            is SaveDetailAction.OnItemClick -> ui.update { it.copy(editingItem = action.item) }
            SaveDetailAction.OnDismissItem -> ui.update { it.copy(editingItem = null) }
            is SaveDetailAction.OnRenameItem -> {
                val item = ui.value.editingItem ?: return
                ui.update { it.copy(editingItem = null) }
                val name = action.name.trim()
                if (name.isNotEmpty() && name != item.name) viewModelScope.launch { library.renameItem(item.id, name) }
            }
            SaveDetailAction.OnDeleteItem -> {
                val item = ui.value.editingItem ?: return
                ui.update { it.copy(editingItem = null) }
                viewModelScope.launch { library.deleteItem(item.id) }
            }
            is SaveDetailAction.OnAddItem -> addItem(action.name.trim())
            is SaveDetailAction.OnCopied -> snackbars.show(UiText.Plural(R.plurals.snack_copied_items, action.count, action.count))
            SaveDetailAction.OnEditClick, SaveDetailAction.OnOpenOriginalClick,
            SaveDetailAction.OnShareClick, SaveDetailAction.OnBackClick -> Unit
        }
    }

    private fun delete() {
        viewModelScope.launch {
            library.delete(listOf(id))
            snackbars.show(
                UiText.Plural(R.plurals.snack_deleted, 1, 1),
                SnackbarAction(UiText.Res(R.string.snack_undo)) { library.undoDelete(listOf(id)) },
            )
            close()
        }
    }

    private fun readAgain() {
        viewModelScope.launch {
            val queued = library.readAgain(id)
            snackbars.show(UiText.Res(if (queued) R.string.snack_read_again else R.string.snack_read_busy))
        }
    }

    private fun startFill() {
        val link = state.value.link
        if (link != null && (link.filling || !link.canFill)) return
        viewModelScope.launch {
            pendingFill = true
            val outcome = fill.check() ?: fill.fill(id)
            pendingFill = outcome == FillOutcome.NeedsConsent || outcome == FillOutcome.OutOfAnswers
            when (outcome) {
                FillOutcome.Filled -> snackbars.show(UiText.Res(R.string.snack_fill_done))
                FillOutcome.PageUnreachable -> snackbars.show(
                    UiText.Res(R.string.snack_fill_unreachable),
                    SnackbarAction(UiText.Res(R.string.snack_add_screenshot)) { _events.trySend(SaveDetailEvent.PickScreenshot) },
                )
                is FillOutcome.Failed -> snackbars.show(
                    UiText.Res(R.string.snack_cloud_no_answer),
                    SnackbarAction(UiText.Res(R.string.snack_try_again)) { startFill() },
                )
                FillOutcome.NeedsConsent -> ui.update { it.copy(dialog = DetailDialog.CloudConsent) }
                FillOutcome.Offline -> snackbars.show(UiText.Res(R.string.snack_fill_offline))
                FillOutcome.OutOfAnswers -> _events.send(SaveDetailEvent.OpenCloudOut)
                FillOutcome.NotAllowed -> Unit // already filled, has an image now, or a fill is on its way
            }
        }
    }

    private fun attach(ref: String) {
        viewModelScope.launch {
            val text = when (keeper.attachImage(id, ref)) {
                ImageAttached.QUEUED -> R.string.snack_shot_reading
                ImageAttached.TOO_BIG -> R.string.snack_shot_too_big
                ImageAttached.UNREADABLE -> R.string.snack_shot_failed
                ImageAttached.NOT_ALLOWED -> return@launch
            }
            snackbars.show(UiText.Res(text))
        }
    }

    private fun addItem(name: String) {
        if (name.isEmpty()) return
        viewModelScope.launch {
            val result = library.addItem(id, name)
            when {
                result.alreadyOnShelf != null ->
                    snackbars.show(UiText.Res(R.string.snack_already_on_shelf, result.alreadyOnShelf))
                !result.added -> snackbars.show(UiText.Res(R.string.snack_already_here))
            }
        }
    }

    /** Once only: our own delete and the save vanishing from Room would otherwise both close the screen. */
    private fun close() {
        if (closing) return
        closing = true
        _events.trySend(SaveDetailEvent.Close)
    }

    companion object {
        const val ARG_ID = "id"
    }
}
