@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.example.walactv.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.walactv.R
import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.model.StreamOption
import com.example.walactv.data.remote.repository.IptvRepository
import com.example.walactv.ui.compose.tvClickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.walactv.ui.theme.*
import kotlinx.coroutines.launch

class UfcDetailFragment : Fragment() {

    companion object {
        private const val TAG = "UfcDetailFragment"

        fun newInstance(item: CatalogItem): UfcDetailFragment {
            Log.d(TAG, "newInstance item=${item.title.take(80)} streamOptions=${item.streamOptions.size}")
            return UfcDetailFragment().apply {
                arguments = createItemBundle(item)
            }
        }

        private fun createItemBundle(item: CatalogItem): Bundle {
            return Bundle().apply {
                putString("stableId", item.stableId)
                putString("title", item.title)
                putString("subtitle", item.subtitle)
                putString("description", item.description)
                putString("imageUrl", item.imageUrl)
                putString("group", item.group)
                putString("badgeText", item.badgeText)
                putSerializable("streamOptions", ArrayList(item.streamOptions))
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val item = parseArguments(requireArguments())
        return ComposeView(requireContext()).apply {
            setContent {
                WalacTVTheme {
                    UfcDetailScreen(
                        item = item,
                        onBackClick = { requireActivity().supportFragmentManager.popBackStack() },
                        onPlayClick = { selectedIndex -> playSelectedSource(selectedIndex) },
                    )
                }
            }
        }
    }

    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    private fun playSelectedSource(selectedIndex: Int) {
        val item = parseArguments(requireArguments())
        val stream = item.streamOptions.getOrNull(selectedIndex) ?: run {
            android.widget.Toast.makeText(
                requireContext(),
                R.string.no_streams_available,
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val repository = IptvRepository(requireContext())
        lifecycleScope.launch {
            val resolvedUrl = try {
                repository.resolveReplayStreamUrl(stream)
            } catch (e: Exception) {
                Log.e(TAG, "resolveReplayStreamUrl failed, usando proxy", e)
                stream.url
            }
            Log.d(TAG, "playSelectedSource: ${stream.label} url=${resolvedUrl.take(80)}")
            launchPlayer(item, stream, resolvedUrl, selectedIndex)
        }
    }

    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    private fun launchPlayer(item: CatalogItem, stream: StreamOption, resolvedUrl: String, selectedIndex: Int) {
        val playerFragment = PlayerFragment()
        playerFragment.initialize(
            streamUrl = resolvedUrl,
            overlayNumber = item.kind.name,
            overlayTitle = item.title,
            overlayMeta = item.subtitle.ifBlank { item.badgeText },
            overlayDescription = item.description,
            overlayRating = item.voteAverage,
            contentKind = item.kind,
            onNavigateChannel = { _ -> },
            onNavigateOption = { _ -> },
            onDirectChannelNumber = { _ -> false },
            onToggleFavorite = { false },
            onOpenFavorites = { false },
            onOpenRecents = { false },
            onOpenGuide = null,
            streamOptionLabels = item.streamOptions.map { it.label },
            currentOptionIndex = selectedIndex,
            showOptionsOnStart = false,
            overlayLogoUrl = item.imageUrl,
            isFavorite = false,
            contentId = item.stableId,
            onProgressSaved = { progressItem ->
                (requireActivity().application as com.example.walactv.WalacApp)
                    .appComponent.homeViewModel.upsertContinueWatchingEntry(progressItem)
            },
            customHeaders = stream.headers,
        )
        val fm = requireActivity().supportFragmentManager
        fm.findFragmentById(R.id.player_container)?.let { fm.beginTransaction().remove(it).commitNow() }
        fm.beginTransaction().replace(R.id.player_container, playerFragment, "player_fragment").commitNow()
        val container = requireActivity().findViewById<FrameLayout>(R.id.player_container)
        container.visibility = View.VISIBLE
        container.isFocusable = true
        container.isFocusableInTouchMode = true
        runCatching { container.requestFocus() }
    }

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun parseArguments(args: Bundle): CatalogItem {
        return CatalogItem(
            stableId = args.getString("stableId") ?: "",
            title = args.getString("title") ?: "",
            subtitle = args.getString("subtitle") ?: "",
            description = args.getString("description") ?: "",
            imageUrl = args.getString("imageUrl") ?: "",
            kind = ContentKind.UFC,
            group = args.getString("group") ?: "",
            badgeText = args.getString("badgeText") ?: "",
            streamOptions = (args.getSerializable("streamOptions") as? List<StreamOption>) ?: emptyList(),
        )
    }
}

@Composable
fun UfcDetailScreen(
    item: CatalogItem,
    onBackClick: () -> Unit,
    onPlayClick: (Int) -> Unit,
) {
    val backFocusRequester = remember { FocusRequester() }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val sourceLabels = remember(item.streamOptions) { item.streamOptions.map { it.label } }

    val backgroundImageUrl = item.imageUrl.takeIf { it.isNotBlank() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IptvBackground),
    ) {
        if (!backgroundImageUrl.isNullOrBlank()) {
            AsyncImage(
                url = backgroundImageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.95f),
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                        ),
                        startX = 0f,
                        endX = Float.POSITIVE_INFINITY,
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.8f),
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 56.dp, end = 24.dp, top = 48.dp, bottom = 48.dp),
        ) {
            Row(
                modifier = Modifier
                    .tvClickable { onBackClick() }
                    .focusRequester(backFocusRequester)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Volver",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(0.6f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (item.badgeText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(IptvLive.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = item.badgeText,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Text(
                    text = item.title,
                    color = IptvTextPrimary,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 56.sp,
                )

                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        color = IptvTextMuted,
                        fontSize = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ActionButton(
                        text = "Reproducir",
                        icon = Icons.Default.PlayArrow,
                        isPrimary = true,
                        onClick = { onPlayClick(selectedIndex) },
                    )
                }

                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        color = IptvTextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (sourceLabels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Fuentes disponibles",
                        color = IptvTextSecondary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        sourceLabels.forEachIndexed { index, label ->
                            SourceButton(
                                label = label,
                                isSelected = index == selectedIndex,
                                onClick = {
                                    selectedIndex = index
                                    onPlayClick(index)
                                },
                                onFocused = { selectedIndex = index },
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        backFocusRequester.requestFocus()
    }
}

@Composable
private fun SourceButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.03f else 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .background(
                if (isSelected) IptvFocusBorder.copy(alpha = 0.35f) else IptvSurfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp),
            )
            .border(
                width = if (isSelected || isFocused) 2.dp else 1.dp,
                color = if (isSelected) IptvFocusBorder else Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
            )
            .onFocusChanged { focused ->
                isFocused = focused.isFocused
                if (focused.isFocused) onFocused()
            }
            .tvClickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (isSelected) IptvFocusBorder else IptvTextMuted,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                color = IptvTextPrimary,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
        },
    )
}

@Composable
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f)

    val bgColor = if (isPrimary) Color.White else Color.Transparent
    val contentColor = if (isPrimary) Color.Black else Color.White
    val borderModifier =
        if (isPrimary) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .scale(scale)
            .then(borderModifier)
            .background(bgColor, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .tvClickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            color = contentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
