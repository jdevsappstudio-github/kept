package com.jdevsappstudio.kept.feature.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * H73 (UX review, 26 Nov): onboarding text is bigger than the app's base scale, and key phrases are bold.
 * Every size lives here so the review panel can tune one number. UX open to the review panel.
 */
internal object ObStyle {
    val buttonHeight = 60.dp

    val promiseTitle: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 44.sp)

    /** Step titles (B1, B2, first save, first card): 24 → 30sp. */
    val title: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.headlineSmall.copy(fontSize = 30.sp, lineHeight = 36.sp)

    val promiseRow: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp)

    val body: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp)

    val button: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp, lineHeight = 22.sp)

    /** B1 chip label, one line. */
    val chip: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)
}

/** [text] with [part] (when present in it) set in semibold. */
internal fun boldPart(text: String, part: String?): AnnotatedString = buildAnnotatedString {
    val at = if (part.isNullOrEmpty()) -1 else text.indexOf(part)
    if (at < 0) {
        append(text)
        return@buildAnnotatedString
    }
    append(text, 0, at)
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) }
    append(text, at + part!!.length, text.length)
}
