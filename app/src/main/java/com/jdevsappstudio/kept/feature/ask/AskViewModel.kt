package com.jdevsappstudio.kept.feature.ask

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.AskTurn
import com.jdevsappstudio.kept.core.model.AskTurnStatus
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.ask.AskOutcome
import com.jdevsappstudio.kept.data.ask.AskRepository
import com.jdevsappstudio.kept.data.ask.AskStarters
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.CloudQuota
import com.jdevsappstudio.kept.data.settings.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * k-ask + k-ask-first + k-consent over it (H29, H58). The chat lives in Room, so [turns] survive leaving the screen;
 * a question in flight keeps going on the app scope and lands in the chat either way.
 */
data class AskState(
    val loading: Boolean = true,
    val turns: List<AskTurn> = emptyList(),
    val input: String = "",
    /** A question is on its way ('Thinking…' bubble; Send and Clear chat disabled). */
    val sending: Boolean = false,
    val online: Boolean = true,
    /** Off → k-consent shows over the screen (H29). */
    val cloudHelp: Boolean = false,
    val answersLeft: Int = 0,
    val answersTotal: Int = 0,
    /** 'N of 5 left today' once 2 or fewer remain. */
    val showCounter: Boolean = false,
    /** k-ask-first: 3 starters from the person's own types and tags. */
    val starters: List<String> = emptyList(),
    /** Under 3 read saves: 'Keep a few saves first, then ask about them', no starters. */
    val fewSaves: Boolean = false,
    /** The answer whose Report sheet is open. */
    val reportingTurnId: Long? = null,
) {
    /** k-ask-first (first open, or after Clear chat). */
    val firstRun: Boolean get() = !loading && turns.isEmpty()
    val canSend: Boolean get() = input.isNotBlank() && !sending && online
}

sealed interface AskAction {
    data class OnInputChange(val text: String) : AskAction
    data object OnSend : AskAction
    data class OnStarter(val question: String) : AskAction
    data class OnRetry(val turnId: Long) : AskAction
    data object OnClearChat : AskAction
    data class OnRefClick(val saveId: Long) : AskAction
    /** The Cloud chip on an answer → k-sent at that request. */
    data class OnCloudChip(val turnId: Long) : AskAction
    data class OnReport(val turnId: Long) : AskAction
    data class OnReportSend(val reason: ReportReason, val note: String) : AskAction
    data object OnReportDismiss : AskAction
    /** 'Show' on a reported (folded) answer. */
    data class OnShowAnswer(val turnId: Long) : AskAction
    data object OnTurnOnCloudHelp : AskAction
    data object OnNotNow : AskAction
}

