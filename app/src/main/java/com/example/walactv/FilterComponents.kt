package com.example.walactv

import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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

        Spacer(modifier = Modifier.weight(1f).focusable(false))

        // ── SearchBar con EditText nativo para soporte real de teclado / IME ──
        NativeSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            focusRequester = searchFocusRequester,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NativeSearchBar — usa un EditText real para que el IME de Android TV funcione
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NativeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    // Referencia al EditText nativo para poder manipularlo desde Compose
    var editTextRef by remember { mutableStateOf<EditText?>(null) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.FocusInteraction.Focus -> isFocused = true
                is androidx.compose.foundation.interaction.FocusInteraction.Unfocus -> isFocused = false
                else -> {}
            }
        }
    }

    Box(
        modifier = modifier
            .width(280.dp)
            .background(
                if (isFocused) IptvFocusBg else IptvCard,
                RoundedCornerShape(6.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) IptvFocusBorder else IptvSurfaceVariant,
                shape = RoundedCornerShape(6.dp)
            )
            .focusRequester(focusRequester)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { editTextRef?.requestFocus() }
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = "Buscar",
                tint = if (isFocused) IptvTextPrimary else IptvTextMuted,
                modifier = Modifier.size(14.dp),
            )

            // EditText nativo: soporta IME completo en Android TV
            AndroidView(
                factory = { context ->
                    EditText(context).apply {
                        hint = "Buscar..."
                        setHintTextColor(IptvTextMuted.copy(alpha = 0.7f).toArgb())
                        setTextColor(IptvTextPrimary.toArgb())
                        textSize = 12f
                        background = null          // quita el underline nativo
                        setSingleLine(true)
                        setLines(1)
                        minHeight = 0
                        minimumHeight = 0
                        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_FULLSCREEN
                        inputType = android.text.InputType.TYPE_CLASS_TEXT

                        setOnFocusChangeListener { _, hasFocus ->
                            isFocused = hasFocus
                            Log.d("FocusTrace", "NativeSearchBar EditText focus=$hasFocus")
                            if (hasFocus) {
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                            }
                        }

                        // Sincroniza texto hacia Compose mientras el usuario escribe
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                            override fun afterTextChanged(s: android.text.Editable?) {
                                val newText = s?.toString() ?: ""
                                Log.d("FocusTrace", "NativeSearchBar textChanged: '$newText'")
                                onQueryChange(newText)
                            }
                        })

                        // Acción "Buscar" del teclado cierra el IME
                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.hideSoftInputFromWindow(windowToken, 0)
                                true
                            } else false
                        }

                        // ESC / Back: borra el texto y cierra el IME
                        setOnKeyListener { v, keyCode, event ->
                            if (event.action == AndroidKeyEvent.ACTION_DOWN &&
                                keyCode == AndroidKeyEvent.KEYCODE_ESCAPE
                            ) {
                                setText("")
                                onQueryChange("")
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.hideSoftInputFromWindow(v.windowToken, 0)
                                true
                            } else false
                        }
                    }.also { editTextRef = it }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FilterTopBarButton — FIX: borde azul consistente con el resto de la app
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FilterTopBarButton(label: String, onClick: () -> Unit, focusRequester: FocusRequester) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.FocusInteraction.Focus -> isFocused = true
                is androidx.compose.foundation.interaction.FocusInteraction.Unfocus -> isFocused = false
                else -> {}
            }
        }
    }

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { state ->
                isFocused = state.isFocused
                Log.d("FocusTrace", "FilterTopBarButton[$label]: isFocused=${state.isFocused}")
            }
            .background(
                color = if (isFocused) IptvFocusBg else IptvCard,
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) IptvFocusBorder else IptvSurfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "$label \u25BE",
            color = if (isFocused) IptvTextPrimary else IptvTextMuted,
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SearchBar (legacy Compose-only, se mantiene por compatibilidad pero
// recomendamos usar NativeSearchBar en FilterTopBar)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    NativeSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        focusRequester = focusRequester,
        modifier = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// FilterDialog — sin cambios funcionales, solo limpieza menor
// ─────────────────────────────────────────────────────────────────────────────
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

    LaunchedEffect(Unit) {
        val idx = filteredOptions.indexOfFirst { it.value == selectedOption }
        if (idx > 0) {
            selectedIndex = idx
            listState.scrollToItem(idx)
        }
        focusRequester.requestFocus()
    }

    LaunchedEffect(selectedIndex) {
        listState.scrollToItem(selectedIndex)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
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
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NativeSearchBar — usa un EditText real para que el IME de Android TV funcione
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DialogFilterItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isFocused -> IptvFocusBg
                    selected -> IptvCard
                    else -> Color.Transparent
                },
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                when {
                    isFocused -> IptvFocusBorder
                    selected -> IptvSurfaceVariant
                    else -> Color.Transparent
                },
                RoundedCornerShape(8.dp)
            )
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