package com.example.walactv.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.example.walactv.shared.domain.PlayerState

@Suppress("UnsafeOptInUsageError")
@Composable
fun MobilePlaybackScreen(
    streamUrl: String,
    title: String,
    subtitle: String = "",
    contentId: String? = null,
    contentType: String? = null,
    onBack: () -> Unit,
    onSaveProgress: ((positionMs: Long, durationMs: Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val player = remember { MobileVideoPlayer(context) }
    val initialPosition = remember { mutableLongStateOf(0L) }

    BackHandler { onBack() }

    DisposableEffect(Unit) {
        player.play(streamUrl)
        if (initialPosition.value > 0) {
            player.seekTo(initialPosition.value)
        }
        onDispose {
            val pos = player.positionMs.value
            val dur = player.durationMs.value
            if (pos > 0 && dur > 0) {
                onSaveProgress?.invoke(pos, dur)
            }
            player.release()
        }
    }

    val isPlaying by player.isPlaying.collectAsState()
    LaunchedEffect(isPlaying) {
        if (isPlaying && onSaveProgress != null) {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                val pos = player.positionMs.value
                val dur = player.durationMs.value
                if (pos > 0 && dur > 0) {
                    onSaveProgress(pos, dur)
                }
            }
        }
    }

    val playerState by player.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player.exoPlayer
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        when (playerState) {
            PlayerState.BUFFERING -> {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            PlayerState.ERROR -> {
                val errorMsg by player.error.collectAsState()
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = errorMsg?.message ?: "Error de reproduccion",
                        color = Color.White,
                    )
                }
            }
            else -> { /* No overlay needed */ }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = Color.White,
            )
        }
    }
}
