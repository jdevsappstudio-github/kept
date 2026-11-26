package com.jdevsappstudio.kept.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.jdevsappstudio.kept.R

// Newsreader (saved content, titles) + Inter (UI). Downloadable fonts; system fallback while they load.
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun family(name: String, vararg weights: FontWeight) = FontFamily(
    weights.map { w -> Font(googleFont = GoogleFont(name), fontProvider = provider, weight = w) },
)

val Newsreader = FontFamily(
    Font(googleFont = GoogleFont("Newsreader"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Newsreader"), fontProvider = provider, weight = FontWeight.SemiBold),
    // Home headline "tagged." is set in italic.
    Font(googleFont = GoogleFont("Newsreader"), fontProvider = provider, weight = FontWeight.SemiBold, style = FontStyle.Italic),
)
val Inter = family("Inter", FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)

// Roles from the design doc: Display 32/36, Title 24/30, Saved title 18/23, Body 16/23, Label 15/20, Meta 13/18.
internal val KeptTypography = Typography(
    displaySmall = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 23.sp, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.1.sp),
)
