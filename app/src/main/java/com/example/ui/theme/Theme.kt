package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val JarvisDarkColorScheme = darkColorScheme(
    primary = HueSky,
    onPrimary = OnMutedHueInk,
    primaryContainer = DarkSurfaceHigh,
    onPrimaryContainer = HueSky,
    secondary = HueSage,
    onSecondary = OnMutedHueInk,
    secondaryContainer = DarkSurfaceHigh,
    onSecondaryContainer = DarkText,
    tertiary = HuePeach,
    onTertiary = OnMutedHueInk,
    tertiaryContainer = DarkSurfaceHigh,
    onTertiaryContainer = DarkText,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = DarkSurfaceLowest,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    outline = DarkHairline,
    outlineVariant = DarkHairline,
    error = HueCoral,
    onError = OnMutedHueInk,
    errorContainer = DarkSurfaceHigh,
    onErrorContainer = HueCoral
)

private val JarvisLightColorScheme = lightColorScheme(
    primary = HueSky,
    onPrimary = OnMutedHueInk,
    primaryContainer = HueSkyLightTint,
    onPrimaryContainer = OnMutedHueInk,
    secondary = HueSage,
    onSecondary = OnMutedHueInk,
    secondaryContainer = HueSageLightTint,
    onSecondaryContainer = OnMutedHueInk,
    tertiary = HuePeach,
    onTertiary = OnMutedHueInk,
    tertiaryContainer = HuePeachLightTint,
    onTertiaryContainer = OnMutedHueInk,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurfaceHigh,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    outline = LightHairline,
    outlineVariant = LightHairline,
    error = HueCoral,
    onError = OnMutedHueInk,
    errorContainer = HueCoralLightTint,
    onErrorContainer = OnMutedHueInk
)

object JarvisThemeTokens {
    val palette: JarvisSemanticPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalJarvisPalette.current
}

@Composable
fun JarvisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) JarvisDarkColorScheme else JarvisLightColorScheme
    val semanticPalette = if (darkTheme) DarkJarvisSemanticPalette else LightJarvisSemanticPalette

    CompositionLocalProvider(LocalJarvisPalette provides semanticPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = JarvisTypography,
            shapes = JarvisShapes,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    JarvisTheme(darkTheme = darkTheme, content = content)
}
