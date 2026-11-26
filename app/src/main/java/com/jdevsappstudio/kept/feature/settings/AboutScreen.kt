package com.jdevsappstudio.kept.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jdevsappstudio.kept.BuildConfig
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.designsystem.KeptMark
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.designsystem.KeptTopBar
import com.jdevsappstudio.kept.core.designsystem.VSpace
import com.jdevsappstudio.kept.core.presentation.openUrl
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

/** About (H68, H69): version, Apache-2.0, privacy policy + source on GitHub, open-source licences in the app. */
@Composable
fun AboutScreen(onBack: () -> Unit, onLicences: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        KeptTopBar("About", onBack = onBack)
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                KeptMark(48.dp)
                Column {
                    Text("Kept", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            VSpace(12.dp)
            RowGroup {
                NavRow(Icons.Outlined.Policy, "Privacy policy") { context.openUrl(SettingsCopy.PRIVACY_URL) }
                RowDivider()
                NavRow(Icons.Outlined.Code, "Source code", "Kept is open source on GitHub") { context.openUrl(SettingsCopy.REPO_URL) }
                RowDivider()
                NavRow(Icons.Outlined.Gavel, "Licence", "Apache-2.0") { context.openUrl(SettingsCopy.LICENSE_URL) }
                RowDivider()
                @Suppress("DEPRECATION") // the AutoMirrored copy looks the same in a left-to-right UI
                NavRow(Icons.Outlined.LibraryBooks, "Open-source licences", "The libraries Kept is built with", onClick = onLicences)
            }
        }
    }
}

/** The list AboutLibraries makes at build time (R.raw.aboutlibraries). */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        KeptTopBar("Open-source licences", onBack = onBack, modifier = Modifier.padding(horizontal = 16.dp))
        LibrariesContainer(libraries, Modifier.fillMaxWidth().weight(1f))
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun AboutPreview() {
    KeptTheme(dark = false) { AboutScreen({}, {}) }
}
