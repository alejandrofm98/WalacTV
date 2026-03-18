package com.example.walactv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.example.walactv.ui.theme.*

@Composable
fun FilterTopBar(
    showIdioma: Boolean,
    selectedIdioma: String,
    selectedGrupo: String,
    onIdiomaClicked: () -> Unit,
    onGrupoClicked: () -> Unit,
    idiomaFocusRequester: FocusRequester,
    grupoFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIdioma) {
            FilterTopBarButton(
                label = "Idioma: $selectedIdioma",
                onClick = onIdiomaClicked,
                focusRequester = idiomaFocusRequester
            )
        }
        FilterTopBarButton(
            label = "Grupo: $selectedGrupo",
            onClick = onGrupoClicked,
            focusRequester = grupoFocusRequester
        )
    }
}

@Composable
fun FilterTopBarButton(label: String, onClick: () -> Unit, focusRequester: FocusRequester) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor = if (isFocused) IptvFocusBg else IptvCard
    val borderColor = if (isFocused) IptvFocusBorder else IptvSurfaceVariant
    val contentColor = if (isFocused) IptvTextPrimary else IptvTextMuted

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = "$label ▾",
            color = contentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun FilterDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredOptions = remember(searchQuery, options) {
        if (searchQuery.isBlank()) {
            options
        } else {
            options.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .background(IptvSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(title, color = IptvTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

                // Search field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IptvCard, RoundedCornerShape(8.dp))
                        .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = IptvTextPrimary,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(IptvAccent),
                        decorationBox = { innerTextField ->
                            Box {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Buscar...",
                                        color = IptvTextMuted,
                                        fontSize = 15.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                if (filteredOptions.isEmpty()) {
                    Text(
                        "No hay resultados",
                        color = IptvTextMuted,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(filteredOptions, selectedOption) {
                        val index = filteredOptions.indexOf(selectedOption)
                        if (index > 0) {
                            listState.scrollToItem(index)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredOptions) { option ->
                            DialogFilterItem(
                                label = option,
                                selected = option == selectedOption,
                                onClick = { onOptionSelected(option) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DialogFilterItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor = when {
        isFocused -> IptvFocusBg
        selected -> IptvCard
        else -> Color.Transparent
    }
    val borderColor = when {
        isFocused -> IptvFocusBorder
        selected -> IptvSurfaceVariant
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .focusable()
            .onFocusChanged {
                isFocused = it.isFocused
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            color = if (isFocused || selected) IptvTextPrimary else IptvTextMuted,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
