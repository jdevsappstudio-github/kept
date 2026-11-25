package com.jdevsappstudio.kept.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jdevsappstudio.kept.BuildConfig
import com.jdevsappstudio.kept.core.designsystem.Kept
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.data.queue.ReadRoute
import com.jdevsappstudio.kept.feature.spike.ReaderSpikeRoot

@Composable
fun HomeRoot(viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, viewModel::onAction)
}

@Composable
fun HomeScreen(state: HomeState, onAction: (HomeAction) -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Kept", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            Text("Your saves, read and searchable.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("ON THIS PHONE", style = MaterialTheme.typography.labelSmall, color = Kept.colors.device)
            QueueStrip(state)
            if (BuildConfig.DEBUG) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cloud help (debug)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = state.cloudHelp, onCheckedChange = { onAction(HomeAction.SetCloudHelp(it)) })
                }
                ReaderSpikeRoot()
            }
        }
    }
}

/** Home queue strip ("Reading 3 of 12 · On this phone"); full design comes with the home grid. */
@Composable
private fun QueueStrip(state: HomeState) {
    val c = state.counts
    val text = when {
        c.pending == 0 && c.needsLook == 0 -> "${c.read} read"
        c.pending == 0 -> "${c.read} read · ${c.needsLook} need a look"
        else -> when (state.pause) {
            QueuePause.NEEDS_CONSENT -> "${c.pending} waiting · turn on cloud help or tag by hand"
            QueuePause.OFFLINE -> "${c.pending} waiting · read when you're back online"
            QueuePause.IN_BACKGROUND -> "${c.pending} waiting"
            QueuePause.CLOUD_CAP -> "${c.pending} waiting · today's cloud reads are used up"
            QueuePause.NOT_SET_UP -> "${c.pending} waiting · reading isn't set up"
            QueuePause.NONE -> "Reading ${c.read + 1} of ${c.read + c.pending}" + when (state.route) {
                ReadRoute.DEVICE -> " · On this phone"
                ReadRoute.CLOUD -> " · Gemini in the cloud"
                else -> ""
            }
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
