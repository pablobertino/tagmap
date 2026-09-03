package com.pablobertino.tagmap.ui.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Botón de capas para elegir el estilo del mapa. */
@Composable
fun MapStyleMenu(current: MapStyle, onSelect: (MapStyle) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Default.Layers, "Estilo de mapa") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        MapStyle.entries.forEach { s ->
            DropdownMenuItem(
                text = { Text(s.label) },
                onClick = { onSelect(s); open = false },
                trailingIcon = { if (s == current) Icon(Icons.Default.Check, null) },
            )
        }
    }
}
