package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Dark Graphite Foundation (Default) — restrained aerospace telemetry console
val DarkBackground = Color(0xFF0E1015)
val DarkSurface = Color(0xFF151922)
val DarkSurfaceHigh = Color(0xFF1C222D)
val DarkSurfaceLowest = Color(0xFF0A0C10)
val DarkSurfaceLow = Color(0xFF12161E)
val DarkSurfaceHighest = Color(0xFF252C3A)
val DarkHairline = Color(0xFF28303E)
val DarkText = Color(0xFFEDF1F7)
val DarkTextSecondary = Color(0xFF94A0B4)

// Light Alabaster Foundation — crisp daytime cockpit with matching contrast
val LightBackground = Color(0xFFF4F6F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceLow = Color(0xFFEEF2F6)
val LightSurfaceHigh = Color(0xFFE5EBF2)
val LightSurfaceHighest = Color(0xFFD9E1EB)
val LightHairline = Color(0xFFD0D8E4)
val LightText = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)

// High-contrast ink drawn directly over tinted action pills/chips
val OnMutedHueInk = Color(0xFF0B1118)

// Restrained Cyan-Blue + Slate Telemetry Accent Tokens (kept compatible with JarvisOrb & semantic slots)
val HueSky = Color(0xFF38BDF8)       // Primary Cyan-Blue accent
val HuePeach = Color(0xFF60A5FA)     // Steel-Blue Voice accent
val HueSage = Color(0xFF2DD4BF)      // Cool Teal Success / Models
val HueSand = Color(0xFFF59E0B)      // Restrained Amber Caution / Device
val HueLavender = Color(0xFF818CF8)  // Muted Indigo System / Settings
val HueCoral = Color(0xFFF87171)     // Crimson Error

// High-legibility dark-mode container fills (keeps OnMutedHueInk >= 6:1 contrast)
val HueSkyDarkTint = Color(0xFF7DD3FC)
val HuePeachDarkTint = Color(0xFF93C5FD)
val HueSageDarkTint = Color(0xFF5EEAD4)
val HueSandDarkTint = Color(0xFFFCD34D)
val HueLavenderDarkTint = Color(0xFFA5B4FC)
val HueCoralDarkTint = Color(0xFFFCA5A5)

// Soft light-mode container fills
val HueSkyLightTint = Color(0xFFD5EEFC)
val HuePeachLightTint = Color(0xFFDCEAFE)
val HueSageLightTint = Color(0xFFCFF5EE)
val HueSandLightTint = Color(0xFFFEF0C7)
val HueLavenderLightTint = Color(0xFFE1E5FE)
val HueCoralLightTint = Color(0xFFFEE2E2)

// WCAG AA ink-safe accents drawn directly on light neutral surfaces
val HueSkyOnLight = Color(0xFF0284C7)
val HuePeachOnLight = Color(0xFF2563EB)
val HueSageOnLight = Color(0xFF0D9488)
val HueSandOnLight = Color(0xFFB45309)
val HueLavenderOnLight = Color(0xFF4F46E5)
val HueCoralOnLight = Color(0xFFDC2626)

// Tonal translucent icon badges for elevated Graphite cards
val BadgeCyanDark = Color(0xFF132638)
val BadgeTealDark = Color(0xFF102A29)
val BadgeAmberDark = Color(0xFF2A2112)
val BadgeIndigoDark = Color(0xFF1A1E36)
val BadgeCrimsonDark = Color(0xFF2E171A)

@Immutable
data class JarvisSemanticPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val hairline: Color,
    val text: Color,
    val textSecondary: Color,
    val onHue: Color,
    // Action / pill container fills paired with onHue ink
    val skyContainer: Color,
    val peachContainer: Color,
    val sageContainer: Color,
    val sandContainer: Color,
    val lavenderContainer: Color,
    val coralContainer: Color,
    // Subtle tinted surfaces for dashboard icon badges & status banners
    val cyanBadgeBg: Color,
    val tealBadgeBg: Color,
    val amberBadgeBg: Color,
    val indigoBadgeBg: Color,
    val crimsonBadgeBg: Color,
    // Section accents/icons safe on surface/background
    val skyAccent: Color,
    val peachAccent: Color,
    val sageAccent: Color,
    val sandAccent: Color,
    val lavenderAccent: Color,
    val coralAccent: Color,
    // Status aliases
    val successContainer: Color,
    val successAccent: Color,
    val warningContainer: Color,
    val warningAccent: Color,
    val errorContainer: Color,
    val errorAccent: Color
)

val DarkJarvisSemanticPalette = JarvisSemanticPalette(
    isDark = true,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceHigh = DarkSurfaceHigh,
    hairline = DarkHairline,
    text = DarkText,
    textSecondary = DarkTextSecondary,
    onHue = OnMutedHueInk,
    skyContainer = HueSkyDarkTint,
    peachContainer = HuePeachDarkTint,
    sageContainer = HueSageDarkTint,
    sandContainer = HueSandDarkTint,
    lavenderContainer = HueLavenderDarkTint,
    coralContainer = HueCoralDarkTint,
    cyanBadgeBg = BadgeCyanDark,
    tealBadgeBg = BadgeTealDark,
    amberBadgeBg = BadgeAmberDark,
    indigoBadgeBg = BadgeIndigoDark,
    crimsonBadgeBg = BadgeCrimsonDark,
    skyAccent = HueSky,
    peachAccent = HuePeach,
    sageAccent = HueSage,
    sandAccent = HueSand,
    lavenderAccent = HueLavender,
    coralAccent = HueCoral,
    successContainer = HueSageDarkTint,
    successAccent = HueSage,
    warningContainer = HueSandDarkTint,
    warningAccent = HueSand,
    errorContainer = HueCoralDarkTint,
    errorAccent = HueCoral
)

val LightJarvisSemanticPalette = JarvisSemanticPalette(
    isDark = false,
    background = LightBackground,
    surface = LightSurface,
    surfaceHigh = LightSurfaceHigh,
    hairline = LightHairline,
    text = LightText,
    textSecondary = LightTextSecondary,
    onHue = OnMutedHueInk,
    skyContainer = HueSkyLightTint,
    peachContainer = HuePeachLightTint,
    sageContainer = HueSageLightTint,
    sandContainer = HueSandLightTint,
    lavenderContainer = HueLavenderLightTint,
    coralContainer = HueCoralLightTint,
    cyanBadgeBg = HueSkyLightTint,
    tealBadgeBg = HueSageLightTint,
    amberBadgeBg = HueSandLightTint,
    indigoBadgeBg = HueLavenderLightTint,
    crimsonBadgeBg = HueCoralLightTint,
    skyAccent = HueSkyOnLight,
    peachAccent = HuePeachOnLight,
    sageAccent = HueSageOnLight,
    sandAccent = HueSandOnLight,
    lavenderAccent = HueLavenderOnLight,
    coralAccent = HueCoralOnLight,
    successContainer = HueSageLightTint,
    successAccent = HueSageOnLight,
    warningContainer = HueSandLightTint,
    warningAccent = HueSandOnLight,
    errorContainer = HueCoralLightTint,
    errorAccent = HueCoralOnLight
)

val LocalJarvisPalette = staticCompositionLocalOf { DarkJarvisSemanticPalette }
