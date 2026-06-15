package com.example.walactv.shared.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = IptvAccent,
    onPrimary = IptvOnAccent,
    background = IptvBackground,
    onBackground = IptvOnBackground,
    surface = IptvSurface,
    onSurface = IptvOnSurface,
)

@Composable
fun WalacTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
