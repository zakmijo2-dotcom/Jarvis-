package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium)
)

// Inter exclusively for UI typography; JetBrains Mono strictly for code and telemetry.
private val SizeGreeting = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 32.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.4).sp
)

private val SizeScreenHeader = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.2).sp
)

private val SizeTitleLead = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.sp
)

private val SizeBodyLarge = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 22.sp, // ~1.4 line height
    letterSpacing = 0.sp
)

private val SizeTitleSmall = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
)

private val SizeBodyMedium = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 21.sp, // exact 1.4 line height
    letterSpacing = 0.sp
)

private val SizeCaptionMedium = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 17.sp,
    letterSpacing = 0.sp
)

private val SizeCaptionRegular = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 17.sp,
    letterSpacing = 0.sp
)

val JarvisTypography = Typography(
    displaySmall = SizeGreeting,
    headlineMedium = SizeGreeting,
    headlineSmall = SizeScreenHeader,
    titleLarge = SizeTitleLead,
    titleMedium = SizeTitleLead,
    titleSmall = SizeTitleSmall,
    bodyLarge = SizeBodyLarge,
    bodyMedium = SizeBodyMedium,
    bodySmall = SizeCaptionRegular,
    labelLarge = SizeTitleSmall,
    labelMedium = SizeCaptionMedium,
    labelSmall = SizeCaptionRegular
)

val JarvisMonoCodeStyle = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp
)

val JarvisMonoTelemetryStyle = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp
)

val JarvisMonoMetricStyle = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
)