sealed interface AskEvent {
    data class OpenSave(val saveId: Long) : AskEvent
    data class OpenSent(val sentId: Long) : AskEvent
    /** 0 answers left: k-cloud-out. After an ad its Root sets [AskViewModel.KEY_ANSWERS_GRANTED] here and the question sends. */
    data object OpenCloudOut : AskEvent
    data class SendReport(val email: ReportEmail) : AskEvent
    /** Not now on k-consent: back where the person came from; nothing sent. */
    data object Close : AskEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class AskViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repo: AskRepository,
    private val quota: CloudQuota,
    private val settings: SettingsStore,
    private val snackbars: Snackbars,
    online: Flow<Boolean>,
    private val appVersion: String,
) : ViewModel() {

    private val input = MutableStateFlow(
        savedStateHandle.get<String>(KEY_INPUT) ?: savedStateHandle.get<String>(ARG_QUESTION).orEmpty(),
    )
    private val sendingLocal = MutableStateFlow(false)
    private val reporting = MutableStateFlow<Long?>(null)
    /** A question held back by consent or the day's answers; it goes once that's sorted. */
    private var pending: String? = savedStateHandle[KEY_PENDING]
        set(value) { field = value; savedStateHandle[KEY_PENDING] = value }

    private val starters = repo.readSaves.distinctUntilChanged().mapLatest { n ->
        n to if (n >= AskStarters.MIN_SAVES) repo.starters() else emptyList()
    }

    private data class Ui(val input: String, val sending: Boolean, val reporting: Long?)

    private val ui = combine(input, sendingLocal, reporting) { i, s, r -> Ui(i, s, r) }

    val state: StateFlow<AskState> = combine(
        repo.turns,
        ui,
        combine(settings.cloudHelp, online.onStart { emit(true) }) { c, o -> c to o },
        quota.observe(CloudMeter.ANSWERS),
        starters,
    ) { turns, u, (cloudHelp, isOnline), meter, (saves, starterList) ->
        AskState(
            loading = false,
            turns = turns,
            input = u.input,
            sending = u.sending || turns.any { it.status == AskTurnStatus.PENDING },
            online = isOnline,
            cloudHelp = cloudHelp,
            answersLeft = meter.left,
            answersTotal = meter.total,
            showCounter = meter.showCounter,
            starters = starterList,
            fewSaves = saves < AskStarters.MIN_SAVES,
            reportingTurnId = u.reporting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AskState(input = input.value))

    private val _events = Channel<AskEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch { repo.recover() }
        // Back from k-cloud-out after a watched ad: the held question goes (k-ad-rewarded → k-ask).
        viewModelScope.launch {
            savedStateHandle.getStateFlow(KEY_ANSWERS_GRANTED, false).filter { it }.collect {
                savedStateHandle[KEY_ANSWERS_GRANTED] = false
                pending?.let { send(it) }
            }
        }
    }

    fun onAction(action: AskAction) {
        when (action) {
            is AskAction.OnInputChange -> setInput(action.text)
            AskAction.OnSend -> send(input.value)
            is AskAction.OnStarter -> send(action.question)
            is AskAction.OnRetry -> retry(action.turnId)
            AskAction.OnClearChat -> if (!state.value.sending) viewModelScope.launch { repo.clear() }
            is AskAction.OnRefClick -> _events.trySend(AskEvent.OpenSave(action.saveId))
            is AskAction.OnCloudChip -> turn(action.turnId)?.sentId?.let { _events.trySend(AskEvent.OpenSent(it)) }
            is AskAction.OnReport -> reporting.value = action.turnId
            is AskAction.OnReportSend -> report(action.reason, action.note)
            AskAction.OnReportDismiss -> reporting.value = null
            is AskAction.OnShowAnswer -> viewModelScope.launch { repo.setHidden(action.turnId, false) }
            AskAction.OnTurnOnCloudHelp -> viewModelScope.launch {
                settings.setCloudHelp(true)
                pending?.let { send(it) }
            }
            AskAction.OnNotNow -> {
                pending = null
                _events.trySend(AskEvent.Close)
            }
        }
    }

    private fun setInput(text: String) {
        input.value = text
        savedStateHandle[KEY_INPUT] = text
    }

    private fun turn(id: Long) = state.value.turns.firstOrNull { it.id == id }

    private fun send(question: String) {
        val q = question.trim()
        if (q.isEmpty() || sendingLocal.value || state.value.sending) return
        sendingLocal.value = true
        viewModelScope.launch {
            try {
                if (hold(q, repo.check())) return@launch
                pending = null
                if (input.value.trim() == q) setInput("")
                // Failed → the turn's own bubble says 'Cloud didn't answer. Try again'; a late gate change → hold it.
                hold(q, repo.send(q).takeUnless { it is AskOutcome.Answered || it is AskOutcome.Failed })
            } finally {
                sendingLocal.value = false
            }
        }
    }

    private fun retry(turnId: Long) {
        if (sendingLocal.value || state.value.sending) return
        sendingLocal.value = true
        viewModelScope.launch {
            try {
                when (repo.retry(turnId)) {
                    AskOutcome.OutOfAnswers -> _events.send(AskEvent.OpenCloudOut)
                    else -> Unit // consent off shows the sheet from state; offline shows the banner
                }
            } finally {
                sendingLocal.value = false
            }
        }
    }

    /** True if [outcome] stops the question; it stays in the box and, where it can, goes later by itself. */
    private suspend fun hold(question: String, outcome: AskOutcome?): Boolean {
        when (outcome) {
            null, AskOutcome.Answered, is AskOutcome.Failed -> return false
            AskOutcome.Blank -> Unit
            AskOutcome.Offline -> Unit // banner from state; Send is disabled until back online
            AskOutcome.NeedsConsent -> pending = question // the sheet shows from state (cloudHelp = false)
            AskOutcome.OutOfAnswers -> {
                pending = question
                _events.send(AskEvent.OpenCloudOut)
            }
        }
        if (input.value.isBlank()) setInput(question)
        return true
    }

    private fun report(reason: ReportReason, note: String) {
        val t = reporting.value?.let(::turn) ?: return
        reporting.value = null
        viewModelScope.launch {
            _events.send(AskEvent.SendReport(ReportEmail.build(t.question, t.answer, reason, note, appVersion)))
            repo.setHidden(t.id, true)
            snackbars.show(UiText.Res(R.string.ask_report_thanks))
        }
    }

    companion object {
        /** Prefill from search's 'Ask about these' / k-search-tag. */
        const val ARG_QUESTION = "question"
        const val KEY_ANSWERS_GRANTED = "answersGranted"
        private const val KEY_INPUT = "input"
        private const val KEY_PENDING = "pending"
    }
}
