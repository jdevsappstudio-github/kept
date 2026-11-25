package com.jdevsappstudio.kept.core.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One snackbar. [action] runs on the app scope, so Undo still works after the screen that sent it has closed. */
data class SnackbarMessage(val text: UiText, val action: SnackbarAction? = null)

class SnackbarAction(val label: UiText, val onAction: suspend () -> Unit)

/**
 * App-wide snackbars: a detail screen deletes, closes, and the Undo shows on the screen below it.
 * The one host in MainActivity collects [messages] and calls [runAction] when the action is tapped.
 */
class Snackbars(private val appScope: CoroutineScope) {
    private val channel = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages: Flow<SnackbarMessage> = channel.receiveAsFlow()

    fun show(message: SnackbarMessage) {
        channel.trySend(message)
    }

    fun show(text: UiText, action: SnackbarAction? = null) = show(SnackbarMessage(text, action))

    fun runAction(action: SnackbarAction) {
        appScope.launch { action.onAction() }
    }
}
