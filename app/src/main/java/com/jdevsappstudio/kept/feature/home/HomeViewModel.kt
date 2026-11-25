package com.jdevsappstudio.kept.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.data.queue.ReadQueue
import com.jdevsappstudio.kept.data.queue.ReadRoute
import com.jdevsappstudio.kept.data.saves.SaveLocalDataSource
import com.jdevsappstudio.kept.data.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val counts: QueueCounts = QueueCounts.EMPTY,
    val route: ReadRoute? = null,
    val pause: QueuePause = QueuePause.NONE,
    val cloudHelp: Boolean = false,
)

sealed interface HomeAction {
    data class SetCloudHelp(val on: Boolean) : HomeAction
}

class HomeViewModel(
    store: SaveLocalDataSource,
    queue: ReadQueue,
    private val settings: SettingsStore,
) : ViewModel() {

    val state: StateFlow<HomeState> = combine(
        store.observeCounts(), queue.route, queue.pause, settings.cloudHelp,
    ) { counts, route, pause, cloudHelp -> HomeState(counts, route, pause, cloudHelp) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.SetCloudHelp -> viewModelScope.launch { settings.setCloudHelp(action.on) }
        }
    }
}
