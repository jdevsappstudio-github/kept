package com.jdevsappstudio.kept.feature.quota

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.designsystem.ButtonShape
import com.jdevsappstudio.kept.core.designsystem.Kept
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.designsystem.PillShape
import com.jdevsappstudio.kept.core.designsystem.VSpace
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.core.presentation.findActivity
import com.jdevsappstudio.kept.data.ads.AdOutcome
import com.jdevsappstudio.kept.data.ads.RewardedAds
import com.jdevsappstudio.kept.data.quota.CloudMeter
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * k-cloud-out, a bottom sheet in a dialog destination over Ask / a save's detail (answers) or Needs a look (reads).
 * The ad is shown here because it needs the Activity; the ViewModel only sees [AdOutcome].
 * [onGranted]: the caller sends the held question / fill-in again. [onClose]: Wait, back or a tap outside.
 */
@Composable
fun CloudOutRoot(
    onGranted: () -> Unit,
    onClose: () -> Unit,
    viewModel: CloudOutViewModel = koinViewModel(),
    ads: RewardedAds = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            CloudOutEvent.ShowAd -> scope.launch {
                val outcome = context.findActivity()?.let { ads.show(it) } ?: AdOutcome.NO_AD
                viewModel.onAction(CloudOutAction.OnAdResult(outcome))
            }
            is CloudOutEvent.Granted -> onGranted()
            CloudOutEvent.Close -> onClose()
        }
    }
    BackHandler { viewModel.onAction(CloudOutAction.OnWait) }
    CloudOutSheet(state, viewModel::onAction)
}

@Composable
fun CloudOutSheet(state: CloudOutState, onAction: (CloudOutAction) -> Unit) {
    val wait = { onAction(CloudOutAction.OnWait) }
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close", onClick = wait)
            .statusBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.background)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {} // taps inside stay inside
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp)
                    .clip(PillShape).background(MaterialTheme.colorScheme.outlineVariant),
            )
            VSpace(16.dp)
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Kept.colors.cloudBg), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Cloud, contentDescription = null, tint = Kept.colors.cloud, modifier = Modifier.size(28.dp))
            }
            VSpace(10.dp)
            Text(
                if (state.loading) "" else CloudOutCopy.title(state.meter, state.total),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            VSpace(6.dp)
            Text(CloudOutCopy.body(state.meter), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VSpace(16.dp)
            if (state.adOffered) WatchAdButton(state, onAction) else {
                Button(onClick = wait, shape = ButtonShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(CloudOutCopy.WAIT, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun WatchAdButton(state: CloudOutState, onAction: (CloudOutAction) -> Unit) {
    Button(
        onClick = { onAction(CloudOutAction.OnWatchAd) },
        enabled = state.canWatchAd,
        shape = ButtonShape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        if (state.showingAd) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Text(
            if (state.noAdWait) CloudOutCopy.NO_AD else CloudOutCopy.watchAd(state.meter, state.perAd),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    VSpace(8.dp)
    OutlinedButton(onClick = { onAction(CloudOutAction.OnWait) }, shape = ButtonShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(CloudOutCopy.WAIT, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview
@Composable
private fun CloudOutAnswersPreview() {
    KeptTheme { CloudOutSheet(CloudOutState(loading = false, left = 0, total = 20, perAd = 3)) {} }
}

@Preview
@Composable
private fun CloudOutWithAdPreview() {
    KeptTheme { CloudOutSheet(CloudOutState(loading = false, left = 0, total = 5, perAd = 3, adOffered = true)) {} }
}

@Preview
@Composable
private fun CloudOutReadsNoAdPreview() {
    KeptTheme {
        CloudOutSheet(CloudOutState(meter = CloudMeter.READS, loading = false, left = 0, total = 100, perAd = 10, noAdWait = true, adOffered = true)) {}
    }
}
