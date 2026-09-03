package com.example.walactv.ui.overlay

import android.widget.ImageView
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text as TvText
import com.bumptech.glide.Glide
import com.example.walactv.datasource.torrent.TorrentStats
import java.util.Locale

private val SEED_COLOR = Color(0xFF46D369)
private val PEER_COLOR = Color(0xFF6FA8DC)
private val ACCENT = Color(0xFF3260F0)

/**
 * Pantalla de carga de peliculas/series con el poster del contenido de fondo.
 *
 * - [stats] != null: carga torrent, muestra seeds/peers, MB, velocidad y ETA.
 * - [stats] == null: carga por enlace directo de proveedor (sin informacion
 *   de descarga; solo titulo y barra indeterminada).
 */
@Composable
fun TorrentLoadingOverlay(
    stats: TorrentStats?,
    title: String,
    posterUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Fondo: poster del contenido a pantalla completa
        if (!posterUrl.isNullOrBlank()) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    Glide.with(imageView)
                        .load(posterUrl)
                        .into(imageView)
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }

        // Velos para legibilidad: oscurecer todo y reforzar la mitad inferior
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.1f),
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.9f),
                        )
                    )
                )
        )

        // Informacion sobre el tercio inferior
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 80.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (title.isNotBlank()) {
                TvText(
                    text = title,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
            }

            if (stats == null) {
                IndeterminateBar(Modifier.fillMaxWidth(0.5f))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatChip(
                        label = if (stats.metadataReady) "${stats.seeds} seeds" else "buscando fuentes…",
                        color = SEED_COLOR,
                    )
                    StatChip(
                        label = if (stats.metadataReady) "${stats.peers} peers" else "…",
                        color = PEER_COLOR,
                    )
                }

                Spacer(Modifier.height(18.dp))
                val progress = stats.progressPercent.coerceIn(0, 100) / 100f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(3.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(5.dp)
                            .background(ACCENT, RoundedCornerShape(3.dp)),
                    )
                }

                Spacer(Modifier.height(10.dp))
                val speedMb = stats.rateBytesPerSec / (1024f * 1024f)
                val downloadedMb = stats.downloadedBytes / (1024f * 1024f)
                val totalGb = stats.totalBytes / (1024f * 1024f * 1024f)
                val mainLine = when {
                    !stats.metadataReady -> "Obteniendo metadatos del torrent…"
                    stats.etaSeconds != null && stats.etaSeconds > 0 ->
                        "%.1f MB/s · empieza en ~%d s".format(Locale.US, speedMb, stats.etaSeconds)
                    else ->
                        "%.1f MB/s · iniciando…".format(Locale.US, speedMb)
                }
                TvText(
                    text = mainLine,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                val infoLine = if (totalGb >= 1f) {
                    "Descargando: %.1f MB de %.1f GB".format(Locale.US, downloadedMb, totalGb)
                } else {
                    "Descargando: %.1f MB".format(Locale.US, downloadedMb)
                }
                TvText(
                    text = infoLine,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** Barra indeterminada para cargas sin progreso medible (enlace directo). */
@Composable
private fun IndeterminateBar(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "indeterminate")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "indeterminate-offset",
    )
    Box(
        modifier = modifier
            .height(5.dp)
            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(3.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.35f)
                .height(5.dp)
                .alpha(1f)
                .background(ACCENT, RoundedCornerShape(3.dp))
                .align(Alignment.CenterStart)
                .offsetX(offset),
        )
    }
}

private fun Modifier.offsetX(fraction: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val maxX = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
        val x = ((maxX) * ((fraction + 1f) / 2f)).toInt()
        layout(constraints.maxWidth, placeable.height) {
            placeable.place(x = x, y = 0)
        }
    }
)

@Composable
private fun StatChip(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Group,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        TvText(text = label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
