package com.jdevsappstudio.kept

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.feature.home.HomeRoot
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KeptTheme {
                Box(Modifier.fillMaxSize()) {
                    HomeRoot()
                    KeptSnackbarHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
                }
            }
        }
    }

    companion object {
        /** Opened from the share sheet ("Open it" / "Add a title"); the detail screen picks it up once it exists. */
        const val EXTRA_SAVE_ID = "save_id"
    }
}

/** The one host for [Snackbars]: Undo stays tappable after the screen that sent it has closed. */
@Composable
private fun KeptSnackbarHost(modifier: Modifier = Modifier, snackbars: Snackbars = koinInject()) {
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
