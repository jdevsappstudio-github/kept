package com.jdevsappstudio.kept.feature.quota

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.data.ads.AdOutcome
import com.jdevsappstudio.kept.data.ads.RewardedAds
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.CloudQuota
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Debug only (H51): answers through CloudOutViewModel (the k-cloud-out path), reads straight on CloudQuota
 * (the queue reacts by itself). 'Use' = what a successful cloud call will do.
 */
@Composable
fun QuotaDebug(
    viewModel: CloudOutViewModel = koinViewModel(),
    quota: CloudQuota = koinInject(),
    ads: RewardedAds = koinInject(),
) {
    val answers by viewModel.state.collectAsStateWithLifecycle()
    val reads by remember { quota.observe(CloudMeter.READS) }.collectAsState(null)
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? Activity
    var last by remember { mutableStateOf("") }
    ObserveAsEvents(viewModel.events) { e ->
        last = e.toString()
        if (e == CloudOutEvent.ShowAd) scope.launch {
            val outcome = activity?.let { ads.show(it) } ?: AdOutcome.NO_AD
            viewModel.onAction(CloudOutAction.OnAdResult(outcome))
        }
    }
    val style = MaterialTheme.typography.bodySmall

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Answers ${answers.left} of ${answers.total}${if (answers.left == 0) " (out)" else ""}", style = style)
        TextButton(onClick = { scope.launch { quota.use(CloudMeter.ANSWERS) } }) { Text("Use") }
        TextButton(onClick = { viewModel.onAction(CloudOutAction.OnWatchAd) }, enabled = !answers.showingAd) {
            Text("Ad +${answers.perAd}")
        }
    }
    reads?.let { r ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Reads ${r.left} of ${r.total}${if (r.showCounter) " (low)" else ""}", style = style)
            TextButton(onClick = { scope.launch { repeat(r.left) { quota.use(CloudMeter.READS) } } }) { Text("Use all") }
            TextButton(onClick = {
                scope.launch { if (activity != null && ads.show(activity) == AdOutcome.REWARDED) quota.grantAd(CloudMeter.READS) }
            }) { Text("Ad +${r.perAd}") }
        }
    }
    if (last.isNotEmpty()) Text("Last event: $last", style = style)
}
