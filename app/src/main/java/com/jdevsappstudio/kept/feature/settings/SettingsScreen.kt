package com.jdevsappstudio.kept.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.BuildConfig
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.designsystem.KeptTopBar
import com.jdevsappstudio.kept.core.designsystem.SectionLabel
import com.jdevsappstudio.kept.core.designsystem.VSpace
import com.jdevsappstudio.kept.core.model.ThemeMode
import com.jdevsappstudio.kept.core.presentation.findActivity
import com.jdevsappstudio.kept.core.presentation.openEmail
import org.koin.androidx.compose.koinViewModel

/** Android lets Kept post: app notifications on and, on 13+, the runtime permission granted. */
private fun Context.notificationsAllowed(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

@Composable
fun SettingsRoot(
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(context.notificationsAllowed()) }
    var blocked by rememberSaveable { mutableStateOf(false) }
    // Back from Android's notification settings (or a change made there): read it again.
    LifecycleResumeEffect(Unit) {
        allowed = context.notificationsAllowed()
        onPauseOrDispose {}
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = context.notificationsAllowed()
        when {
            granted -> viewModel.onAction(SettingsAction.OnReadingUpdatesChange(true))
            // Denied for good: Android shows no prompt any more, so point to its settings (flows: k-settings).
            Build.VERSION.SDK_INT >= 33 &&
                context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false -> blocked = true
        }
    }
    SettingsScreen(state, notificationsAllowed = allowed) { action ->
        when (action) {
            SettingsAction.OnBack -> onBack()
            SettingsAction.OnAppearanceClick -> onAppearance()
            SettingsAction.OnPrivacyClick -> onPrivacy()
            SettingsAction.OnAboutClick -> onAbout()
            SettingsAction.OnFeedbackClick ->
                context.openEmail(SettingsCopy.feedbackEmail(BuildConfig.VERSION_NAME, "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.RELEASE))
            is SettingsAction.OnReadingUpdatesChange -> when {
                !action.on || allowed -> viewModel.onAction(action)
                Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                    ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> blocked = true // app notifications switched off in Android's settings
            }
            else -> viewModel.onAction(action)
        }
    }
    if (blocked) {
        AlertDialog(
            onDismissRequest = { blocked = false },
            title = { Text(SettingsCopy.NOTIF_BLOCKED_TITLE) },
            text = { Text(SettingsCopy.NOTIF_BLOCKED_BODY) },
            confirmButton = {
                TextButton(onClick = {
                    blocked = false
                    // The person wants them: once Android allows it on return, the switch shows on.
                    viewModel.onAction(SettingsAction.OnReadingUpdatesChange(true))
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }) { Text(SettingsCopy.NOTIF_BLOCKED_OPEN) }
            },
            dismissButton = { TextButton(onClick = { blocked = false }) { Text("Not now") } },
        )
    }
}

/** k-settings (H68): no Rate Kept, no Ad privacy, no Pro rows in the MVP. */
@Composable
fun SettingsScreen(state: SettingsState, notificationsAllowed: Boolean, onAction: (SettingsAction) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        KeptTopBar("Settings", onBack = { onAction(SettingsAction.OnBack) })
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("App")
            RowGroup {
                NavRow(Icons.Outlined.LightMode, "Appearance", SettingsCopy.appearanceLine(state.themeMode)) {
                    onAction(SettingsAction.OnAppearanceClick)
                }
                RowDivider()
                SwitchRow(
                    Icons.Outlined.Notifications, "Reading updates", SettingsCopy.READING_UPDATES_BODY,
                    checked = state.readingUpdates && notificationsAllowed,
                    onCheckedChange = { onAction(SettingsAction.OnReadingUpdatesChange(it)) },
                )
            }
            VSpace(8.dp)
            SectionLabel("Privacy")
            RowGroup {
                NavRow(Icons.Outlined.Shield, "Privacy and AI", state.privacyLine.ifEmpty { null }) { onAction(SettingsAction.OnPrivacyClick) }
            }
            VSpace(8.dp)
            SectionLabel("More")
            RowGroup {
                NavRow(Icons.Outlined.MailOutline, "Send feedback") { onAction(SettingsAction.OnFeedbackClick) }
                RowDivider()
                NavRow(Icons.Outlined.Info, "About", SettingsCopy.aboutLine(BuildConfig.VERSION_NAME)) { onAction(SettingsAction.OnAboutClick) }
            }
            if (BuildConfig.DEBUG) {
                VSpace(8.dp)
                SectionLabel("Debug")
                RowGroup {
                    NavRow(Icons.Outlined.Info, "Replay onboarding", "Shows the first run again; saves stay") {
                        onAction(SettingsAction.OnReplayOnboarding)
                    }
                }
            }
        }
    }
}

@Composable
fun AppearanceRoot(onBack: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppearanceScreen(state) { action -> if (action == SettingsAction.OnBack) onBack() else viewModel.onAction(action) }
}

/** k-appearance (H68): mode only; the whole app recolours at once. Plum is the one theme (H39), so no swatches. */
@Composable
fun AppearanceScreen(state: SettingsState, onAction: (SettingsAction) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        KeptTopBar("Appearance", onBack = { onAction(SettingsAction.OnBack) })
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Mode")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = state.themeMode == mode,
                        onClick = { onAction(SettingsAction.OnThemeModeClick(mode)) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(mode.label) }
                }
            }
            Text(SettingsCopy.MODE_HINT, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun SettingsPreview() {
    KeptTheme(dark = false) {
        SettingsScreen(SettingsState(privacyLine = "No AI on this phone · Cloud help off", loaded = true), notificationsAllowed = true) {}
    }
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun AppearancePreview() {
    KeptTheme(dark = true) { AppearanceScreen(SettingsState(themeMode = ThemeMode.DARK)) {} }
}
