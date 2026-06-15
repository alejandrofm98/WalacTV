package com.example.walactv.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walactv.shared.domain.PlayerState
import com.example.walactv.shared.domain.VideoPlayer
import com.example.walactv.shared.ui.theme.*

@Composable
fun PlaybackScreen(
    player: VideoPlayer,
    title: String,
    subtitle: String = "",
    streamUrl: String? = null,
    headers: Map<String, String> = emptyMap(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    videoContent: @Composable (() -> Unit)? = null,
) {
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val state by player.state.collectAsState()
    val error by player.error.collectAsState()

    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0f) }

    LaunchedEffect(streamUrl) {
        streamUrl?.let { player.play(it, headers) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IptvBackground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (videoContent != null) {
                videoContent()
            }
            when {
                error != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = error!!.title,
                            color = IptvLive,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = error!!.message,
                            color = IptvOnSurface,
                            fontSize = 14.sp,
                        )
                    }
                }
                state == PlayerState.BUFFERING || state == PlayerState.READY -> {
                    CircularProgressIndicator(color = IptvAccent)
                }
                state == PlayerState.ENDED -> {
                    Text(
                        text = "Reproduccion finalizada",
                        color = IptvOnSurface,
                        fontSize = 14.sp,
                    )
                }
                else -> {
                    if (videoContent == null) {
                        Text(
                            text = "Selecciona un contenido para reproducir",
                            color = IptvOnSurface,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = IptvOnSurface,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IptvSurface),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = IptvOnSurface,
                )
            }

            IconButton(
                onClick = {
                    when {
                        isPlaying -> player.pause()
                        state == PlayerState.PAUSED -> player.resume()
                        else -> {}
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IptvAccent),
                enabled = state != PlayerState.BUFFERING && state != PlayerState.IDLE,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                )
            }

            Text(
                text = formatDuration(position),
                color = IptvOnSurface,
                fontSize = 13.sp,
            )

            Slider(
                value = if (isSeeking) seekPosition else if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
                onValueChange = { value ->
                    isSeeking = true
                    seekPosition = value
                },
                onValueChangeFinished = {
                    player.seekTo((seekPosition * duration).toLong())
                    isSeeking = false
                },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = IptvAccent,
                    activeTrackColor = IptvAccent,
                    inactiveTrackColor = IptvSurfaceVariant,
                ),
            )

            Text(
                text = formatDuration(duration),
                color = IptvOnSurface,
                fontSize = 13.sp,
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
