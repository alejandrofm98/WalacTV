package com.example.walactv.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

fun Modifier.tvClickable(onClick: () -> Unit): Modifier = this
    .clickable(onClick = onClick)
    .onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown &&
            (event.key == Key.Enter || event.key == Key.DirectionCenter)
        ) {
            onClick()
            true
        } else false
    }

