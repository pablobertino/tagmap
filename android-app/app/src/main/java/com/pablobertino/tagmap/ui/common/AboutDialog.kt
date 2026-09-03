package com.pablobertino.tagmap.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pablobertino.tagmap.BuildConfig
import com.pablobertino.tagmap.ui.theme.AppTheme

@Composable
fun AboutDialog(currentTheme: AppTheme, onTheme: (AppTheme) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TagMap ${BuildConfig.VERSION_NAME}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Apariencia", style = MaterialTheme.typography.labelMedium)
                Row {
                    AppTheme.entries.forEach { t ->
                        FilterChip(selected = t == currentTheme, onClick = { onTheme(t) }, label = { Text(t.label) },
                            modifier = Modifier.padding(end = 6.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Posiciones de tags Xiaomi vía Google Find Hub. No es ubicación en vivo: cada punto es la última " +
                        "detección de la red. Push: ${if (BuildConfig.HAS_FIREBASE) "activas" else "no configuradas"}.",
                    style = MaterialTheme.typography.bodySmall,
                )
                CHANGELOG.forEach { r ->
                    Spacer(Modifier.height(12.dp))
                    Text("${r.version} · ${r.date}", style = MaterialTheme.typography.titleSmall)
                    r.changes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
