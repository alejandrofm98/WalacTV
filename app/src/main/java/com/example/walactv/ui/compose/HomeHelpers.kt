package com.example.walactv.ui.compose

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.model.isVodContent

// ── Constantes de diseño ───────────────────────────────────────────────────

// Cards VOD — solo imagen, sin texto debajo (el título está en el hero)
internal val VOD_CARD_WIDTH        = 120.dp
internal val VOD_IMAGE_HEIGHT       = 180.dp   // ratio 2:3 exact
internal val VOD_TEXT_AREA_HEIGHT = 0.dp     // sin texto para VOD

// Hero inmersivo — ocupa ~55% de la pantalla (el backdrop es fillMaxSize)
internal val HOME_HERO_FRACTION    = 0.56f

// Cards canal / evento — mantienen su texto
internal val CH_CARD_WIDTH       = 180.dp
internal val CH_IMAGE_HEIGHT     = 100.dp
internal val CH_TEXT_AREA_HEIGHT = 60.dp

// Cards evento — texto integrado sobre imagen, estilo evento deportivo
internal val EVENT_CARD_WIDTH       = 240.dp
internal val EVENT_IMAGE_HEIGHT     = 150.dp
internal val EVENT_TEXT_AREA_HEIGHT = 52.dp

// Custom BringIntoViewSpec for Stremio-style focus scrolling (snap to left edge)
@OptIn(ExperimentalFoundationApi::class)
internal val StremioBringIntoViewSpec = object : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = snap()

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailingEdge = offset + size
        return when {
            offset < 0f && trailingEdge > containerSize -> 0f
            else -> offset
        }
    }
}

// ── Shared helpers ─────────────────────────────────────────────────────────

@Composable
internal fun WatchedBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Visibility,
            contentDescription = "Visto",
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(13.dp),
        )
        Text("VISTO", color = Color(0xFF4CAF50), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
internal fun EventSportPlaceholder(item: CatalogItem) {
    val category = listOf(item.title, item.subtitle, item.group, item.description).joinToString(" ").lowercase()
    val text = java.text.Normalizer.normalize(category, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    val colors = when {
        text.contains("futbol") -> listOf(Color(0xFF0B6E4F), Color(0xFF1A936F))
        text.contains("baloncesto") -> listOf(Color(0xFF7F4F24), Color(0xFFD68C45))
        text.contains("tenis") -> listOf(Color(0xFF254441), Color(0xFF43AA8B))
        text.contains("motociclismo") || text.contains("automovilismo") -> listOf(Color(0xFF1D3557), Color(0xFF457B9D))
        text.contains("mma") || text.contains("boxeo") -> listOf(Color(0xFF5F0F40), Color(0xFF9A031E))
        else -> listOf(Color(0xFF102A43), Color(0xFFD64550))
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = item.group.ifBlank { "EVENTO" }.uppercase().take(18),
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun CatalogItem.resolveDisplayTitle(): String = when {
    !tmdbTitle.isNullOrBlank() -> tmdbTitle
    kind == ContentKind.SERIES && !seriesName.isNullOrBlank() -> seriesName
    else -> normalizedTitle?.takeUnless { it.equals("null", ignoreCase = true) }?.takeIf { it.isNotBlank() }
        ?: title.takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
}

internal fun CatalogItem.isHeroContent(): Boolean = isVodContent() || kind == ContentKind.EVENT

internal fun CatalogItem.eventCompetitionText(): String {
    if (kind != ContentKind.EVENT) return ""
    val withoutTime = subtitle
        .replace(badgeText, "")
        .replace("  •  ", " ")
        .replace(" • ", " ")
        .replace("•", " ")
        .trim()
    return withoutTime.replace(Regex("\\s+"), " ")
}

internal val REDUNDANT_BADGES = setOf("CINE", "SERIE", "Pelicula", "Serie")
