package com.pablobertino.tagmap.ui.events

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pablobertino.tagmap.data.AppEvent
import com.pablobertino.tagmap.ui.EventsViewModel
import com.pablobertino.tagmap.ui.common.dateTimeText

/** Centro de notificaciones (spec §6.6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(onBack: () -> Unit, vm: EventsViewModel = viewModel(factory = EventsViewModel.Factory)) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eventos") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = vm::markAllRead) { Icon(Icons.Default.DoneAll, "Marcar leídos") }
                    IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Actualizar") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize()) {
                ui.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
                items(ui.events, key = { it.id }) { EventRow(it) }
                if (!ui.loading && ui.events.isEmpty()) {
                    item {
                        Text("Sin eventos todavía. Creá un lugar y activá 'Llega' o 'Sale' para algún tag.",
                            Modifier.padding(16.dp))
                    }
                }
            }
            if (ui.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun EventRow(e: AppEvent) {
    val title = if (e.isEntry) "${e.trackerName} llegó a ${e.placeName}" else "${e.trackerName} salió de ${e.placeName}"
    val detail = buildString {
        append(if (e.isEntry) "Detectado dentro del área a las " else "Última detección fuera del área a las ")
        append(e.observed.dateTimeText())
        e.distanceM?.let { append(" · a ${it.toInt()} m del centro") }
        if (e.status == "SUPPRESSED") append(" · sin aviso (${suppressLabel(e.suppressReason)})")
    }
    ListItem(
        leadingContent = {
            Icon(
                if (e.isEntry) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout, null,
                tint = if (e.isEntry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            )
        },
        headlineContent = { Text(title, fontWeight = if (e.unread) FontWeight.Bold else FontWeight.Normal) },
        supportingContent = { Text(detail) },
    )
}

private fun suppressLabel(r: String?) = when (r) {
    "cooldown" -> "muy seguido"; "muted" -> "silenciado"; "schedule" -> "fuera de horario"
    "quiet_hours" -> "horas de silencio"; "notify_disabled" -> "aviso apagado"; "no_devices" -> "sin dispositivo"
    "fcm_failed" -> "falló el envío"; else -> r ?: "-"
}
