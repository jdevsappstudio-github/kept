package com.jdevsappstudio.kept.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Direction B · Rich (H10). Source of truth: tools/docbuild/kept_kit.py (COLOURS).

internal val LightScheme = lightColorScheme(
    background = Color(0xFFF5F1EA),
    onBackground = Color(0xFF1D1A1F),
    surface = Color(0xFFFFFDF9),
    onSurface = Color(0xFF1D1A1F),
    surfaceContainer = Color(0xFFECE6DC),
    surfaceContainerHigh = Color(0xFFECE6DC),
    surfaceContainerLow = Color(0xFFFFFDF9),
    surfaceVariant = Color(0xFFECE6DC),
    onSurfaceVariant = Color(0xFF4A4550),
    outline = Color(0xFFC3B9AA),
    outlineVariant = Color(0xFFE3DCD0),
    primary = Color(0xFF6E2A5E),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF2F4F9A),
    secondaryContainer = Color(0xFFE4E9F5),
    tertiary = Color(0xFF2F6B4A),
    tertiaryContainer = Color(0xFFE3EEE6),
    error = Color(0xFFA3261E),
)

internal val DarkScheme = darkColorScheme(
    background = Color(0xFF16131A),
    onBackground = Color(0xFFF3EEF2),
    surface = Color(0xFF201C24),
    onSurface = Color(0xFFF3EEF2),
    surfaceContainer = Color(0xFF2A2530),
    surfaceContainerHigh = Color(0xFF2A2530),
    surfaceContainerLow = Color(0xFF201C24),
    surfaceVariant = Color(0xFF2A2530),
    onSurfaceVariant = Color(0xFFCBC3CC),
    outline = Color(0xFF4B4452),
    outlineVariant = Color(0xFF332D39),
    primary = Color(0xFFE2A6D3),
    onPrimary = Color(0xFF2A0F24),
    secondary = Color(0xFFA9BCF0),
    secondaryContainer = Color(0xFF1B2238),
    tertiary = Color(0xFF8FCDA8),
    tertiaryContainer = Color(0xFF182821),
    error = Color(0xFFF2B8B5),
)

/** Colours Material has no slot for: meta text, the routing chips, the waiting state. */
@Immutable
data class KeptColors(
    val text3: Color,
    val device: Color,
    val deviceBg: Color,
    val cloud: Color,
    val cloudBg: Color,
    val wait: Color,
    val waitBg: Color,
)

internal val LightKept = KeptColors(
    text3 = Color(0xFF655F69),
    device = Color(0xFF2F6B4A), deviceBg = Color(0xFFE3EEE6),
    cloud = Color(0xFF2F4F9A), cloudBg = Color(0xFFE4E9F5),
    wait = Color(0xFF7A5612), waitBg = Color(0xFFF5ECD9),
)

internal val DarkKept = KeptColors(
    text3 = Color(0xFFA39AA6),
    device = Color(0xFF8FCDA8), deviceBg = Color(0xFF182821),
    cloud = Color(0xFFA9BCF0), cloudBg = Color(0xFF1B2238),
    wait = Color(0xFFE0B868), waitBg = Color(0xFF2A2316),
)

val LocalKeptColors = staticCompositionLocalOf { LightKept }
