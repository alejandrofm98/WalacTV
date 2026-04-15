package com.example.walactv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text as TvText
import com.example.walactv.PlaybackError
import com.example.walactv.PlaybackErrorType
import kotlinx.coroutines.delay

@Composable
fun PlayerErrorOverlay(
    error: PlaybackError,
    isRetrying: Boolean,
    onAutoAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        visible = true
    }

    LaunchedEffect(visible, onAutoAction) {
        if (visible && onAutoAction != null) {
            delay(3000)
            onAutoAction()
        }
    }

    if (visible && error != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(48.dp),
            ) {
                Icon(
                    imageVector = getErrorIcon(error.type),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(72.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))

                TvText(
                    text = error.title,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                TvText(
                    text = error.message,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )

                if (isRetrying) {
                    Spacer(modifier = Modifier.height(32.dp))
                    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "spin",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(rotation),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TvText(
                            text = "Reintentando...",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun getErrorIcon(type: PlaybackErrorType): ImageVector {
    return when (type) {
        PlaybackErrorType.NETWORK -> Icons.Default.WifiOff
        PlaybackErrorType.CODEC_INCOMPATIBLE -> Icons.Default.Visibility
        else -> Icons.Default.ErrorOutline
    }
}
