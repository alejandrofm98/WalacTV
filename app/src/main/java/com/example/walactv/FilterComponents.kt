package com.example.walactv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.example.walactv.ui.theme.*

internal const val COUNTRY_FILTER_LABEL = "Pais"
internal const val COUNTRY_FILTER_DIALOG_TITLE = "Selecciona pais"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FilterTopBar(
    showIdioma: Boolean,
    selectedIdioma: String,
    selectedGrupo: String,
    onIdiomaClicked: () -> Unit,
    onGrupoClicked: () -> Unit,
    idiomaFocusRequester: FocusRequester,
    grupoFocusRequester: FocusRequester,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchFocusRequester: FocusRequester,
    idiomaLabel: String = "Idioma",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIdioma) {
            FilterTopBarButton(
                label = "$idiomaLabel: $selectedIdioma",
                onClick = onIdiomaClicked,
                focusRequester = idiomaFocusRequester
            )
        }
        FilterTopBarButton(
            label = "Grupo: $selectedGrupo",
            onClick = onGrupoClicked,
            focusRequester = grupoFocusRequester
        )

        Spacer(modifier = Modifier.weight(1f))

        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            focusRequester = searchFocusRequester
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor = if (isFocused) IptvFocusBg else IptvCard
    val borderColor = if (isFocused) IptvFocusBorder else IptvSurfaceVariant

    Box(
        modifier = modifier
            .width(220.dp)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = "Buscar",
                tint = if (isFocused) IptvTextPrimary else IptvTextMuted,
                modifier = Modifier.size(18.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Buscar...",
                        color = IptvTextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = IptvTextPrimary,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(IptvAccent),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box {
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FilterTopBarButton(label: String, onClick: () -> Unit, focusRequester: FocusRequester) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor = if (isFocused) IptvFocusBg else IptvCard
    val borderColor = if (isFocused) IptvFocusBorder else IptvSurfaceVariant
    val contentColor = if (isFocused) IptvTextPrimary else IptvTextMuted

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = "$label \u25BE",
            color = contentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FilterDialog(
    title: String,
    options: List<CatalogFilterOption>,
    selectedOption: String,
    onOptionSelected: (CatalogFilterOption) -> Unit,
    onDismiss: () -> Unit
) {
    val filteredOptions = remember(options) { options }

    var selectedIndex by remember {
        mutableStateOf(filteredOptions.indexOfFirst { it.value == selectedOption }.coerceAtLeast(0))
    }

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // Scroll to selected item when dialog opens
    LaunchedEffect(Unit) {
        val idx = filteredOptions.indexOfFirst { it.value == selectedOption }
        if (idx > 0) {
            selectedIndex = idx
            listState.scrollToItem(idx)
        }
        focusRequester.requestFocus()
    }

    // Auto-scroll when selectedIndex changes via D-pad
    LaunchedEffect(selectedIndex) {
        listState.scrollToItem(selectedIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        if (selectedIndex > 0) selectedIndex--
                        true
                    }
                    Key.DirectionDown -> {
                        if (selectedIndex < filteredOptions.size - 1) selectedIndex++
                        true
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        filteredOptions.getOrNull(selectedIndex)?.let { onOptionSelected(it) }
                        true
                    }
                    Key.Back, Key.Escape -> {
                        onDismiss()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .background(IptvSurface, RoundedCornerShape(12.dp))
                .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, color = IptvTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

            if (filteredOptions.isEmpty()) {
                Text(
                    "No hay opciones",
                    color = IptvTextMuted,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(filteredOptions) { index, option ->
                        val isHighlighted = index == selectedIndex
                        val bgColor = when {
                            isHighlighted -> IptvFocusBg
                            option.value == selectedOption -> IptvCard
                            else -> Color.Transparent
                        }
                        val borderClr = when {
                            isHighlighted -> IptvFocusBorder
                            option.value == selectedOption -> IptvSurfaceVariant
                            else -> Color.Transparent
                        }
                        val textColor = when {
                            isHighlighted -> IptvTextPrimary
                            option.value == selectedOption -> IptvTextPrimary
                            else -> IptvTextMuted
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bgColor, RoundedCornerShape(8.dp))
                                .border(1.dp, borderClr, RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Text(
                                option.label,
                                color = textColor,
                                fontSize = 16.sp,
                                fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Text(
                    "\u25B2\u25BC para navegar \u2022 OK para seleccionar",
                    color = IptvTextMuted.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
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
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
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
