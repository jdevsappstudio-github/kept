package com.jdevsappstudio.kept.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun KeptTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKeptColors provides if (dark) DarkKept else LightKept) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = KeptTypography,
            content = content,
        )
    }
}

object Kept {
    val colors: KeptColors
        @Composable @ReadOnlyComposable get() = LocalKeptColors.current
}
