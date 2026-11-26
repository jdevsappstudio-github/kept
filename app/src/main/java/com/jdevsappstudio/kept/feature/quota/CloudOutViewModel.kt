package com.jdevsappstudio.kept.feature.quota

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.ads.AdOutcome
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.CloudQuota
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * k-cloud-out (answers: from Ask / Fill in) and the 'Watch a short ad: +10 reads' row on k-failed (reads).
 * One sheet, one rule (H57): every watched ad adds [CloudOutState.perAd], no cap on ads.
 */
data class CloudOutState(
    val meter: CloudMeter = CloudMeter.ANSWERS,
    val loading: Boolean = true,
    val left: Int = 0,
    val total: Int = 0,
    /** '+3 answers' / '+10 reads' on the button. */
    val perAd: Int = 0,
    /** An ad is on screen or loading: the button is disabled so a double tap can't start two. */
    val showingAd: Boolean = false,
    /** 'No ad right now': the button stays greyed for [CloudOutViewModel.NO_AD_WAIT_MS] (k-ad-rewarded). */
    val noAdWait: Boolean = false,
    /** False in the MVP (H66): no ad button, 'Wait till midnight' is the only way. */
    val adOffered: Boolean = false,
) {
    val canWatchAd: Boolean get() = adOffered && !loading && !showingAd && !noAdWait
}

sealed interface CloudOutAction {
    data object OnWatchAd : CloudOutAction
    /** The Root showed the ad (it holds the Activity) and reports how it ended. */
    data class OnAdResult(val outcome: AdOutcome) : CloudOutAction
    data object OnWait : CloudOutAction
}

sealed interface CloudOutEvent {
    /** Root: show a rewarded ad, then send [CloudOutAction.OnAdResult]. */
    data object ShowAd : CloudOutEvent
    /** Bonus added: close; the caller sends the question again / the queue restarts by itself. */
    data class Granted(val added: Int) : CloudOutEvent
    /** Wait till midnight. */
    data object Close : CloudOutEvent
}

class CloudOutViewModel(
    savedStateHandle: SavedStateHandle,
    private val quota: CloudQuota,
    private val snackbars: Snackbars,
    private val adsOn: Boolean,
) : ViewModel() {

    private val meter: CloudMeter =
        savedStateHandle.get<String>(ARG_METER)?.let { n -> CloudMeter.entries.firstOrNull { it.name == n } } ?: CloudMeter.ANSWERS
    private val showingAd = MutableStateFlow(false)
    private val noAdWait = MutableStateFlow(false)
    private var noAdJob: Job? = null

    val state: StateFlow<CloudOutState> = combine(quota.observe(meter), showingAd, noAdWait) { m, ad, wait ->
        CloudOutState(
            meter = meter, loading = false, left = m.left, total = m.total, perAd = m.perAd, showingAd = ad, noAdWait = wait, adOffered = adsOn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloudOutState(meter = meter, adOffered = adsOn))

    private val _events = Channel<CloudOutEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: CloudOutAction) {
        when (action) {
            CloudOutAction.OnWatchAd -> {
                if (!adsOn || showingAd.value || noAdWait.value) return
                showingAd.value = true
                _events.trySend(CloudOutEvent.ShowAd)
            }
            is CloudOutAction.OnAdResult -> viewModelScope.launch {
                when (action.outcome) {
                    AdOutcome.REWARDED -> {
                        val added = quota.grantAd(meter)
                        snackbars.show(UiText.Plural(addedText(), added, added))
                        _events.send(CloudOutEvent.Granted(added))
                    }
                    AdOutcome.CLOSED_EARLY -> Unit // back on the sheet, nothing added
                    AdOutcome.NO_AD -> {
                        snackbars.show(UiText.Res(R.string.quota_no_ad))
                        startNoAdWait()
                    }
                }
                showingAd.value = false
            }
            CloudOutAction.OnWait -> _events.trySend(CloudOutEvent.Close)
        }
    }

    private fun startNoAdWait() {
        noAdJob?.cancel()
        noAdWait.value = true
        noAdJob = viewModelScope.launch {
            delay(NO_AD_WAIT_MS)
            noAdWait.value = false
        }
    }

    private fun addedText() = if (meter == CloudMeter.ANSWERS) R.plurals.quota_answers_added else R.plurals.quota_reads_added

    companion object {
        const val ARG_METER = "meter"
        const val NO_AD_WAIT_MS = 60_000L
    }
}
