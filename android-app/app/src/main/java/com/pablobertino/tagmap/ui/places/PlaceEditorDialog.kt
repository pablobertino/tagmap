package com.pablobertino.tagmap.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pablobertino.tagmap.data.Place
import com.pablobertino.tagmap.ui.common.parseHexColor

val PLACE_COLORS = listOf("#43A047", "#1E88E5", "#E53935", "#FB8C00", "#8E24AA", "#00ACC1", "#6D4C41", "#546E7A")

/** Alta/edición de lugar (spec §6.5): nombre, radio (100–2000 m), color. */
@Composable
fun PlaceEditorDialog(
    initial: Place?,
    lat: Double,
    lon: Double,
    onDismiss: () -> Unit,
    onSave: (name: String, radiusM: Int, color: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var radius by remember { mutableFloatStateOf((initial?.radiusM ?: 200).toFloat()) }
    var color by remember { mutableStateOf(initial?.color ?: PLACE_COLORS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo lugar" else "Editar lugar") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre (Casa, Colegio…)") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Text("Radio: ${radius.toInt()} m", style = MaterialTheme.typography.labelMedium)
                Slider(value = radius, onValueChange = { radius = it }, valueRange = 100f..2000f, steps = 18)
                Text(
                    "Mínimo 100 m: la red Find Hub tiene precisión variable. Para casas, 200–300 m evita falsas alarmas.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text("Color", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    PLACE_COLORS.forEach { c ->
                        Box(
                            Modifier.padding(4.dp).size(32.dp).background(parseHexColor(c), CircleShape)
                                .border(if (c == color) 3.dp else 0.dp, Color.White, CircleShape)
                                .clickable { color = c }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), radius.toInt(), color) }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
