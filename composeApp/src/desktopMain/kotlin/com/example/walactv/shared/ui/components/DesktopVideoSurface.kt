package com.example.walactv.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walactv.shared.data.DesktopVideoPlayer
import com.example.walactv.shared.domain.PlayerState
import com.example.walactv.shared.ui.theme.*

@Composable
fun DesktopVideoSurface(
    player: DesktopVideoPlayer,
    modifier: Modifier = Modifier,
) {
    val state by player.state.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val error by player.error.collectAsState()

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            PlayerState.IDLE -> {
                SwingPanel(
                    factory = { player.getVideoComponent() },
                    modifier = Modifier.fillMaxSize(),
                )
                Text("Selecciona un contenido para reproducir", color = IptvOnSurface, fontSize = 16.sp)
            }
            PlayerState.BUFFERING, PlayerState.READY -> {
                SwingPanel(
                    factory = { player.getVideoComponent() },
                    modifier = Modifier.fillMaxSize(),
                )
                CircularProgressIndicator(color = IptvAccent)
            }
            PlayerState.PLAYING, PlayerState.PAUSED -> {
                SwingPanel(
                    factory = { player.getVideoComponent() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PlayerState.ERROR -> {
                SwingPanel(
                    factory = { player.getVideoComponent() },
                    modifier = Modifier.fillMaxSize(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error de reproduccion", color = IptvLive, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    error?.let {
                        Text(it.message, color = IptvOnSurface, fontSize = 14.sp)
                    }
                }
            }
            PlayerState.ENDED -> {
                SwingPanel(
                    factory = { player.getVideoComponent() },
                    modifier = Modifier.fillMaxSize(),
                )
                Text("Reproduccion finalizada", color = IptvOnSurface, fontSize = 14.sp)
            }
        }
    }
}
