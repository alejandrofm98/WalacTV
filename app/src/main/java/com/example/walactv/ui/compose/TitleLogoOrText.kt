@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.example.walactv.ui.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

/** Logo opcional del título; el texto sigue siendo visible si falta o falla la imagen. */
@Composable
fun TitleLogoOrText(
    text: String,
    logoUrl: String?,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    lineHeight: TextUnit,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val fallback: @Composable () -> Unit = {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
    if (logoUrl.isNullOrBlank()) {
        fallback()
    } else {
        SubcomposeAsyncImage(
            model = logoUrl,
            contentDescription = text,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxWidth(),
            loading = { fallback() },
            error = { fallback() },
            success = { SubcomposeAsyncImageContent(modifier = Modifier.height(90.dp)) },
        )
    }
}
