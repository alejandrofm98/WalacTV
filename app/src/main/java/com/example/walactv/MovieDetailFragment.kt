@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.example.walactv

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.walactv.ui.theme.*

class MovieDetailFragment : Fragment() {

    companion object {
        private const val ARG_CATALOG_ITEM = "catalog_item"
        private const val TAG = "MovieDetailFragment"

        fun newInstance(item: CatalogItem): MovieDetailFragment {
            return MovieDetailFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_CATALOG_ITEM, createItemBundle(item))
                }
            }
        }

        private fun createItemBundle(item: CatalogItem): Bundle {
            Log.d(TAG, "TMDB_DETAIL bundle item=${item.tmdbDebug()}")
            return Bundle().apply {
                putString("stableId", item.stableId)
                putString("title", item.title)
                putString("description", item.description)
                putString("imageUrl", item.imageUrl)
                putString("backdropUrl", item.backdropUrl)
                putFloat("voteAverage", item.voteAverage ?: 0f)
                putInt("voteCount", item.voteCount ?: 0)
                putString("tagline", item.tagline)
                putString("releaseDate", item.releaseDate)
                putInt("runtimeMinutes", item.runtimeMinutes ?: 0)
                putStringArrayList("genres", ArrayList(item.genres))
                putString("group", item.group)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val item = parseArguments(requireArguments())

        return ComposeView(requireContext()).apply {
            setContent {
                WalacTVTheme {
                    MovieDetailScreen(
                        item = item,
                        onBackClick = { requireActivity().supportFragmentManager.popBackStack() },
                        onPlayClick = {
                            // TODO: Navegar al reproductor
                        }
                    )
                }
            }
        }
    }

    private fun parseArguments(args: Bundle): MovieDetailItem {
        return MovieDetailItem(
            stableId = args.getString("stableId") ?: "",
            title = args.getString("title") ?: "",
            description = args.getString("description") ?: "",
            imageUrl = args.getString("imageUrl") ?: "",
            backdropUrl = args.getString("backdropUrl"),
            voteAverage = args.getFloat("voteAverage").takeIf { it > 0 },
            voteCount = args.getInt("voteCount").takeIf { it > 0 },
            tagline = args.getString("tagline"),
            releaseDate = args.getString("releaseDate"),
            runtimeMinutes = args.getInt("runtimeMinutes").takeIf { it > 0 },
            genres = args.getStringArrayList("genres")?.toList() ?: emptyList(),
            group = args.getString("group") ?: ""
        ).also { item ->
            Log.d(
                TAG,
                "TMDB_DETAIL parsed id=${item.stableId} title=${item.title.take(120)} " +
                    "desc=${item.description.take(160)} image=${item.imageUrl.take(160)} backdrop=${item.backdropUrl.orEmpty().take(160)} " +
                    "rating=${item.voteAverage} runtime=${item.runtimeMinutes} genres=${item.genres.joinToString("|").take(120)}",
            )
        }
    }
}

data class MovieDetailItem(
    val stableId: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val backdropUrl: String?,
    val voteAverage: Float?,
    val voteCount: Int?,
    val tagline: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val group: String
)

@Composable
fun MovieDetailScreen(
    item: MovieDetailItem,
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(item.stableId, item.backdropUrl, item.description, item.imageUrl) {
        Log.d(
            "MovieDetailFragment",
            "TMDB_DETAIL compose id=${item.stableId} title=${item.title.take(120)} hasDesc=${item.description.isNotBlank()} " +
                "image=${item.imageUrl.take(160)} backdrop=${item.backdropUrl.orEmpty().take(160)}",
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fondo: Backdrop image
        Box(modifier = Modifier.fillMaxSize()) {
            // Backdrop
            val backdropUrl = item.backdropUrl
            if (!backdropUrl.isNullOrBlank()) {
                AsyncImage(
                    url = backdropUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Overlay degradado
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.95f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
        }

        // Contenido scrollable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 48.dp, vertical = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(200.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Poster izquierdo
                if (item.imageUrl.isNotBlank()) {
                    PosterImage(
                        url = item.imageUrl,
                        modifier = Modifier
                            .width(240.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                // Info principal
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Tagline
                    item.tagline?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = IptvAccent,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Título
                    Text(
                        text = item.title,
                        color = IptvTextPrimary,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Meta info (año, duración, rating)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Año
                        item.releaseDate?.takeIf { it.length >= 4 }?.let {
                            MetaBadge(text = it.substring(0, 4))
                        }

                        // Duración
                        item.runtimeMinutes?.let { minutes ->
                            val hours = minutes / 60
                            val mins = minutes % 60
                            MetaBadge(
                                text = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                            )
                        }

                        // Rating TMDB
                        item.voteAverage?.let { rating ->
                            RatingBadge(rating = rating, voteCount = item.voteCount)
                        }
                    }

                    // Géneros
                    if (item.genres.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item.genres.take(4).forEach { genre ->
                                GenreChip(genre = genre)
                            }
                        }
                    }

                    // Descripción
                    if (item.description.isNotBlank()) {
                        Text(
                            text = item.description,
                            color = IptvTextSecondary,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Botones de acción
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        // Botón Reproducir
                        ActionButton(
                            text = "Reproducir",
                            icon = Icons.Default.PlayArrow,
                            isPrimary = true,
                            onClick = onPlayClick,
                            modifier = Modifier.focusRequester(focusRequester)
                        )

                        // Botón Volver
                        ActionButton(
                            text = "Volver",
                            icon = Icons.Default.ArrowBack,
                            isPrimary = false,
                            onClick = onBackClick
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // Botón flotante "Volver arriba" cuando se hace scroll
        val showScrollToTop by remember {
            derivedStateOf { scrollState.value > 500 }
        }

        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(32.dp)
        ) {
            ScrollToTopButton(onClick = { /* TODO: scroll to top */ })
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun PosterImage(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        modifier = modifier,
        update = { imageView ->
            Glide.with(imageView)
                .load(url)
                .placeholder(R.drawable.ic_launcher_background)
                .into(imageView)
        }
    )
}

@Composable
private fun AsyncImage(url: String, contentDescription: String?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        modifier = modifier,
        update = { imageView ->
            Glide.with(imageView)
                .load(url)
                .into(imageView)
        }
    )
}

@Composable
private fun MetaBadge(text: String) {
    Text(
        text = text,
        color = IptvTextMuted,
        fontSize = 14.sp,
        modifier = Modifier
            .background(IptvSurfaceVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun RatingBadge(rating: Float, voteCount: Int?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(Color(0xFF1DB954).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = Color(0xFF1DB954),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = String.format("%.1f", rating),
            color = Color(0xFF1DB954),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        voteCount?.let {
            Text(
                text = "(${it / 1000}K)",
                color = IptvTextMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun GenreChip(genre: String) {
    Text(
        text = genre,
        color = IptvTextSecondary,
        fontSize = 13.sp,
        modifier = Modifier
            .background(IptvSurface, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .scale(scale)
            .background(
                if (isPrimary) IptvAccent else IptvSurface,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isPrimary) Color.White else IptvTextPrimary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            color = if (isPrimary) Color.White else IptvTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ScrollToTopButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(IptvAccent, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = "Volver arriba",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}
