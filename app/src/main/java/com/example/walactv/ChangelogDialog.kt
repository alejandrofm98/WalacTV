@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.example.walactv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.walactv.ui.theme.IptvAccent
import com.example.walactv.ui.theme.IptvCard
import com.example.walactv.ui.theme.IptvFocusBg
import com.example.walactv.ui.theme.IptvFocusBorder
import com.example.walactv.ui.theme.IptvSurface
import com.example.walactv.ui.theme.IptvSurfaceVariant
import com.example.walactv.ui.theme.IptvTextMuted
import com.example.walactv.ui.theme.IptvTextPrimary

@Composable
fun ChangelogDialog(
    versionName: String,
    markdown: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(720.dp)
                    .background(IptvSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(12.dp))
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Novedades v$versionName",
                        color = IptvTextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    CloseButton(onClick = onDismiss)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height(420.dp)
                        .background(IptvCard, RoundedCornerShape(8.dp))
                        .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    MarkdownText(markdown)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    var closeFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .background(
                                if (closeFocused) IptvAccent else IptvCard,
                                RoundedCornerShape(8.dp),
                            )
                            .border(
                                1.dp,
                                if (closeFocused) IptvFocusBorder else IptvSurfaceVariant,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { onDismiss() }
                            .focusable()
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    ) {
                        Text("Cerrar", color = IptvTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                if (isFocused) IptvFocusBg else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .border(
                1.dp,
                if (isFocused) IptvFocusBorder else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable { onClick() }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Close,
            contentDescription = "Cerrar",
            tint = IptvTextPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MarkdownText(markdown: String) {
    val lines = markdown.split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (line in lines) {
            val trimmed = line.trimEnd()
            when {
                trimmed.startsWith("### ") -> {
                    Text(
                        renderInlineMarkdown(trimmed.removePrefix("### ")),
                        color = IptvTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                trimmed.startsWith("## ") -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        renderInlineMarkdown(trimmed.removePrefix("## ")),
                        color = IptvAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                trimmed.startsWith("# ") -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        renderInlineMarkdown(trimmed.removePrefix("# ")),
                        color = IptvTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                trimmed.startsWith("---") || trimmed.startsWith("***") -> {
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(IptvSurfaceVariant),
                    )
                    Spacer(Modifier.height(2.dp))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val content = trimmed.substring(2)
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text("•  ", color = IptvAccent, fontSize = 15.sp)
                        Text(
                            renderInlineMarkdown(content),
                            color = IptvTextPrimary,
                            fontSize = 15.sp,
                        )
                    }
                }
                Regex("^\\d+\\. ").matches(trimmed) -> {
                    val match = Regex("^(\\d+)\\. (.*)").find(trimmed)
                    val num = match?.groupValues?.getOrNull(1) ?: ""
                    val content = match?.groupValues?.getOrNull(2) ?: trimmed
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text("$num. ", color = IptvAccent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            renderInlineMarkdown(content),
                            color = IptvTextPrimary,
                            fontSize = 15.sp,
                        )
                    }
                }
                trimmed.startsWith("> ") -> {
                    Text(
                        renderInlineMarkdown(trimmed.removePrefix("> ")),
                        color = IptvTextMuted,
                        fontSize = 15.sp,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                trimmed.isBlank() -> {
                    Spacer(Modifier.height(4.dp))
                }
                else -> {
                    Text(
                        renderInlineMarkdown(trimmed),
                        color = IptvTextPrimary,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

private fun renderInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Bold **text**
                i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Inline code `text`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0xFF2A2A35),
                                color = IptvAccent,
                                fontSize = 14.sp,
                            ),
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Link [text](url)
                text[i] == '[' -> {
                    val closeBracket = text.indexOf(']', i + 1)
                    val openParen = if (closeBracket != -1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') closeBracket + 1 else -1
                    val closeParen = if (openParen != -1) text.indexOf(')', openParen + 1) else -1
                    if (closeParen != -1) {
                        val linkText = text.substring(i + 1, closeBracket)
                        withStyle(
                            SpanStyle(
                                color = IptvAccent,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ) {
                            append(linkText)
                        }
                        i = closeParen + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Strikethrough ~~text~~
                i + 1 < text.length && text[i] == '~' && text[i + 1] == '~' -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = IptvTextMuted)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Single *italic*
                text[i] == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1 && end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
