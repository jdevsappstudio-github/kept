package com.jdevsappstudio.kept.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.designsystem.ButtonShape
import com.jdevsappstudio.kept.core.designsystem.Kept
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.designsystem.KeptTopBar
import com.jdevsappstudio.kept.core.designsystem.SectionLabel
import com.jdevsappstudio.kept.core.designsystem.VSpace
import com.jdevsappstudio.kept.core.designsystem.card
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.core.presentation.openUrl
import com.jdevsappstudio.kept.data.ai.DeviceModelState
import com.jdevsappstudio.kept.feature.needlook.NeedLookRules
import org.koin.androidx.compose.koinViewModel

@Composable
fun PrivacyRoot(
    onBack: () -> Unit,
    onSent: () -> Unit,
    onUnsupported: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: PrivacyViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event -> if (event == PrivacyEvent.Deleted) onDeleted() }
    PrivacyScreen(state) { action ->
        when (action) {
            PrivacyAction.OnBack -> onBack()
            PrivacyAction.OnSentClick -> onSent()
            PrivacyAction.OnReadingRowClick -> if (state.readingRow.opensUnsupported) onUnsupported()
            PrivacyAction.OnPrivacyPolicyClick -> context.openUrl(SettingsCopy.PRIVACY_URL)
            else -> viewModel.onAction(action)
        }
    }
}

/** k-privacy (H68, H69): every line is state-aware; no Ad privacy (no ads, H66), no Terms page yet. */
@Composable
fun PrivacyScreen(state: PrivacyState, onAction: (PrivacyAction) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        KeptTopBar("Privacy", onBack = { onAction(PrivacyAction.OnBack) })
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("Reading your saves")
            val row = state.readingRow
            val onPhone = state.device != DeviceModelState.UNAVAILABLE
            RowGroup {
                NavRow(
                    if (onPhone) Icons.Outlined.PhoneAndroid else Icons.Outlined.Cloud, row.title, row.body,
                    iconTint = if (onPhone) Kept.colors.device else Kept.colors.cloud,
                    iconBg = if (onPhone) Kept.colors.deviceBg else Kept.colors.cloudBg,
                    onClick = if (row.opensUnsupported) ({ onAction(PrivacyAction.OnReadingRowClick) }) else null,
                )
            }
            VSpace(8.dp)
            SectionLabel("Cloud help")
            RowGroup {
                SwitchRow(
                    Icons.Outlined.Cloud, "Cloud help", SettingsCopy.cloudHelpBody(state.device),
                    checked = state.cloudHelp, onCheckedChange = { onAction(PrivacyAction.OnCloudHelpChange(it)) },
                    iconTint = Kept.colors.cloud, iconBg = Kept.colors.cloudBg,
                )
                RowDivider()
                NavRow(Icons.Outlined.Visibility, "What was sent", SettingsCopy.sentLine(state.sentCount)) {
                    onAction(PrivacyAction.OnSentClick)
                }
            }
            Text(SettingsCopy.CLOUD_FOOTNOTE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VSpace(8.dp)
            SectionLabel("Your data")
            RowGroup {
                NavRow(
                    Icons.Outlined.DeleteOutline, "Delete everything",
                    iconTint = MaterialTheme.colorScheme.error, iconBg = MaterialTheme.colorScheme.errorContainer,
                    onClick = if (state.deleting) null else ({ onAction(PrivacyAction.OnDeleteClick) }),
                )
            }
            VSpace(8.dp)
            Text(SettingsCopy.PRIVACY_FOOTNOTE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onAction(PrivacyAction.OnPrivacyPolicyClick) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Privacy policy", style = MaterialTheme.typography.titleSmall, textDecoration = TextDecoration.Underline)
            }
        }
    }
    when (state.dialog) {
        PrivacyDialog.CLOUD_CONSENT -> AlertDialog(
            onDismissRequest = { onAction(PrivacyAction.OnDismissDialog) },
            title = { Text(NeedLookRules.CONSENT_TITLE) },
            text = { Text(NeedLookRules.CONSENT_BODY) },
            confirmButton = { TextButton(onClick = { onAction(PrivacyAction.OnConsentYes) }) { Text(NeedLookRules.CONSENT_YES) } },
            dismissButton = { TextButton(onClick = { onAction(PrivacyAction.OnDismissDialog) }) { Text(NeedLookRules.CONSENT_NO) } },
        )
        // H69: a plain dialog and one red tap; no Undo after it.
        PrivacyDialog.DELETE -> AlertDialog(
            onDismissRequest = { onAction(PrivacyAction.OnDismissDialog) },
            title = { Text(SettingsCopy.DELETE_TITLE) },
            text = { Text(SettingsCopy.deleteBody(state.saveCount)) },
            confirmButton = {
                TextButton(
                    onClick = { onAction(PrivacyAction.OnDeleteConfirm) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { onAction(PrivacyAction.OnDismissDialog) }) { Text("Cancel") } },
        )
        PrivacyDialog.NONE -> Unit
    }
}

@Composable
fun UnsupportedRoot(onBack: () -> Unit, viewModel: PrivacyViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event -> if (event == PrivacyEvent.Close) onBack() }
    UnsupportedScreen(state) { action ->
        when (action) {
            PrivacyAction.OnBack -> onBack()
            PrivacyAction.OnPrivacyPolicyClick -> context.openUrl(SettingsCopy.PRIVACY_URL)
            else -> viewModel.onAction(action)
        }
    }
}

/** k-unsupported (H69): the page names images and private details, so Read with cloud is the consent itself. */
@Composable
fun UnsupportedScreen(state: PrivacyState, onAction: (PrivacyAction) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        KeptTopBar("", onBack = { onAction(PrivacyAction.OnBack) })
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Kept.colors.cloudBg), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Cloud, contentDescription = null, tint = Kept.colors.cloud, modifier = Modifier.size(28.dp))
            }
            Text(SettingsCopy.UNSUPPORTED_TITLE, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            Text(SettingsCopy.UNSUPPORTED_BODY, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().card(MaterialTheme.colorScheme.surface, 1.dp).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(
                    SettingsCopy.unsupportedLimits(state.reads, state.answers),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Button(
                onClick = { onAction(PrivacyAction.OnReadWithCloud) }, shape = ButtonShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Read with cloud", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = { onAction(PrivacyAction.OnTagByHand) }, shape = ButtonShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Tag by hand instead", modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                SettingsCopy.UNSUPPORTED_HAND, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { onAction(PrivacyAction.OnPrivacyPolicyClick) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Privacy policy", style = MaterialTheme.typography.titleSmall, textDecoration = TextDecoration.Underline)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PrivacyPreview() {
    KeptTheme(dark = false) {
        PrivacyScreen(PrivacyState(device = DeviceModelState.UNAVAILABLE, cloudHelp = true, sentCount = 12, saveCount = 40)) {}
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun UnsupportedPreview() {
    KeptTheme(dark = false) { UnsupportedScreen(PrivacyState(device = DeviceModelState.UNAVAILABLE)) {} }
}
