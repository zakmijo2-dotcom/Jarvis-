package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JarvisDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = VoidBlack,
    primaryContainer = DeepCyanContainer,
    onPrimaryContainer = TextPrimary,
    secondary = IceBlue,
    onSecondary = VoidBlack,
    secondaryContainer = SoftIceContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = SignalAmber,
    onTertiary = VoidBlack,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = SignalAmber,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = MidnightNavy,
    onSurface = TextPrimary,
    surfaceVariant = TacticalNavy,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = VoidBlack,
    surfaceContainerLow = DeepSpaceNavy,
    surfaceContainer = MidnightNavy,
    surfaceContainerHigh = TacticalNavy,
    surfaceContainerHighest = ElevatedNavy,
    outline = HudBorderCyan,
    outlineVariant = TacticalNavy,
    error = CriticalRed,
    onError = VoidBlack,
    errorContainer = RedContainer,
    onErrorContainer = CriticalRed
)

@Composable
fun JarvisTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisDarkColorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    JarvisTheme(content = content)
}
