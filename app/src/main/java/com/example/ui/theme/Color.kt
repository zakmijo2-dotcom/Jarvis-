package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Dark palette (default) — soft charcoal neutrals, never pure black/white
val DarkBackground = Color(0xFF15161A)
val DarkSurface = Color(0xFF1E2026)
val DarkSurfaceHigh = Color(0xFF272A32)
val DarkSurfaceLowest = Color(0xFF111216)
val DarkSurfaceLow = Color(0xFF191B20)
val DarkSurfaceHighest = Color(0xFF2E323C)
val DarkHairline = Color(0xFF33363F)
val DarkText = Color(0xFFECEAE4)
val DarkTextSecondary = Color(0xFFA5A8B0)

// Light palette — warm paper neutrals, never pure white/black
val LightBackground = Color(0xFFF6F3EE)
val LightSurface = Color(0xFFFBF9F5)
val LightSurfaceLow = Color(0xFFF2EFE8)
val LightSurfaceHigh = Color(0xFFEBE7DF)
val LightSurfaceHighest = Color(0xFFE2DDD4)
val LightHairline = Color(0xFFDDD8CE)
val LightText = Color(0xFF23252B)
val LightTextSecondary = Color(0xFF5D606A)

// Dark text placed directly on any of the five muted hues (contrast >= AA)
val OnMutedHueInk = Color(0xFF16171A)

// Five muted hues (exact dark tokens)
val HueSky = Color(0xFF8FB8DE)       // Chat & primary action
val HuePeach = Color(0xFFF0B49A)     // Voice
val HueSage = Color(0xFFA9CDB0)      // Models & success
val HueSand = Color(0xFFE3CF9E)      // Device & warning
val HueLavender = Color(0xFFB8AEE0)  // Settings
val HueCoral = Color(0xFFE08A7B)     // Error

// Soft light-mode tile tints derived from the five muted hues
val HueSkyLightTint = Color(0xFFD7E6F4)
val HuePeachLightTint = Color(0xFFF7DFD4)
val HueSageLightTint = Color(0xFFDCECE0)
val HueSandLightTint = Color(0xFFF2E8CC)
val HueLavenderLightTint = Color(0xFFE4DFF4)
val HueCoralLightTint = Color(0xFFF5DDD8)

// WCAG AA ink-safe muted accents when drawn directly as icons/labels on neutral surfaces
val HueSkyOnLight = Color(0xFF36628C)
val HuePeachOnLight = Color(0xFF9C5438)
val HueSageOnLight = Color(0xFF3B6B46)
val HueSandOnLight = Color(0xFF786028)
val HueLavenderOnLight = Color(0xFF584C8C)
val HueCoralOnLight = Color(0xFFA64234)

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
    // Section tile/pill fills (muted pastel hues)
    val skyContainer: Color,
    val peachContainer: Color,
    val sageContainer: Color,
    val sandContainer: Color,
    val lavenderContainer: Color,
    val coralContainer: Color,
    // Section accents/icons safe for neutral surfaces
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
    skyContainer = HueSky,
    peachContainer = HuePeach,
    sageContainer = HueSage,
    sandContainer = HueSand,
    lavenderContainer = HueLavender,
    coralContainer = HueCoral,
    skyAccent = HueSky,
    peachAccent = HuePeach,
    sageAccent = HueSage,
    sandAccent = HueSand,
    lavenderAccent = HueLavender,
    coralAccent = HueCoral,
    successContainer = HueSage,
    successAccent = HueSage,
    warningContainer = HueSand,
    warningAccent = HueSand,
    errorContainer = HueCoral,
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
