package com.example.walactv.ui.compose

import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.walactv.data.remote.api.dto.FilterOptionDto
import com.example.walactv.ui.compose.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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



// ─────────────────────────────────────────────────────────────────────────────
// BackInterceptingEditText — captura Back/Esc ANTES de que el IME lo toque
// ─────────────────────────────────────────────────────────────────────────────
class BackInterceptingEditText(context: Context) : AppCompatEditText(context) {
    var onBackPressed: (() -> Unit)? = null

    override fun onKeyPreIme(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (event?.action == android.view.KeyEvent.ACTION_DOWN &&
            (keyCode == android.view.KeyEvent.KEYCODE_BACK || keyCode == android.view.KeyEvent.KEYCODE_ESCAPE)
        ) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            Log.d("FocusTrace", "NativeSearchBar onKeyPreIme: key=$keyCode isAcceptingText=${imm.isAcceptingText}")
            // Solo consumir si el IME está realmente abierto
            if (imm.isAcceptingText) {
                Log.d("FocusTrace", "NativeSearchBar onKeyPreIme: IME open -> consuming Back")
                onBackPressed?.invoke()
                return true
            }
            Log.d("FocusTrace", "NativeSearchBar onKeyPreIme: IME closed -> letting Back flow")
        }
        return super.onKeyPreIme(keyCode, event)
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
    onImeDismissed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    // Referencia al EditText nativo para poder manipularlo desde Compose
    var editTextRef by remember { mutableStateOf<EditText?>(null) }
    var editTextHasFocus by remember { mutableStateOf(false) }
    // Ref al query actual para que el TextWatcher pueda comparar en el closure
    val queryRef = remember { mutableStateOf(query) }
    queryRef.value = query
    // Debounce: evita doble onImeDismissed (Enter + layout listener)
    var lastImeDismissTime by remember { mutableStateOf(0L) }

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
                if (isFocused) IptvFocusBg else IptvBackground,
                RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) IptvFocusBorder else IptvSurfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionDown -> editTextHasFocus
                        Key.DirectionLeft, Key.DirectionRight -> editTextHasFocus
                        else -> false
                    }
                } else false
            }
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
                    BackInterceptingEditText(context).apply {
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
                            editTextHasFocus = hasFocus
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
                                val ref = queryRef.value
                                Log.d("FocusTrace", "NativeSearchBar textChanged: '$newText' queryRef='$ref' match=${newText == ref}")
                                if (newText == ref) return  // ya sincronizado, ignorar
                                Log.d("FocusTrace", "NativeSearchBar textChanged: PROPAGATING '$newText' to onQueryChange")
                                onQueryChange(newText)
                            }
                        })

                        // Acción "Buscar" del teclado cierra el IME
                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                                Log.d("FocusTrace", "NativeSearchBar EDITOR_ACTION_SEARCH query='$query'")
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.hideSoftInputFromWindow(windowToken, 0)
                                clearFocus()
                                Log.d("FocusTrace", "NativeSearchBar calling onImeDismissed() after SEARCH")
                                lastImeDismissTime = System.currentTimeMillis()
                                onImeDismissed()
                                true
                            } else false
                        }

                        // ESC / Back fallback (si onKeyPreIme no se dispara)
                        setOnKeyListener { _, keyCode, event ->
                            if (event.action == AndroidKeyEvent.ACTION_DOWN &&
                                (keyCode == AndroidKeyEvent.KEYCODE_ESCAPE ||
                                 keyCode == AndroidKeyEvent.KEYCODE_BACK)
                            ) {
                                Log.d("FocusTrace", "NativeSearchBar KEY fallback: key=$keyCode")
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.hideSoftInputFromWindow(windowToken, 0)
                                clearFocus()
                                onImeDismissed()
                                true
                            } else false
                        }

                        // Intercepta Back/Esc ANTES de que el IME lo toque
                        this.onBackPressed = {
                            Log.d("FocusTrace", "NativeSearchBar onBackPressed callback firing")
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(windowToken, 0)
                            clearFocus()
                            onImeDismissed()
                        }
                    }.also { editTextRef = it }
                },
                update = { editText ->
                    val current = editText.text.toString()
                    Log.d("FocusTrace", "NativeSearchBar UPDATE: current='$current' query='$query' match=${current == query}")
                    if (current != query) {
                        Log.d("FocusTrace", "NativeSearchBar UPDATE: setting text to '$query'")
                        editText.setText(query)
                    }
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
    val interactionSource = remember { MutableInteractionSource() }

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
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                color = if (isFocused) IptvTextPrimary else IptvTextMuted,
                fontSize = 13.sp,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = if (isFocused) IptvTextPrimary else IptvTextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FilterDialog — sin cambios funcionales, solo limpieza menor
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FilterDialog(
    title: String,
    options: List<FilterOptionDto>,
    selectedOption: String,
    onOptionSelected: (FilterOptionDto) -> Unit,
    onDismiss: () -> Unit
) {
    val filteredOptions = remember(options) { options }

    var selectedIndex by remember {
        mutableIntStateOf(filteredOptions.indexOfFirst { it.value == selectedOption }.coerceAtLeast(0))
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
