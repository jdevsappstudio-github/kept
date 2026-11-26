package com.jdevsappstudio.kept.core.presentation

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.data.settings.SettingsStore

/**
 * KeptTheme in the person's mode (k-appearance, H68). Nothing is drawn until the mode is read (a few ms from
 * DataStore), so a Dark choice never flashes light. [edgeToEdge]: also set the system bar icons to match.
 */
@Composable
fun ComponentActivity.KeptAppTheme(settings: SettingsStore, edgeToEdge: Boolean = true, content: @Composable () -> Unit) {
    val mode by settings.themeMode.collectAsStateWithLifecycle(initialValue = null)
    val systemDark = isSystemInDarkTheme()
    val dark = (mode ?: return).isDark(systemDark)
    if (edgeToEdge) {
        DisposableEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
            )
            onDispose {}
        }
    }
    KeptTheme(dark = dark, content = content)
}

// androidx.activity's own defaults for the 3-button nav bar scrim.
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

