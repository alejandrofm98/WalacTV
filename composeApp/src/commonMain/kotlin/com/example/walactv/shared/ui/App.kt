package com.example.walactv.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.walactv.shared.ui.screens.LoginScreen
import com.example.walactv.shared.ui.theme.WalacTVTheme

@Composable
fun App(
    isLoggedIn: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onLogin: (String, String) -> Unit = { _, _ -> },
    onLogout: () -> Unit = {},
    homeContent: @Composable () -> Unit = {},
) {
    WalacTVTheme {
        if (!isLoggedIn) {
            LoginScreen(
                onLogin = onLogin,
                errorMessage = errorMessage,
                isLoading = isLoading,
            )
        } else {
            homeContent()
        }
    }
}
