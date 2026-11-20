package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object JarvisSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}

object JarvisRadii {
    val xs: Dp = 6.dp
    val sm: Dp = 10.dp
    val md: Dp = 14.dp
    val lg: Dp = 18.dp
    val xl: Dp = 22.dp

    val cardShape = RoundedCornerShape(16.dp)
    val chipShape = RoundedCornerShape(10.dp)
    val inputShape = RoundedCornerShape(14.dp)
}

object JarvisIconSize {
    val sm: Dp = 16.dp
    val md: Dp = 18.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val badgeContainer: Dp = 36.dp
}

object JarvisElevation {
    val flat: Dp = 0.dp
    val subtle: Dp = 2.dp
    val modal: Dp = 6.dp
}

object JarvisMotion {
    const val quickMs: Int = 180
    const val standardMs: Int = 280
    const val pulseCycleMs: Int = 1100
}
