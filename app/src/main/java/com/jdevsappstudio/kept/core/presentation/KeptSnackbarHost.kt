package com.jdevsappstudio.kept.core.presentation

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** The one host for [Snackbars]: Undo stays tappable after the screen that sent it has closed. */
@Composable
fun KeptSnackbarHost(modifier: Modifier = Modifier, snackbars: Snackbars = koinInject()) {
    val host = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ObserveAsEvents(snackbars.messages) { message ->
        host.currentSnackbarData?.dismiss()
        scope.launch {
            val result = host.showSnackbar(
                message = message.text.asString(context),
                actionLabel = message.action?.label?.asString(context),
                duration = if (message.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) message.action?.let(snackbars::runAction)
        }
    }
    SnackbarHost(host, modifier)
}
