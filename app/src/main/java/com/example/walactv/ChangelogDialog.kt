@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.example.walactv

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import com.example.walactv.ui.theme.IptvTextSecondary
import kotlinx.coroutines.launch

@Composable
fun ChangelogDialog(
    versionName: String,
    markdown: String,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
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
            LaunchedEffect(Unit) {
                Log.d("ChangelogDialog", "DIALOG_FOCUS: calling requestFocus()")
                focusRequester.requestFocus()
                Log.d("ChangelogDialog", "DIALOG_FOCUS: requestFocus() called")
            }
            Column(
                modifier = Modifier
                    .width(780.dp)
                    .background(IptvSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(16.dp))
                    .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "Novedades",
                            color = IptvTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "v$versionName",
                            color = IptvAccent,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    CloseButton(onClick = onDismiss)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height(550.dp)
                        .background(IptvCard, RoundedCornerShape(12.dp))
                        .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(24.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            Log.d("ChangelogDialog", "DIALOG_FOCUS: scrollBox hasFocus=${it.hasFocus} isFocused=${it.isFocused}")
                        }
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> {
                                    Log.d("ChangelogDialog", "DIALOG_KEY: DOWN")
                                    scrollScope.launch { scrollState.animateScrollBy(140f) }
                                    true
                                }

                                Key.DirectionUp -> {
                                    Log.d("ChangelogDialog", "DIALOG_KEY: UP")
                                    scrollScope.launch { scrollState.animateScrollBy(-140f) }
                                    true
                                }

                                Key.Back, Key.Escape -> {
                                    Log.d("ChangelogDialog", "DIALOG_KEY: BACK")
                                    onDismiss()
                                    true
                                }

                                else -> false
                            }
                        }
                        .verticalScroll(scrollState),
                ) {
                    MarkdownText(markdown)
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
            .size(40.dp)
            .background(
                if (isFocused) IptvFocusBg else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .border(
                1.dp,
                if (isFocused) IptvFocusBorder else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Close,
            contentDescription = "Cerrar",
            tint = IptvTextPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

// ── Markdown block parser ──────────────────────────────────────────────

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class Bullet(val text: String) : MdBlock()
    data class Checkbox(val text: String, val checked: Boolean) : MdBlock()
    data class NumberedItem(val number: String, val text: String) : MdBlock()
    data class Blockquote(val text: String) : MdBlock()
    data class CodeBlock(val code: String) : MdBlock()
    data object HorizontalRule : MdBlock()
    data object BlankLine : MdBlock()
}

private fun parseMarkdown(raw: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = raw.split("\n")
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimEnd()

        // Code block fence
        if (trimmed.startsWith("```")) {
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimEnd().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MdBlock.CodeBlock(codeLines.joinToString("\n")))
            i++
            continue
        }

        when {
            trimmed.startsWith("# ") -> blocks.add(MdBlock.Heading(1, trimmed.removePrefix("# ")))
            trimmed.startsWith("## ") -> blocks.add(MdBlock.Heading(2, trimmed.removePrefix("## ")))
            trimmed.startsWith("### ") -> blocks.add(MdBlock.Heading(3, trimmed.removePrefix("### ")))
            trimmed.startsWith("#### ") -> blocks.add(MdBlock.Heading(4, trimmed.removePrefix("#### ")))
            trimmed.startsWith("---") || trimmed.startsWith("***") || trimmed.startsWith("___") -> {
                if (trimmed.all { it == '-' } || trimmed.all { it == '*' } || trimmed.all { it == '_' }) {
                    blocks.add(MdBlock.HorizontalRule)
                } else {
                    blocks.add(MdBlock.Paragraph(trimmed))
                }
            }
            // Checkbox: - [x] text  or  - [ ] text  or  * [x] text
            Regex("^[-*] \\[[xX ]\\] ").matches(trimmed) -> {
                val checked = trimmed[3].lowercaseChar() == 'x'
                val text = trimmed.substring(6)
                blocks.add(MdBlock.Checkbox(text, checked))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                blocks.add(MdBlock.Bullet(trimmed.substring(2)))
            }
            Regex("^\\d+\\. ").matches(trimmed) -> {
                val match = Regex("^(\\d+)\\. (.*)").find(trimmed)
                val num = match?.groupValues?.getOrNull(1) ?: ""
                val text = match?.groupValues?.getOrNull(2) ?: trimmed
                blocks.add(MdBlock.NumberedItem(num, text))
            }
            trimmed.startsWith("> ") -> {
                // Collect consecutive blockquote lines
                val bqLines = mutableListOf(trimmed.removePrefix("> "))
                while (i + 1 < lines.size && lines[i + 1].trimEnd().startsWith("> ")) {
                    i++
                    bqLines.add(lines[i].trimEnd().removePrefix("> "))
                }
                blocks.add(MdBlock.Blockquote(bqLines.joinToString("\n")))
            }
            trimmed.isBlank() -> blocks.add(MdBlock.BlankLine)
            else -> blocks.add(MdBlock.Paragraph(trimmed))
        }
        i++
    }
    return blocks
}

