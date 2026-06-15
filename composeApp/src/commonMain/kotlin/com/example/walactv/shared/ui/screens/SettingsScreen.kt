package com.example.walactv.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walactv.shared.ui.theme.*

@Composable
fun SettingsScreen(
    versionName: String,
    channelCount: Int,
    contentCount: Int,
    preferredLanguage: String,
    onLanguageChange: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val languages = listOf("ES" to "Español", "EN" to "Inglés")
    var showLanguagePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IptvBackground)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = "Ajustes",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )

        Column(
            modifier = Modifier
                .width(600.dp)
                .background(IptvSurface, RoundedCornerShape(10.dp))
                .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(10.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsRow(
                label = "Idioma de series",
                value = languages.find { it.first == preferredLanguage }?.second ?: "Español",
                onClick = { showLanguagePicker = true },
            )
            SettingsRow(label = "Version", value = versionName)
            SettingsRow(label = "Canales", value = channelCount.toString())
            SettingsRow(label = "Contenido", value = contentCount.toString())

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IptvLive.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onSignOut)
                    .padding(14.dp),
            ) {
                Text("Cerrar sesion", color = IptvLive, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showLanguagePicker) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showLanguagePicker = false }) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .background(IptvSurface, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Idioma", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                languages.forEach { (code, name) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (code == preferredLanguage) IptvAccent.copy(alpha = 0.2f) else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable {
                                onLanguageChange(code)
                                showLanguagePicker = false
                            }
                            .padding(14.dp),
                    ) {
                        Text(name, color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = IptvOnSurface, fontSize = 16.sp)
        Text(value, color = Color.White, fontSize = 16.sp)
    }
}
