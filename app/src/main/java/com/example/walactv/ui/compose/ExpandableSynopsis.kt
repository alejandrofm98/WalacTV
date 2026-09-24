package com.example.walactv.ui.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.example.walactv.R
import com.example.walactv.ui.theme.IptvTextAccent
import com.example.walactv.ui.theme.IptvTextMuted

@Composable
fun ExpandableSynopsis(
    text: String,
    collapsedMaxLines: Int,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    actionFocusRequester: FocusRequester? = null,
    onExpandableChanged: (Boolean) -> Unit = {},
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var canExpand by remember(text) { mutableStateOf(false) }
    var expandActionFocused by remember(text) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded && canExpand != result.hasVisualOverflow) {
                    canExpand = result.hasVisualOverflow
                    onExpandableChanged(canExpand)
                }
            },
        )

        if (canExpand) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(if (expanded) R.string.details_show_less else R.string.details_show_more),
                color = if (expandActionFocused) IptvTextAccent else IptvTextMuted,
                fontSize = fontSize * 0.9f,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .then(if (actionFocusRequester != null) Modifier.focusRequester(actionFocusRequester) else Modifier)
                    .onFocusChanged { expandActionFocused = it.isFocused }
                    .tvClickable { expanded = !expanded },
            )
        }
    }
}