@Composable
private fun MarkdownText(markdown: String) {
    val blocks = parseMarkdown(markdown)
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        for ((index, block) in blocks.withIndex()) {
            // Add top spacing before headings and after horizontal rules for visual breathing
            val needsTopSpacing = when (block) {
                is MdBlock.Heading -> index > 0
                is MdBlock.HorizontalRule -> index > 0
                else -> false
            }
            if (needsTopSpacing) {
                Spacer(Modifier.height(8.dp))
            }

            when (block) {
                is MdBlock.Heading -> HeadingBlock(block)
                is MdBlock.Paragraph -> ParagraphBlock(block)
                is MdBlock.Bullet -> BulletBlock(block)
                is MdBlock.Checkbox -> CheckboxBlock(block)
                is MdBlock.NumberedItem -> NumberedBlock(block)
                is MdBlock.Blockquote -> BlockquoteBlock(block)
                is MdBlock.CodeBlock -> CodeBlock(block)
                MdBlock.HorizontalRule -> HrBlock()
                MdBlock.BlankLine -> Spacer(Modifier.height(10.dp))
            }

            // Extra spacing after headings for separation from body
            if (block is MdBlock.Heading) {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun HeadingBlock(block: MdBlock.Heading) {
    val (fontSize, color, weight) = when (block.level) {
        1 -> Triple(24.sp, IptvTextPrimary, FontWeight.Bold)
        2 -> Triple(20.sp, IptvAccent, FontWeight.Bold)
        3 -> Triple(17.sp, IptvTextPrimary, FontWeight.SemiBold)
        else -> Triple(15.sp, IptvTextSecondary, FontWeight.SemiBold)
    }
    Text(
        renderInlineMarkdown(block.text),
        color = color,
        fontSize = fontSize,
        fontWeight = weight,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun ParagraphBlock(block: MdBlock.Paragraph) {
    Text(
        renderInlineMarkdown(block.text),
        color = IptvTextSecondary,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
}

@Composable
private fun BulletBlock(block: MdBlock.Bullet) {
    Row(
        modifier = Modifier.padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "\u2022",
            color = IptvAccent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 1.dp),
        )
        Text(
            renderInlineMarkdown(block.text),
            color = IptvTextSecondary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CheckboxBlock(block: MdBlock.Checkbox) {
    Row(
        modifier = Modifier.padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val checkColor = if (block.checked) IptvAccent else IptvTextMuted
        val checkmark = if (block.checked) "\u2611" else "\u2610"
        Text(
            checkmark,
            color = checkColor,
            fontSize = 17.sp,
            modifier = Modifier.padding(top = 1.dp),
        )
        Text(
            renderInlineMarkdown(block.text),
            color = if (block.checked) IptvTextSecondary else IptvTextMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NumberedBlock(block: MdBlock.NumberedItem) {
    Row(
        modifier = Modifier.padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "${block.number}.",
            color = IptvAccent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(24.dp).padding(top = 1.dp),
        )
        Text(
            renderInlineMarkdown(block.text),
            color = IptvTextSecondary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BlockquoteBlock(block: MdBlock.Blockquote) {
    Text(
        renderInlineMarkdown(block.text),
        color = IptvTextMuted,
        fontSize = 14.sp,
        fontStyle = FontStyle.Italic,
        lineHeight = 21.sp,
        modifier = Modifier
            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            .drawVerticalAccentLine(),
    )
}

@Composable
private fun CodeBlock(block: MdBlock.CodeBlock) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12121A), RoundedCornerShape(8.dp))
            .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(8.dp))
            .padding(16.dp),
    ) {
        if (block.code.contains("\n")) {
            val codeLines = block.code.split("\n")
            for (codeLine in codeLines) {
                Text(
                    codeLine.ifBlank { " " },
                    color = IptvAccent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }
        } else {
            Text(
                block.code,
                color = IptvAccent,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun HrBlock() {
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(IptvSurfaceVariant),
    )
    Spacer(Modifier.height(8.dp))
}

// ── Inline markdown renderer ───────────────────────────────────────────

private fun renderInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Bold **text**
                i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = IptvTextPrimary)) {
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
                                background = Color(0xFF22222E),
                                color = IptvAccent,
                                fontSize = 13.sp,
                            ),
                        ) {
                            append(" ")
                            append(text.substring(i + 1, end))
                            append(" ")
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
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = IptvTextSecondary)) {
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

private fun Modifier.drawVerticalAccentLine(): Modifier = this.drawBehind {
    drawLine(
        color = androidx.compose.ui.graphics.Color(0xFF00C3FF).copy(alpha = 0.5f),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(0f, size.height),
        strokeWidth = 3.dp.toPx(),
    )
}
