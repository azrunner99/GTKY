package com.gtky.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = GTKYOrange,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Pink80,
    secondary = PurpleGrey40,
    background = GTKYBackground,
    surface = GTKYSurface,
    onBackground = androidx.compose.ui.graphics.Color(0xFF1C1B1F),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1C1B1F)
)

@Composable
fun GTKYTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}
