package com.example.walactv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WalacTVTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = IptvAccent,
        secondary = IptvAccentDark,
        tertiary = IptvOnline,
        background = IptvBackground,
        surface = IptvSurface,
        onPrimary = IptvTextPrimary,
        onSecondary = IptvTextPrimary,
        onBackground = IptvTextPrimary,
        onSurface = IptvTextPrimary,
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
