package com.jdevsappstudio.kept.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jdevsappstudio.kept.BuildConfig
import com.jdevsappstudio.kept.core.designsystem.Kept
import com.jdevsappstudio.kept.feature.spike.ReaderSpikeRoot

@Composable
fun HomeRoot() {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Kept", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            Text("Your saves, read and searchable.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("ON THIS PHONE", style = MaterialTheme.typography.labelSmall, color = Kept.colors.device)
            if (BuildConfig.DEBUG) ReaderSpikeRoot()
        }
    }
}
